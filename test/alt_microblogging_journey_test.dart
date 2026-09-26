import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/combined_groups.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/home/_feed.dart';
import 'package:xta/home/alt_microblogging.dart';
import 'package:xta/home/alt_microblogging_selector.dart';
import 'package:xta/home/chrome_avatar.dart';
import 'package:xta/home/feed_strip_store.dart';
import 'package:xta/home/home_account_filter.dart';
import 'package:xta/home/home_group_filter.dart';
import 'package:xta/home/network_recents_store.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_likes_store.dart';
import 'package:xta/plugins/bluesky/bluesky_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';
import 'package:xta/plugins/mastodon/mastodon_screen.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'support/bluesky_reading_harness.dart';
import 'support/mastodon_harness.dart';

void main() {
  setUpAll(() async {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfiNoIsolate;
    final db = await Directory.systemTemp.createTemp('xta-alt-microblogging');
    await databaseFactory.setDatabasesPath(db.path);
    await Repository().migrate();
    if (const bool.fromEnvironment('RENDER_ALT_MICROBLOGGING')) {
      autoUpdateGoldenFiles = true;
      await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-Regular.ttf'))).load();
      await (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
    }
  });

  testWidgets('real Home retains loaded readers and their selected sections when grouping is undone', (tester) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final mastodon = MastodonHarness();
    final blue = BlueReadingHarness();
    final prefs = mastodon.prefs;
    for (final entry in <String, Object>{
      optionPluginBlueskyEnabled: true,
      optionPluginMastodonEnabled: true,
      optionPluginThreadsEnabled: true,
      optionHomeFeedStripPlugins: ['bluesky', 'threads', 'mastodon'],
      optionSeededStripPlugins: ['bluesky', 'threads', 'mastodon'],
    }.entries) {
      await prefs.set(entry.key, entry.value);
    }
    final grouping = AltMicrobloggingStore(prefs);
    final selection = FeedTabStore(const FeedTab('bluesky'));
    final strip = FeedStripStore(prefs);
    final groups = GroupsModel(prefs);
    final feed = BlueskyFeedStore(blue.client, blue.accounts);
    final subscriptions = SubscriptionsModel(prefs, groups);
    await tester.pumpWidget(
      mastodon.app(
        dark: true,
        child: MultiProvider(
          providers: [
            Provider<AltMicrobloggingStore>.value(value: grouping),
            Provider<FeedTabStore>.value(value: selection),
            Provider<FeedStripStore>.value(value: strip),
            Provider<GroupsModel>.value(value: groups),
            Provider<SubscriptionsModel>.value(value: subscriptions),
            Provider(create: (_) => HomeAccountFilterStore(prefs), dispose: (_, store) => store.destroy()),
            Provider(create: (_) => HomeGroupFilterStore(prefs), dispose: (_, store) => store.destroy()),
            Provider(create: (_) => ChromeAvatarStore(prefs), dispose: (_, store) => store.destroy()),
            Provider(create: (_) => NetworkRecentsStore(prefs), dispose: (_, store) => store.destroy()),
            Provider(create: (_) => CombinedGroupsStore(), dispose: (_, store) => store.destroy()),
            Provider<BlueskyClient>.value(value: blue.client),
            Provider<BlueskyAccountsStore>.value(value: blue.accounts),
            Provider<BlueskyLikesStore>.value(value: blue.likes),
            Provider<BlueskyFeedStore>.value(value: feed),
          ],
          child: Scaffold(
            drawer: const Drawer(),
            body: FeedScreen(scrollController: mastodon.scroll, id: '-1', name: 'Home'),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byType(BlueskyScreen), findsOneWidget);
    expect(find.byType(MastodonScreen), findsNothing);
    expect(blue.client.calls, hasLength(1));
    final readerState = tester.state(find.byType(BlueskyScreen));
    if (const bool.fromEnvironment('RENDER_ALT_MICROBLOGGING')) {
      await expectLater(
        find.byKey(const ValueKey('mastodon-window')),
        matchesGoldenFile('../review-artifacts/renders/alt-microblogging-reader.png'),
      );
    }
    final likedTab = find.byTooltip(L10n.current.plugin_bluesky_liked);
    await tester.scrollUntilVisible(
      likedTab,
      160,
      scrollable: find.descendant(of: find.byType(PluginHomeChrome), matching: find.byType(Scrollable)).first,
    );
    await tester.pumpAndSettle();
    await tester.tap(likedTab);
    await tester.pumpAndSettle();
    expect(find.text(bluePost('root').text), findsOneWidget);
    final posts = feed.state;
    final likes = blue.likes.state;
    await grouping.setGrouped(false);
    await tester.pumpAndSettle();
    expect(find.byType(AltMicrobloggingSelector), findsNothing);
    expect(tester.state(find.byType(BlueskyScreen)), same(readerState));
    expect(find.text(bluePost('root').text), findsOneWidget);
    expect(selection.state.id, 'bluesky');
    await tester.tap(find.byKey(const ValueKey('home-source-picker')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('home-source-bluesky')), findsOneWidget);
    expect(find.byKey(const ValueKey('home-source-threads')), findsOneWidget);
    expect(find.byKey(const ValueKey('home-source-mastodon')), findsOneWidget);
    await tester.tap(find.byTooltip(L10n.current.close));
    await tester.pumpAndSettle();
    await grouping.setGrouped(true);
    await tester.pumpAndSettle();
    expect(tester.state(find.byType(BlueskyScreen)), same(readerState));
    final mastodonChip = find.byKey(const ValueKey('alt-microblogging-service-mastodon'));
    await tester.ensureVisible(mastodonChip);
    await tester.tap(mastodonChip);
    await tester.pumpAndSettle();
    expect(selection.state.id, 'mastodon');
    expect(find.byType(MastodonScreen), findsOneWidget);
    expect(find.byType(BlueskyScreen), findsNothing);
    final blueChip = find.byKey(const ValueKey('alt-microblogging-service-bluesky'));
    await tester.ensureVisible(blueChip);
    await tester.tap(blueChip);
    await tester.pumpAndSettle();
    expect(selection.state.id, 'bluesky');
    expect(find.text(bluePost('root').text), findsOneWidget);
    expect(feed.state, same(posts));
    expect(blue.likes.state, same(likes));
    expect(blue.client.calls, hasLength(1));
    expect(prefs.get<String>(optionAltMicrobloggingLastSource), 'bluesky');
    expect(prefs.getStringList(optionHomeFeedStripPlugins), ['bluesky', 'threads', 'mastodon']);
    expect(tester.takeException(), isNull);
    await mastodon.close(tester);
    await blue.close(tester);
    await feed.destroy();
    await subscriptions.destroy();
    await groups.destroy();
    await strip.destroy();
    await selection.destroy();
    await grouping.destroy();
  });
}
