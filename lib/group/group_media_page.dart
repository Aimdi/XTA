import 'package:xta/client/client.dart';
import 'package:xta/profile/media_grid/media_grid_items/media_grid_item.dart';
import 'package:xta/utils/paging.dart';

/// Sentinel next-cursor after a first media page seeded from disk preview.
///
/// The next request must not treat this as an X / SQLite cursor: if the tweet
/// list has loaded since, reuse those chains; otherwise share the list's
/// in-flight first page instead of starting another Search fan-out.
const String groupMediaPreviewContinueCursor = 'preview-continue';

/// Coalesces concurrent first-page loads into one [fetch].
///
/// The group tweet list and the image tab both used to call `_listTweets(null)`
/// on open. With several subscription chunks that is two Search fan-outs at
/// once — enough to 429 the media endpoint on a 37-subscription group.
class SharedAsyncLoad<T> {
  Future<T>? _inFlight;

  Future<T> load(Future<T> Function() fetch) {
    return _inFlight ??= fetch().whenComplete(() {
      _inFlight = null;
    });
  }

  bool get isLoading => _inFlight != null;
}

/// First media-grid page taken from tweets already in memory.
///
/// `null` means nothing is loaded yet — the caller should fetch.
/// A returned page must not trigger a first-page Search fan-out.
CursorPage<String, MediaGridItem>? seedGroupMediaPage({
  required List<TweetChain>? loadedChains,
  required List<TweetChain>? previewChains,
  required String? feedNextCursor,
  required List<MediaGridItem> Function(List<TweetChain> chains) itemsOf,
}) {
  if (loadedChains != null) {
    return (items: itemsOf(loadedChains), nextCursor: feedNextCursor);
  }
  if (previewChains == null || previewChains.isEmpty) {
    return null;
  }
  final items = itemsOf(previewChains);
  if (items.isEmpty) {
    return null;
  }
  return (items: items, nextCursor: groupMediaPreviewContinueCursor);
}

/// Reuses in-memory tweets for the first media page, or for the page after a
/// preview seed. Real pagination cursors return `null` so the caller fetches.
CursorPage<String, MediaGridItem>? reuseGroupMediaPage({
  required String? cursor,
  required List<TweetChain>? loadedChains,
  required List<TweetChain>? previewChains,
  required String? feedNextCursor,
  required List<MediaGridItem> Function(List<TweetChain> chains) itemsOf,
}) {
  if (cursor != null && cursor != groupMediaPreviewContinueCursor) {
    return null;
  }
  return seedGroupMediaPage(
    loadedChains: loadedChains,
    previewChains: cursor == null ? previewChains : null,
    feedNextCursor: feedNextCursor,
    itemsOf: itemsOf,
  );
}

/// Loads one group media-grid page, preferring tweets already in memory.
Future<CursorPage<String, MediaGridItem>> groupMediaPage({
  required String? cursor,
  required List<TweetChain>? loadedChains,
  required List<TweetChain>? previewChains,
  required String? feedNextCursor,
  required Future<ChainPage> Function(String? cursor) fetch,
  required List<MediaGridItem> Function(List<TweetChain> chains) itemsOf,
}) async {
  final reused = reuseGroupMediaPage(
    cursor: cursor,
    loadedChains: loadedChains,
    previewChains: previewChains,
    feedNextCursor: feedNextCursor,
    itemsOf: itemsOf,
  );
  if (reused != null &&
      (reused.items.isNotEmpty || reused.nextCursor == null)) {
    return reused;
  }

  final next = reused?.nextCursor ?? cursor;
  final fetchCursor = next == groupMediaPreviewContinueCursor ? null : next;
  return mediaPageWithLookahead(fetchCursor, fetch, itemsOf, maxLookahead: 1);
}
