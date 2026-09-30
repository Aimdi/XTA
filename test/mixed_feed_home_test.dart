import 'dart:io';

import 'package:flutter/material.dart';
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
import 'package:xta/home/home_group_filter.dart';
import 'package:xta/home/network_recents_store.dart';
import 'package:xta/plugins/hackernews/hn_client.dart';
import 'package:xta/plugins/hackernews/hn_models.dart';
import 'package:xta/plugins/hackernews/hn_store.dart';
import 'package:xta/plugins/plugin_session.dart';
import 'package:xta/plugins/rss/rss_client.dart';
import 'package:xta/plugins/rss/rss_models.dart';
import 'package:xta/plugins/rss/rss_store.dart';
import 'package:xta/reading/mixed_feed_definition.dart';
import 'package:xta/reading/mixed_feed_store.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/ui/x_look_theme.dart';

class _HnClient extends HackerNewsClient {
  var calls = 0;
  @override
  Future<HnStoryPage> feed(HnFeed feed, {int page = 0}) async {
    calls++;
    return HnStoryPage(
      page: page,
      hasMore: false,
      stories: [
        for (var i = 0; i < 3; i++)
          HnStory(id: 100 + i, title: 'Story $i', createdAt: DateTime.utc(2026, 9, 30, 12 - i * 2)),
      ],
    );
  }
}

class _RssClient extends RssClient {
  var calls = 0;
  @override
  Future<List<RssItem>> fetchItems(RssFeed feed) async {
    calls++;
    return [
      RssItem(
        id: 'article',
        feedId: feed.id,
        feedTitle: feed.name,
        title: 'A quiet article',
        publishedAt: DateTime.utc(2026, 9, 30, 11),
      ),
    ];
  }
}

class _Feeds extends RssFeedsStore {
  _Feeds(super.prefs) {
    update(const [RssFeed(id: 'one', feedUrl: 'https://example.test/rss', name: 'Reading room')]);
  }

  @override
  Future<void> load() async {}

  @override
  Future<List<RssFeed>> saved() async => state;
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

const _mix = MixedFeedDefinition(
  id: 'm1',
  name: 'Morning reading',
  sources: [
    MixedFeedSource(kind: 'hackernews.feed', value: 'top', label: 'Hacker News · Top'),
    MixedFeedSource(kind: 'rss.all', label: 'All followed feeds'),
  ],
);

void main() {
  setUpAll(() async {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfiNoIsolate;
    final dir = await Directory.systemTemp.createTemp('xta-mixed-feed-home');
    await databaseFactory.setDatabasesPath(dir.path);
    await Repository().migrate();
  });

  testWidgets('a mix is a Home tab that keeps its posts through edits and leaves when deleted', (tester) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final prefs = PrefServiceCache(
      defaults: {
        optionPluginHnEnabled: true,
        optionPluginRssEnabled: true,
        optionHomeFeedStripPlugins: <String>[],
        optionSeededStripPlugins: [pluginIdHackerNews, pluginIdRss],
        optionSubscriptionGroupsOrderByField: 'name',
        optionSubscriptionGroupsOrderByAscending: true,
      },
    );
    final mixes = MixedFeedStore.forPrefs(prefs);
    await tester.runAsync(() => mixes.save(_mix));
    final selected = FeedTabStore(FeedTab(_mix.tabId));
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
            Provider<RssClient>.value(value: rss),
            Provider<RssFeedsStore>.value(value: feeds),
            Provider<RssTimelineStore>.value(value: timeline),
            Provider<RssReadStore>(create: (_) => _Read(prefs)),
            Provider<RssTagsStore>(create: (_) => _Tags(prefs)),
          ],
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
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
    expect(find.text('Morning reading'), findsOneWidget);
    expect(find.text('Story 0'), findsOneWidget);
    expect(find.text('A quiet article'), findsOneWidget);
    expect(tester.getTopLeft(find.text('Story 0')).dy, lessThan(tester.getTopLeft(find.text('A quiet article')).dy));
    expect((hn.calls, rss.calls), (1, 1));

    await tester.runAsync(() => mixes.save(_mix.copyWith(name: 'Evening reading')));
    await tester.pumpAndSettle();
    expect(find.text('Evening reading'), findsOneWidget);
    expect(find.text('A quiet article'), findsOneWidget);
    expect((hn.calls, rss.calls), (1, 1), reason: 'A rename must not read the sources again.');

    await tester.tap(find.byKey(ValueKey('plugin-source-picker-${_mix.tabId}')));
    await tester.pumpAndSettle();
    expect(find.byKey(ValueKey('home-source-${_mix.tabId}')), findsOneWidget);
    await tester.tapAt(const Offset(4, 4));
    await tester.pumpAndSettle();

    await tester.runAsync(() => mixes.remove(_mix.id));
    await tester.pumpAndSettle();
    expect(selected.state, FeedTab.following);
    expect(find.text('A quiet article'), findsNothing);

    await tester.pumpWidget(const SizedBox());
    await tester.pump();
    await session.destroy();
    outer.dispose();
    timeline.destroy();
    await mixes.destroy();
  });
}
