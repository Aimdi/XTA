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
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/plugin_session.dart';
import 'package:xta/plugins/reddit/reddit_auth.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_gallery_loader.dart';
import 'package:xta/plugins/reddit/reddit_store.dart';
import 'package:xta/plugins/reddit/reddit_subreddit_avatar.dart';
import 'package:xta/plugins/reddit/reddit_votes_store.dart';
import 'package:xta/saved/liked_tweet_model.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/subscriptions/users_model.dart';

/// Real SQLite on the widget-test clock, migrated once per test file.
Future<void> setUpHomeSourcesDatabase(String name) async {
  sqfliteFfiInit();
  databaseFactory = databaseFactoryFfiNoIsolate;
  final dir = await Directory.systemTemp.createTemp(name);
  await databaseFactory.setDatabasesPath(dir.path);
  await Repository().migrate();
  await Repository.readOnly();
}

class FakeRedditClient extends RedditClient {
  final requests = <({String subreddit, RedditSort sort})>[];

  @override
  Future<RedditListing> fetchSubreddit(
    String subreddit, {
    required String clientId,
    RedditSort sort = RedditSort.hot,
    RedditTimeFilter timeFilter = RedditTimeFilter.day,
    int limit = kRedditListingPageSize,
    String? after,
    String? userToken,
    bool preferPublic = false,
  }) async {
    requests.add((subreddit: subreddit, sort: sort));
    return RedditListing(
      posts: [
        for (var i = 0; i < 3; i++)
          RedditPost(
            id: '$subreddit-$i',
            subreddit: subreddit,
            title: 'A calm thread in r/$subreddit about reading on small screens, part ${i + 1}',
            author: 'reader$i',
            permalink: '/r/$subreddit/comments/$subreddit$i/',
            createdAt: DateTime(2026, 10, 7, 9, i),
            score: 120 + i,
            commentCount: 8 + i,
            isSelf: true,
          ),
      ],
    );
  }
}

class _NoIcons implements RedditIcons {
  @override
  RedditClient get client => throw UnimplementedError();

  @override
  Future<String?> iconFor(
    String subreddit, {
    String clientId = '',
    String? userToken,
    bool preferPublic = false,
  }) async => null;
}

class _Subreddits extends RedditSubredditsStore {
  _Subreddits(super.prefs, List<String> names) {
    update(names);
  }

  @override
  Future<void> load({bool force = false}) async {}
}

class _RedditSaved extends RedditSavedStore {
  _RedditSaved(super.prefs);

  @override
  Future<void> load() async {}
}

/// Assembled Home with Following, X, Pixiv and Reddit pinned.
class HomeSourcesHarness {
  final prefs = PrefServiceCache(
    defaults: {
      optionHomeFeedStripPlugins: [pluginIdPixiv, pluginIdReddit],
      optionSeededStripPlugins: [pluginIdPixiv, pluginIdReddit],
      optionPluginPixivEnabled: true,
      optionPluginPixivShowTab: true,
      optionPluginPixivRefreshToken: '',
      optionPluginRedditEnabled: true,
      optionPluginRedditShowTab: true,
      optionPluginRedditSort: redditSortHot,
      optionPluginRedditTimeFilter: redditTimeFilterDay,
      optionPluginRedditFeedMode: redditFeedModeFollowing,
      optionPluginRedditSelectedSubreddit: '',
      optionPluginRedditSource: redditSourceAuto,
      optionPluginRedditNsfwMode: redditNsfwModeTap,
      optionPluginRedditShowSpoilers: false,
      optionPluginRedditClientId: '',
      optionPluginRedditRefreshToken: '',
      optionMediaDefaultMute: true,
      optionThemeTrueBlack: true,
      optionThemeTrueBlackTweetCards: true,
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
  final FeedTabStore selected;
  final cache = FeedSessionCache();
  final scroll = ScrollController();
  final reddit = FakeRedditClient();
  final redditAuth = RedditAuth();
  final redditVotes = RedditVotesStore();
  late final groups = GroupsModel(prefs);
  late final subscriptions = SubscriptionsModel(prefs, groups);
  late final accountFilter = HomeAccountFilterStore(prefs);
  late final redditSubreddits = _Subreddits(prefs, const ['flutter', 'technology']);
  late final redditSaved = _RedditSaved(prefs);
  late final redditFeed = RedditFeedStore(reddit, redditSubreddits, prefs, auth: redditAuth);
  late final pixivClient = PixivClient(prefs);
  late final pixivMute = PixivMuteStore(prefs);
  late final pixivFeed = PixivFeedStore(pixivClient, filter: pixivMute.filter);

  HomeSourcesHarness(FeedTab tab) : selected = FeedTabStore(tab);

  Widget app({ThemeData? theme, double scale = 1, bool rtl = false, Locale? locale, Key? key}) => PrefService(
    service: prefs,
    child: MultiProvider(
      providers: [
        Provider<FeedTabStore>.value(value: selected),
        Provider(create: (_) => FeedStripStore(prefs), dispose: (_, store) => store.destroy()),
        Provider<HomeAccountFilterStore>.value(value: accountFilter),
        Provider(create: (_) => HomeGroupFilterStore(prefs), dispose: (_, store) => store.destroy()),
        Provider(create: (_) => NetworkRecentsStore(prefs), dispose: (_, store) => store.destroy()),
        Provider(create: (_) => ChromeAvatarStore(prefs), dispose: (_, store) => store.destroy()),
        Provider<GroupsModel>.value(value: groups),
        Provider<SubscriptionsModel>.value(value: subscriptions),
        Provider(create: (_) => CombinedGroupsStore(), dispose: (_, store) => store.destroy()),
        Provider(create: (_) => PluginSessionStore(), dispose: (_, store) => store.destroy()),
        Provider<FeedSessionCache>.value(value: cache),
        Provider(create: (_) => LikedTweetModel(), dispose: (_, store) => store.destroy()),
        Provider(create: (_) => SavedTweetModel(), dispose: (_, store) => store.destroy()),
        Provider<RedditClient>.value(value: reddit),
        Provider<RedditAuth>.value(value: redditAuth),
        Provider<RedditIcons>.value(value: _NoIcons()),
        Provider(create: (_) => RedditGalleryLoader(reddit)),
        Provider<RedditVotesStore>.value(value: redditVotes),
        Provider<RedditSubredditsStore>.value(value: redditSubreddits),
        Provider<RedditSavedStore>.value(value: redditSaved),
        Provider<RedditFeedStore>.value(value: redditFeed),
        Provider<PixivClient>.value(value: pixivClient),
        Provider<PixivMuteStore>.value(value: pixivMute),
        Provider<PixivFeedStore>.value(value: pixivFeed),
      ],
      child: MaterialApp(
        key: key,
        debugShowCheckedModeBanner: false,
        theme: theme,
        locale: locale,
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
        home: Scaffold(
          drawer: const Drawer(child: Center(child: Text('Drawer fixture'))),
          body: FeedScreen(scrollController: scroll, id: '-1', name: 'Home'),
        ),
      ),
    ),
  );

  Future<void> close(WidgetTester tester) async {
    await tester.pumpWidget(const SizedBox());
    await tester.pump();
    scroll.dispose();
    await selected.destroy();
    await accountFilter.destroy();
    await subscriptions.destroy();
    await groups.destroy();
    await redditFeed.destroy();
    await redditSubreddits.destroy();
    await redditSaved.destroy();
    await pixivFeed.destroy();
    await pixivMute.destroy();
    redditAuth.httpClient.close();
    reddit.httpClient.close();
  }
}
