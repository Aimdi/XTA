import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

/// Session overrides for which illusts the reader has bookmarked.
///
/// Pixiv's `is_bookmarked` on each card is the source of truth until a tap;
/// this store only remembers what changed in this session so the grid and the
/// viewer stay in step.
class PixivBookmarkStore extends Store<Map<int, bool>> {
  PixivBookmarkStore() : super(const {});

  bool isBookmarked(PixivIllust illust) =>
      state[illust.id] ?? illust.isBookmarked;

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

  Future<void> toggle(PixivClient client, PixivIllust illust) async {
    final next = !isBookmarked(illust);
    if (next) {
      await client.addBookmark(illust.id);
    } else {
      await client.deleteBookmark(illust.id);
    }
    update({...state, illust.id: next});
  }
}
