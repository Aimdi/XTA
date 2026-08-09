import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

typedef PixivIllustPageLoader = Future<PixivIllustPage> Function({String? nextUrl});
typedef PixivIllustListFilter = List<PixivIllust> Function(List<PixivIllust> illusts);

/// How many consecutive fully-filtered pages to skip before giving up.
///
/// R18 / mute filters can zero out an API page while `next_url` remains —
/// without advancing, the grid shows empty and never scrolls into `loadMore`.
const pixivEmptyPageAdvanceLimit = 5;

/// Paginated illust list — following, ranking, bookmarks, search, related.
class PixivIllustListStore extends Store<List<PixivIllust>> {
  PixivIllustPageLoader _loader;
  PixivIllustListFilter? filter;

  String? _nextUrl;
  bool _loadingMore = false;

  PixivIllustListStore(this._loader, {this.filter}) : super(const []);

  bool get hasMore => _nextUrl != null && _nextUrl!.isNotEmpty;
  bool get loadingMore => _loadingMore;

  /// Swap the source (e.g. ranking mode) and clear the list.
  ///
  /// The clear is the point: leaving the old grid in state let a failed
  /// refresh on the new mode show the old mode's illusts under the new label.
  void useLoader(PixivIllustPageLoader loader) {
    _loader = loader;
    _nextUrl = null;
    update(const []);
  }

  /// First load shows the store loading state; later pulls keep the grid up
  /// (Pixez-style soft refresh — no decode waterfall from a blank spinner).
  Future<void> refresh() async {
    if (state.isNotEmpty) {
      try {
        final page = await _loadVisiblePage();
        _nextUrl = page.nextUrl;
        update(page.illusts);
      } catch (_) {
        // Keep the healthy grid — soft refresh must never blank or stick.
        update(state);
      }
      return;
    }

    await execute(() async {
      final page = await _loadVisiblePage();
      _nextUrl = page.nextUrl;
      return page.illusts;
    });
  }

  Future<void> loadMore() async {
    if (_loadingMore || !hasMore) {
      return;
    }
    _loadingMore = true;
    update(state);
    try {
      final page = await _loadVisiblePage(nextUrl: _nextUrl);
      _nextUrl = page.nextUrl;
      update(mergePixivIllusts(state, page.illusts));
    } catch (e) {
      // Keep a healthy grid — only first-page failures become full errors.
      if (state.isEmpty) {
        setError(e);
      } else {
        update(state);
      }
    } finally {
      _loadingMore = false;
      if (state.isNotEmpty) {
        update(state);
      }
    }
  }

  /// Fetches until a page has visible illusts, or pagination ends.
  Future<PixivIllustPage> _loadVisiblePage({String? nextUrl}) async {
    var cursor = nextUrl;
    for (var attempt = 0; attempt < pixivEmptyPageAdvanceLimit; attempt++) {
      final page = cursor == null || cursor.isEmpty ? await _loader() : await _loader(nextUrl: cursor);
      final visible = _applyFilter(page.illusts);
      final exhausted = page.nextUrl == null || page.nextUrl!.isEmpty;
      if (visible.isNotEmpty || exhausted) {
        return PixivIllustPage(illusts: visible, nextUrl: page.nextUrl);
      }
      cursor = page.nextUrl;
    }

    final page = await _loader(nextUrl: cursor);
    return PixivIllustPage(illusts: _applyFilter(page.illusts), nextUrl: page.nextUrl);
  }

  List<PixivIllust> _applyFilter(List<PixivIllust> illusts) {
    return filter == null ? illusts : filter!(illusts);
  }
}

/// Append [incoming] skipping ids already in [existing].
List<PixivIllust> mergePixivIllusts(List<PixivIllust> existing, List<PixivIllust> incoming) {
  if (incoming.isEmpty) {
    return existing;
  }
  final seen = {for (final illust in existing) illust.id};
  return [
    ...existing,
    for (final illust in incoming)
      if (seen.add(illust.id)) illust,
  ];
}

/// Following-timeline store kept for the plugin home tab and uninstall wipe.
class PixivFeedStore extends PixivIllustListStore {
  PixivFeedStore(PixivClient client, {PixivIllustListFilter? filter})
    : super(({nextUrl}) => client.following(nextUrl: nextUrl), filter: filter);
}
