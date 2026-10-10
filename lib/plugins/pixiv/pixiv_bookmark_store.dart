import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

/// Session overrides for which works, by id, the reader has bookmarked, and
/// which bookmark writes are still on their way.
///
/// Pixiv's `is_bookmarked` on each card is the source of truth until a write;
/// this store only remembers what changed in this session so every list and
/// screen showing the work stays in step. Illustrations and novels each have
/// their own, since their ids are separate.
class PixivBookmarkOverrides extends Store<Map<int, bool>> {
  PixivBookmarkOverrides() : super(const {});

  final _busy = <int>{};

  /// Whether work [id] is bookmarked, given what its card was [sent].
  bool bookmarkedOr(int id, {required bool sent}) => state[id] ?? sent;

  /// [total] as Pixiv sent it, moved by one when this session changed the bookmark.
  int countOf(int id, {required bool sent, required int total}) {
    final bookmarked = bookmarkedOr(id, sent: sent);
    if (bookmarked == sent) {
      return total;
    }
    if (bookmarked) {
      return total + 1;
    }
    return (total - 1).clamp(0, 1 << 30);
  }

  /// Whether a write for [id] is on its way, so a second tap waits.
  bool isBusy(int id) => _busy.contains(id);

  /// Records that a write left [id] bookmarked or not.
  void mark(int id, bool bookmarked) => update({...state, id: bookmarked});

  /// Runs [write] for [id] unless one already is; null when skipped.
  Future<T?> exclusive<T>(int id, Future<T> Function() write) async {
    if (!_busy.add(id)) return null;
    update(state, force: true);
    try {
      return await write();
    } finally {
      _busy.remove(id);
      update(state, force: true);
    }
  }
}

/// The illustrations' overrides. Writes go through `PixivBookmarkActions`.
class PixivBookmarkStore extends PixivBookmarkOverrides {
  bool isBookmarked(PixivIllust illust) => bookmarkedOr(illust.id, sent: illust.isBookmarked);

  int bookmarkCount(PixivIllust illust) => countOf(illust.id, sent: illust.isBookmarked, total: illust.totalBookmarks);
}
