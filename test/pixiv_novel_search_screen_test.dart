import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:provider/single_child_widget.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_api.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_card.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_models.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_search_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_series_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/utils/json.dart';

import 'support/pixiv_discovery_fakes.dart';
import 'support/pixiv_novel_fakes.dart';
import 'support/pixiv_reader_harness.dart';
import 'support/pixiv_search_fakes.dart';

Map<String, Object?> _novelJson(int id, {String title = 'Tale', int xRestrict = 0}) => {
  'id': id,
  'title': '$title $id',
  'image_urls': {'medium': 'https://i.pximg.net/c/240x480_80/novel-cover-master/$id.jpg'},
  'user': {'id': 5, 'name': 'Writer', 'account': 'writer'},
  'x_restrict': xRestrict,
};

PixivUserPreview _writer() => PixivUserPreview(
  user: const PixivUser(id: 5, name: 'Writer', account: 'writer', comment: ''),
  illusts: [pixivWork(id: 50, pages: 1, type: 'illust')],
  novels: [Json(_novelJson(61)), Json(_novelJson(62, xRestrict: 1)), Json(_novelJson(63)), const Json('junk')],
);

FakePixivNovelApi _novelApi({
  List<PixivNovel> found = const [],
  Map<String?, PixivNovelSeriesPage> series = const {},
}) => FakePixivNovelApi(
  PixivClient(PrefServiceCache()),
  searchNovels: found,
  trending: [
    PixivTrendTag(
      name: '恋愛',
      translatedName: 'romance',
      illust: pixivWork(id: 70, pages: 1, type: 'illust'),
    ),
  ],
  seriesPages: series,
);

/// Novel search's own history, as main.dart provides it, over the harness's prefs.
SingleChildWidget _novelHistory() => Provider<PixivNovelSearchHistory>(
  create: (context) => PixivNovelSearchHistory(PrefService.of(context, listen: false)),
  dispose: (_, store) => store.destroy(),
);

/// Seeds string prefs before the screens read them.
FakePixivClient Function(PrefServiceCache prefs) _seeded(Map<String, String> values) => (prefs) {
  for (final MapEntry(:key, :value) in values.entries) {
    prefs.set<String>(key, value);
  }
  return FakePixivClient(prefs);
};

Future<PixivHarness> _pump(
  WidgetTester tester, {
  String? query,
  FakePixivNovelApi? novels,
  FakePixivSearchApi? search,
  Map<String, String> prefs = const {},
  Size size = const Size(390, 844),
  double textScale = 1,
}) => pumpPixiv(
  tester,
  PixivNovelSearchScreen(initialQuery: query),
  size: size,
  textScale: textScale,
  client: _seeded({optionPluginPixivNovelSearchHistory: '[]', optionPluginPixivNovelSearchFilters: '', ...prefs}),
  extraProviders: [...(novels ?? _novelApi()).providers, (search ?? FakePixivSearchApi()).provider, _novelHistory()],
);

Future<void> _tap(WidgetTester tester, Finder finder) async {
  await tester.ensureVisible(finder);
  await tester.pump();
  await tester.tap(finder);
  await settlePixiv(tester);
}

void main() {
  testWidgets('the landing shows recent novel searches and trending novel tags, not the works\' ones', (tester) async {
    final novels = _novelApi();
    final search = FakePixivSearchApi();
    await _pump(
      tester,
      novels: novels,
      search: search,
      prefs: {
        optionPluginPixivSearchHistory: jsonEncode(['works-only']),
        optionPluginPixivNovelSearchHistory: jsonEncode(['rain']),
      },
    );

    expect(find.text('Search for novels or users'), findsOneWidget);
    expect(find.widgetWithText(ActionChip, 'rain'), findsOneWidget);
    expect(find.text('works-only'), findsNothing);
    expect(find.byKey(const ValueKey('pixiv-trend-恋愛')), findsOneWidget);
    expect(find.text('romance'), findsOneWidget);
    expect(find.text('Suggested creators'), findsNothing);
    expect(novels.calls, ['trending']);
    expect(search.calls, isNot(contains('creators')));
    expect(find.byTooltip('Search by image'), findsNothing);
    await disposePixiv(tester);
  });

  testWidgets('a search lists novels and creators with their novel covers and titles', (tester) async {
    final novels = _novelApi(found: [pixivNovel(id: 1, title: 'Rainy letters')]);
    final harness = await _pump(
      tester,
      novels: novels,
      search: FakePixivSearchApi(creatorsFound: [_writer()]),
    );

    await tester.enterText(find.byKey(const ValueKey('pixiv-search-field')), 'rain');
    await tester.testTextInput.receiveAction(TextInputAction.search);
    await settlePixiv(tester);
    expect(find.widgetWithText(Tab, 'Novels'), findsOneWidget);
    expect(find.widgetWithText(PixivNovelCard, 'Rainy letters'), findsOneWidget);
    expect(novels.calls, contains('search:rain'));
    expect(jsonDecode(harness.prefs.get<String>(optionPluginPixivNovelSearchHistory)!), ['rain']);

    await _tap(tester, find.widgetWithText(Tab, 'Users'));
    expect(find.byKey(const ValueKey('pixiv-user-card-novel-61')), findsOneWidget);
    expect(find.text('Tale 61'), findsOneWidget);
    expect(find.byKey(const ValueKey('pixiv-user-card-novel-62')), findsNothing, reason: 'Show R-18 is off');
    expect(find.byKey(const ValueKey('pixiv-user-card-novel-63')), findsOneWidget);
    expect(find.byKey(const ValueKey('pixiv-user-card-work-50')), findsNothing);
    expect(tester.getSize(find.byKey(const ValueKey('pixiv-user-card-novel-61'))).height, greaterThanOrEqualTo(48));
    await disposePixiv(tester);
  });

  testWidgets('the filter sheet offers the novel places to look and keeps popular for Premium', (tester) async {
    final novels = _novelApi(found: [pixivNovel(id: 1)]);
    await _pump(tester, query: 'rain', novels: novels);

    await _tap(tester, find.byKey(const ValueKey('pixiv-search-filters')));
    expect(find.widgetWithText(ChoiceChip, 'Body text'), findsOneWidget);
    expect(find.widgetWithText(ChoiceChip, 'Tags, title, caption'), findsOneWidget);
    expect(find.widgetWithText(ChoiceChip, 'Title/caption'), findsNothing);
    expect(find.widgetWithText(ChoiceChip, 'Oldest'), findsOneWidget);
    expect(find.widgetWithText(ChoiceChip, 'Popular'), findsNothing);
    expect(find.text('Sorting by popularity needs Pixiv Premium.'), findsOneWidget);
    expect(find.text('Ugoira'), findsNothing);

    await _tap(tester, find.widgetWithText(ChoiceChip, 'Body text'));
    await _tap(tester, find.byKey(const ValueKey('pixiv-search-filters-apply')));
    expect(novels.queries.last['search_target'], 'text');
    expect(find.byKey(const ValueKey('pixiv-search-bookmarks')), findsNothing);
    await disposePixiv(tester);
  });

  testWidgets('Premium sorts novels by popularity', (tester) async {
    final novels = _novelApi(found: [pixivNovel(id: 1)]);
    await _pump(tester, query: 'rain', novels: novels, search: FakePixivSearchApi(premium: true));

    await _tap(tester, find.byKey(const ValueKey('pixiv-search-filters')));
    await _tap(tester, find.widgetWithText(ChoiceChip, 'Popular'));
    await _tap(tester, find.byKey(const ValueKey('pixiv-search-filters-apply')));
    expect(novels.queries.last['sort'], 'popular_desc');
    expect(find.byKey(const ValueKey('pixiv-search-bookmarks')), findsNothing, reason: 'novels take no bracket');
    await disposePixiv(tester);
  });

  testWidgets('a number offers the novel, its series and its author, and opens the series', (tester) async {
    final novels = _novelApi(
      series: {
        null: PixivNovelSeriesPage(
          series: PixivNovelSeries(id: 4321, title: 'Seasons', user: pixivNovel().user),
          chapters: [PixivNovelChapter(order: 1, novel: pixivNovel(id: 10, title: 'Spring'))],
          listed: 1,
        ),
      },
    );
    await _pump(tester, query: '4321', novels: novels);
    for (final key in ['pixiv-open-novel-4321', 'pixiv-open-novel-series-4321', 'pixiv-open-user-4321']) {
      expect(find.byKey(ValueKey(key)), findsOneWidget);
      expect(tester.getSize(find.byKey(ValueKey(key))).height, greaterThanOrEqualTo(48));
    }
    expect(find.text('Open novel #4321'), findsOneWidget);
    expect(find.byKey(const ValueKey('pixiv-open-artwork-4321')), findsNothing);

    await _tap(tester, find.byKey(const ValueKey('pixiv-open-novel-series-4321')));
    expect(find.byType(PixivNovelSeriesScreen), findsOneWidget);
    expect(novels.calls, contains('series:4321:null'));
    await disposePixiv(tester);
  });

  testWidgets('the shortcuts and creator cards fit a narrow phone at large text', (tester) async {
    await _pump(tester, query: '1234567890', size: const Size(320, 640), textScale: 2);
    expect(find.byKey(const ValueKey('pixiv-open-novel-series-1234567890')), findsOneWidget);
    expect(tester.takeException(), isNull);
    await disposePixiv(tester);

    await _pump(
      tester,
      query: 'rain',
      size: const Size(320, 640),
      textScale: 2,
      search: FakePixivSearchApi(creatorsFound: [_writer()]),
    );
    await _tap(tester, find.widgetWithText(Tab, 'Users'));
    expect(find.byKey(const ValueKey('pixiv-user-card-novel-61')), findsOneWidget);
    expect(tester.takeException(), isNull);
    await disposePixiv(tester);
  });

  testWidgets('Novel mode\'s Search section is novel search, and Illustration mode\'s the works one', (tester) async {
    final scroll = ScrollController();
    addTearDown(scroll.dispose);
    final feed = PixivFeedStore(FakePixivScreenClient(PrefServiceCache()));
    addTearDown(feed.destroy);
    await pumpPixiv(
      tester,
      PixivScreen(scrollController: scroll),
      client: (prefs) {
        prefs.set(optionPluginPixivNovelSearchHistory, '[]');
        prefs.set(optionPluginPixivNovelSearchFilters, '');
        return FakePixivScreenClient(prefs);
      },
      extraProviders: [
        ..._novelApi().providers,
        FakePixivSearchApi().provider,
        _novelHistory(),
        Provider<PixivFeedStore>.value(value: feed),
        Provider<PixivDiscoveryApi>.value(value: FakePixivDiscoveryApi(PixivClient(PrefServiceCache()))),
      ],
    );

    await _tap(tester, find.byTooltip('Search'));
    expect(find.text('Search for illustrations or users'), findsOneWidget);
    await _tap(tester, find.byTooltip('Switch to novels'));
    expect(find.text('Search for novels or users'), findsOneWidget);
    expect(find.byKey(const ValueKey('pixiv-trend-恋愛')), findsOneWidget);
    await _tap(tester, find.byTooltip('Switch to illustrations'));
    expect(find.text('Search for illustrations or users'), findsOneWidget);
    await disposePixiv(tester);
  });
}
