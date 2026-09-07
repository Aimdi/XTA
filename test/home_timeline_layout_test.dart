import 'dart:io';

import 'package:dart_twitter_api/twitter_api.dart' show User;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/combined_groups.dart';
import 'package:xta/group/feed_session_cache.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/group/group_screen.dart';
import 'package:xta/home/_feed.dart';
import 'package:xta/home/_for_you.dart';
import 'package:xta/home/chrome_avatar.dart';
import 'package:xta/home/feed_strip_store.dart';
import 'package:xta/home/home_account_filter.dart';
import 'package:xta/home/home_chrome.dart';
import 'package:xta/home/home_group_filter.dart';
import 'package:xta/home/network_recents_store.dart';
import 'package:xta/plugins/plugin_session.dart';
import 'package:xta/saved/liked_tweet_model.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/ui/x_look_theme.dart';

const _before = bool.fromEnvironment('HOME_LAYOUT_BEFORE');
const _media = ValueKey('home-media-toggle');
const _order = ValueKey('home-order-menu');

List<TweetChain> _posts() {
  const texts = [
    'Took the long way home today. The best part of the walk was the bit without a destination.',
    'A small weekend project: a reading lamp made from an old camera tripod. Finally found a use for the spare parts.',
    'A reminder to keep making things, even when the first version is a little rough around the edges.',
    'Early trains, a notebook, and a whole day ahead. A good way to start a Monday.',
    'One more field note from the weekend: leave room for a detour.',
  ];
  const names = ['Maya Chen', 'Alex Morgan', 'Sam Rivera', 'Jordan Lee', 'Maya Chen'];
  return [
    for (var i = 0; i < texts.length; i++)
      TweetChain(
        id: '${100 + i}',
        isPinned: false,
        tweets: [
          TweetWithCard()
            ..idStr = '${100 + i}'
            ..fullText = texts[i]
            ..lang = 'en'
            ..createdAt = DateTime(2026, 9, 7, 8, i)
            ..favoriteCount = 12 + i
            ..retweetCount = 3 + i
            ..user = (User()
              ..idStr = 'reader'
              ..name = names[i]
              ..screenName = ['mayac', 'alexm', 'samr', 'jordanl', 'mayac'][i]
              ..verified = false),
        ],
      ),
  ];
}

class _HomeHarness {
  final prefs = PrefServiceCache(
    defaults: {
      optionHomeFeedStripPlugins: <String>[],
      optionSeededStripPlugins: <String>[],
      optionMediaDefaultMute: true,
      optionMediaGridColumns: 2,
      optionMediaGridLayout: mediaGridLayoutMasonry,
      optionNonConfirmationBiasMode: false,
      optionThemeTrueBlack: true,
      optionThemeTrueBlackTweetCards: true,
      optionTweetsShowSubscribeBadge: false,
      optionUseAbsoluteTimestamp: true,
      optionShareBaseUrl: 'https://x.com',
      optionLocale: optionLocaleDefault,
      optionZenMode: false,
      optionCalmMode: false,
      optionDisableAnimations: true,
      optionSubscriptionGroupsOrderByField: 'name',
      optionSubscriptionGroupsOrderByAscending: true,
      optionGlobalIncludeReplies: true,
      optionGlobalIncludeRetweets: true,
    },
  );
  final selected = FeedTabStore(FeedTab.following);
  final cache = FeedSessionCache();
  final scroll = ScrollController();
  late final groups = GroupsModel(prefs);

  Future<void> seed() async {
    final db = await Repository.writable();
    await db.delete(tableSubscription);
    await db.update(tableSubscriptionGroup, {'popular': 0, 'custom': 0}, where: 'id = ?', whereArgs: ['-1']);
    await db.insert(
      tableSubscription,
      UserSubscription(
        id: 'reader',
        screenName: 'reader',
        name: 'Reading fixture',
        profileImageUrlHttps: null,
        verified: false,
        createdAt: DateTime(2026, 9, 7),
        inFeed: true,
      ).toMap(),
    );
    final controller = cache.getOrCreateController('home--1');
    controller.loader = (_) async => (chains: _posts(), nextCursor: null);
    await controller.softRefresh();
    expect(controller.items, hasLength(5));
  }

  Widget app(ThemeData theme, {double scale = 1, bool rtl = false}) => PrefService(
    service: prefs,
    child: MultiProvider(
      providers: [
        Provider<FeedTabStore>.value(value: selected),
        Provider(create: (_) => FeedStripStore(prefs), dispose: (_, store) => store.destroy()),
        Provider(create: (_) => HomeAccountFilterStore(prefs), dispose: (_, store) => store.destroy()),
        Provider(create: (_) => HomeGroupFilterStore(prefs), dispose: (_, store) => store.destroy()),
        Provider(create: (_) => NetworkRecentsStore(prefs), dispose: (_, store) => store.destroy()),
        Provider(create: (_) => ChromeAvatarStore(prefs), dispose: (_, store) => store.destroy()),
        Provider<GroupsModel>.value(value: groups),
        Provider(create: (_) => SubscriptionsModel(prefs, groups), dispose: (_, store) => store.destroy()),
        Provider(create: (_) => CombinedGroupsStore(), dispose: (_, store) => store.destroy()),
        Provider(create: (_) => PluginSessionStore(), dispose: (_, store) => store.destroy()),
        Provider<FeedSessionCache>.value(value: cache),
        Provider(create: (_) => LikedTweetModel(), dispose: (_, store) => store.destroy()),
        Provider(create: (_) => SavedTweetModel(), dispose: (_, store) => store.destroy()),
      ],
      child: MaterialApp(
        theme: theme,
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: L10n.delegate.supportedLocales,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(scale)),
          child: Directionality(textDirection: rtl ? TextDirection.rtl : TextDirection.ltr, child: child!),
        ),
        home: RepaintBoundary(
          key: const ValueKey('home-render'),
          child: Scaffold(
            drawer: const Drawer(child: Center(child: Text('Drawer fixture'))),
            body: FeedScreen(scrollController: scroll, id: '-1', name: 'Home'),
            bottomNavigationBar: HomeNavigationBar(
              selectedIndex: 0,
              showLabels: true,
              disableAnimations: true,
              onSelected: (_) {},
              items: const [
                HomeNavigationItem(label: 'Home', icon: Icon(Icons.home_outlined), selectedIcon: Icon(Icons.home)),
                HomeNavigationItem(
                  label: 'Subscriptions',
                  icon: Icon(Icons.people_outlined),
                  selectedIcon: Icon(Icons.people),
                ),
                HomeNavigationItem(
                  label: 'Discover',
                  icon: Icon(Icons.search_outlined),
                  selectedIcon: Icon(Icons.search),
                ),
                HomeNavigationItem(
                  label: 'Saved',
                  icon: Icon(Icons.bookmark_border_outlined),
                  selectedIcon: Icon(Icons.bookmark),
                ),
              ],
            ),
          ),
        ),
      ),
    ),
  );

  Future<void> close(WidgetTester tester) async {
    await tester.pumpWidget(const SizedBox());
    await tester.pump();
    scroll.dispose();
    selected.destroy();
    groups.destroy();
    cache.getOrCreateController('home--1').dispose();
  }
}

Future<void> _waitForFollowing(WidgetTester tester) async {
  final post = find.textContaining('Took the long way home', findRichText: true);
  for (var frame = 0; frame < 12 && post.evaluate().isEmpty; frame++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
  await tester.pumpAndSettle();
}

void main() {
  setUpAll(() async {
    autoUpdateGoldenFiles = true;
    await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-Regular.ttf'))).load();
    await (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfiNoIsolate;
    final dir = await Directory.systemTemp.createTemp('xta-home-layout');
    await databaseFactory.setDatabasesPath(dir.path);
    await Repository().migrate();
  });

  for (final variant in ['light', 'dark', 'black', 'large-rtl']) {
    testWidgets('populated Home layout $variant', (tester) async {
      final large = variant == 'large-rtl';
      tester.view.physicalSize = large ? const Size(320, 740) : const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final h = _HomeHarness();
      addTearDown(() => h.close(tester));
      await tester.runAsync(h.seed);
      final theme = switch (variant) {
        'dark' => xLookDimTheme(null),
        'black' => xLookLightsOutTheme(null),
        _ => xLookLightTheme(null),
      };
      await tester.pumpWidget(h.app(theme, scale: large ? 2 : 1, rtl: large));
      await _waitForFollowing(tester);
      expect(tester.takeException(), isNull);
      await expectLater(
        find.byKey(const ValueKey('home-render')),
        matchesGoldenFile('../review-artifacts/renders/home-following-$variant.png'),
      );
      expect(find.textContaining('Took the long way home', findRichText: true), findsOneWidget);
      if (!_before) {
        final dock = tester.getRect(find.byType(HomeFeedStrip));
        final feed = tester.getRect(find.byType(SubscriptionGroupScreenContent));
        final nav = tester.getRect(find.byType(HomeNavigationBar));
        expect(feed.bottom, lessThanOrEqualTo(dock.top));
        expect(dock.bottom, closeTo(nav.top, 1));
        expect(tester.getSize(find.byKey(_media)).height, greaterThanOrEqualTo(48));
        expect(find.byTooltip('Filters').hitTestable(), findsOneWidget);
        expect(find.byTooltip('Home feed accounts').hitTestable(), findsOneWidget);
        final media = find.byKey(_media);
        await tester.ensureVisible(media);
        expect(media.hitTestable(), findsOneWidget);
      }
    });
  }

  testWidgets('Home media reuses cached posts and survives switching sources', (tester) async {
    final h = _HomeHarness();
    addTearDown(() => h.close(tester));
    await tester.runAsync(h.seed);
    await tester.pumpWidget(h.app(xLookLightTheme(null)));
    await _waitForFollowing(tester);
    final cached = h.cache.getOrCreateController('home--1');
    final items = cached.items;
    await tester.tap(find.byKey(_media));
    await tester.pumpAndSettle();
    expect(
      tester.widget<SubscriptionGroupScreenContent>(find.byType(SubscriptionGroupScreenContent)).mediaOnly,
      isTrue,
    );
    expect(cached.items, same(items));
    expect(h.cache.readMediaOnly('home--1'), isTrue);
    expect(find.text(L10n.current.could_not_find_any_posts_with_media), findsOneWidget);
    h.selected.select(FeedTab.foryou);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));
    expect(find.byKey(_media), findsNothing);
    final original = tester.widget<ForYouTweets>(find.byType(ForYouTweets)).feed;
    await tester.tap(find.byIcon(Icons.refresh));
    await tester.pump();
    expect(tester.widget<ForYouTweets>(find.byType(ForYouTweets)).feed, isNot(same(original)));
    h.selected.select(FeedTab.following);
    await tester.pumpAndSettle();
    expect(
      tester.widget<SubscriptionGroupScreenContent>(find.byType(SubscriptionGroupScreenContent)).mediaOnly,
      isTrue,
    );
    await tester.tap(find.byKey(_media));
    await tester.pumpAndSettle();
    expect(cached.items, same(items));
    expect(find.textContaining('Took the long way home', findRichText: true), findsOneWidget);
    expect(tester.takeException(), isNull);
  }, skip: _before);

  testWidgets('Home order updates the actual Following group and keeps filters reachable', (tester) async {
    final h = _HomeHarness();
    addTearDown(() => h.close(tester));
    await tester.runAsync(h.seed);
    await tester.pumpWidget(h.app(xLookLightTheme(null)));
    await _waitForFollowing(tester);
    await tester.tap(find.byTooltip('Filters'));
    await tester.pumpAndSettle();
    expect(find.text(L10n.current.include_replies), findsOneWidget);
    Navigator.pop(tester.element(find.text(L10n.current.include_replies)));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(_order));
    await tester.pumpAndSettle();
    await tester.tap(find.text(L10n.current.popular));
    await tester.pump(const Duration(milliseconds: 100));
    await tester.pump();
    final model = tester.element(find.byType(SubscriptionGroupScreenContent)).read<GroupModel>();
    expect(model.state.popular, isTrue);
    final rows = await tester.runAsync(() async {
      final db = await Repository.readOnly();
      return db.query(tableSubscriptionGroup, where: 'id = ?', whereArgs: ['-1']);
    });
    expect(rows!.single['popular'], 1);
    expect(tester.takeException(), isNull);
  }, skip: _before);
}
