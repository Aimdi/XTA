import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/bluesky/bluesky_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';
import 'package:xta/plugins/bluesky/bluesky_post_card.dart';
import 'package:xta/plugins/threads/threads_client.dart';
import 'package:xta/plugins/threads/threads_direct_client.dart';
import 'package:xta/plugins/threads/threads_likes_store.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_post_card.dart';
import 'package:xta/plugins/threads/threads_screen.dart';
import 'package:xta/plugins/threads/threads_store.dart';
import 'support/bluesky_reading_harness.dart';
import 'package:xta/plugins/mastodon/mastodon_post_card.dart';
import 'package:xta/plugins/mastodon/mastodon_plugin.dart';
import 'support/mastodon_harness.dart';

class _ThreadsFeed extends ThreadsFeedStore {
  int refreshes = 0;
  _ThreadsFeed(super.client, super.direct, super.prefs, super.accounts);
  @override
  Future<void> refresh({bool force = false}) async {
    refreshes++;
  }
}

class _ThreadsAccounts extends ThreadsAccountsStore {
  @override
  Future<void> remove(String handle) async => update(state.where((a) => a.handle != handle).toList());
}

Future<void> _capture(WidgetTester tester, String service, bool large) async {
  if (!const bool.fromEnvironment('RENDER_UNIFIED_HOME')) return;
  await expectLater(
    find.byType(MaterialApp),
    matchesGoldenFile('../review-artifacts/renders/microblog-$service-${large ? 'large-rtl' : 'light'}.png'),
  );
}

void main() {
  setUpAll(() async {
    if (const bool.fromEnvironment('RENDER_UNIFIED_HOME')) {
      autoUpdateGoldenFiles = true;
      await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-Regular.ttf'))).load();
      await (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
    }
  });
  testWidgets('a narrow pushed reader respects system insets and returns with Back', (tester) async {
    tester.view.physicalSize = const Size(320, 844);
    tester.view.devicePixelRatio = 1;
    tester.view.padding = const FakeViewPadding(top: 24, bottom: 24);
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetPadding);
    final h = MastodonHarness();
    addTearDown(() => h.close(tester));
    await tester.pumpWidget(
      h.app(
        scale: 2,
        child: Builder(
          builder: (context) => Scaffold(
            body: TextButton(
              onPressed: () => Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => MastodonPlugin().clientScreen(scrollController: h.scroll)),
              ),
              child: const Text('Open reader'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Open reader'));
    await tester.pumpAndSettle();
    final header = find.byKey(const ValueKey('microblog-header-mastodon'));
    expect(tester.getRect(header).top, 24);
    expect(tester.getSize(header).height, 52);
    expect(tester.getSize(find.byType(BackButton)), const Size(48, 48));
    expect(find.byKey(const ValueKey('home-plugin-options')), findsOneWidget);
    await tester.pageBack();
    await tester.pumpAndSettle();
    expect(find.text('Open reader'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  for (final large in [false, true]) {
    testWidgets('Mastodon has one slim reader header, large=$large', (tester) async {
      tester.view.physicalSize = Size(large ? 320 : 390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final h = MastodonHarness();
      addTearDown(() => h.close(tester));
      await tester.pumpWidget(h.app(scale: large ? 2 : 1, rtl: large));
      await tester.pumpAndSettle();
      expect(find.byType(NavigationBar), findsNothing);
      expect(find.byType(MastodonPostCard), findsWidgets);
      final options = find.byKey(const ValueKey('home-plugin-options'));
      expect(tester.getRect(options).bottom, lessThanOrEqualTo(52));
      expect(tester.getSize(options), const Size(48, 48));
      await tester.tap(options);
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('home-section-1')));
      await tester.pumpAndSettle();
      expect(tester.getTopLeft(find.byType(MastodonPostCard).first).dy, lessThanOrEqualTo(64));
      await _capture(tester, 'mastodon', large);
      await tester.tap(options);
      await tester.pumpAndSettle();
      expect(find.text('studio.example'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    testWidgets('Bluesky keeps one header and local state, large=$large', (tester) async {
      tester.view.physicalSize = Size(large ? 320 : 390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final h = BlueReadingHarness();
      final feed = BlueskyFeedStore(h.client, h.accounts);
      final scroll = ScrollController();
      addTearDown(() async {
        await h.close(tester);
        await feed.destroy();
        scroll.dispose();
      });
      await tester.pumpWidget(
        h.app(
          Provider<BlueskyFeedStore>.value(
            value: feed,
            child: BlueskyScreen(scrollController: scroll),
          ),
          scale: large ? 2 : 1,
          rtl: large,
          dark: large,
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.getTopLeft(find.byType(BlueskyPostCard).first).dy, lessThanOrEqualTo(64));
      expect(tester.getRect(find.byTooltip(L10n.current.plugin_bluesky_search)).bottom, lessThanOrEqualTo(52));
      await _capture(tester, 'bluesky', large);
      final before = tester.state(find.byType(BlueskyScreen));
      final calls = h.client.calls.length;
      final options = find.byKey(const ValueKey('home-plugin-options'));
      await tester.tap(options);
      await tester.pumpAndSettle();
      final add = find.widgetWithText(ListTile, L10n.current.plugin_bluesky_add);
      await tester.ensureVisible(add);
      await tester.tap(add);
      await tester.pumpAndSettle();
      expect(find.byType(AlertDialog), findsOneWidget);
      await tester.tap(find.text(L10n.current.cancel));
      await tester.pumpAndSettle();
      await tester.tap(options);
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('home-section-3')));
      await tester.pumpAndSettle();
      expect(find.text(bluePost('root').text), findsOneWidget);
      expect(tester.state(find.byType(BlueskyScreen)), same(before));
      expect(h.client.calls, hasLength(calls));
      expect(tester.takeException(), isNull);
    });

    testWidgets('Threads accounts move out of the feed and remain manageable, large=$large', (tester) async {
      tester.view.physicalSize = Size(large ? 320 : 390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final h = MastodonHarness();
      final client = ThreadsClient();
      final direct = ThreadsDirectClient(h.prefs);
      final accounts = _ThreadsAccounts()..update([const ThreadsAccount(handle: 'maya', name: 'Maya Chen')]);
      final posts = List.generate(
        12,
        (i) => ThreadsPost(id: '$i', handle: 'maya', authorName: 'Maya Chen', text: 'A quiet morning. Field notes $i.'),
      );
      final likes = ThreadsLikesStore(h.prefs)..update([posts.last]);
      final feed = _ThreadsFeed(client, direct, h.prefs, accounts)..update(posts);
      addTearDown(() async {
        await h.close(tester);
        await accounts.destroy();
        await likes.destroy();
        await feed.destroy();
        client.httpClient.close();
        direct.httpClient.close();
      });
      await tester.pumpWidget(
        h.app(
          scale: large ? 2 : 1,
          rtl: large,
          dark: large,
          child: MultiProvider(
            providers: [
              Provider<ThreadsAccountsStore>.value(value: accounts),
              Provider<ThreadsLikesStore>.value(value: likes),
              Provider<ThreadsFeedStore>.value(value: feed),
              Provider<ThreadsDirectClient>.value(value: direct),
              Provider<ThreadsClient>.value(value: client),
            ],
            child: ThreadsScreen(scrollController: h.scroll),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.getTopLeft(find.byType(ThreadsPostCard).first).dy, lessThanOrEqualTo(64));
      expect(tester.getRect(find.byTooltip(L10n.current.plugin_threads_search)).bottom, lessThanOrEqualTo(52));
      expect(find.byKey(const ValueKey('threads-following')), findsNothing);
      await _capture(tester, 'threads', large);
      final options = find.byKey(const ValueKey('home-plugin-options'));
      await tester.tap(options);
      await tester.pumpAndSettle();
      final following = find.byKey(const ValueKey('threads-following'));
      await tester.ensureVisible(following);
      await tester.tap(following);
      await tester.pumpAndSettle();
      expect(find.widgetWithText(ListTile, '@maya'), findsOneWidget);
      await tester.tap(find.byTooltip(L10n.current.plugin_threads_unfollow));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(TextButton, L10n.current.plugin_threads_unfollow));
      await tester.pumpAndSettle();
      expect(accounts.state, isEmpty);
      expect(feed.refreshes, 2);
      expect(tester.takeException(), isNull);
    });
  }
}
