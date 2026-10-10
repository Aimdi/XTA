import 'package:flutter/widgets.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_actions.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_store.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_api.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_models.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';

int _novelId(PixivNovel novel) => novel.id;

/// A paged novel list — recommended, following, rankings, bookmarks.
class PixivNovelListStore extends PixivPagedListStore<PixivNovel> {
  PixivNovelListStore(super.loader, {super.filter}) : super(keyOf: _novelId);
}

/// The novels' bookmark overrides for the session, app-wide like the
/// illustrations' so every list and screen showing a novel agrees.
class PixivNovelBookmarkStore extends PixivBookmarkOverrides {
  bool isBookmarked(PixivNovel novel) => bookmarkedOr(novel.id, sent: novel.isBookmarked);

  int bookmarkCount(PixivNovel novel) => countOf(novel.id, sent: novel.isBookmarked, total: novel.totalBookmarks);
}

/// Every novel bookmark write. A tap bookmarks with the reader's default
/// visibility or removes the bookmark; a long press always files it privately.
class PixivNovelBookmarkActions {
  final PixivNovelApi api;
  final PixivNovelBookmarkStore bookmarks;
  final BasePrefService prefs;

  const PixivNovelBookmarkActions({required this.api, required this.bookmarks, required this.prefs});

  factory PixivNovelBookmarkActions.of(BuildContext context) => PixivNovelBookmarkActions(
    api: PixivNovelApi.of(context),
    bookmarks: context.read<PixivNovelBookmarkStore>(),
    prefs: PrefService.of(context, listen: false),
  );

  Future<bool> _add(PixivNovel novel, String restrict) async {
    await api.addBookmark(novel.id, restrict: restrict);
    bookmarks.mark(novel.id, true);
    return true;
  }

  Future<bool> _remove(PixivNovel novel) async {
    await api.deleteBookmark(novel.id);
    bookmarks.mark(novel.id, false);
    return false;
  }

  /// Where the novel's bookmark now stands; null when a write for it was already on its way.
  Future<bool?> toggle(PixivNovel novel) => bookmarks.exclusive(
    novel.id,
    () => bookmarks.isBookmarked(novel) ? _remove(novel) : _add(novel, pixivDefaultBookmarkRestrict(prefs)),
  );

  /// Bookmarks privately, or turns a public bookmark private.
  Future<bool?> bookmarkPrivately(PixivNovel novel) => bookmarks.exclusive(novel.id, () => _add(novel, 'private'));
}
