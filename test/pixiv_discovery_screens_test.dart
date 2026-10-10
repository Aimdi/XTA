import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:provider/single_child_widget.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_detail_stats.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_api.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_models.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_home_section.dart';
import 'package:xta/plugins/pixiv/pixiv_illust_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_recommended_users_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_series_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_session_account.dart';
import 'package:xta/plugins/pixiv/pixiv_sign_in_body.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/pixiv/pixiv_user_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_watchlist.dart';
import 'package:xta/plugins/pixiv/pixivision_article_screen.dart';
import 'package:xta/plugins/pixiv/pixivision_list_screen.dart';
import 'package:xta/plugins/pixiv/pixivision_parser.dart';
import 'package:xta/ui/errors.dart';

import 'support/pixiv_discovery_fakes.dart';
import 'support/pixiv_reader_harness.dart';

const _watched = PixivWatchlistSeries(
  id: 266067,
  title: 'Autumn Diary',
  userId: 42,
  userName: 'Mika',
  latestContentId: 125547965,
  publishedCount: 12,
);

final _series = PixivIllustSeries(
  id: 266067,
  title: 'Autumn Diary',
  user: const PixivUser(id: 42, name: 'Mika', account: 'mika', comment: ''),
  caption: 'Leaves and cats',
  workCount: 5,
  createdAt: DateTime(2026, 9, 1),
);

PixivUserPreview _creator(int id, String name) => PixivUserPreview(
  user: PixivUser(id: id, name: name, account: name.toLowerCase(), comment: ''),
  illusts: [pixivWork(id: id * 10, pages: 1)],
);

const _article = PixivSpotlightArticle(
  id: 9876,
  title: 'Cats in Autumn',
  thumbnailUrl: 'https://i.pximg.net/c/w1200/9876.jpg',
  articleUrl: 'https://www.pixivision.net/en/a/9876',
);

/// A work in a series, as the detail receives it.
PixivIllust _seriesWork(int id) => PixivIllust(
  id: id,
  title: 'Part $id',
  caption: '',
  type: 'manga',
  thumbnailUrl: 'https://i.pximg.net/c/540x540_70/img-master/$id.jpg',
  pageCount: 1,
  width: 1200,
  height: 900,
  userId: 42,
  userName: 'Mika',
  userAccount: 'mika',
  series: const PixivSeriesRef(id: 266067, title: 'Autumn Diary'),
);

FakePixivDiscoveryApi _api({
  List<PixivIllust> manga = const [],
  List<PixivWatchlistSeries> watchlist = const [],
  List<PixivUserPreview> users = const [],
  List<PixivSpotlightArticle> articles = const [],
  List<PixivIllust> walkthrough = const [],
  PixivIllustSeries? series,
  List<PixivIllust> seriesWorks = const [],
  PixivSeriesContext? context,
  PixivisionArticle? article,
}) => FakePixivDiscoveryApi(
  PixivClient(PrefServiceCache()),
  manga: manga,
  watchlist: watchlist,
  users: users,
  articles: articles,
  walkthroughWorks: walkthrough,
  series: series,
  seriesWorks: seriesWorks,
  context: context,
  article: article,
);

Future<PixivHarness> _pump(
  WidgetTester tester,
  Widget home,
  FakePixivDiscoveryApi api, {
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
  extraProviders: [
    Provider<PixivDiscoveryApi>.value(value: api),
    ...more,
  ],
);

Future<void> _tap(WidgetTester tester, Finder finder) async {
  await tester.tap(finder);
  await settlePixiv(tester);
}

/// How far the one vertical list on screen is scrolled.
double _verticalOffset(WidgetTester tester) => tester
    .stateList<ScrollableState>(find.byType(Scrollable))
    .where((state) => state.position.axis == Axis.vertical)
    .single
    .position
    .pixels;

/// Home's chip row scrolls sideways; a chip past the edge is brought in first.
Future<void> _tapChip(WidgetTester tester, String source) async {
  final chip = find.byKey(ValueKey('pixiv-home-$source'));
  await tester.ensureVisible(chip);
  await tester.pump();
  await _tap(tester, chip);
}

PixivSpotlightArticle _articleNumber(int index) => PixivSpotlightArticle(
  id: 9000 + index,
  title: 'Article $index',
  thumbnailUrl: '',
  publishedAt: DateTime(2026, 9, 30),
);

/// Recommended works that always have a next page, counting each ask.
class _PagedRecommendedClient extends FakePixivScreenClient {
  final asked = <String?>[];

  _PagedRecommendedClient(super.prefs);

  @override
  Future<PixivIllustPage> recommended({String? nextUrl}) async {
    asked.add(nextUrl);
    return PixivIllustPage(
      illusts: [pixivWork(id: 700 + asked.length, pages: 1)],
      nextUrl: 'https://app-api.pixiv.net/v1/illust/recommended?offset=${asked.length}',
    );
  }
}

/// A work's detail that arrives only when [gate] completes.
class _SlowDetailClient extends FakePixivClient {
  final gate = Completer<void>();
  var details = 0;

  _SlowDetailClient(super.prefs);

  @override
  Future<PixivIllust> illustDetail(int illustId) async {
    details++;
    await gate.future;
    return pixivWork(id: illustId);
  }
}

void main() {
  group('Home', () {
    late PixivFeedStore feed;
    late ScrollController scroll;

    setUp(() {
      scroll = ScrollController();
    });

    tearDown(() {
      feed.destroy();
      scroll.dispose();
    });

    Future<PixivHarness> pumpHome(
      WidgetTester tester,
      FakePixivDiscoveryApi api, {
      int userId = 0,
      FakePixivScreenClient Function(PrefServiceCache prefs)? client,
    }) {
      final screenClient = FakePixivScreenClient(
        PrefServiceCache(),
        followingWorks: [pixivWork(id: 600, pages: 1, title: 'Followed work')],
      );
      feed = PixivFeedStore(screenClient);
      return _pump(
        tester,
        PixivScreen(scrollController: scroll),
        api,
        client: (prefs) {
          prefs.set(optionPluginPixivUserId, userId);
          return client?.call(prefs) ??
              FakePixivScreenClient(prefs, recommendedWorks: [pixivWork(id: 700, pages: 1, title: 'Suggested work')]);
        },
        more: [Provider<PixivFeedStore>.value(value: feed)],
      );
    }

    testWidgets('the chips switch between Following, Recommended, Manga and Watchlist', (tester) async {
      final api = _api(
        manga: [pixivWork(id: 800, pages: 1, title: 'Manga work')],
        watchlist: [_watched],
        users: [_creator(7, 'Sora')],
        articles: [_article],
      );
      await pumpHome(tester, api);
      expect(find.text('Followed work'), findsOneWidget);

      await _tapChip(tester, 'manga');
      expect(api.calls, contains('manga'));
      expect(find.text('Manga work'), findsOneWidget);

      await _tapChip(tester, 'watchlist');
      expect(api.calls, contains('watchlist'));
      expect(find.text('Autumn Diary'), findsOneWidget);

      await _tapChip(tester, 'recommended');
      expect(find.text('Suggested work'), findsOneWidget);
      expect(find.text('Pixivision articles'), findsOneWidget);
      expect(find.text('Cats in Autumn'), findsOneWidget);
      expect(find.byKey(const ValueKey('pixiv-recommended-user-7')), findsOneWidget);
      expect(find.byKey(const ValueKey('pixiv-follow-restrict')), findsNothing);
      await disposePixiv(tester);
    });

    testWidgets('Following can be narrowed to public or private follows from one button', (tester) async {
      final api = _api();
      await pumpHome(tester, api);
      final control = find.byKey(const ValueKey('pixiv-follow-restrict'));
      expect(tester.getSize(control).width, lessThanOrEqualTo(48), reason: 'the source chips keep their room');
      expect(find.byTooltip('All followed creators'), findsOneWidget);

      await _tap(tester, control);
      await _tap(tester, find.byKey(const ValueKey('pixiv-follow-restrict-private')));
      expect(api.calls.last, 'following:private');
      expect(find.byTooltip('Privately followed creators'), findsOneWidget);
      await _tap(tester, control);
      await _tap(tester, find.byKey(const ValueKey('pixiv-follow-restrict-public')));
      expect(api.calls.last, 'following:public');

      await tester.pumpWidget(const SizedBox());
      feed.refresh();
      await tester.pump(const Duration(milliseconds: 100));
      expect(api.calls.last, 'following:all', reason: 'the app-wide feed is not left narrowed after Home');
      await disposePixiv(tester);
    });

    testWidgets('See all opens the full lists of articles and creators', (tester) async {
      final api = _api(users: [_creator(7, 'Sora')], articles: [_article]);
      await pumpHome(tester, api);
      await _tapChip(tester, 'recommended');
      expect(_verticalOffset(tester), 0, reason: 'a new feed opens at its top, not at the chip row\'s offset');

      await _tap(tester, find.text('See all').first);
      expect(find.byType(PixivisionListScreen), findsOneWidget);
      await tester.pageBack();
      await settlePixiv(tester);

      await _tap(tester, find.text('See all').last);
      expect(find.byType(PixivRecommendedUsersScreen), findsOneWidget);
      expect(find.text('Sora'), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('sideways scrolls in the Recommended header do not page its works', (tester) async {
      late _PagedRecommendedClient client;
      final api = _api(
        users: [for (var id = 1; id <= 8; id++) _creator(id, 'Creator $id')],
        articles: [for (var index = 0; index < 8; index++) _articleNumber(index)],
      );
      await pumpHome(tester, api, client: (prefs) => client = _PagedRecommendedClient(prefs));
      await _tapChip(tester, 'recommended');
      expect(client.asked, [null]);

      for (var drag = 0; drag < 4; drag++) {
        await tester.drag(find.byType(PixivisionCarousel), const Offset(-200, 0));
        await settlePixiv(tester);
      }
      await tester.drag(find.byType(PixivRecommendedUsersStrip), const Offset(-200, 0));
      await settlePixiv(tester);
      expect(client.asked, [null]);
      await disposePixiv(tester);
    });

    testWidgets('pulling Recommended down also refreshes its articles and creators', (tester) async {
      final api = _api(users: [_creator(7, 'Sora')], articles: [_article]);
      await pumpHome(tester, api);
      await _tapChip(tester, 'recommended');
      expect(api.calls.where((call) => call == 'spotlight' || call == 'users'), ['spotlight', 'users']);

      await tester.fling(find.text('Suggested work'), const Offset(0, 400), 1200);
      await settlePixiv(tester);
      expect(api.calls.where((call) => call == 'spotlight'), hasLength(2));
      expect(api.calls.where((call) => call == 'users'), hasLength(2));
      await disposePixiv(tester);
    });

    testWidgets('another account signing in empties the lists the last one loaded and reloads the shown one', (
      tester,
    ) async {
      final api = _api(watchlist: [_watched]);
      final harness = await pumpHome(tester, api, userId: 5);
      await _tapChip(tester, 'watchlist');
      expect(find.text('Autumn Diary'), findsOneWidget);

      api.watchlist = const [PixivWatchlistSeries(id: 3, title: 'Winter Notes', userId: 9, userName: 'Ren')];
      await harness.prefs.set(optionPluginPixivUserId, 6);
      await settlePixiv(tester);
      expect(find.text('Autumn Diary'), findsNothing);
      expect(find.text('Winter Notes'), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('tapping the Manga or Watchlist chip again brings its list back to the top', (tester) async {
      final api = _api(
        manga: [for (var id = 800; id < 840; id++) pixivWork(id: id, pages: 1, title: 'Manga $id')],
        watchlist: [
          for (var id = 1; id <= 40; id++)
            PixivWatchlistSeries(id: id, title: 'Series $id', userId: 9, userName: 'Ren'),
        ],
      );
      await pumpHome(tester, api);
      for (final source in ['manga', 'watchlist']) {
        await _tapChip(tester, source);
        await tester.drag(find.byType(CustomScrollView).last, const Offset(0, -600));
        await settlePixiv(tester);
        expect(_verticalOffset(tester), greaterThan(0), reason: source);

        await _tapChip(tester, source);
        await settlePixiv(tester);
        expect(_verticalOffset(tester), 0, reason: source);
      }
      await disposePixiv(tester);
    });

    testWidgets('turning Show R-18 off reloads the lists loaded under it', (tester) async {
      final api = _api(manga: [pixivWork(id: 800, pages: 1, title: 'Loaded with R-18 on')]);
      final harness = await pumpHome(tester, api, userId: 5);
      await harness.prefs.set(optionPluginPixivShowR18, true);
      await settlePixiv(tester);
      await _tapChip(tester, 'manga');
      expect(find.text('Loaded with R-18 on'), findsOneWidget);

      api.manga = [pixivWork(id: 801, pages: 1, title: 'Loaded with R-18 off')];
      await harness.prefs.set(optionPluginPixivShowR18, false);
      await settlePixiv(tester);
      expect(find.text('Loaded with R-18 on'), findsNothing);
      expect(find.text('Loaded with R-18 off'), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('the Recommended header fits a narrow phone with large text', (tester) async {
      final api = _api(users: [_creator(7, 'Sora')], articles: [_articleNumber(1), _articleNumber(2)]);
      final mute = PixivMuteStore(PrefServiceCache());
      final articles = pixivSpotlightStore(api)..refresh();
      final users = pixivRecommendedUsersStore(api, mute)..refresh();
      addTearDown(mute.destroy);
      addTearDown(articles.destroy);
      addTearDown(users.destroy);
      for (final textScale in [2.0, 3.0]) {
        await _pump(
          tester,
          Scaffold(
            body: SingleChildScrollView(
              child: PixivRecommendedHeader(articles: articles, users: users),
            ),
          ),
          api,
          size: const Size(320, 900),
          textScale: textScale,
        );
        expect(tester.takeException(), isNull, reason: 'text scale $textScale');
        expect(find.text('Article 1'), findsOneWidget);
        await disposePixiv(tester);
      }
    });
  });

  group('the watchlist', () {
    late PixivWatchlistStore store;

    Future<FakePixivDiscoveryApi> pumpWatchlist(
      WidgetTester tester, {
      double textScale = 1,
      Size size = const Size(390, 844),
      FakePixivClient Function(PrefServiceCache prefs)? client,
    }) async {
      final api = _api(watchlist: [_watched], series: _series, seriesWorks: [pixivWork(id: 11, pages: 1)]);
      store = pixivMangaWatchlistStore(api)..refresh();
      addTearDown(store.destroy);
      await _pump(
        tester,
        Scaffold(body: PixivMangaWatchlistFeed(store: store)),
        api,
        textScale: textScale,
        size: size,
        client: client,
      );
      return api;
    }

    testWidgets('View latest opens the newest work', (tester) async {
      await pumpWatchlist(tester);
      await _tap(tester, find.byKey(const ValueKey('pixiv-watchlist-latest-266067')));
      final opened = tester.widget<PixivIllustScreen>(find.byType(PixivIllustScreen));
      expect(opened.illust.id, 125547965);
      await disposePixiv(tester);
    });

    testWidgets('View latest waits for the newest work, so a second tap opens it once', (tester) async {
      late _SlowDetailClient client;
      await pumpWatchlist(tester, client: (prefs) => client = _SlowDetailClient(prefs));
      final latest = find.byKey(const ValueKey('pixiv-watchlist-latest-266067'));
      await tester.tap(latest);
      await tester.pump();
      expect(tester.widget<ButtonStyleButton>(latest).onPressed, isNull);
      await tester.tap(latest, warnIfMissed: false);
      await tester.pump();
      expect(client.details, 1);

      client.gate.complete();
      await settlePixiv(tester);
      expect(find.byType(PixivIllustScreen), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('a row opens its series', (tester) async {
      await pumpWatchlist(tester);
      await _tap(tester, find.text('Autumn Diary'));
      expect(tester.widget<PixivSeriesScreen>(find.byType(PixivSeriesScreen)).seriesId, 266067);
      await disposePixiv(tester);
    });

    testWidgets('a watchlist change on the series page refreshes the list', (tester) async {
      final api = await pumpWatchlist(tester);
      expect(api.calls.where((call) => call == 'watchlist'), hasLength(1));
      await _tap(tester, find.text('Autumn Diary'));
      await _tap(tester, find.text('Add to watchlist'));
      expect(api.calls.where((call) => call == 'watchlist'), hasLength(2));
      await disposePixiv(tester);
    });

    testWidgets('rows fit a narrow phone with large text', (tester) async {
      await pumpWatchlist(tester, textScale: 2, size: const Size(320, 700));
      expect(tester.takeException(), isNull);
      final latest = tester.getSize(find.byKey(const ValueKey('pixiv-watchlist-latest-266067')));
      expect(latest.height, greaterThanOrEqualTo(48));
      await disposePixiv(tester);
    });
  });

  group('a series', () {
    testWidgets('shows its header and works, and goes on and off the watchlist', (tester) async {
      final api = _api(
        series: _series,
        seriesWorks: [pixivWork(id: 11, pages: 1, title: 'First part')],
      );
      await _pump(tester, const PixivSeriesScreen(seriesId: 266067), api, size: const Size(390, 1400));
      expect(api.calls, ['series:266067']);
      expect(find.text('Leaves and cats'), findsOneWidget);
      expect(find.text('First part'), findsOneWidget);

      await _tap(tester, find.text('Add to watchlist'));
      expect(api.calls.last, 'watch:266067');
      expect(find.text('Remove from watchlist'), findsOneWidget);

      await _tap(tester, find.text('Remove from watchlist'));
      expect(api.calls.last, 'unwatch:266067');
      expect(find.text('Add to watchlist'), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('keeps its header and watchlist toggle when no work can be shown', (tester) async {
      final api = _api(series: _series.copyWith(watchlistAdded: true));
      await _pump(tester, const PixivSeriesScreen(seriesId: 266067), api);
      expect(find.text('No works in this series you can see'), findsOneWidget);
      expect(find.text('Leaves and cats'), findsOneWidget);

      await _tap(tester, find.text('Remove from watchlist'));
      expect(api.calls.last, 'unwatch:266067');
      expect(find.text('Add to watchlist'), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('the header fits a narrow phone with large text', (tester) async {
      final api = _api(series: _series, seriesWorks: [pixivWork(id: 11, pages: 1)]);
      await _pump(tester, const PixivSeriesScreen(seriesId: 266067), api, size: const Size(320, 1400), textScale: 2);
      expect(tester.takeException(), isNull);
      expect(tester.getSize(find.byKey(const ValueKey('pixiv-series-watchlist'))).height, greaterThanOrEqualTo(48));
      await disposePixiv(tester);
    });
  });

  group('the work detail', () {
    testWidgets('names the series and moves through it with previous and next', (tester) async {
      final seed = _seriesWork(12);
      final api = _api(
        series: _series,
        context: PixivSeriesContext(order: 2, total: 5, previous: _seriesWork(11), next: _seriesWork(13)),
      );
      await _pump(
        tester,
        PixivIllustScreen(illust: seed),
        api,
        size: const Size(390, 1600),
        client: (prefs) => FakePixivClient(prefs, detail: seed),
      );
      expect(api.calls, contains('context:12'));
      expect(find.text('Series: Autumn Diary'), findsOneWidget);
      expect(find.text('#2 of 5'), findsOneWidget);

      await _tap(tester, find.byKey(const ValueKey('pixiv-series-next')));
      expect(find.byType(PixivIllustScreen), findsOneWidget);
      expect(tester.widget<PixivIllustScreen>(find.byType(PixivIllustScreen)).illust.id, 13);

      await _tap(tester, find.byKey(const ValueKey('pixiv-series-link-266067')));
      expect(find.byType(PixivSeriesScreen), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('a series line under a tile opens the series', (tester) async {
      final api = _api(series: _series, seriesWorks: [_seriesWork(11)]);
      await _pump(
        tester,
        Scaffold(
          body: Align(
            alignment: Alignment.topLeft,
            child: SizedBox(width: 190, child: PixivIllustTile(illust: _seriesWork(12))),
          ),
        ),
        api,
      );
      await _tap(tester, find.byKey(const ValueKey('pixiv-series-link-266067')));
      expect(find.byType(PixivSeriesScreen), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('shows the artwork ID, which copies, and the size', (tester) async {
      String? copied;
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
        if (call.method == 'Clipboard.setData') copied = (call.arguments as Map)['text'] as String?;
        return null;
      });
      addTearDown(() => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, null));
      await _pump(tester, Scaffold(body: PixivDetailStats(illust: pixivWork(id: 120))), _api());
      expect(find.text('1200 × 1700 px'), findsOneWidget);
      expect(tester.getSize(find.byKey(const ValueKey('pixiv-illust-id'))).height, greaterThanOrEqualTo(48));

      await _tap(tester, find.text('Artwork ID 120'));
      expect(copied, '120');
      expect(find.text('Artwork ID copied'), findsOneWidget);
      await disposePixiv(tester);
    });
  });

  group('signed out', () {
    testWidgets('previews Pixiv\'s walkthrough works and asks to sign in to open one', (tester) async {
      var signIns = 0;
      final api = _api(walkthrough: [pixivWork(id: 31, pages: 1)]);
      await _pump(tester, Scaffold(body: PixivSignInBody(signingIn: false, onSignIn: () => signIns++)), api);
      expect(api.calls, ['walkthrough']);
      expect(find.text('Popular on Pixiv'), findsOneWidget);

      final semantics = tester.ensureSemantics();
      expect(
        tester.getSemantics(find.byKey(const ValueKey('pixiv-walkthrough-31'))),
        isSemantics(label: 'Sommerfest', isButton: true, hasTapAction: true),
      );
      semantics.dispose();

      await _tap(tester, find.byKey(const ValueKey('pixiv-walkthrough-31')));
      expect(find.text('Sign in to open this work'), findsOneWidget);
      expect(find.byType(PixivIllustScreen), findsNothing);

      await _tap(tester, find.byType(FilledButton));
      expect(signIns, 1);
      await disposePixiv(tester);
    });
  });

  group('Pixivision', () {
    final parsed = parsePixivisionArticle(File('test/fixtures/Pixivision/article_en.html').readAsStringSync());

    testWidgets('the list opens an article', (tester) async {
      final api = _api(articles: [_article], article: parsed);
      await _pump(tester, const PixivisionListScreen(), api);
      await _tap(tester, find.byKey(const ValueKey('pixivision-article-9876')));
      expect(find.byType(PixivisionArticleScreen), findsOneWidget);
      expect(api.calls, ['spotlight', 'pixivision:9876']);
      await disposePixiv(tester);
    });

    testWidgets('an article shows its intro and works; a work opens, its artist too', (tester) async {
      final api = _api(article: parsed);
      await _pump(tester, const PixivisionArticleScreen(articleId: 9876, article: _article), api);
      expect(find.text('Autumn is here, and the cats know it.\n\nEnjoy these cosy pieces!'), findsOneWidget);
      expect(find.text('Maple Cat'), findsOneWidget);

      await _tap(tester, find.byKey(const ValueKey('pixivision-work-1001')));
      expect(tester.widget<PixivIllustScreen>(find.byType(PixivIllustScreen)).illust.id, 1001);
      await tester.pageBack();
      await settlePixiv(tester);

      await tester.ensureVisible(find.text('Sora'));
      await tester.pump();
      await _tap(tester, find.text('Sora'));
      expect(tester.widget<PixivUserScreen>(find.byType(PixivUserScreen)).userId, 502);
      await disposePixiv(tester);
    });

    testWidgets("an article's works leave out muted creators and works, as soon as they are muted", (tester) async {
      final api = _api(article: parsed);
      await _pump(tester, const PixivisionArticleScreen(articleId: 9876, article: _article), api);
      expect(find.byKey(const ValueKey('pixivision-work-1001')), findsOneWidget);
      expect(find.byKey(const ValueKey('pixivision-work-1002')), findsOneWidget);

      final mute = Provider.of<PixivMuteStore>(tester.element(find.byType(PixivisionArticleScreen)), listen: false);
      await mute.muteIllust(1001);
      await settlePixiv(tester);
      expect(find.byKey(const ValueKey('pixivision-work-1001')), findsNothing);
      expect(find.byKey(const ValueKey('pixivision-work-1002')), findsOneWidget);

      await mute.muteAuthor(parsed.works.last.userId);
      await settlePixiv(tester);
      expect(find.byKey(const ValueKey('pixivision-work-1002')), findsNothing);
      await disposePixiv(tester);
    });

    test('a pull keeps the article on screen, even when the page cannot be fetched', () async {
      final api = _api(article: parsed);
      final store = PixivisionArticleStore(api, 9876);
      addTearDown(store.destroy);
      await store.load();
      final loading = <bool>[];
      final stop = store.observer(onLoading: loading.add);
      addTearDown(stop);

      api.article = null;
      await store.refresh();
      expect(store.state, same(parsed));
      expect(store.error, isNull);
      expect(loading, isEmpty, reason: 'no full-page spinner over the article');
    });

    testWidgets('an article that cannot be read says so and offers a retry', (tester) async {
      await _pump(tester, const PixivisionArticleScreen(articleId: 1), _api());
      expect(find.byType(FullPageErrorWidget), findsOneWidget);
      await disposePixiv(tester);
    });
  });

  test('the session account counts signing out and a new account as a switch, not learning the id', () async {
    final prefs = PrefServiceCache(defaults: {optionPluginPixivUserId: 0});
    var switches = 0;
    final account = PixivSessionAccountStore(prefs, onChanged: () => switches++);
    addTearDown(account.destroy);
    Future<void> store(int id) async {
      await prefs.set(optionPluginPixivUserId, id);
      expect(account.behind, isTrue);
      account.check();
      expect(account.behind, isFalse);
    }

    await store(5);
    expect(switches, 0);
    await store(6);
    expect(switches, 1);
    await store(0);
    expect(switches, 2);
    account.check();
    expect(switches, 2, reason: 'checking again with nothing changed is not a switch');
  });

  test('turning Show R-18 or Hide AI either way empties the session lists', () async {
    final prefs = PrefServiceCache(
      defaults: {optionPluginPixivUserId: 5, optionPluginPixivShowR18: true, optionPluginPixivHideAi: false},
    );
    var changes = 0;
    final account = PixivSessionAccountStore(prefs, onChanged: () => changes++);
    addTearDown(account.destroy);
    for (final (key, value) in [
      (optionPluginPixivShowR18, false),
      (optionPluginPixivHideAi, true),
      (optionPluginPixivShowR18, true),
    ]) {
      await prefs.set(key, value);
      expect(account.behind, isTrue);
      account.check();
      expect(account.behind, isFalse);
    }
    expect(changes, 3);
  });

  testWidgets('suggested creators leave out authors the reader mutes', (tester) async {
    final api = _api(users: [_creator(7, 'Sora'), _creator(8, 'Kai')]);
    await _pump(tester, const PixivRecommendedUsersScreen(), api);
    expect(find.text('Kai'), findsOneWidget);

    final mute = Provider.of<PixivMuteStore>(tester.element(find.text('Sora')), listen: false);
    await mute.muteAuthor(8);
    await settlePixiv(tester);
    expect(find.text('Kai'), findsNothing);
    expect(find.text('Sora'), findsOneWidget);
    await disposePixiv(tester);
  });
}
