import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

typedef PixivPageLoader<T> = Future<PixivPage<T>> Function({String? nextUrl});
typedef PixivListFilter<T> = List<T> Function(List<T> items);
typedef PixivIllustPageLoader = Future<PixivIllustPage> Function({String? nextUrl});
typedef PixivIllustListFilter = PixivListFilter<PixivIllust>;

/// How many consecutive fully-filtered pages to skip before giving up.
///
/// R18 / mute filters can zero out an API page while `next_url` remains —
/// without advancing, the grid shows empty and never scrolls into `loadMore`.
const pixivEmptyPageAdvanceLimit = 5;

/// Any paged Pixiv list — works, creators, novels, comments — kept in order
/// without duplicates as `next_url` pages arrive.
class PixivPagedListStore<T> extends Store<List<T>> {
  PixivPageLoader<T> _loader;
  PixivListFilter<T>? filter;

  /// What makes two items the same entry when pages overlap.
  final Object Function(T item) keyOf;

  String? _nextUrl;
  bool _loadingMore = false;
  bool _loadMoreFailed = false;
  Future<void>? _pendingMore;

  /// Bumped by every refresh and source swap; a page that lands for an older
  /// generation is dropped, so a slow page from the old ranking mode or a
  /// pre-refresh loadMore never lands in the new list.
  int _generation = 0;

  PixivPagedListStore(this._loader, {required this.keyOf, this.filter}) : super(const []);

  bool get hasMore => _nextUrl != null && _nextUrl!.isNotEmpty;
  bool get loadingMore => _loadingMore;

  /// Whether the last next page failed while the list kept what it had, so a pager can offer Retry.
  bool get loadMoreFailed => _loadMoreFailed;

  /// Swap the source (e.g. ranking mode) and clear the list.
  ///
  /// The clear is the point: leaving the old grid in state let a failed
  /// refresh on the new mode show the old mode's illusts under the new label.
  void useLoader(PixivPageLoader<T> loader) {
    _loader = loader;
    _nextUrl = null;
    _restart();
    update(const []);
  }

  int _restart() {
    _loadingMore = false;
    _loadMoreFailed = false;
    return ++_generation;
  }

  /// First load shows the store loading state; later pulls keep the grid up
  /// (Pixez-style soft refresh — no decode waterfall from a blank spinner).
  Future<void> refresh() async {
    final generation = _restart();
    bool current() => generation == _generation;
    if (state.isNotEmpty) {
      try {
        final page = await _loadVisiblePage();
        if (!current()) return;
        _nextUrl = page.nextUrl;
        update(page.items);
      } catch (_) {
        // Keep the healthy grid — soft refresh must never blank or stick.
        if (current()) update(state);
      }
      return;
    }

    await execute(() async {
      final page = await _loadVisiblePage();
      if (!current()) return state;
      _nextUrl = page.nextUrl;
      return page.items;
    });
  }

  /// The next page; while one is already loading, the same load, so every caller can wait for it.
  Future<void> loadMore() {
    if (_loadingMore) {
      return _pendingMore ?? Future.value();
    }
    if (!hasMore) {
      return Future.value();
    }
    return _pendingMore = _loadNextPage();
  }

  Future<void> _loadNextPage() async {
    final generation = _generation;
    _loadingMore = true;
    _loadMoreFailed = false;
    update(state);
    try {
      final page = await _loadVisiblePage(nextUrl: _nextUrl);
      if (generation != _generation) return;
      _nextUrl = page.nextUrl;
      update(mergePixivPage(state, page.items, keyOf));
    } catch (e) {
      if (generation != _generation) return;
      // Keep a healthy grid — only first-page failures become full errors.
      if (state.isEmpty) {
        setError(e);
      } else {
        _loadMoreFailed = true;
        update(state);
      }
    } finally {
      if (generation == _generation) {
        _loadingMore = false;
        if (state.isNotEmpty) {
          update(state);
        }
      }
    }
  }

  /// Fetches until a page has visible items, or pagination ends.
  Future<PixivPage<T>> _loadVisiblePage({String? nextUrl}) async {
    var cursor = nextUrl;
    for (var attempt = 0; attempt < pixivEmptyPageAdvanceLimit; attempt++) {
      final page = cursor == null || cursor.isEmpty ? await _loader() : await _loader(nextUrl: cursor);
      final visible = _applyFilter(page.items);
      final exhausted = page.nextUrl == null || page.nextUrl!.isEmpty;
      if (visible.isNotEmpty || exhausted) {
        return PixivPage(visible, nextUrl: page.nextUrl);
      }
      cursor = page.nextUrl;
    }

    final page = await _loader(nextUrl: cursor);
    return PixivPage(_applyFilter(page.items), nextUrl: page.nextUrl);
  }

  List<T> _applyFilter(List<T> items) {
    return filter == null ? items : filter!(items);
  }
}

/// Paginated illust list — following, ranking, bookmarks, search, related.
class PixivIllustListStore extends PixivPagedListStore<PixivIllust> {
  PixivIllustListStore(super.loader, {super.filter}) : super(keyOf: _illustId);
}

int _illustId(PixivIllust illust) => illust.id;

/// Append [incoming] skipping keys already in [existing].
List<T> mergePixivPage<T>(List<T> existing, List<T> incoming, Object Function(T item) keyOf) {
  if (incoming.isEmpty) {
    return existing;
  }
  final seen = {for (final item in existing) keyOf(item)};
  return [
    ...existing,
    for (final item in incoming)
      if (seen.add(keyOf(item))) item,
  ];
}

/// Append [incoming] skipping ids already in [existing].
List<PixivIllust> mergePixivIllusts(List<PixivIllust> existing, List<PixivIllust> incoming) =>
    mergePixivPage(existing, incoming, _illustId);

/// Following-timeline store kept for the plugin home tab and uninstall wipe.
class PixivFeedStore extends PixivIllustListStore {
  PixivFeedStore(PixivClient client, {PixivIllustListFilter? filter})
    : super(({nextUrl}) => client.following(nextUrl: nextUrl), filter: filter);
}
