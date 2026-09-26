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
import 'package:xta/home/chrome_avatar.dart';
import 'package:xta/home/feed_strip_store.dart';
import 'package:xta/home/home_account_filter.dart';
import 'package:xta/home/home_group_filter.dart';
import 'package:xta/home/network_recents_store.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_likes_store.dart';
import 'package:xta/plugins/bluesky/bluesky_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';
import 'package:xta/plugins/plugin_home_dock.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'support/bluesky_reading_harness.dart';
import 'support/mastodon_harness.dart';

import 'package:xta/plugins/bluesky/bluesky_post_card.dart';
import 'package:xta/plugins/bluesky/bluesky_reader_store.dart';
import 'package:xta/plugins/bluesky/bluesky_reader_view.dart';

void main() {
  setUpAll(() async {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfiNoIsolate;
    final db = await Directory.systemTemp.createTemp('xta-unified-home');
    await databaseFactory.setDatabasesPath(db.path);
    await Repository().migrate();
    if (const bool.fromEnvironment('RENDER_UNIFIED_HOME')) {
      autoUpdateGoldenFiles = true;
      await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-Regular.ttf'))).load();
      await (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
    }
  });

  for (final (width, scale, rtl, dark) in <(double, double, bool, bool)>[
    (390, 1, false, false),
    (390, 1, false, true),
    (320, 2, true, true),
    (840, 2, false, false),
  ]) {
    testWidgets('assembled Home layout $width $scale rtl=$rtl dark=$dark', (tester) async {
      tester.view.physicalSize = Size(width, 844);
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
          dark: dark,
          scale: scale,
          rtl: rtl,
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
      final first = find.byType(BlueskyPostCard).first;
      expect(first, findsOneWidget);
      if (width >= 390 && scale == 1) {
        expect(tester.getTopLeft(first).dy, lessThanOrEqualTo(112));
      }
      final search = find.byTooltip(L10n.current.plugin_bluesky_search);
      expect(search, findsOneWidget);
      expect(tester.getTopLeft(search).dy, lessThan(56));
      expect(tester.getSize(find.byKey(const ValueKey('alt-microblogging-service-bluesky'))).width, 48);
      expect(blue.client.calls, hasLength(1));
      expect(tester.takeException(), isNull);
      if (const bool.fromEnvironment('RENDER_UNIFIED_HOME')) {
        await expectLater(
          find.byKey(const ValueKey('mastodon-window')),
          matchesGoldenFile('../review-artifacts/renders/home-$width-$scale-$rtl-$dark.png'),
        );
      }
      if (width == 390 && scale == 1) {
        final filters = find.byTooltip(L10n.current.filters);
        final readerView = find.byType(BlueskyReaderView);
        final originalReader = tester.state(find.byType(BlueskyScreen));
        mastodon.scroll.jumpTo(80);
        await tester.pumpAndSettle();
        expect(filters.hitTestable(), findsOneWidget, reason: 'Restoring scroll is not a reading gesture.');
        mastodon.scroll.jumpTo(0);
        await tester.pumpAndSettle();
        await tester.drag(readerView, const Offset(0, -320));
        await tester.pumpAndSettle();
        expect(filters.hitTestable(), findsNothing, reason: 'Secondary controls recede during deliberate reading.');
        expect(search.hitTestable(), findsOneWidget, reason: 'The main header remains accessible.');
        await tester.drag(readerView, const Offset(0, 100));
        await tester.pumpAndSettle();
        expect(filters.hitTestable(), findsOneWidget, reason: 'An upward gesture restores controls.');
        final more = find.descendant(of: find.byType(PluginDockActions), matching: find.byType(PopupMenuButton<String>));
        await tester.tap(more);
        await tester.pumpAndSettle();
        final pin = find.byKey(const ValueKey('home-pin-controls'));
        expect(pin, findsOneWidget);
        await tester.tap(pin);
        await tester.pumpAndSettle();
        await tester.drag(readerView, const Offset(0, -280));
        await tester.pumpAndSettle();
        expect(filters.hitTestable(), findsOneWidget, reason: 'Pinned controls stay visible.');
        expect(prefs.get<bool>('home_keep_controls_visible'), isTrue);
        await tester.tap(more);
        await tester.pumpAndSettle();
        await tester.tap(pin);
        await tester.pumpAndSettle();
        expect(prefs.get<bool>('home_keep_controls_visible'), isFalse);
        mastodon.scroll.jumpTo(0);
        await tester.pumpAndSettle();
        expect(tester.state(find.byType(BlueskyScreen)), same(originalReader));
        expect(blue.client.calls, hasLength(1));
      }
      // The relocated filter controls still steer this exact reader store.
      final reader = tester.element(find.byType(BlueskyReaderView)).read<BlueskyReaderStore>();
      final beforeState = tester.state(find.byType(BlueskyScreen));
      await tester.tap(find.byTooltip(L10n.current.filters));
      await tester.pumpAndSettle();
      expect(find.text(L10n.current.plugin_mastodon_loaded_controls), findsOneWidget);
      final oldest = find.widgetWithText(ChoiceChip, L10n.current.plugin_mastodon_order_oldest);
      await tester.ensureVisible(oldest);
      await tester.tap(oldest);
      await tester.pumpAndSettle();
      expect(reader.options('following').order, BlueskyReaderOrder.oldest);
      await tester.tap(find.byTooltip(L10n.current.close));
      await tester.pumpAndSettle();
      expect(tester.state(find.byType(BlueskyScreen)), same(beforeState));
      expect(blue.client.calls, hasLength(1), reason: 'Local controls must not refetch the feed.');
      final menu = find.descendant(of: find.byType(PluginDockActions), matching: find.byType(PopupMenuButton<String>));
      await tester.tap(menu);
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('open-client-bluesky')), findsOneWidget);
      expect(
        tester.widgetList<PopupMenuItem<String>>(find.byType(PopupMenuItem<String>)).map((item) => item.value),
        containsAll(['add', 'saved', 'following', 'list', 'starter', 'settings', 'xta:open-client']),
      );
      await tester.tapAt(const Offset(8, 730));
      await tester.pumpAndSettle();
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
}
