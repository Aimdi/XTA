import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:provider/single_child_widget.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_accounts.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_api.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_models.dart';
import 'package:xta/plugins/pixiv/pixiv_favorites_section.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_card.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_list.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_models.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_series_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_store.dart';
import 'package:xta/plugins/pixiv/pixiv_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/pixiv/pixiv_user_tabs.dart';

import 'support/pixiv_discovery_fakes.dart';
import 'support/pixiv_novel_fakes.dart';
import 'support/pixiv_reader_harness.dart';
import 'support/pixiv_social_fakes.dart';

const _seasons = PixivSeriesRef(id: 77, title: 'Seasons');

FakePixivNovelApi _novelApi({
  List<PixivNovel> recommended = const [],
  List<PixivNovel> following = const [],
  List<PixivNovel> ranking = const [],
  List<PixivNovel> bookmarks = const [],
  List<PixivWatchlistSeries> watchlist = const [],
  Map<String?, PixivNovelSeriesPage> series = const {},
}) => FakePixivNovelApi(
  PixivClient(PrefServiceCache()),
  recommendedNovels: recommended,
  followingNovels: following,
  rankingNovels: ranking,
  bookmarkNovels: bookmarks,
  watchlistSeries: watchlist,
  seriesPages: series,
);

Future<PixivHarness> _pump(
  WidgetTester tester,
  Widget home,
  FakePixivNovelApi api, {
  Size size = const Size(390, 844),
  double textScale = 1,
  FakePixivClient Function(PrefServiceCache prefs)? client,
  List<SingleChildWidget> more = const [],
}) => pumpPixiv(
  tester,
  home,
  size: size,
  textScale: textScale,
  client: client,
  extraProviders: [...api.providers, ...more],
);

Future<void> _tap(WidgetTester tester, Finder finder) async {
  await tester.ensureVisible(finder);
  await tester.pump();
  await tester.tap(finder);
  await settlePixiv(tester);
}

Widget _list(List<PixivNovel> novels) => Scaffold(
  body: ListView(children: [for (final novel in novels) PixivNovelCard(novel: novel)]),
);

PixivNovelSeriesPage _seriesPage({bool concluded = true, bool startHidden = false}) => PixivNovelSeriesPage(
  series: PixivNovelSeries(
    id: 77,
    title: 'Seasons',
    user: pixivNovel().user,
    captionHtml: 'Four <b>seasons</b>',
    caption: 'Four seasons',
    isConcluded: concluded,
    contentCount: 3,
    totalCharacterCount: 54321,
  ),
  first: startHidden ? null : pixivNovel(id: 10, title: 'Spring'),
  latest: pixivNovel(id: 12, title: 'Autumn'),
  chapters: [
    PixivNovelChapter(
      order: 1,
      novel: pixivNovel(id: 10, title: 'Spring', series: _seasons),
    ),
    PixivNovelChapter(
      order: 3,
      novel: pixivNovel(id: 12, title: 'Autumn', series: _seasons),
    ),
  ],
  listed: 3,
);

void main() {
  group('Novel mode', () {
    late PixivFeedStore feed;
    late ScrollController scroll;

    setUp(() => scroll = ScrollController());

    tearDown(() {
      feed.destroy();
      scroll.dispose();
    });

    Future<PixivHarness> pumpScreen(WidgetTester tester, FakePixivNovelApi api, {bool showR18 = false}) {
      feed = PixivFeedStore(
        FakePixivScreenClient(
          PrefServiceCache(),
          followingWorks: [pixivWork(id: 600, pages: 1, title: 'Followed work')],
        ),
      );
      final discovery = FakePixivDiscoveryApi(PixivClient(PrefServiceCache()));
      return _pump(
        tester,
        PixivScreen(scrollController: scroll),
        api,
        client: (prefs) {
          prefs.set(optionPluginPixivUserId, 77);
          prefs.set(optionPluginPixivShowR18, showR18);
          return FakePixivScreenClient(prefs);
        },
        more: [
          Provider<PixivFeedStore>.value(value: feed),
          Provider<PixivDiscoveryApi>.value(value: discovery),
        ],
      );
    }

    testWidgets('the mode button swaps every section to novels and back', (tester) async {
      final api = _novelApi(
        recommended: [pixivNovel(id: 1, title: 'Recommended tale')],
        ranking: [pixivNovel(id: 2, title: 'Ranked tale')],
        bookmarks: [pixivNovel(id: 3, title: 'Kept tale')],
      );
      await pumpScreen(tester, api);
      expect(find.text('Followed work'), findsOneWidget);
      final toggle = find.byKey(const ValueKey('pixiv-mode-toggle'));
      expect(tester.getSize(toggle).height, greaterThanOrEqualTo(48));

      await _tap(tester, find.byTooltip('Switch to novels'));
      expect(find.text('Recommended tale'), findsOneWidget);
      expect(find.byKey(const ValueKey('pixiv-novel-home-watchlist')), findsOneWidget);
      expect(find.byTooltip('Switch to illustrations'), findsOneWidget);

      await _tap(tester, find.byTooltip('Ranking'));
      expect(find.text('Ranked tale'), findsOneWidget);
      expect(api.calls, contains('rank:day:null'));
      expect(find.byKey(const ValueKey('pixiv-ranking-mode-week_ai')), findsOneWidget);
      expect(find.byKey(const ValueKey('pixiv-ranking-mode-day_r18')), findsNothing, reason: 'Show R-18 is off');

      await _tap(tester, find.byTooltip('Favorites'));
      expect(find.text('Kept tale'), findsOneWidget);
      await _tap(tester, find.text('Private'));
      expect(api.calls.where((call) => call.startsWith('own:')), ['own:public', 'own:private']);

      await _tap(tester, find.byTooltip('Switch to illustrations'));
      expect(find.byType(PixivFavoritesSection), findsOneWidget);
      expect(find.text('Kept tale'), findsNothing);
      await disposePixiv(tester);
    });

    testWidgets('Home switches between Recommended, Following by visibility and the Watchlist', (tester) async {
      final api = _novelApi(
        following: [pixivNovel(id: 4, title: 'Followed tale')],
        watchlist: const [
          PixivWatchlistSeries(id: 77, title: 'Seasons', userId: 42, userName: 'Mika', latestContentId: 12),
        ],
        series: {null: _seriesPage()},
      );
      await pumpScreen(tester, api);
      await _tap(tester, find.byTooltip('Switch to novels'));

      await _tap(tester, find.byKey(const ValueKey('pixiv-novel-home-following')));
      expect(find.text('Followed tale'), findsOneWidget);
      await _tap(tester, find.text('Private'));
      expect(api.calls.where((call) => call.startsWith('following:')), ['following:public', 'following:private']);

      await _tap(tester, find.byKey(const ValueKey('pixiv-novel-home-watchlist')));
      expect(find.text('Seasons'), findsOneWidget);
      expect(find.byKey(const ValueKey('pixiv-watchlist-latest-77')), findsOneWidget);
      await _tap(tester, find.text('Seasons'));
      expect(find.byType(PixivNovelSeriesScreen), findsOneWidget);
      expect(api.calls.last, 'series:77:null');
      await disposePixiv(tester);
    });

    testWidgets('R-18 boards are offered while Show R-18 is on, and the shown one goes with it', (tester) async {
      final api = _novelApi();
      final harness = await pumpScreen(tester, api, showR18: true);
      await _tap(tester, find.byTooltip('Switch to novels'));
      await _tap(tester, find.byTooltip('Ranking'));

      await _tap(tester, find.byKey(const ValueKey('pixiv-ranking-edit')));
      expect(find.byKey(const ValueKey('pixiv-ranking-pin-week_r18g')), findsOneWidget);
      await _tap(tester, find.byKey(const ValueKey('pixiv-ranking-pin-week_r18g')));
      await tester.tapAt(const Offset(195, 40));
      await settlePixiv(tester);
      await _tap(tester, find.byKey(const ValueKey('pixiv-ranking-mode-week_r18g')));
      expect(api.calls.last, 'rank:week_r18g:null');

      await harness.prefs.set(optionPluginPixivShowR18, false);
      await settlePixiv(tester);
      expect(find.byKey(const ValueKey('pixiv-ranking-mode-week_r18g')), findsNothing);
      expect(api.calls.last, 'rank:day:null');
      final day = tester.widget<ChoiceChip>(find.byKey(const ValueKey('pixiv-ranking-mode-day')));
      expect(day.selected, isTrue);
      await disposePixiv(tester);
    });
  });

  group('the novel card', () {
    testWidgets('shows cover, title, author, length, ratings, series and tags', (tester) async {
      final api = _novelApi();
      await _pump(tester, _list([pixivNovel(series: _seasons, xRestrict: 2, ai: true)]), api);

      expect(find.text('Autumn Letters'), findsOneWidget);
      expect(find.text('Mika'), findsOneWidget);
      expect(find.text('12.3K characters'), findsOneWidget);
      expect(find.text('R-18G'), findsOneWidget);
      expect(find.text('AI'), findsOneWidget);
      expect(find.text('Series: Seasons'), findsOneWidget);
      expect(find.text('#autumn'), findsOneWidget);
      expect(tester.getSize(find.byKey(const ValueKey('pixiv-series-link-77'))).height, greaterThanOrEqualTo(48));
      await disposePixiv(tester);
    });

    testWidgets('the heart bookmarks with the default visibility, removes, and a long press files privately', (
      tester,
    ) async {
      final api = _novelApi();
      final harness = await _pump(tester, _list([pixivNovel(bookmarks: 10)]), api);
      final heart = find.byKey(const ValueKey('pixiv-novel-bookmark-900'));
      expect(tester.getSize(heart).width, greaterThanOrEqualTo(48));
      expect(tester.getSize(heart).height, greaterThanOrEqualTo(48));

      await _tap(tester, heart);
      expect(api.calls.last, 'bookmark:900:public');
      expect(find.text('11'), findsOneWidget);
      expect(find.bySemanticsLabel('Remove bookmark'), findsOneWidget);

      await _tap(tester, heart);
      expect(api.calls.last, 'unbookmark:900');
      expect(find.text('10'), findsOneWidget);

      await tester.longPress(heart);
      await settlePixiv(tester);
      expect(api.calls.last, 'bookmark:900:private');

      await _tap(tester, heart);
      await harness.prefs.set(optionPluginPixivDefaultPrivateBookmark, true);
      await _tap(tester, heart);
      expect(api.calls.last, 'bookmark:900:private');
      await disposePixiv(tester);
    });

    testWidgets('a failed bookmark says why and leaves the heart as it was', (tester) async {
      final api = _novelApi()..writeError = PixivException(PixivErrorKind.network, 'offline');
      await _pump(tester, _list([pixivNovel()]), api);

      await _tap(tester, find.byKey(const ValueKey('pixiv-novel-bookmark-900')));
      expect(find.byType(SnackBar), findsOneWidget);
      expect(find.byIcon(Icons.favorite_border), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('a long press offers bookmark, copy link and mutes; muting the novel hides it', (tester) async {
      final api = _novelApi(
        recommended: [
          pixivNovel(id: 1, title: 'Kept'),
          pixivNovel(id: 2, title: 'Muted'),
        ],
      );
      final store = PixivNovelListStore(api.recommended)..refresh();
      addTearDown(store.destroy);
      await _pump(
        tester,
        Scaffold(
          body: PixivNovelFeed(store: store, emptyMessage: 'none'),
        ),
        api,
      );

      await tester.longPress(find.text('Muted'));
      await settlePixiv(tester);
      for (final id in ['pixiv-novel-bookmark', 'pixiv-copy-link', 'pixiv-mute-author', 'pixiv-mute-novel']) {
        expect(find.byKey(ValueKey('plugin-post-action-$id')), findsOneWidget);
      }
      await _tap(tester, find.byKey(const ValueKey('plugin-post-action-pixiv-mute-novel')));
      await _tap(tester, find.widgetWithText(FilledButton, 'Mute this novel'));

      expect(find.text('Muted'), findsNothing);
      expect(find.text('Kept'), findsOneWidget);
      final mute = Provider.of<PixivMuteStore>(tester.element(find.text('Kept')), listen: false);
      expect(mute.state.novelIds, {2});
      await disposePixiv(tester);
    });

    testWidgets('large text on a narrow screen wraps rather than overflows', (tester) async {
      final api = _novelApi();
      await _pump(
        tester,
        _list([pixivNovel(series: _seasons, xRestrict: 1, ai: true, title: 'A rather long title ' * 4)]),
        api,
        size: const Size(320, 700),
        textScale: 2,
      );
      expect(tester.takeException(), isNull);
      expect(find.text('R-18'), findsOneWidget);
      await disposePixiv(tester);
    });
  });

  group('the series page', () {
    testWidgets('shows its state, totals and caption, numbers its chapters and toggles the watchlist', (tester) async {
      final api = _novelApi(series: {null: _seriesPage()});
      var changed = 0;
      await _pump(tester, PixivNovelSeriesScreen(seriesId: 77, onWatchlistChanged: () => changed++), api);

      expect(find.text('Completed · 3 chapters · 54.3K characters'), findsOneWidget);
      expect(find.text('Four seasons'), findsOneWidget);
      expect(find.text('#1'), findsOneWidget);
      expect(find.text('#3'), findsOneWidget, reason: 'a chapter the filters hid keeps its place');
      expect(find.text('Series: Seasons'), findsNothing, reason: 'the series page does not link to itself');
      expect(tester.widget<FilledButton>(find.byKey(const ValueKey('pixiv-novel-series-start'))).onPressed, isNotNull);
      expect(find.text('View latest'), findsOneWidget);

      await _tap(tester, find.byKey(const ValueKey('pixiv-series-watchlist')));
      expect(api.calls.last, 'watch:77');
      expect(find.text('Remove from watchlist'), findsOneWidget);
      expect(changed, 1);

      api.writeError = PixivException(PixivErrorKind.network, 'offline');
      await _tap(tester, find.byKey(const ValueKey('pixiv-series-watchlist')));
      expect(find.byType(SnackBar), findsOneWidget);
      expect(find.text('Remove from watchlist'), findsOneWidget);
      expect(changed, 1);
      await disposePixiv(tester);
    });

    testWidgets('an ongoing series whose first chapter is hidden offers no start', (tester) async {
      final api = _novelApi(series: {null: _seriesPage(concluded: false, startHidden: true)});
      await _pump(tester, const PixivNovelSeriesScreen(seriesId: 77), api);

      expect(find.textContaining('Ongoing'), findsOneWidget);
      expect(tester.widget<FilledButton>(find.byKey(const ValueKey('pixiv-novel-series-start'))).onPressed, isNull);
      await disposePixiv(tester);
    });

    testWidgets('a series that fails to load offers a retry', (tester) async {
      final api = _novelApi();
      await _pump(tester, const PixivNovelSeriesScreen(seriesId: 77), api);

      expect(find.text('Novel series'), findsOneWidget);
      expect(find.text('Retry'), findsOneWidget);
      await disposePixiv(tester);
    });
  });

  testWidgets('a profile\'s Bookmarks tab switches to the creator\'s public novel bookmarks', (tester) async {
    final api = _novelApi(bookmarks: [pixivNovel(id: 5, title: 'Their pick')]);
    final social = FakePixivSocialApi(bookmarks: [pixivWork(id: 300, pages: 1, title: 'Their work')]);
    await _pump(tester, const Scaffold(body: PixivProfileBookmarks(userId: 9)), api, more: [social.provider]);
    expect(find.text('Their work'), findsOneWidget);

    await _tap(tester, find.text('Novels'));
    expect(api.calls, ['bookmarks:9:public']);
    expect(find.text('Their pick'), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('another account drops the novel bookmarks this session made', (tester) async {
    final api = _novelApi();
    await _pump(tester, _list([pixivNovel()]), api);
    final context = tester.element(find.byType(PixivNovelCard));
    final store = Provider.of<PixivNovelBookmarkStore>(context, listen: false)..mark(900, true);

    pixivAccountDataForgetter(context)();
    expect(store.state, isEmpty);
    await disposePixiv(tester);
  });
}
