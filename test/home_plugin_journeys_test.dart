import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/combined_groups.dart';
import 'package:xta/group/feed_session_cache.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/home/_feed.dart';
import 'package:xta/home/chrome_avatar.dart';
import 'package:xta/home/feed_strip_store.dart';
import 'package:xta/home/home_account_filter.dart';
import 'package:xta/home/home_chrome.dart';
import 'package:xta/home/home_group_filter.dart';
import 'package:xta/home/network_recents_store.dart';
import 'package:xta/plugins/hackernews/hn_client.dart';
import 'package:xta/plugins/hackernews/hn_models.dart';
import 'package:xta/plugins/hackernews/hn_screen.dart';
import 'package:xta/plugins/hackernews/hn_store.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_session.dart';
import 'package:xta/plugins/rss/rss_client.dart';
import 'package:xta/plugins/rss/rss_models.dart';
import 'package:xta/plugins/rss/rss_store.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/ui/x_look_theme.dart';

class _HnClient extends HackerNewsClient {
  final calls = <HnFeed>[];
  @override
  Future<HnStoryPage> feed(HnFeed feed, {int page = 0}) async {
    calls.add(feed);
    return HnStoryPage(
      page: page,
      hasMore: false,
      stories: [
        for (var i = 0; i < 20; i++)
          HnStory(id: 100 + i, title: '${feed.name} story $i — a useful reading fixture', score: 23, commentCount: 4),
      ],
    );
  }
}

class _Feeds extends RssFeedsStore {
  _Feeds(super.prefs) {
    update(const [RssFeed(id: 'one', feedUrl: 'https://example.test/rss', name: 'Reading room')]);
  }
  @override
  Future<void> load() async {}
}

class _Read extends RssReadStore {
  _Read(super.prefs);
  @override
  Future<void> load() async {}
}

class _Tags extends RssTagsStore {
  _Tags(super.prefs);
  @override
  Future<void> load() async {}
}

class _RssClient extends RssClient {
  var calls = 0;
  @override
  Future<List<RssItem>> fetchItems(RssFeed feed) async {
    calls++;
    return const [RssItem(id: 'article', feedId: 'one', feedTitle: 'Reading room', title: 'A quiet article')];
  }
}

void main() {
  setUpAll(() async {
    autoUpdateGoldenFiles = true;
    await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-Regular.ttf'))).load();
    await (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
    final dir = await Directory.systemTemp.createTemp('xta-home-plugin-journeys');
    await databaseFactory.setDatabasesPath(dir.path);
    await Repository().migrate();
  });

  testWidgets('real Home keeps source, sections, loaded pages and scroll through full client and pin edits', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final prefs = PrefServiceCache(
      defaults: {
        optionPluginHnEnabled: true,
        optionPluginHnShowTab: true,
        optionPluginRssEnabled: true,
        optionPluginRssShowTab: true,
        optionHomeFeedStripPlugins: [pluginIdHackerNews, pluginIdRss],
        optionSeededStripPlugins: [pluginIdHackerNews, pluginIdRss],
        optionSubscriptionGroupsOrderByField: 'name',
        optionSubscriptionGroupsOrderByAscending: true,
      },
    );
    final selected = FeedTabStore(const FeedTab(pluginIdHackerNews));
    final strip = FeedStripStore(prefs);
    final outer = ScrollController();
    final hn = _HnClient();
    final rss = _RssClient();
    final groups = GroupsModel(prefs);
    final feeds = _Feeds(prefs);
    final timeline = RssTimelineStore(rss, feeds);
    final session = PluginSessionStore();
    await tester.pumpWidget(
      PrefService(
        service: prefs,
        child: MultiProvider(
          providers: [
            Provider<FeedTabStore>.value(value: selected),
            Provider<FeedStripStore>.value(value: strip),
            Provider(create: (_) => HomeAccountFilterStore(prefs)),
            Provider(create: (_) => HomeGroupFilterStore(prefs)),
            Provider(create: (_) => NetworkRecentsStore(prefs)),
            Provider(create: (_) => ChromeAvatarStore(prefs)),
            Provider<GroupsModel>.value(value: groups),
            Provider(create: (_) => SubscriptionsModel(prefs, groups)),
            Provider(create: (_) => CombinedGroupsStore()),
            Provider<PluginSessionStore>.value(value: session),
            Provider(create: (_) => FeedSessionCache()),
            Provider<HackerNewsClient>.value(value: hn),
            Provider(create: (_) => HnLikesStore(prefs)),
            Provider(create: (_) => HnSavedStore(prefs)),
            Provider(create: (_) => HnFollowsStore(prefs)),
            Provider<RssFeedsStore>.value(value: feeds),
            Provider<RssTimelineStore>.value(value: timeline),
            Provider<RssReadStore>(create: (_) => _Read(prefs)),
            Provider<RssTagsStore>(create: (_) => _Tags(prefs)),
          ],
          child: MaterialApp(
            theme: xLookLightTheme(null),
            localizationsDelegates: const [
              L10n.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
              GlobalCupertinoLocalizations.delegate,
            ],
            supportedLocales: L10n.delegate.supportedLocales,
            home: Scaffold(
              drawer: const Drawer(),
              body: FeedScreen(scrollController: outer, id: '-1', name: 'Home'),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byType(HnScreen), findsOneWidget);
    expect(hn.calls, [HnFeed.top]);
    await expectLater(find.byType(FeedScreen), matchesGoldenFile('../review-artifacts/renders/home-hackernews.png'));
    expect(rss.calls, 0, reason: 'An unvisited plugin must not fetch.');
    expect(find.byTooltip('Home feed accounts'), findsNothing);
    final sections = find.descendant(of: find.byType(PluginHomeChrome), matching: find.text('New'));
    await tester.tap(sections);
    await tester.pumpAndSettle();
    expect(hn.calls, [HnFeed.top, HnFeed.newest]);
    final hnList = find.descendant(of: find.byType(HnScreen), matching: find.byType(Scrollable)).last;
    await tester.drag(hnList, const Offset(0, -500));
    await tester.pumpAndSettle();
    final scrollBefore = tester.state<ScrollableState>(hnList).position.pixels;
    expect(scrollBefore, greaterThan(0));
    final open = find.byKey(const ValueKey('open-client-hackernews'));
    expect(tester.getSize(open).height, greaterThanOrEqualTo(48));
    await tester.tap(open);
    await tester.pumpAndSettle();
    expect(find.byType(BackButton), findsOneWidget);
    expect(hn.calls, [HnFeed.top, HnFeed.newest], reason: 'Full-client entry reuses the selected feed.');
    await tester.tap(find.byType(BackButton));
    await tester.pumpAndSettle();
    expect(selected.state.id, pluginIdHackerNews);
    expect(tester.state<ScrollableState>(hnList).position.pixels, closeTo(scrollBefore, 1));
    final homeRss = find.descendant(of: find.byType(HomeFeedStrip), matching: find.text('RSS'));
    await tester.ensureVisible(homeRss);
    await tester.tap(homeRss);
    await tester.pumpAndSettle();
    expect(rss.calls, 1);
    expect(find.text('A quiet article'), findsOneWidget);
    await expectLater(find.byType(FeedScreen), matchesGoldenFile('../review-artifacts/renders/home-rss.png'));
    final rssContext = tester.element(find.text('A quiet article'));
    final markRead = rssContext.read<RssReadStore>().markRead('article');
    // Store.execute debounces for 50ms; advance the widget clock before awaiting.
    await tester.pump(const Duration(milliseconds: 100));
    await markRead;
    await tester.tap(find.widgetWithText(FilterChip, 'Unread'));
    await tester.pumpAndSettle();
    expect(find.text('No items match these filters'), findsOneWidget);
    await tester.tap(find.text('Reset filters'));
    await tester.pumpAndSettle();
    expect(find.text('A quiet article'), findsOneWidget);
    expect(rss.calls, 1, reason: 'Filtering and resetting must reuse loaded articles.');
    await strip.reorder(1, 0);
    await tester.pumpAndSettle();
    expect(selected.state.id, pluginIdRss);
    final homeHn = find.descendant(of: find.byType(HomeFeedStrip), matching: find.text('Hacker News'));
    await tester.ensureVisible(homeHn);
    await tester.tap(homeHn);
    await tester.pumpAndSettle();
    expect(hn.calls, [HnFeed.top, HnFeed.newest]);
    expect(tester.state<ScrollableState>(hnList).position.pixels, closeTo(scrollBefore, 1));
    // Disabling through the same persisted setting + strip update as management.
    await prefs.set(optionPluginHnEnabled, false);
    await strip.remove(pluginIdHackerNews);
    await tester.pump();
    expect(selected.state, FeedTab.following);
    expect(find.byType(HnScreen), findsNothing);
    expect(find.byKey(const ValueKey('open-client-hackernews')), findsNothing);
    await tester.pumpWidget(const SizedBox());
    await tester.pump();
    await session.destroy();
    outer.dispose();
    timeline.destroy();
    feeds.destroy();
    groups.destroy();
    selected.destroy();
    strip.destroy();
    expect(tester.takeException(), isNull);
  });
}
