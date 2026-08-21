import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/home_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_butterfly_icon.dart';
import 'package:xta/plugins/bluesky/bluesky_feeds_store.dart';
import 'package:xta/plugins/bluesky/bluesky_likes_store.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_search_sheet.dart';
import 'package:xta/plugins/bluesky/bluesky_settings.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/settings/backup_rows.dart';
import 'package:xta/plugins/plugin_backup.dart';
import 'package:xta/settings/backup_category.dart';
import 'package:xta/plugins/bluesky/bluesky_interleaved.dart';
import 'package:xta/plugins/subscription_source.dart';
import 'package:xta/tweet/interleaved_items.dart';
import 'package:xta/plugins/bluesky/bluesky_profile_screen.dart';
import 'package:xta/user.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_category.dart';

/// Account-free Bluesky reading: local follows, public AppView feeds.
///
/// Skylib (AGPL) inspired the approach — not the code. See
/// docs/specs/bluesky-plugin.md.
class BlueskyPlugin extends XtaPlugin with SubscriptionSource {
  BlueskyPlugin();

  @override
  String get id => pluginIdBluesky;

  @override
  String get enabledPrefKey => optionPluginBlueskyEnabled;

  @override
  String? get homeTabPrefKey => optionPluginBlueskyShowTab;

  /// Store row still needs an [IconData]; the home tab uses the painted butterfly.
  @override
  IconData get icon => Icons.cloud;

  @override
  PluginCategory get category => PluginCategory.social;

  @override
  Color get brandColor => const Color(0xFF0085FF);

  @override
  String title(BuildContext context) => L10n.of(context).plugin_bluesky_title;

  @override
  String description(BuildContext context) =>
      L10n.of(context).plugin_bluesky_description;

  @override
  NavigationPage homePage(BuildContext context) {
    return NavigationPage(
      pluginIdBluesky,
      (c) => L10n.of(c).plugin_bluesky_title,
      const BlueskyButterflyIcon(size: 22),
      const BlueskyButterflyIcon(size: 22),
    );
  }

  @override
  Widget homeScreen({required ScrollController scrollController}) {
    return BlueskyScreen(scrollController: scrollController);
  }

  @override
  Widget? settingsScreen(BuildContext context) => const BlueskySettingsScreen();

  @override
  bool get supportsSearch => true;

  @override
  Future<void> openSearch(BuildContext context, {String? initialQuery}) {
    return showBlueskySearchSheet(context, initialQuery: initialQuery);
  }

  @override
  List<String> get tables => const [
    tableBlueskySubscription,
    tableBlueskyLocalLike,
  ];

  @override
  String get subscriptionTable => tableBlueskySubscription;

  @override
  Subscription subscriptionFromMap(Map<String, Object?> row) =>
      BlueskySubscription.fromMap(row);

  @override
  bool owns(Subscription subscription) => subscription is BlueskySubscription;

  @override
  Widget avatarFor(Subscription subscription, {double size = 40}) => Stack(
    alignment: Alignment.bottomRight,
    children: [
      UserAvatar(uri: subscription.profileImageUrlHttps),
      const BlueskyButterflyIcon(size: 12),
    ],
  );

  @override
  Widget Function()? destinationFor(Subscription subscription) =>
      () => BlueskyProfileScreen(actor: subscription.id);

  @override
  Future<void> reloadFromDatabase(BuildContext context) =>
      context.read<BlueskyAccountsStore>().load();

  @override
  Future<void> unfollow(BuildContext context, Subscription subscription) =>
      context.read<BlueskyAccountsStore>().remove(subscription.id);

  @override
  bool inHomeFeed(BuildContext context) =>
      blueskyInHomeFeed(PrefService.of(context, listen: false));

  @override
  List<String> homeFeedIds(BuildContext context) => blueskyHomeIds(context);

  @override
  Future<List<InterleavedItem>> interleavedPosts(
    BuildContext context,
    List<String> ids,
  ) => loadBlueskyInterleaved(context, ids);

  @override
  List<PluginBackupSection> get backupSections => [
    PluginBackupSection(
      jsonKey: 'blueskySubscriptions',
      table: tableBlueskySubscription,
      category: BackupCategory.bluesky,
      fromMap: BlueskySubscription.fromMap,
    ),
    PluginBackupSection(
      jsonKey: 'blueskyLocalLikes',
      table: tableBlueskyLocalLike,
      category: BackupCategory.blueskyLikes,
      fromMap: BlueskyLocalLike.fromMap,
    ),
  ];

  @override
  Future<void> resetPreferences(BasePrefService prefs) async {
    await prefs.set(optionPluginBlueskyInstance, kBlueskyDefaultAppView);
    await prefs.set(optionPluginBlueskyLikedPosts, '[]');
    await prefs.set(optionPluginBlueskySearchHistory, '[]');
    await prefs.set(optionPluginBlueskyPinnedFeeds, '[]');
    await prefs.set(optionPluginBlueskyPinnedLists, '[]');
    await prefs.set(optionPluginBlueskyHandle, '');
  }

  @override
  Future<void> forgetLoadedData(BuildContext context) async {
    final accounts = context.read<BlueskyAccountsStore>();
    final likes = context.read<BlueskyLikesStore>();
    final feed = context.read<BlueskyFeedStore>();
    final algos = context.read<BlueskyAlgoStore>();
    final lists = context.read<BlueskyListsStore>();
    await accounts.load();
    await likes.load();
    await feed.refresh(force: true);
    await algos.ensureLoaded(force: true);
    await lists.ensureLoaded(force: true);
  }
}
