import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_feed_skeleton.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_feed_list.dart';
import 'package:xta/plugins/reddit/reddit_store.dart';
import 'package:xta/tweet/tweet_skeleton.dart';

/// One subreddit followed and a fetch that never answers — the first paint the
/// reader actually sees when a plugin tab opens.
class _OneSub extends RedditSubredditsStore {
  _OneSub() : super(PrefServiceCache()) {
    update(const ['dartlang']);
  }

  @override
  Future<void> load({bool force = false}) async {}
}

class _HangingClient extends RedditClient {
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
  }) {
    return Completer<RedditListing>().future;
  }
}

/// A feed store parked in the state a tab opens in: nothing yet, still asking.
class _LoadingFeed extends RedditFeedStore {
  _LoadingFeed(super.client, super.subs, super.prefs) {
    setLoading(true);
  }

  @override
  Future<void> refresh({RedditSort? sort, bool force = false}) async {}
}

Widget _app({required Widget body, Locale locale = const Locale('de')}) {
  return MaterialApp(
    locale: locale,
    localizationsDelegates: const [
      L10n.delegate,
      GlobalMaterialLocalizations.delegate,
      GlobalWidgetsLocalizations.delegate,
      GlobalCupertinoLocalizations.delegate,
    ],
    supportedLocales: L10n.delegate.supportedLocales,
    home: Scaffold(body: body),
  );
}

void main() {
  testWidgets('the skeleton never takes the outer scroll controller', (
    tester,
  ) async {
    final outer = ScrollController();
    addTearDown(outer.dispose);

    await tester.pumpWidget(
      PrefService(
        service: PrefServiceCache(),
        child: _app(
          body: NestedScrollView(
            controller: outer,
            headerSliverBuilder: (context, inner) => [
              const SliverAppBar(pinned: true, title: Text('Start')),
            ],
            body: const PluginEmbedded(child: PluginFeedSkeleton()),
          ),
        ),
      ),
    );
    await tester.pump();

    expect(tester.takeException(), isNull);
    expect(outer.positions.length, 1);
    expect(find.byType(TweetSkeletonTile), findsWidgets);
  });

  testWidgets('a grid skeleton paints tiles without a scroll position clash', (
    tester,
  ) async {
    final outer = ScrollController();
    addTearDown(outer.dispose);

    await tester.pumpWidget(
      PrefService(
        service: PrefServiceCache(),
        child: _app(
          body: NestedScrollView(
            controller: outer,
            headerSliverBuilder: (context, inner) => [
              const SliverAppBar(pinned: true, title: Text('Start')),
            ],
            body: const PluginEmbedded(
              child: PluginGridSkeleton(columns: 2, count: 4),
            ),
          ),
        ),
      ),
    );
    await tester.pump();

    expect(tester.takeException(), isNull);
    expect(outer.positions.length, 1);
  });

  testWidgets('the bones stop pulsing when animations are switched off', (
    tester,
  ) async {
    final prefs = PrefServiceCache();
    await prefs.set(optionDisableAnimations, true);

    await tester.pumpWidget(
      PrefService(
        service: prefs,
        child: _app(body: const PluginFeedSkeleton(count: 2)),
      ),
    );
    // pumpAndSettle times out on a repeating animation, so reaching the end of
    // it is the assertion.
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.byType(TweetSkeletonTile), findsWidgets);
  });

  testWidgets('a loading Reddit feed shows bones, not a bare spinner', (
    tester,
  ) async {
    final prefs = PrefServiceCache();
    final subs = _OneSub();
    final feed = _LoadingFeed(_HangingClient(), subs, prefs);
    addTearDown(() {
      feed.destroy();
      subs.destroy();
    });

    await tester.pumpWidget(
      PrefService(
        service: prefs,
        child: MultiProvider(
          providers: [
            Provider<RedditSubredditsStore>.value(value: subs),
            Provider<RedditFeedStore>.value(value: feed),
          ],
          child: _app(body: const RedditFeedList()),
        ),
      ),
    );
    await tester.pump();

    expect(find.byType(PluginFeedSkeleton), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsNothing);
    expect(tester.takeException(), isNull);
  });
}
