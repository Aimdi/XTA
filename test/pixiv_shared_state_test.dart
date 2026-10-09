import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_search_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_view_state.dart';

PrefServiceCache _prefs([Map<String, Object> cache = const {}]) => PrefServiceCache(
  cache: {
    optionPluginPixivMutedAuthors: '[]',
    optionPluginPixivMutedTags: '[]',
    optionPluginPixivMutedIllusts: '[]',
    optionPluginPixivMutedComments: '[]',
    optionPluginPixivMutedNovels: '[]',
    optionPluginPixivSearchHistory: '[]',
    ...cache,
  },
);

void main() {
  group('comment and novel mutes', () {
    test('are saved, reloaded and removed like the other mutes', () async {
      final prefs = _prefs();
      final store = PixivMuteStore(prefs);
      addTearDown(store.destroy);

      await store.muteComment(31);
      await store.muteNovel(77);
      expect(jsonDecode(prefs.get<String>(optionPluginPixivMutedComments)!), [31]);
      expect(jsonDecode(prefs.get<String>(optionPluginPixivMutedNovels)!), [77]);

      final reloaded = PixivMuteStore(prefs);
      addTearDown(reloaded.destroy);
      await reloaded.load();
      expect((reloaded.state.isCommentMuted(31), reloaded.state.isNovelMuted(77)), (true, true));
      expect(reloaded.state.isEmpty, isFalse);

      await reloaded.unmuteComment(31);
      await reloaded.unmuteNovel(77);
      expect(reloaded.state.isEmpty, isTrue);
      expect(prefs.get<String>(optionPluginPixivMutedComments), '[]');
    });

    test('a damaged pref reads as nothing muted', () async {
      final store = PixivMuteStore(
        _prefs({optionPluginPixivMutedComments: 'not json', optionPluginPixivMutedNovels: '{}'}),
      );
      addTearDown(store.destroy);
      await store.load();
      expect(store.state.isEmpty, isTrue);
    });

    test('copyWith keeps every set it was not asked to change', () {
      const state = PixivMuteState(authorIds: {1}, commentIds: {2}, novelIds: {3});
      final next = state.copyWith(tags: {'cat'});
      expect(
        [next.authorIds, next.commentIds, next.novelIds, next.tags],
        [
          {1},
          {2},
          {3},
          {'cat'},
        ],
      );
    });
  });

  group('view state', () {
    test('defaults to today\'s Home, Following and bookmarks', () {
      const view = PixivViewState();
      expect(view.homeSource, PixivHomeSource.following);
      expect(view.followRestrict, 'all');
      expect(view.bookmarkTag, isNull);
    });

    test('copyWith sets and clears the bookmark tag and keeps the rest', () {
      final tagged = const PixivViewState(section: 2).copyWith(bookmarkTag: 'Favs', followRestrict: 'private');
      expect((tagged.section, tagged.bookmarkTag, tagged.followRestrict), (2, 'Favs', 'private'));
      expect(tagged.copyWith(clearBookmarkTag: true).bookmarkTag, isNull);
      expect(tagged.copyWith(homeSource: PixivHomeSource.recommended).bookmarkTag, 'Favs');
    });
  });

  group('search history', () {
    test('keeps what the old Pixiv history stored under the same pref', () {
      final prefs = _prefs({
        optionPluginPixivSearchHistory: jsonEncode(['初音ミク', 'landscape']),
      });
      final history = pixivSearchHistory(prefs);
      addTearDown(history.destroy);
      expect(history.state, ['初音ミク', 'landscape']);
    });

    test('a search differing only in case replaces the older one', () async {
      final prefs = _prefs({
        optionPluginPixivSearchHistory: jsonEncode(['Landscape', 'cat']),
      });
      final history = pixivSearchHistory(prefs);
      addTearDown(history.destroy);

      await history.remember('landscape');
      expect(history.state, ['landscape', 'cat']);
      await history.forget('cat');
      expect(jsonDecode(prefs.get<String>(optionPluginPixivSearchHistory)!), ['landscape']);
    });
  });
}
