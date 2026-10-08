import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/reddit/reddit_actions.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_home_source.dart';
import 'package:xta/plugins/reddit/reddit_screen.dart';
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
  Future<String?> fetchSubredditIcon(
    String subreddit, {
    String clientId = '',
    String? userToken,
    bool preferPublic = false,
  }) async => null;

  @override
  Future<RedditSubredditAbout> fetchSubredditAbout(
    String subreddit, {
    required String clientId,
    String? userToken,
    bool preferPublic = false,
  }) async {
    return RedditSubredditAbout(
      name: subreddit,
      subscribers: 12000,
      iconUrl: 'https://styles.redditmedia.com/t5_$subreddit/icon.png',
    );
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

Future<void> _noRefresh() async {}

/// The actions laid out the way the standalone bar lays them out.
Widget _actions({VoidCallback? onOpenSaved, Future<void> Function()? onRefresh}) => RedditFeedActions(
  onRefresh: onRefresh ?? _noRefresh,
  onOpenSaved: onOpenSaved ?? () {},
  builder: (actions) => Row(mainAxisSize: MainAxisSize.min, children: actions),
);

Widget _app(Widget child, {BasePrefService? prefs}) {
  return PrefService(
    service: prefs ?? PrefServiceCache(),
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
  testWidgets('full Reddit actions and back fit a 320dp screen; saved stays reachable', (tester) async {
    tester.view.physicalSize = const Size(320, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    var saved = 0;
    await tester.pumpWidget(
      _app(
        Builder(
          builder: (context) => TextButton(
            child: const Text('Open Reddit'),
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) => Scaffold(
                  body: RedditFeedActions(
                    onRefresh: _noRefresh,
                    onOpenSaved: () => saved++,
                    builder: (actions) => RedditHomeChrome(
                      source: const RedditHomeSource(mode: RedditFeedMode.following),
                      onMode: (_) {},
                      actions: actions,
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Open Reddit'));
    await tester.pumpAndSettle();
    expect(find.byType(BackButton), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.tap(find.byIcon(Icons.more_vert));
    await tester.pumpAndSettle();
    expect(find.text('Subscriptions'), findsOneWidget);
    await tester.tap(find.text('Saved'));
    await tester.pumpAndSettle();
    expect(saved, 1);
    await tester.tap(find.byType(BackButton));
    await tester.pumpAndSettle();
    expect(find.text('Open Reddit'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Reddit chrome leads with search, then sort and one menu, not a plus', (tester) async {
    await tester.pumpWidget(_app(_actions()));
    await tester.pumpAndSettle();

    final search = find.byIcon(Icons.search);
    final sort = find.byTooltip('Sort');
    final menu = find.byIcon(Icons.more_vert);
    expect(search, findsOneWidget);
    expect(sort, findsOneWidget);
    expect(menu, findsOneWidget);
    // Home keeps the first action visible beside its options button.
    expect(tester.getCenter(search).dx, lessThan(tester.getCenter(sort).dx));
    expect(tester.getCenter(sort).dx, lessThan(tester.getCenter(menu).dx));
    expect(find.byIcon(Icons.add), findsNothing);
    expect(find.byIcon(Icons.list), findsNothing, reason: 'Communities live in the menu.');
  });

  testWidgets('overflow offers saved, communities and settings, not Reddit sign-in', (tester) async {
    await tester.pumpWidget(_app(_actions()));
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(Icons.more_vert));
    await tester.pumpAndSettle();

    expect(find.text('Sign in to Reddit'), findsNothing);
    expect(find.text('Saved'), findsOneWidget);
    expect(find.text('Subscriptions'), findsOneWidget);
    expect(find.text('Reddit · Settings'), findsOneWidget);
    expect(find.text('Best available'), findsOneWidget);
    expect(find.text('Without an account'), findsOneWidget);
    expect(find.text('Settings'), findsNothing, reason: 'App settings stay in the drawer.');
    expect(find.text('Open Reddit'), findsNothing, reason: 'Home adds the full-client entry itself.');
  });

  testWidgets('choosing how Reddit is read stores it and refetches the visible feed', (tester) async {
    final prefs = PrefServiceCache();
    var refreshes = 0;
    await tester.pumpWidget(_app(_actions(onRefresh: () async => refreshes++), prefs: prefs));
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(Icons.more_vert));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Without an account'));
    await tester.pumpAndSettle();
    expect(prefs.get<String>(optionPluginRedditSource), redditSourcePublic);
    expect(refreshes, 1);

    await tester.tap(find.byIcon(Icons.more_vert));
    await tester.pumpAndSettle();
    final public = tester.widget<ListTile>(
      find.ancestor(of: find.text('Without an account'), matching: find.byType(ListTile)),
    );
    expect(public.trailing, isA<Icon>());
  });

  testWidgets('the list sheet opens a community when its row is tapped', (tester) async {
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
            home: Scaffold(appBar: AppBar(actions: [_actions()])),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(Icons.more_vert));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Subscriptions'));
    await tester.pumpAndSettle();

    expect(find.text('Your communities'), findsOneWidget);
    expect(find.text('r/girlsfrontline2'), findsOneWidget);
    expect(find.text('r/novelai'), findsOneWidget);
    expect(find.byType(RedditSubredditAvatar), findsNWidgets(2));
    expect(
      tester
          .widgetList<RedditSubredditAvatar>(find.byType(RedditSubredditAvatar))
          .every((avatar) => avatar.url != null && avatar.url!.isNotEmpty),
      isTrue,
    );
    expect(find.byIcon(Icons.people_outline), findsNWidgets(2));
    expect(find.text('Add subreddit'), findsOneWidget);

    final tile = tester.widget<ListTile>(
      find.ancestor(of: find.text('r/girlsfrontline2'), matching: find.byType(ListTile)).first,
    );
    expect(tile.onTap, isNotNull);
  });
}
