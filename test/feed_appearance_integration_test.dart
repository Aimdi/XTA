import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_feeds_pane.dart';
import 'package:xta/plugins/bluesky/bluesky_feeds_store.dart';
import 'package:xta/plugins/bluesky/bluesky_post_card.dart';
import 'package:xta/plugins/plugin_home_dock.dart';
import 'package:xta/reading/feed_appearance_controls.dart';
import 'package:xta/reading/feed_appearance_scope.dart';
import 'package:xta/reading/feed_appearance_store.dart';
import 'package:xta/reading/reader_tools_settings.dart';
import 'package:xta/settings/settings.dart';
import 'package:xta/ui/x_look_theme.dart';
import 'support/bluesky_reading_harness.dart';

Widget _app(BasePrefService prefs, Widget child, {String locale = 'en', int theme = 0, double scale = 1}) =>
    PrefService(
      service: prefs,
      child: MaterialApp(
        theme: [xLookLightTheme(null), xLookDimTheme(null), xLookLightsOutTheme(null)][theme],
        locale: Locale(locale),
        supportedLocales: L10n.delegate.supportedLocales,
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(scale), disableAnimations: true),
          child: child!,
        ),
        home: child,
      ),
    );

void main() {
  for (final locale in ['de', 'ar']) {
    for (var theme = 0; theme < 3; theme++) {
      testWidgets('appearance sheet 320dp $locale 2x theme $theme', (tester) async {
        tester.view.physicalSize = const Size(320, 900);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        final prefs = PrefServiceCache();
        final store = FeedAppearanceStore.forPrefs(prefs);
        await tester.pumpWidget(
          _app(
            prefs,
            Scaffold(
              body: Builder(
                builder: (context) => TextButton(
                  onPressed: () => showFeedAppearance(context, const FeedIdentity('x', 'following')),
                  child: Text(L10n.of(context).feed_appearance),
                ),
              ),
            ),
            locale: locale,
            theme: theme,
            scale: 2,
          ),
        );
        await tester.pumpAndSettle();
        await tester.tap(find.byType(TextButton));
        await tester.pumpAndSettle();
        for (final key in [
          'appearance-preset',
          'appearance-counts',
          'appearance-links',
          'appearance-media',
          'appearance-reset',
        ]) {
          final choice = find.byKey(ValueKey(key));
          expect(choice, findsOneWidget);
          await tester.ensureVisible(choice);
          await tester.pumpAndSettle();
          expect(tester.getSize(choice).height, greaterThanOrEqualTo(48));
        }
        await tester.ensureVisible(find.byKey(const ValueKey('appearance-preset')));
        await tester.pumpAndSettle();
        await tester.tap(find.byKey(const ValueKey('appearance-preset')));
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
        await tester.pumpWidget(const SizedBox());
        await tester.pumpAndSettle();
        await store.destroy();
      });
    }
  }

  testWidgets('actual Settings exposes Reader tools and persisted feed appearance', (tester) async {
    final prefs = PrefServiceCache();
    final store = FeedAppearanceStore.forPrefs(prefs);
    await tester.pumpWidget(_app(prefs, const SettingsScreen()));
    await tester.pumpAndSettle();
    final readerTools = find.text('Reader tools');
    await tester.ensureVisible(readerTools);
    await tester.tap(readerTools);
    await tester.pumpAndSettle();
    expect(find.byType(ReaderToolsSettings), findsOneWidget);
    await tester.tap(find.text('Feed appearance'));
    await tester.pumpAndSettle();
    expect(find.byType(FeedAppearanceSettings), findsOneWidget);
    await tester.tap(find.text('Following'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('appearance-counts')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Hide').last);
    await tester.pumpAndSettle();
    expect(store.appearance(const FeedIdentity('x', 'following')).counts, isFalse);
    expect(store.appearance(const FeedIdentity('x', 'for-you')).inherits, isTrue);
    await tester.tap(find.byKey(const ValueKey('appearance-reset')));
    await tester.pumpAndSettle();
    expect(store.appearance(const FeedIdentity('x', 'following')).inherits, isTrue);
    await tester.pumpWidget(const SizedBox());
    await tester.pumpAndSettle();
    await store.destroy();
  });

  testWidgets('standalone feed labels survive into Settings without publishing a dock action', (tester) async {
    final prefs = PrefServiceCache();
    final store = FeedAppearanceStore.forPrefs(prefs);
    final dock = PluginHomeDockStore();
    const rss = FeedIdentity('rss', 'saved-feed-id');
    const substack = FeedIdentity('substack', 'publication-id');
    Future<void> showScopes(String publicationLabel) async {
      await tester.pumpWidget(
        _app(
          prefs,
          Scaffold(
            body: PluginHomeDockScope(
              store: dock,
              source: 'rss',
              enabled: true,
              openClientLabel: '',
              onOpenClient: null,
              child: Column(
                children: [
                  FeedAppearanceScope(
                    feed: rss,
                    label: 'Coastal journal',
                    publishAction: false,
                    child: const SizedBox(),
                  ),
                  FeedAppearanceScope(
                    feed: substack,
                    label: publicationLabel,
                    publishAction: false,
                    child: const SizedBox(),
                  ),
                ],
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
    }

    await showScopes('Field notes');
    expect(store.labelFor(rss), 'Coastal journal');
    expect(store.labelFor(substack), 'Field notes');
    expect(dock.content('rss', 'appearance'), isNull);
    await showScopes('Updated field notes');
    expect(store.labelFor(substack), 'Updated field notes');
    expect(dock.content('rss', 'appearance'), isNull);
    await store.save(rss, const FeedAppearance(media: false));
    await store.save(substack, const FeedAppearance(counts: false));
    await tester.pumpWidget(_app(prefs, const FeedAppearanceSettings()));
    await tester.pumpAndSettle();
    expect(find.text('Coastal journal'), findsOneWidget);
    expect(find.text('Updated field notes'), findsOneWidget);
    await tester.tap(find.text('Updated field notes'));
    await tester.pumpAndSettle();
    expect(tester.widget<FeedAppearanceControls>(find.byType(FeedAppearanceControls)).feed, substack);
    await tester.pumpWidget(const SizedBox());
    await tester.pumpAndSettle();
    await store.destroy();
    await dock.destroy();
    prefs.dispose();
  });

  testWidgets('false and throwing writes surface honest controls failure without changing state', (tester) async {
    final prefs = PrefServiceCache();
    var throws = false;
    final store = FeedAppearanceStore(
      prefs,
      write: (_, _) async {
        if (throws) throw StateError('write failed');
        return false;
      },
    );
    await tester.pumpWidget(
      _app(
        prefs,
        Scaffold(
          body: SingleChildScrollView(
            child: FeedAppearanceControls(feed: const FeedIdentity('x', 'following'), store: store),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    for (var i = 0; i < 2; i++) {
      throws = i == 1;
      await tester.tap(find.byKey(const ValueKey('appearance-counts')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Hide').last);
      await tester.pumpAndSettle();
      expect(find.text('Could not save the appearance. Try again.'), findsOneWidget);
      expect(store.state, isEmpty);
    }
    await tester.pumpWidget(const SizedBox());
    await tester.pumpAndSettle();
    await store.destroy();
  });

  test('appearance capability requires explicit mixed-feed opt in', () {
    expect(feedAppearanceCapable('mix'), isFalse);
    expect(feedAppearanceCapable('mix', mixed: true), isTrue);
    expect(feedAppearanceCapable('hackernews'), isFalse);
    expect(feedAppearanceCapable('bluesky'), isTrue);
    expect(homeFeedIdentity('following'), const FeedIdentity('x', 'following'));
    expect(homeFeedIdentity('x'), const FeedIdentity('x', 'for-you'));
  });

  testWidgets('native selected Bluesky URI drives actual Home options, counts and cached reader', (tester) async {
    final host = BlueReadingHarness();
    const first = 'at://did:plc:curator/app.bsky.feed.generator/first';
    const second = 'at://did:plc:curator/app.bsky.feed.generator/second';
    var feedRequests = 0;
    final client = BlueskyClient(
      httpClient: MockClient((request) async {
        if (request.url.path.endsWith('getFeed')) {
          feedRequests++;
          return http.Response(
            jsonEncode({
              'feed': [
                {
                  'post': {
                    'uri': bluePost('root').uri,
                    'cid': 'c',
                    'author': {'did': 'did:plc:maya', 'handle': 'maya.bsky.social'},
                    'record': {'text': 'Actual generator post'},
                    'replyCount': 2,
                    'repostCount': 8,
                    'likeCount': 42,
                  },
                },
              ],
            }),
            200,
          );
        }
        return http.Response(jsonEncode({'feeds': []}), 200);
      }),
    );
    final algo = BlueskyAlgoStore(client, host.prefs);
    final dock = PluginHomeDockStore();
    final scroll = ScrollController();
    final appearance = FeedAppearanceStore.forPrefs(host.prefs);
    await algo.open(first, name: 'First feed');
    await tester.pumpWidget(
      host.app(
        Provider<BlueskyAlgoStore>.value(
          value: algo,
          child: Provider<BlueskyClient>.value(
            value: client,
            child: Scaffold(
              body: PluginHomeDockScope(
                store: dock,
                source: 'bluesky',
                enabled: true,
                compact: true,
                openClientLabel: '',
                onOpenClient: null,
                child: Column(
                  children: [
                    PluginDockOptionsButton(store: dock, source: 'bluesky'),
                    Expanded(child: BlueskyAlgoPane(scrollController: scroll)),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    final card = tester.element(find.byType(BlueskyPostCard));
    expect(FeedAppearanceScope.feedOf(card), const FeedIdentity('bluesky', first));
    final requestsBefore = feedRequests;
    await tester.tap(find.byKey(const ValueKey('home-plugin-options')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('feed-appearance-action')), findsOneWidget);
    expect(
      tester.widget<FeedAppearanceAction>(find.byType(FeedAppearanceAction)).feed,
      const FeedIdentity('bluesky', first),
    );
    await tester.tap(find.byKey(const ValueKey('feed-appearance-action')));
    await tester.pumpAndSettle();
    expect(find.text('First feed'), findsWidgets);
    await tester.tap(find.byKey(const ValueKey('appearance-counts')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Hide').last);
    await tester.pumpAndSettle();
    expect(appearance.appearance(const FeedIdentity('bluesky', first)).counts, isFalse);
    expect(feedRequests, requestsBefore);
    expect(tester.element(find.byType(BlueskyPostCard)), same(card));
    await tester.tap(find.byTooltip('Close').last);
    await tester.pumpAndSettle();
    expect(find.text('2'), findsNothing);
    await algo.open(second, name: 'Second feed');
    await tester.pumpAndSettle();
    expect(
      FeedAppearanceScope.feedOf(tester.element(find.byType(BlueskyPostCard))),
      const FeedIdentity('bluesky', second),
    );
    expect(find.text('2'), findsOneWidget);
    expect(appearance.appearance(const FeedIdentity('bluesky', second)).inherits, isTrue);
    await host.close(tester);
    await algo.destroy();
    await dock.destroy();
    await appearance.destroy();
    scroll.dispose();
    client.httpClient.close();
  });
}
