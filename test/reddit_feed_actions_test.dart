import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/reddit/reddit_actions.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_store.dart';
import 'package:xta/plugins/reddit/reddit_subreddit_avatar.dart';

class _Subs extends RedditSubredditsStore {
  _Subs() : super(PrefServiceCache()) {
    update(const ['girlsfrontline2', 'novelai']);
  }

  @override
  Future<void> load({bool force = false}) async {}
}

class _Client extends RedditClient {
  @override
  Future<String?> fetchSubredditIcon(String subreddit) async => null;

  @override
  Future<RedditSubredditAbout> fetchSubredditAbout(
    String subreddit, {
    required String clientId,
    String? userToken,
    bool preferPublic = false,
  }) async {
    return RedditSubredditAbout(name: subreddit, subscribers: 12000);
  }

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
    return const RedditListing(posts: []);
  }
}

Widget _app(Widget child) {
  return PrefService(
    service: PrefServiceCache(),
    child: MaterialApp(
      localizationsDelegates: const [
        L10n.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: L10n.delegate.supportedLocales,
      home: Scaffold(appBar: AppBar(actions: [child])),
    ),
  );
}

void main() {
  testWidgets('Reddit chrome has search, not a plus next to it', (
    tester,
  ) async {
    await tester.pumpWidget(_app(const RedditFeedActions()));
    await tester.pumpAndSettle();

    expect(find.byIcon(Icons.search), findsOneWidget);
    expect(find.byIcon(Icons.add), findsNothing);
    expect(find.byIcon(Icons.list), findsOneWidget);
    expect(find.byIcon(Icons.more_vert), findsOneWidget);
  });

  testWidgets('overflow offers settings, not Reddit sign-in', (tester) async {
    await tester.pumpWidget(_app(const RedditFeedActions()));
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(Icons.more_vert));
    await tester.pumpAndSettle();

    expect(find.text('Sign in to Reddit'), findsNothing);
    expect(find.text('Reddit · Settings'), findsOneWidget);
    expect(find.text('Best available'), findsOneWidget);
    expect(find.text('Without an account'), findsOneWidget);
  });

  testWidgets('the list sheet opens a community when its row is tapped', (
    tester,
  ) async {
    final prefs = PrefServiceCache();
    final client = _Client();
    final subs = _Subs();
    addTearDown(subs.destroy);

    await tester.pumpWidget(
      PrefService(
        service: prefs,
        child: MultiProvider(
          providers: [
            Provider<RedditClient>.value(value: client),
            Provider<RedditIcons>.value(value: RedditIcons(client)),
            Provider<RedditSubredditsStore>.value(value: subs),
          ],
          child: MaterialApp(
            localizationsDelegates: const [
              L10n.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
              GlobalCupertinoLocalizations.delegate,
            ],
            supportedLocales: L10n.delegate.supportedLocales,
            home: const Scaffold(
              appBar: AppBar(actions: [RedditFeedActions()]),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(Icons.list));
    await tester.pumpAndSettle();

    expect(find.text('Your communities'), findsOneWidget);
    expect(find.text('r/girlsfrontline2'), findsOneWidget);
    expect(find.text('r/novelai'), findsOneWidget);
    expect(find.byType(RedditSubredditAvatar), findsNWidgets(2));

    final tile = tester.widget<ListTile>(
      find
          .ancestor(
            of: find.text('r/girlsfrontline2'),
            matching: find.byType(ListTile),
          )
          .first,
    );
    expect(tile.onTap, isNotNull);
  });
}
