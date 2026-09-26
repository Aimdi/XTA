import 'dart:io';
import 'package:flutter/material.dart';
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
import 'package:xta/plugins/bluesky/bluesky_post_card.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'support/bluesky_reading_harness.dart';
import 'support/mastodon_harness.dart';

void main() {
  setUpAll(() async {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfiNoIsolate;
    final db = await Directory.systemTemp.createTemp('xta-unified-home');
    await databaseFactory.setDatabasesPath(db.path);
    await Repository().migrate();
  });

  testWidgets('assembled Bluesky Home starts content within two control rows', (tester) async {
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
    expect(find.byType(BlueskyPostCard), findsWidgets);
    expect(tester.getTopLeft(find.byType(BlueskyPostCard).first).dy, lessThanOrEqualTo(112));
    final search = find.byTooltip(L10n.current.plugin_bluesky_search);
    expect(search, findsOneWidget);
    expect(tester.getTopLeft(search).dy, lessThan(56));
    expect(blue.client.calls, hasLength(1));
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
