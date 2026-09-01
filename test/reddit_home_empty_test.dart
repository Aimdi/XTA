import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_feed_list.dart';
import 'package:xta/plugins/reddit/reddit_home_source.dart';
import 'package:xta/plugins/reddit/reddit_screen.dart';
import 'package:xta/plugins/reddit/reddit_store.dart';
import 'package:xta/ui/empty_pane.dart';

class _EmptySubs extends RedditSubredditsStore {
  var loads = 0;

  _EmptySubs() : super(PrefServiceCache()) {
    update(const []);
  }

  @override
  Future<void> load({bool force = false}) async {
    loads++;
  }
}

class _CountingClient extends RedditClient {
  var calls = 0;

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
    calls++;
    return const RedditListing(posts: []);
  }
}

class _TabHost extends StatefulWidget {
  final Widget reddit;

  const _TabHost({required this.reddit});

  @override
  State<_TabHost> createState() => _TabHostState();
}

class _TabHostState extends State<_TabHost> {
  var _reddit = true;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        TextButton(
          onPressed: () => setState(() => _reddit = !_reddit),
          child: Text(_reddit ? 'foryou' : 'reddit'),
        ),
        Expanded(child: _reddit ? widget.reddit : const SizedBox.expand()),
      ],
    );
  }
}

Widget _shell({
  required ScrollController outer,
  required Widget body,
  Locale locale = const Locale('en'),
}) {
  return MaterialApp(
    locale: locale,
    localizationsDelegates: const [
      L10n.delegate,
      GlobalMaterialLocalizations.delegate,
      GlobalWidgetsLocalizations.delegate,
      GlobalCupertinoLocalizations.delegate,
    ],
    supportedLocales: L10n.delegate.supportedLocales,
    home: Scaffold(
      body: NestedScrollView(
        controller: outer,
        headerSliverBuilder: (context, inner) => [
          const SliverAppBar(pinned: true, title: Text('Start')),
        ],
        body: PluginEmbedded(child: body),
      ),
    ),
  );
}

void main() {
  group('RedditFeedStore', () {
    test('zero follows does not fetch or enter loading', () async {
      final prefs = PrefServiceCache();
      final subs = _EmptySubs();
      final client = _CountingClient();
      final feed = RedditFeedStore(client, subs, prefs);

      expect(feed.isLoading, isFalse);
      await feed.refresh();
      expect(feed.isLoading, isFalse);
      expect(feed.state, isEmpty);
      expect(client.calls, 0);
      expect(feed.fetchedAt, isNotNull);

      final first = feed.fetchedAt;
      await feed.refresh();
      expect(identical(feed.fetchedAt, first), isTrue);
      expect(client.calls, 0);

      feed.destroy();
      subs.destroy();
    });

    test('a remount refresh with zero follows stays a no-op', () async {
      final prefs = PrefServiceCache();
      final subs = _EmptySubs();
      final client = _CountingClient();
      final feed = RedditFeedStore(client, subs, prefs);

      await feed.refresh();
      var notified = 0;
      feed.observer(onState: (_) => notified++);
      await feed.refresh();
      await feed.refresh(force: true);
      expect(notified, 0);
      expect(client.calls, 0);

      feed.destroy();
      subs.destroy();
    });
  });

  testWidgets(
    'Following with zero subreddits paints empty and does not attach the outer controller',
    (tester) async {
      final outer = ScrollController();
      addTearDown(outer.dispose);
      final prefs = PrefServiceCache();
      final subs = _EmptySubs();
      final client = _CountingClient();
      final feed = RedditFeedStore(client, subs, prefs);
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
            child: _shell(
              outer: outer,
              locale: const Locale('de'),
              body: RedditFeedList(scrollController: outer),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(tester.takeException(), isNull);
      expect(outer.positions.length, 1);
      expect(find.byType(EmptyPane), findsOneWidget);
      expect(
        find.text('Füge ein Subreddit hinzu, um zu lesen'),
        findsOneWidget,
      );
      expect(find.text('Subreddit hinzufügen'), findsOneWidget);
      expect(client.calls, 0);
    },
  );

  testWidgets('tapping add opens a sheet and cancel leaves the empty pane', (
    tester,
  ) async {
    final outer = ScrollController();
    addTearDown(outer.dispose);
    final prefs = PrefServiceCache();
    final subs = _EmptySubs();
    final client = _CountingClient();
    final feed = RedditFeedStore(client, subs, prefs);
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
          child: _shell(
            outer: outer,
            body: RedditFeedList(scrollController: outer),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(FilledButton, 'Add subreddit'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    expect(find.byType(AlertDialog), findsOneWidget);

    await tester.tap(find.text('Cancel'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    expect(find.byType(AlertDialog), findsNothing);
    expect(find.byType(EmptyPane), findsOneWidget);
    expect(outer.positions.length, 1);
    expect(tester.takeException(), isNull);
    expect(client.calls, 0);
  });

  testWidgets('switching off Reddit and back does not refetch or freeze', (
    tester,
  ) async {
    final outer = ScrollController();
    addTearDown(outer.dispose);
    final prefs = PrefServiceCache();
    final subs = _EmptySubs();
    final client = _CountingClient();
    final feed = RedditFeedStore(client, subs, prefs);
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
          child: _shell(
            outer: outer,
            body: _TabHost(reddit: RedditFeedList(scrollController: outer)),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byType(EmptyPane), findsOneWidget);
    expect(subs.loads, 1);

    await tester.tap(find.text('foryou'));
    await tester.pumpAndSettle();
    expect(find.byType(EmptyPane), findsNothing);

    await tester.tap(find.text('reddit'));
    await tester.pumpAndSettle();
    expect(find.byType(EmptyPane), findsOneWidget);
    expect(outer.positions.length, 1);
    expect(tester.takeException(), isNull);
    expect(client.calls, 0);
    expect(feed.isLoading, isFalse);
  });

  testWidgets('an empty chip strip is not a list', (tester) async {
    final home = RedditHomeStore(PrefServiceCache());
    final subs = _EmptySubs();
    addTearDown(() {
      home.destroy();
      subs.destroy();
    });

    await tester.pumpWidget(
      PrefService(
        service: home.prefs,
        child: Provider<RedditSubredditsStore>.value(
          value: subs,
          child: MaterialApp(
            localizationsDelegates: const [
              L10n.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
              GlobalCupertinoLocalizations.delegate,
            ],
            supportedLocales: L10n.delegate.supportedLocales,
            home: Scaffold(body: RedditSubredditChips(home: home)),
          ),
        ),
      ),
    );
    await tester.pump();

    expect(find.byType(ChoiceChip), findsNothing);
    expect(find.byType(ListView), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'EmptyPane under the home strip does not reuse the outer controller',
    (tester) async {
      final outer = ScrollController();
      addTearDown(outer.dispose);

      await tester.pumpWidget(
        _shell(
          outer: outer,
          body: EmptyPane(
            icon: Icons.forum_outlined,
            message: 'empty',
            scrollController: outer,
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(tester.takeException(), isNull);
      expect(outer.positions.length, 1);
      expect(find.text('empty'), findsOneWidget);
    },
  );
}
