import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

/// Session overrides for which illusts the reader has bookmarked, and which
/// bookmark writes are still on their way.
///
/// Pixiv's `is_bookmarked` on each card is the source of truth until a write;
/// this store only remembers what changed in this session so the grid and the
/// viewer stay in step. Writes go through `PixivBookmarkActions`.
class PixivBookmarkStore extends Store<Map<int, bool>> {
  PixivBookmarkStore() : super(const {});

  final _busy = <int>{};

  bool isBookmarked(PixivIllust illust) => state[illust.id] ?? illust.isBookmarked;

  int bookmarkCount(PixivIllust illust) {
    final bookmarked = isBookmarked(illust);
    if (bookmarked == illust.isBookmarked) {
      return illust.totalBookmarks;
    }
    if (bookmarked) {
      return illust.totalBookmarks + 1;
    }
    return (illust.totalBookmarks - 1).clamp(0, 1 << 30);
  }

  /// Whether a write for [illustId] is on its way, so a second tap waits.
  bool isBusy(int illustId) => _busy.contains(illustId);

  /// Records that a write left [illustId] bookmarked or not.
  void mark(int illustId, bool bookmarked) => update({...state, illustId: bookmarked});

  /// Runs [write] for [illustId] unless one already is; null when skipped.
  Future<T?> exclusive<T>(int illustId, Future<T> Function() write) async {
    if (!_busy.add(illustId)) return null;
    update(state, force: true);
    try {
      return await write();
    } finally {
      _busy.remove(illustId);
      update(state, force: true);
    }
  }
}
