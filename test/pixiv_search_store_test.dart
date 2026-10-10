import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_search_filters.dart';
import 'package:xta/plugins/pixiv/pixiv_search_store.dart';
import 'package:xta/plugins/plugin_search_history.dart';

import 'support/pixiv_reader_harness.dart';
import 'support/pixiv_search_fakes.dart';

void main() {
  late PrefServiceCache prefs;
  late FakePixivSearchApi api;
  late PluginSearchHistoryStore history;
  late PixivMuteStore mute;

  setUp(() {
    prefs = PrefServiceCache(
      cache: {
        optionPluginPixivSearchHistory: '[]',
        optionPluginPixivSearchFilters: '',
        optionPluginPixivMutedAuthors: '[]',
        optionPluginPixivMutedTags: '[]',
        optionPluginPixivMutedIllusts: '[]',
      },
    );
    api = FakePixivSearchApi(
      client: PixivClient(prefs),
      works: [
        pixivWork(id: 1, type: 'illust'),
        pixivWork(id: 2, type: 'ugoira'),
      ],
      preview: [pixivWork(id: 9, type: 'illust')],
    );
    history = PluginSearchHistoryStore(prefs, optionPluginPixivSearchHistory);
    mute = PixivMuteStore(prefs);
  });

  tearDown(() async {
    await history.destroy();
    await mute.destroy();
  });

  PixivSearchStore build({bool illustsOnly = false}) {
    final store = PixivSearchStore(
      api: api,
      prefs: prefs,
      mute: mute,
      history: history,
      illustsOnly: illustsOnly,
      debounce: Duration.zero,
      clock: () => DateTime(2026, 10, 10),
    );
    addTearDown(store.destroy);
    return store;
  }

  List<int> ids(List<PixivIllust> illusts) => [for (final illust in illusts) illust.id];

  test('a search keeps what was typed and sends the popularity tag only to Pixiv', () async {
    final store = build();
    await store.applyFilter(store.state.filter.withUsersIri(1000));
    await store.search('  miku ');

    expect(store.state.word, 'miku');
    expect(history.state, ['miku']);
    expect(api.queries.single['word'], 'miku 1000users入り');
    expect(api.calls, containsAll(['illusts:miku 1000users入り', 'users:miku', 'preview:miku']));
    expect(ids(store.results.state), [1, 2]);
    expect(ids(store.state.popular), [9], reason: 'date-sorted results keep the preview strip');
  });

  test('popular without Premium fills the grid from the free preview', () async {
    final store = build();
    await store.applyFilter(const PixivSearchFilter(sort: PixivSearchSort.popular));
    await store.search('miku');

    expect(store.state.previewMode, isTrue);
    expect(ids(store.results.state), [9]);
    expect(api.calls.where((call) => call.startsWith('illusts')), isEmpty);
    expect(store.state.showsPopularStrip, isFalse);
  });

  test('popular with Premium asks the real search for popular_desc', () async {
    api.premium = true;
    final store = build();
    await store.applyFilter(const PixivSearchFilter(sort: PixivSearchSort.popular));
    await store.search('miku');

    expect(store.state.previewMode, isFalse);
    expect(api.queries.single['sort'], 'popular_desc');
    expect(store.state.popular, isEmpty);
  });

  test('Remember keeps the filter for the next search screen; turning it off forgets it', () async {
    final store = build();
    await store.applyFilter(const PixivSearchFilter(target: PixivSearchTarget.exactTags), remember: true);
    expect(jsonDecode(prefs.get<String>(optionPluginPixivSearchFilters)!)['target'], 'exact_match_for_tags');

    final next = build();
    expect((next.state.filter.target, next.state.remembered), (PixivSearchTarget.exactTags, true));

    await next.applyFilter(next.state.filter.copyWith(ugoira: PixivUgoiraFilter.only));
    expect(jsonDecode(prefs.get<String>(optionPluginPixivSearchFilters)!)['ugoira'], 'only');
    await next.applyFilter(next.state.filter, remember: false);
    expect(prefs.get<String>(optionPluginPixivSearchFilters), '');
  });

  test('a remembered Premium order falls back to newest after Premium ends', () async {
    await savePixivSearchFilter(prefs, const PixivSearchFilter(sort: PixivSearchSort.popularMale));
    expect(build().state.filter.sort, PixivSearchSort.newest);
    api.premium = true;
    expect(build().state.filter.sort, PixivSearchSort.popularMale);
  });

  test('a new search hides AI works exactly when the feeds do', () async {
    await prefs.set(optionPluginPixivHideAi, true);
    expect(build().state.filter.hideAi, isTrue);
  });

  test('the ugoira choice filters loaded works on the device', () async {
    final store = build();
    await store.applyFilter(store.state.filter.copyWith(ugoira: PixivUgoiraFilter.none));
    await store.search('miku');
    expect(ids(store.results.state), [1]);

    await store.applyFilter(store.state.filter.copyWith(ugoira: PixivUgoiraFilter.only));
    expect(ids(store.results.state), [2]);
  });

  test('suggestions complete the last word; a pick replaces it or searches it', () async {
    api.suggestions = {
      'ri': [const PixivTrendTag(name: '鏡音リン', translatedName: 'Kagamine Rin')],
      'mi': [const PixivTrendTag(name: '初音ミク')],
    };
    final store = build();

    store.type('miku ri');
    await Future<void>.delayed(const Duration(milliseconds: 20));
    expect(api.calls, ['autocomplete:ri']);
    expect(store.state.picking, isTrue);
    expect(store.pick(store.state.suggestions.single), 'miku 鏡音リン ');
    expect((store.state.typed, store.state.searched), ('miku 鏡音リン ', false));
    expect(store.state.suggestions, isEmpty);

    store.type('mi');
    await Future<void>.delayed(const Duration(milliseconds: 20));
    expect(store.pick(store.state.suggestions.single), '初音ミク');
    await Future<void>.delayed(const Duration(milliseconds: 120));
    expect(store.state.word, '初音ミク');
    expect(history.state, ['初音ミク']);
  });

  test('a number offers its shortcuts; a link asks for no suggestions', () async {
    final store = build();
    store.type('12345');
    expect((store.state.numericId, store.state.picking), (12345, true));

    store.type('https://www.pixiv.net/artworks/12345');
    await Future<void>.delayed(const Duration(milliseconds: 20));
    expect(store.state.numericId, isNull);
    expect(api.calls.where((call) => call.contains('pixiv.net')), isEmpty);

    await store.search('12345');
    expect(store.state.picking, isFalse, reason: 'the searched number is the results, not a question');
  });

  test('trending and creators load apart: one failing leaves the other', () async {
    api
      ..trendingError = PixivException(PixivErrorKind.network, 'offline')
      ..creators = [const PixivUser(id: 3, name: 'Rin', account: 'rin', comment: '')];
    final store = build();
    await store.loadLanding();

    expect(store.trending.error, isA<PixivException>());
    expect(store.creators.state.single.name, 'Rin');

    api
      ..trendingError = null
      ..trending = [const PixivTrendTag(name: '風景')];
    await store.trending.load();
    expect(store.trending.state.single.name, '風景');
  });

  test('a favourite tag\'s tab only fetches works and keeps no history', () async {
    final store = build(illustsOnly: true);
    await store.search('風景');
    expect(api.calls, ['illusts:風景']);
    expect(history.state, isEmpty);
  });

  test('muted tags leave trending, and a muted picture leaves its tag bare', () {
    final mute = const PixivMuteState(tags: {'hidden'}, illustIds: {5});
    final tags = pixivVisibleTrendTags([
      const PixivTrendTag(name: 'Hidden'),
      PixivTrendTag(name: 'shown', illust: pixivWork(id: 5)),
      PixivTrendTag(name: 'kept', illust: pixivWork(id: 6)),
    ], mute);
    expect([for (final tag in tags) (tag.name, tag.illust?.id)], [('shown', null), ('kept', 6)]);
  });
}
