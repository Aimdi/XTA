import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:provider/single_child_widget.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_detail_stats.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_api.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_models.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_illust_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_recommended_users_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_series_screen.dart';
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

    Future<void> pumpHome(WidgetTester tester, FakePixivDiscoveryApi api) async {
      final screenClient = FakePixivScreenClient(
        PrefServiceCache(),
        followingWorks: [pixivWork(id: 600, pages: 1, title: 'Followed work')],
      );
      feed = PixivFeedStore(screenClient);
      await _pump(
        tester,
        PixivScreen(scrollController: scroll),
        api,
        client: (prefs) =>
            FakePixivScreenClient(prefs, recommendedWorks: [pixivWork(id: 700, pages: 1, title: 'Suggested work')]),
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

    testWidgets('Following can be narrowed to public or private follows', (tester) async {
      final api = _api();
      await pumpHome(tester, api);
      expect(find.byKey(const ValueKey('pixiv-follow-restrict')), findsOneWidget);

      await _tap(tester, find.byTooltip('Privately followed creators'));
      expect(api.calls.last, 'following:private');
      await _tap(tester, find.byTooltip('Publicly followed creators'));
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
  });

  group('the watchlist', () {
    late PixivWatchlistStore store;

    Future<void> pumpWatchlist(WidgetTester tester, {double textScale = 1, Size size = const Size(390, 844)}) async {
      final api = _api(watchlist: [_watched], series: _series, seriesWorks: [pixivWork(id: 11, pages: 1)]);
      store = pixivMangaWatchlistStore(api)..refresh();
      addTearDown(store.destroy);
      await _pump(
        tester,
        Scaffold(body: PixivMangaWatchlistFeed(store: store)),
        api,
        textScale: textScale,
        size: size,
      );
    }

    testWidgets('View latest opens the newest work', (tester) async {
      await pumpWatchlist(tester);
      await _tap(tester, find.byKey(const ValueKey('pixiv-watchlist-latest-266067')));
      final opened = tester.widget<PixivIllustScreen>(find.byType(PixivIllustScreen));
      expect(opened.illust.id, 125547965);
      await disposePixiv(tester);
    });

    testWidgets('a row opens its series', (tester) async {
      await pumpWatchlist(tester);
      await _tap(tester, find.text('Autumn Diary'));
      expect(tester.widget<PixivSeriesScreen>(find.byType(PixivSeriesScreen)).seriesId, 266067);
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

    testWidgets('an article that cannot be read says so and offers a retry', (tester) async {
      await _pump(tester, const PixivisionArticleScreen(articleId: 1), _api());
      expect(find.byType(FullPageErrorWidget), findsOneWidget);
      await disposePixiv(tester);
    });
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
