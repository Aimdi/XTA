import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_search_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_search_filters.dart';
import 'package:xta/plugins/pixiv/pixiv_search_store.dart';
import 'package:xta/plugins/pixiv/pixiv_links.dart';

import 'support/pixiv_novel_fakes.dart';
import 'support/pixiv_search_fakes.dart';
import 'support/pixiv_social_fakes.dart';

const _novels = PixivSearchKind.novels;

void main() {
  group('novel filters', () {
    test('offer the novel places to look, and popular only to Premium', () {
      expect(_novels.targets, [
        PixivSearchTarget.partialTags,
        PixivSearchTarget.exactTags,
        PixivSearchTarget.text,
        PixivSearchTarget.keyword,
      ]);
      expect(pixivSearchSorts(isPremium: false, kind: _novels), [PixivSearchSort.newest, PixivSearchSort.oldest]);
      expect(pixivSearchSorts(isPremium: true, kind: _novels), [
        PixivSearchSort.newest,
        PixivSearchSort.oldest,
        PixivSearchSort.popular,
      ]);
    });

    test('the novel query takes no AI or bookmark parameters', () {
      final filter = const PixivSearchFilter(
        target: PixivSearchTarget.keyword,
        sort: PixivSearchSort.popular,
        hideAi: true,
      ).withBookmarks((min: 50, max: 99)).withDates(PixivDatePreset.day);
      final query = pixivSearchQuery(filter, ' rain ', now: DateTime(2026, 10, 10), kind: _novels);

      expect(query, {
        'word': 'rain',
        'search_target': 'keyword',
        'sort': 'popular_desc',
        'start_date': '2026-10-09',
        'end_date': '2026-10-10',
        'merge_plain_keyword_results': 'true',
        'filter': 'for_android',
      });
    });

    test('a filter carried over keeps only what novels take', () {
      final carried = const PixivSearchFilter(
        target: PixivSearchTarget.titleCaption,
        sort: PixivSearchSort.popularMale,
        ugoira: PixivUgoiraFilter.only,
        hideAi: true,
      ).withBookmarks((min: 50, max: 99)).withUsersIri(500);
      final premium = carried.forAccount(isPremium: true, kind: _novels);
      expect(
        (premium.target, premium.sort, premium.ugoira, premium.bookmarks, premium.usersIri, premium.hideAi),
        (PixivSearchTarget.partialTags, PixivSearchSort.newest, PixivUgoiraFilter.all, null, 500, true),
      );

      final popular = const PixivSearchFilter(sort: PixivSearchSort.popular, target: PixivSearchTarget.text);
      expect(popular.forAccount(isPremium: false, kind: _novels).sort, PixivSearchSort.newest);
      expect(popular.forAccount(isPremium: true, kind: _novels), popular);
      expect(
        const PixivSearchFilter(sort: PixivSearchSort.oldest).forAccount(isPremium: false, kind: _novels).sort,
        PixivSearchSort.oldest,
        reason: 'oldest first is free for novels',
      );
    });

    test('novels remember their own filter apart from the works\'', () async {
      final prefs = PrefServiceCache(
        cache: {optionPluginPixivSearchFilters: '', optionPluginPixivNovelSearchFilters: ''},
      );
      await savePixivSearchFilter(prefs, const PixivSearchFilter(target: PixivSearchTarget.text), kind: _novels);

      expect(readPixivSearchFilter(prefs), isNull);
      expect(readPixivSearchFilter(prefs, kind: _novels)?.target, PixivSearchTarget.text);
      expect(pixivStartingFilter(prefs, isPremium: false, kind: _novels).target, PixivSearchTarget.text);
      expect(jsonDecode(prefs.get<String>(optionPluginPixivNovelSearchFilters)!)['target'], 'text');
    });

    test('a pasted number opens a novel; a link opens what it names', () {
      expect(pixivNovelSearchLink(' 4321 '), isA<PixivNovelLinkRef>().having((ref) => ref.id, 'id', 4321));
      expect(pixivNovelSearchLink('https://www.pixiv.net/users/9'), isA<PixivUserLinkRef>());
      expect(pixivNovelSearchLink('rain'), isNull);
    });
  });

  group('novel search store', () {
    late PrefServiceCache prefs;
    late FakePixivSearchApi api;
    late FakePixivNovelApi novels;
    late PixivNovelSearchHistory history;
    late PixivMuteStore mute;

    setUp(() {
      prefs = PrefServiceCache(
        cache: {
          optionPluginPixivSearchHistory: '[]',
          optionPluginPixivNovelSearchHistory: '[]',
          optionPluginPixivSearchFilters: '',
          optionPluginPixivNovelSearchFilters: '',
          optionPluginPixivMutedAuthors: '[]',
          optionPluginPixivMutedTags: '[]',
          optionPluginPixivMutedIllusts: '[]',
          optionPluginPixivMutedNovels: '[]',
        },
      );
      api = FakePixivSearchApi(client: PixivClient(prefs), creatorsFound: [pixivPreviewOf(5)]);
      novels = FakePixivNovelApi(
        PixivClient(prefs),
        searchNovels: [
          pixivNovel(id: 1, title: 'Rain'),
          pixivNovel(id: 2, title: 'Muted'),
        ],
        trending: const [PixivTrendTag(name: '恋愛')],
      );
      history = PixivNovelSearchHistory(prefs);
      mute = PixivMuteStore(prefs);
    });

    tearDown(() async {
      await history.destroy();
      await mute.destroy();
    });

    PixivSearchStore build() {
      final store = PixivSearchStore(
        api: api,
        novelApi: novels,
        kind: _novels,
        prefs: prefs,
        mute: mute,
        history: history,
        debounce: Duration.zero,
        clock: () => DateTime(2026, 10, 10),
      );
      addTearDown(store.destroy);
      return store;
    }

    test('a search finds novels and creators and keeps the word in the novel history', () async {
      await mute.muteNovel(2);
      final store = build();
      await store.search(' rain ');

      expect(novels.calls, ['search:rain']);
      expect(novels.queries.single['search_target'], 'partial_match_for_tags');
      expect(api.calls, contains('users:rain'));
      expect(api.calls.where((call) => call.startsWith('illusts') || call.startsWith('preview')), isEmpty);
      expect([for (final novel in store.novels.state) novel.id], [1], reason: 'a muted novel stays out');
      expect([for (final preview in store.users.state) preview.user.id], [5]);
      expect(history.state, ['rain']);
      expect(prefs.get<String>(optionPluginPixivSearchHistory), '[]');
    });

    test('a filter change searches again and is remembered under the novel key', () async {
      final store = build();
      await store.search('rain');
      await store.applyFilter(store.state.filter.copyWith(target: PixivSearchTarget.keyword), remember: true);

      expect(novels.queries.last['search_target'], 'keyword');
      expect(jsonDecode(prefs.get<String>(optionPluginPixivNovelSearchFilters)!)['target'], 'keyword');
      expect(prefs.get<String>(optionPluginPixivSearchFilters), '');
    });

    test('the landing loads the novel trending tags and no suggested creators', () async {
      final store = build();
      await store.loadLanding();

      expect(store.trending.state.single.name, '恋愛');
      expect(novels.calls, ['trending']);
      expect(api.calls, isNot(contains('creators')));
      expect(api.calls, isNot(contains('trending')));
    });
  });
}
