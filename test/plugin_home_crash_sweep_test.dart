import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/feed_strip_add_sheet.dart';
import 'package:xta/home/feed_strip_store.dart';
import 'package:xta/home/network_recents_store.dart';
import 'package:xta/home/network_switcher.dart';
import 'package:xta/plugins/bluesky/bluesky_screen.dart';
import 'package:xta/plugins/booru/booru_grid.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/ehviewer/eh_grid.dart';
import 'package:xta/plugins/ehviewer/eh_models.dart';
import 'package:xta/plugins/ehviewer/eh_reader_screen.dart';
import 'package:xta/plugins/mastodon/mastodon_screen.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_lazy_tabs.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/plugins/reddit/reddit_account.dart';
import 'package:xta/plugins/rss/rss_client.dart';
import 'package:xta/plugins/rss/rss_models.dart';
import 'package:xta/plugins/rss/rss_screen.dart';
import 'package:xta/plugins/rss/rss_store.dart';
import 'package:xta/plugins/stocks/stocks_add_sheet.dart';
import 'package:xta/plugins/stocks/stocks_screen.dart';
import 'package:xta/plugins/stocks/stocks_store.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_screen.dart';
import 'package:xta/plugins/substack/substack_store.dart';
import 'package:xta/plugins/threads/threads_client.dart';
import 'package:xta/plugins/threads/threads_direct_client.dart';
import 'package:xta/plugins/threads/threads_likes_store.dart';
import 'package:xta/plugins/threads/threads_screen.dart';
import 'package:xta/plugins/threads/threads_store.dart';
import 'package:xta/tweet/ticker/ticker_quote_cache.dart';
import 'package:xta/ui/empty_pane.dart';
import 'package:xta/ui/feed_list.dart';

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

class _TabHost extends StatefulWidget {
  final Widget plugin;

  const _TabHost({required this.plugin});

  @override
  State<_TabHost> createState() => _TabHostState();
}

class _TabHostState extends State<_TabHost> {
  var _plugin = false;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        TextButton(
          onPressed: () => setState(() => _plugin = !_plugin),
          child: Text(_plugin ? 'foryou' : 'plugin'),
        ),
        Expanded(child: _plugin ? widget.plugin : const SizedBox.expand()),
      ],
    );
  }
}

class _IdleRssFeeds extends RssFeedsStore {
  _IdleRssFeeds(super.prefs);

  @override
  Future<void> load() async {}
}

class _IdleRssRead extends RssReadStore {
  _IdleRssRead(super.prefs);

  @override
  Future<void> load() async {}
}

class _IdleRssTags extends RssTagsStore {
  _IdleRssTags(super.prefs);

  @override
  Future<void> load() async {}
}

class _IdleRssTimeline extends RssTimelineStore {
  _IdleRssTimeline(super.client, super.feeds);

  @override
  Future<void> refresh({bool force = false}) async {}

  @override
  void syncReadIds(Set<String> readIds) {}

  @override
  void syncTags(Map<String, List<String>> tags) {}
}

class _IdlePubs extends SubstackPublicationsStore {
  _IdlePubs(super.prefs);

  @override
  Future<void> load() async {}
}

class _IdleNotes extends SubstackNotesStore {
  _IdleNotes(super.client, super.publications);

  @override
  Future<void> refresh({bool force = false}) async {}
}

class _IdleSubstackRead extends SubstackReadStore {
  _IdleSubstackRead(super.prefs);

  @override
  Future<void> load() async {}
}

class _IdleSubstackLikes extends SubstackLikesStore {
  _IdleSubstackLikes(super.prefs);

  @override
  Future<void> load() async {}
}

class _IdleSubstackSaved extends SubstackSavedStore {
  _IdleSubstackSaved(super.prefs);

  @override
  Future<void> load() async {}
}

class _IdleThreadsAccounts extends ThreadsAccountsStore {
  @override
  Future<void> load() async {}
}

class _IdleThreadsLikes extends ThreadsLikesStore {
  _IdleThreadsLikes(super.prefs);

  @override
  Future<void> load() async {}
}

class _IdleThreadsFeed extends ThreadsFeedStore {
  _IdleThreadsFeed(super.client, super.direct, super.prefs, super.accounts);

  @override
  Future<void> refresh({bool force = false}) async {}
}

class _IdleWatchlist extends StocksWatchlistStore {
  @override
  Future<void> load() async {}
}

void main() {
  group('lists under the home NestedScrollView', () {
    testWidgets('FeedListView empty does not attach the outer controller', (
      tester,
    ) async {
      final outer = ScrollController();
      addTearDown(outer.dispose);

      await tester.pumpWidget(
        _shell(
          outer: outer,
          body: FeedListView(
            controller: outer,
            itemCount: 0,
            itemBuilder: (_, _) => const SizedBox.shrink(),
          ),
        ),
      );
      await tester.pump();

      expect(tester.takeException(), isNull);
      expect(outer.positions.length, 1);
    });

    testWidgets('FeedListView with one row does not attach the outer', (
      tester,
    ) async {
      final outer = ScrollController();
      addTearDown(outer.dispose);

      await tester.pumpWidget(
        _shell(
          outer: outer,
          body: FeedListView(
            controller: outer,
            itemCount: 1,
            itemBuilder: (_, _) => const ListTile(title: Text('one')),
          ),
        ),
      );
      await tester.pump();

      expect(tester.takeException(), isNull);
      expect(outer.positions.length, 1);
      expect(find.text('one'), findsOneWidget);
    });

    testWidgets('EmptyPane keeps the outer on NestedScrollView only', (
      tester,
    ) async {
      final outer = ScrollController();
      addTearDown(outer.dispose);

      await tester.pumpWidget(
        _shell(
          outer: outer,
          body: EmptyPane(
            icon: Icons.rss_feed,
            message: 'empty',
            scrollController: outer,
          ),
        ),
      );
      await tester.pump();

      expect(tester.takeException(), isNull);
      expect(outer.positions.length, 1);
    });

    testWidgets('a ListView without a controller uses the inner one', (
      tester,
    ) async {
      final outer = ScrollController();
      addTearDown(outer.dispose);

      await tester.pumpWidget(
        _shell(
          outer: outer,
          body: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            children: const [Text('guest empty')],
          ),
        ),
      );
      await tester.pump();

      expect(tester.takeException(), isNull);
      expect(outer.positions.length, 1);
      expect(find.text('guest empty'), findsOneWidget);
    });

    testWidgets('EhGalleryGrid empty and one tile stay off the outer', (
      tester,
    ) async {
      final outer = ScrollController();
      addTearDown(outer.dispose);
      final prefs = PrefServiceCache();

      await tester.pumpWidget(
        PrefService(
          service: prefs,
          child: _shell(
            outer: outer,
            body: const EhGalleryGrid(galleries: [], scrollController: null),
          ),
        ),
      );
      await tester.pump();
      expect(tester.takeException(), isNull);
      expect(outer.positions.length, 1);

      await tester.pumpWidget(
        PrefService(
          service: prefs,
          child: _shell(
            outer: outer,
            body: EhGalleryGrid(
              galleries: const [
                EhGallery(gid: 1, token: 't', title: 'Gallery'),
              ],
              scrollController: outer,
            ),
          ),
        ),
      );
      await tester.pump();
      expect(tester.takeException(), isNull);
      expect(outer.positions.length, 1);
      expect(find.text('Gallery'), findsOneWidget);
    });

    testWidgets('BooruPostGrid empty and one tile stay off the outer', (
      tester,
    ) async {
      final outer = ScrollController();
      addTearDown(outer.dispose);

      await tester.pumpWidget(
        _shell(
          outer: outer,
          body: const BooruPostGrid(posts: [], scrollController: null),
        ),
      );
      await tester.pump();
      expect(tester.takeException(), isNull);
      expect(outer.positions.length, 1);

      await tester.pumpWidget(
        _shell(
          outer: outer,
          body: BooruPostGrid(
            posts: const [
              BooruPost(
                id: '1',
                host: 'danbooru.donmai.us',
                engine: 'danbooru',
                tags: ['safe'],
                rating: BooruRating.general,
                score: 1,
                width: 100,
                height: 100,
                previewUrl: '',
                sampleUrl: '',
                fileUrl: '',
                fileExt: 'jpg',
                source: null,
                createdAt: null,
              ),
            ],
            scrollController: outer,
          ),
        ),
      );
      await tester.pump();
      expect(tester.takeException(), isNull);
      expect(outer.positions.length, 1);
    });
  });

  group('RSS home', () {
    test('zero feeds does not enter loading', () async {
      final prefs = PrefServiceCache();
      final feeds = _IdleRssFeeds(prefs);
      final timeline = RssTimelineStore(RssClient(), feeds);

      await timeline.refresh();
      expect(timeline.isLoading, isFalse);
      expect(timeline.state.items, isEmpty);
      expect(timeline.fetchedAt, isNotNull);

      final first = timeline.fetchedAt;
      await timeline.refresh();
      await timeline.refresh(force: true);
      expect(identical(timeline.fetchedAt, first), isTrue);

      timeline.destroy();
      feeds.destroy();
    });

    testWidgets('empty Following paints and does not attach the outer', (
      tester,
    ) async {
      final outer = ScrollController();
      addTearDown(outer.dispose);
      final prefs = PrefServiceCache();
      final feeds = _IdleRssFeeds(prefs);
      final read = _IdleRssRead(prefs);
      final tags = _IdleRssTags(prefs);
      final timeline = RssTimelineStore(RssClient(), feeds);
      addTearDown(() {
        timeline.destroy();
        feeds.destroy();
        read.destroy();
        tags.destroy();
      });

      await tester.pumpWidget(
        PrefService(
          service: prefs,
          child: MultiProvider(
            providers: [
              Provider<RssFeedsStore>.value(value: feeds),
              Provider<RssReadStore>.value(value: read),
              Provider<RssTagsStore>.value(value: tags),
              Provider<RssTimelineStore>.value(value: timeline),
            ],
            child: _shell(
              outer: outer,
              body: RssScreen(scrollController: outer),
            ),
          ),
        ),
      );
      await tester.pump();
      await tester.pump();

      expect(tester.takeException(), isNull);
      expect(outer.positions.length, 1);
      expect(find.byType(EmptyPane), findsOneWidget);
      expect(find.text('Follow a feed to see its posts here'), findsOneWidget);
    });

    testWidgets('one feed row does not attach the outer', (tester) async {
      final outer = ScrollController();
      addTearDown(outer.dispose);
      final prefs = PrefServiceCache();
      final feeds = _IdleRssFeeds(prefs);
      feeds.update(const [
        RssFeed(
          id: 'https://example.com/feed',
          feedUrl: 'https://example.com/feed',
          name: 'Example',
        ),
      ]);
      final read = _IdleRssRead(prefs);
      final tags = _IdleRssTags(prefs);
      final timeline = _IdleRssTimeline(RssClient(), feeds);
      timeline.update(
        const RssFeedSnapshot(
          items: [
            RssItem(
              id: '1',
              title: 'Hello RSS',
              feedId: 'https://example.com/feed',
              feedTitle: 'Example',
            ),
          ],
        ),
      );
      addTearDown(() {
        timeline.destroy();
        feeds.destroy();
        read.destroy();
        tags.destroy();
      });

      await tester.pumpWidget(
        PrefService(
          service: prefs,
          child: MultiProvider(
            providers: [
              Provider<RssFeedsStore>.value(value: feeds),
              Provider<RssReadStore>.value(value: read),
              Provider<RssTagsStore>.value(value: tags),
              Provider<RssTimelineStore>.value(value: timeline),
            ],
            child: _shell(
              outer: outer,
              body: RssScreen(scrollController: outer),
            ),
          ),
        ),
      );
      await tester.pump();
      await tester.pump();

      expect(tester.takeException(), isNull);
      expect(outer.positions.length, 1);
      expect(find.text('Hello RSS'), findsOneWidget);
    });

    testWidgets('Für dich → RSS remount does not attach two controllers', (
      tester,
    ) async {
      final outer = ScrollController();
      addTearDown(outer.dispose);
      final prefs = PrefServiceCache();
      final feeds = _IdleRssFeeds(prefs);
      final read = _IdleRssRead(prefs);
      final tags = _IdleRssTags(prefs);
      final timeline = RssTimelineStore(RssClient(), feeds);
      addTearDown(() {
        timeline.destroy();
        feeds.destroy();
        read.destroy();
        tags.destroy();
      });

      await tester.pumpWidget(
        PrefService(
          service: prefs,
          child: MultiProvider(
            providers: [
              Provider<RssFeedsStore>.value(value: feeds),
              Provider<RssReadStore>.value(value: read),
              Provider<RssTagsStore>.value(value: tags),
              Provider<RssTimelineStore>.value(value: timeline),
            ],
            child: _shell(
              outer: outer,
              body: _TabHost(plugin: RssScreen(scrollController: outer)),
            ),
          ),
        ),
      );
      await tester.pump();
      await tester.tap(find.text('plugin'));
      await tester.pump();
      await tester.pump();
      expect(find.byType(EmptyPane), findsOneWidget);
      expect(outer.positions.length, 1);

      await tester.tap(find.text('foryou'));
      await tester.pump();
      await tester.tap(find.text('plugin'));
      await tester.pump();
      await tester.pump();

      expect(tester.takeException(), isNull);
      expect(outer.positions.length, 1);
      expect(timeline.isLoading, isFalse);
    });
  });

  group('Substack home', () {
    test('zero publications does not enter loading', () async {
      final prefs = PrefServiceCache();
      final pubs = _IdlePubs(prefs);
      final feed = SubstackFeedStore(SubstackClient(), pubs);

      await feed.refresh();
      expect(feed.isLoading, isFalse);
      expect(feed.state.posts, isEmpty);
      expect(feed.fetchedAt, isNotNull);

      final first = feed.fetchedAt;
      await feed.refresh(force: true);
      expect(identical(feed.fetchedAt, first), isTrue);

      feed.destroy();
      pubs.destroy();
    });

    testWidgets('empty Following paints and does not attach the outer', (
      tester,
    ) async {
      final outer = ScrollController();
      addTearDown(outer.dispose);
      final prefs = PrefServiceCache();
      final pubs = _IdlePubs(prefs);
      final client = SubstackClient();
      final feed = SubstackFeedStore(client, pubs);
      final read = _IdleSubstackRead(prefs);
      final likes = _IdleSubstackLikes(prefs);
      final saved = _IdleSubstackSaved(prefs);
      final notes = _IdleNotes(client, pubs);
      addTearDown(() {
        feed.destroy();
        pubs.destroy();
        read.destroy();
        likes.destroy();
        saved.destroy();
        notes.destroy();
      });

      await tester.pumpWidget(
        PrefService(
          service: prefs,
          child: MultiProvider(
            providers: [
              Provider<SubstackPublicationsStore>.value(value: pubs),
              Provider<SubstackFeedStore>.value(value: feed),
              Provider<SubstackReadStore>.value(value: read),
              Provider<SubstackLikesStore>.value(value: likes),
              Provider<SubstackSavedStore>.value(value: saved),
              Provider<SubstackNotesStore>.value(value: notes),
            ],
            child: _shell(
              outer: outer,
              body: SubstackScreen(scrollController: outer),
            ),
          ),
        ),
      );
      await tester.pump();
      await tester.pump();

      expect(tester.takeException(), isNull);
      expect(outer.positions.length, 1);
      expect(find.text('No Substack publications yet'), findsOneWidget);
    });
  });

  group('Threads home', () {
    testWidgets('empty Following paints and does not attach the outer', (
      tester,
    ) async {
      final outer = ScrollController();
      addTearDown(outer.dispose);
      final prefs = PrefServiceCache();
      final accounts = _IdleThreadsAccounts();
      final likes = _IdleThreadsLikes(prefs);
      final client = ThreadsClient();
      final direct = ThreadsDirectClient(prefs);
      final feed = _IdleThreadsFeed(client, direct, prefs, accounts);
      addTearDown(() {
        feed.destroy();
        accounts.destroy();
        likes.destroy();
      });

      await tester.pumpWidget(
        PrefService(
          service: prefs,
          child: MultiProvider(
            providers: [
              Provider<ThreadsAccountsStore>.value(value: accounts),
              Provider<ThreadsLikesStore>.value(value: likes),
              Provider<ThreadsFeedStore>.value(value: feed),
              Provider<ThreadsClient>.value(value: client),
              Provider<ThreadsDirectClient>.value(value: direct),
            ],
            child: _shell(
              outer: outer,
              body: ThreadsScreen(scrollController: outer),
            ),
          ),
        ),
      );
      await tester.pump();
      await tester.pump();

      expect(tester.takeException(), isNull);
      expect(outer.positions.length, 1);
      expect(find.text('No accounts yet'), findsOneWidget);
    });

    testWidgets('add-account cancel leaves the empty pane mounted', (
      tester,
    ) async {
      final outer = ScrollController();
      addTearDown(outer.dispose);
      final prefs = PrefServiceCache();
      final accounts = _IdleThreadsAccounts();
      final likes = _IdleThreadsLikes(prefs);
      final client = ThreadsClient();
      final direct = ThreadsDirectClient(prefs);
      final feed = _IdleThreadsFeed(client, direct, prefs, accounts);
      addTearDown(() {
        feed.destroy();
        accounts.destroy();
        likes.destroy();
      });

      await tester.pumpWidget(
        PrefService(
          service: prefs,
          child: MultiProvider(
            providers: [
              Provider<ThreadsAccountsStore>.value(value: accounts),
              Provider<ThreadsLikesStore>.value(value: likes),
              Provider<ThreadsFeedStore>.value(value: feed),
              Provider<ThreadsClient>.value(value: client),
              Provider<ThreadsDirectClient>.value(value: direct),
            ],
            child: _shell(
              outer: outer,
              body: ThreadsScreen(scrollController: outer),
            ),
          ),
        ),
      );
      await tester.pump();
      await tester.pump();

      await tester.tap(find.widgetWithText(FilledButton, 'Add account'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      expect(find.byType(AlertDialog), findsOneWidget);

      await tester.tap(find.text('Cancel'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      expect(find.byType(AlertDialog), findsNothing);
      expect(find.text('No accounts yet'), findsOneWidget);
      expect(outer.positions.length, 1);
      expect(tester.takeException(), isNull);
    });
  });

  group('Stocks home', () {
    testWidgets('empty watchlist paints and does not attach the outer', (
      tester,
    ) async {
      final outer = ScrollController();
      addTearDown(outer.dispose);
      final watchlist = _IdleWatchlist();
      final quotes = TickerQuoteCache();
      addTearDown(() {
        watchlist.destroy();
        quotes.destroy();
      });

      await tester.pumpWidget(
        MultiProvider(
          providers: [
            Provider<StocksWatchlistStore>.value(value: watchlist),
            Provider<TickerQuoteCache>.value(value: quotes),
          ],
          child: _shell(
            outer: outer,
            body: StocksScreen(scrollController: outer),
          ),
        ),
      );
      await tester.pump();

      expect(tester.takeException(), isNull);
      expect(outer.positions.length, 1);
      expect(
        find.text('Add a ticker to start your watchlist feed'),
        findsOneWidget,
      );
    });
  });

  group('sheets after pop', () {
    testWidgets('Bluesky add cancel disposes with the dialog', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: const [
            L10n.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: L10n.delegate.supportedLocales,
          home: Builder(
            builder: (context) => TextButton(
              onPressed: () => showBlueskyAddAccountDialog(context),
              child: const Text('open'),
            ),
          ),
        ),
      );
      await tester.tap(find.text('open'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      expect(find.byType(AlertDialog), findsOneWidget);
      await tester.tap(find.text('Cancel'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      expect(find.byType(AlertDialog), findsNothing);
      expect(tester.takeException(), isNull);
    });

    testWidgets('Mastodon add cancel disposes with the dialog', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: const [
            L10n.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: L10n.delegate.supportedLocales,
          home: Builder(
            builder: (context) => TextButton(
              onPressed: () => showMastodonAddAccountDialog(context),
              child: const Text('open'),
            ),
          ),
        ),
      );
      await tester.tap(find.text('open'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      await tester.tap(find.text('Cancel'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      expect(find.byType(AlertDialog), findsNothing);
      expect(tester.takeException(), isNull);
    });

    testWidgets('Reddit client-id cancel disposes with the dialog', (
      tester,
    ) async {
      final prefs = PrefServiceCache();
      await tester.pumpWidget(
        PrefService(
          service: prefs,
          child: MaterialApp(
            localizationsDelegates: const [
              L10n.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
              GlobalCupertinoLocalizations.delegate,
            ],
            supportedLocales: L10n.delegate.supportedLocales,
            home: Builder(
              builder: (context) => TextButton(
                onPressed: () => editRedditClientId(context),
                child: const Text('open'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('open'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      expect(find.byType(AlertDialog), findsOneWidget);
      await tester.tap(find.text('Cancel'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      expect(find.byType(AlertDialog), findsNothing);
      expect(tester.takeException(), isNull);
    });

    testWidgets('EH page-jump cancel disposes with the dialog', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: const [
            L10n.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: L10n.delegate.supportedLocales,
          home: Builder(
            builder: (context) => TextButton(
              onPressed: () => showEhJumpDialog(context, current: 3, total: 12),
              child: const Text('open'),
            ),
          ),
        ),
      );
      await tester.tap(find.text('open'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      expect(find.byType(AlertDialog), findsOneWidget);
      await tester.tap(find.text('Cancel'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      expect(find.byType(AlertDialog), findsNothing);
      expect(tester.takeException(), isNull);
    });

    testWidgets('Stocks add sheet cancel leaves the host mounted', (
      tester,
    ) async {
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: const [
            L10n.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: L10n.delegate.supportedLocales,
          home: Builder(
            builder: (context) => TextButton(
              onPressed: () => showStocksAddSheet(context),
              child: const Text('open'),
            ),
          ),
        ),
      );
      await tester.tap(find.text('open'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      expect(find.byType(TextField), findsOneWidget);
      await tester.tapAt(const Offset(8, 8));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      expect(tester.takeException(), isNull);
    });

    testWidgets('Add timeline after Networks pop stays mounted', (
      tester,
    ) async {
      final prefs = PrefServiceCache(
        cache: {
          optionHomeFeedStripPlugins: [pluginIdRss],
          optionPluginRssEnabled: true,
        },
      );

      await tester.pumpWidget(
        PrefService(
          service: prefs,
          child: MultiProvider(
            providers: [
              Provider(create: (_) => FeedStripStore(prefs)),
              Provider(create: (_) => NetworkRecentsStore(prefs)),
            ],
            child: MaterialApp(
              localizationsDelegates: const [
                L10n.delegate,
                GlobalMaterialLocalizations.delegate,
                GlobalWidgetsLocalizations.delegate,
                GlobalCupertinoLocalizations.delegate,
              ],
              supportedLocales: L10n.delegate.supportedLocales,
              home: Scaffold(
                body: Builder(
                  builder: (context) => TextButton(
                    onPressed: () => showNetworkSwitcherSheet(
                      context,
                      plugins: [pluginById(pluginIdRss)!],
                      currentId: pluginIdRss,
                    ),
                    child: const Text('open'),
                  ),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pump();
      await tester.tap(find.text('open'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      await tester.tap(find.text('Add timeline'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      expect(find.byKey(homeNetworksSheetKey), findsNothing);
      expect(find.text('Plugin timelines'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    testWidgets('feed-strip add sheet cancel leaves the host', (tester) async {
      final prefs = PrefServiceCache(cache: {optionPluginRssEnabled: true});
      await tester.pumpWidget(
        PrefService(
          service: prefs,
          child: Provider(
            create: (_) => FeedStripStore(prefs),
            child: MaterialApp(
              localizationsDelegates: const [
                L10n.delegate,
                GlobalMaterialLocalizations.delegate,
                GlobalWidgetsLocalizations.delegate,
                GlobalCupertinoLocalizations.delegate,
              ],
              supportedLocales: L10n.delegate.supportedLocales,
              home: Scaffold(
                body: Builder(
                  builder: (context) => TextButton(
                    onPressed: () => showFeedStripAddSheet(context),
                    child: const Text('open'),
                  ),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pump();
      await tester.tap(find.text('open'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      expect(find.text('Plugin timelines'), findsOneWidget);
      await tester.tapAt(const Offset(8, 8));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      expect(tester.takeException(), isNull);
    });
  });

  testWidgets('PluginLazyTabs remount does not keep two panes', (tester) async {
    final outer = ScrollController();
    addTearDown(outer.dispose);
    var builds = 0;

    await tester.pumpWidget(
      _shell(
        outer: outer,
        body: PluginLazyTabs(
          index: 0,
          children: [
            (_) {
              builds++;
              return EmptyPane(
                icon: Icons.home_outlined,
                message: 'one',
                scrollController: outer,
              );
            },
            (_) => EmptyPane(
              icon: Icons.star_outline,
              message: 'two',
              scrollController: outer,
            ),
          ],
        ),
      ),
    );
    await tester.pump();
    expect(builds, 1);
    expect(find.text('one'), findsOneWidget);

    await tester.pumpWidget(
      _shell(
        outer: outer,
        body: PluginLazyTabs(
          index: 1,
          children: [
            (_) {
              builds++;
              return EmptyPane(
                icon: Icons.home_outlined,
                message: 'one',
                scrollController: outer,
              );
            },
            (_) => EmptyPane(
              icon: Icons.star_outline,
              message: 'two',
              scrollController: outer,
            ),
          ],
        ),
      ),
    );
    await tester.pump();
    expect(builds, 1);
    expect(find.text('two'), findsOneWidget);
    expect(outer.positions.length, 1);
    expect(tester.takeException(), isNull);
  });
}
