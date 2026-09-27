import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_profile_tabs.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_auth.dart';
import 'package:xta/plugins/reddit/reddit_screen.dart';
import 'package:xta/plugins/reddit/reddit_store.dart';
import 'package:xta/plugins/stocks/stocks_screen.dart';
import 'package:xta/plugins/stocks/stocks_store.dart';
import 'package:xta/plugins/threads/threads_api.dart';
import 'package:xta/plugins/threads/threads_client.dart';
import 'package:xta/plugins/threads/threads_direct_client.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_profile_screen.dart';
import 'package:xta/plugins/threads/threads_store.dart';
import 'package:xta/tweet/ticker/ticker_quote_cache.dart';
import 'package:xta/ui/reader_swipe_navigation.dart';

Widget _app(Widget child) => MaterialApp(
  localizationsDelegates: const [
    L10n.delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
  ],
  supportedLocales: L10n.delegate.supportedLocales,
  home: child,
);

class _RedditSubs extends RedditSubredditsStore {
  _RedditSubs(super.prefs);
  @override
  Future<void> load({bool force = false}) async {}
}

class _RedditClient extends RedditClient {
  final requests = <String>[];
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
    requests.add(subreddit);
    return const RedditListing(posts: []);
  }
}

class _RedditSaved extends RedditSavedStore {
  _RedditSaved(super.prefs);
  @override
  Future<void> load() async {}
}

class _Watchlist extends StocksWatchlistStore {
  @override
  Future<void> load() async {}
}

class _Quotes extends TickerQuoteCache {
  @override
  Future<void> ensure(Iterable<String> symbols) async {}
}

class _ThreadsDirect extends ThreadsDirectClient {
  _ThreadsDirect(super.prefs);
  @override
  Future<ThreadsProfile> fetchGuestProfile(String handle) async =>
      ThreadsProfile.fromJson({'pk': '1', 'id': '1', 'username': handle, 'full_name': 'Reader'});
}

class _ThreadsFeed extends ThreadsFeedStore {
  _ThreadsFeed(super.client, super.direct, super.prefs, super.accounts);
  var reads = 0;
  @override
  Future<List<ThreadsPost>> postsFor(
    List<String> handles, {
    bool forceRefresh = false,
    void Function(List<ThreadsPost>)? onPartial,
  }) async {
    reads++;
    return [];
  }
}

void main() {
  for (final embedded in [false, true]) {
    testWidgets('Reddit body ${embedded ? 'defers to Home' : 'switches discovery sections'}', (tester) async {
      final prefs = PrefServiceCache();
      final subs = _RedditSubs(prefs);
      final saved = _RedditSaved(prefs);
      final auth = RedditAuth();
      final client = _RedditClient();
      final feed = RedditFeedStore(client, subs, prefs);
      final scroll = ScrollController();
      final homeChanges = <int>[];
      Widget reader = RedditScreen(scrollController: scroll);
      if (embedded) {
        reader = ReaderSwipeNavigation(
          index: 0,
          count: 3,
          identity: 'home',
          onChanged: (next) {
            homeChanges.add(next);
            return true;
          },
          child: PluginEmbedded(child: reader),
        );
      }
      await tester.pumpWidget(
        PrefService(
          service: prefs,
          child: MultiProvider(
            providers: [
              Provider<RedditSubredditsStore>.value(value: subs),
              Provider<RedditSavedStore>.value(value: saved),
              Provider<RedditAuth>.value(value: auth),
              Provider<RedditClient>.value(value: client),
              Provider<RedditFeedStore>.value(value: feed),
            ],
            child: _app(reader),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.dragFrom(const Offset(500, 400), const Offset(-140, 0));
      await tester.pumpAndSettle();
      final chrome = tester.widget<RedditHomeChrome>(find.byType(RedditHomeChrome));
      expect(chrome.source.mode, embedded ? RedditFeedMode.following : RedditFeedMode.popular);
      expect(homeChanges, embedded ? [1] : isEmpty);
      if (!embedded) {
        expect(prefs.get<String>(optionPluginRedditFeedMode), 'popular');
        expect(client.requests, ['popular']);
      }
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox());
      await tester.pump();
      await feed.destroy();
      await subs.destroy();
      await saved.destroy();
      client.httpClient.close();
      auth.httpClient.close();
      scroll.dispose();
    });

    testWidgets('Stocks body ${embedded ? 'defers to Home' : 'switches sections'}', (tester) async {
      final watchlist = _Watchlist();
      final quotes = _Quotes();
      final scroll = ScrollController();
      final homeChanges = <int>[];
      Widget reader = StocksScreen(scrollController: scroll);
      if (embedded) {
        reader = ReaderSwipeNavigation(
          index: 0,
          count: 3,
          identity: 'home',
          onChanged: (next) {
            homeChanges.add(next);
            return true;
          },
          child: PluginEmbedded(child: reader),
        );
      }
      await tester.pumpWidget(
        MultiProvider(
          providers: [
            Provider<StocksWatchlistStore>.value(value: watchlist),
            Provider<TickerQuoteCache>.value(value: quotes),
          ],
          child: _app(reader),
        ),
      );
      await tester.pumpAndSettle();
      await tester.dragFrom(const Offset(500, 400), const Offset(-140, 0));
      await tester.pumpAndSettle();
      final chrome = tester.widget<PluginHomeChrome>(find.byType(PluginHomeChrome));
      expect(chrome.tabs[embedded ? 0 : 1].selected, isTrue);
      expect(homeChanges, embedded ? [1] : isEmpty);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox());
      await tester.pump();
      await watchlist.destroy();
      await quotes.destroy();
      scroll.dispose();
    });
  }

  testWidgets('Threads profile body swipes and visible tabs share selection without reloading', (tester) async {
    final prefs = PrefServiceCache();
    final accounts = ThreadsAccountsStore();
    final client = ThreadsClient();
    final direct = _ThreadsDirect(prefs);
    final feed = _ThreadsFeed(client, direct, prefs, accounts);
    final api = ThreadsApi();
    await tester.pumpWidget(
      PrefService(
        service: prefs,
        child: MultiProvider(
          providers: [
            Provider<ThreadsAccountsStore>.value(value: accounts),
            Provider<ThreadsFeedStore>.value(value: feed),
            Provider<ThreadsDirectClient>.value(value: direct),
            Provider<ThreadsApi>.value(value: api),
          ],
          child: _app(const ThreadsProfileScreen(username: 'reader')),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.dragFrom(const Offset(500, 500), const Offset(-140, 0));
    await tester.pumpAndSettle();
    expect(tester.widget<PluginProfileTabBar>(find.byType(PluginProfileTabBar)).selected, PluginProfileFeedTab.replies);
    await tester.dragFrom(const Offset(500, 500), const Offset(-140, 0));
    await tester.pumpAndSettle();
    expect(tester.widget<PluginProfileTabBar>(find.byType(PluginProfileTabBar)).selected, PluginProfileFeedTab.media);
    await tester.tap(find.text(L10n.current.tweets));
    await tester.pumpAndSettle();
    expect(tester.widget<PluginProfileTabBar>(find.byType(PluginProfileTabBar)).selected, PluginProfileFeedTab.posts);
    expect(feed.reads, 1);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
    await tester.pump();
    await accounts.destroy();
    await feed.destroy();
    client.httpClient.close();
    direct.httpClient.close();
    api.httpClient.close();
  });
}
