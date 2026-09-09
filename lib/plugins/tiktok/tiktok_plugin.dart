import 'package:xta/database/entities.dart';
import 'package:xta/plugins/plugin_account_subscription.dart';
import 'package:xta/plugins/subscription_source.dart';
import 'package:xta/plugins/tiktok/tiktok_group.dart';
import 'package:xta/plugins/tiktok/tiktok_profile_screen.dart';
import 'package:xta/tweet/interleaved_items.dart';
import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/home_screen.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_backup.dart';
import 'package:xta/plugins/plugin_category.dart';
import 'package:xta/plugins/tiktok/tiktok_screen.dart';
import 'package:xta/plugins/tiktok/tiktok_search_sheet.dart';
import 'package:xta/plugins/tiktok/tiktok_settings.dart';
import 'package:xta/plugins/tiktok/tiktok_store.dart';
import 'package:xta/settings/backup_category.dart';

/// Private guest TikTok plugin. No account; follows stay on this device.
///
/// See docs/specs/tiktok-plugin.md.
class TikTokPlugin extends XtaPlugin with SubscriptionSource {
  TikTokPlugin();

  @override
  String get id => pluginIdTiktok;

  @override
  String get enabledPrefKey => optionPluginTiktokEnabled;

  @override
  String? get homeTabPrefKey => optionPluginTiktokShowTab;

  @override
  bool get isPrivate => true;

  @override
  IconData get icon => Icons.music_video_outlined;

  @override
  PluginCategory get category => PluginCategory.social;

  @override
  Color get brandColor => const Color(0xFFFE2C55);

  @override
  String title(BuildContext context) => L10n.of(context).plugin_tiktok_title;

  @override
  String description(BuildContext context) =>
      L10n.of(context).plugin_tiktok_description;

  @override
  NavigationPage homePage(BuildContext context) {
    return NavigationPage(
      pluginIdTiktok,
      (c) => L10n.of(c).plugin_tiktok_title,
      const Icon(Icons.music_video_outlined),
      const Icon(Icons.music_video),
    );
  }

  @override
  Widget homeScreen({required ScrollController scrollController}) {
    return TikTokScreen(scrollController: scrollController);
  }

  @override
  Widget? settingsScreen(BuildContext context) => const TikTokSettingsScreen();

  @override
  bool get supportsSearch => true;

  @override
  Future<void> openSearch(BuildContext context, {String? initialQuery}) {
    return showTikTokSearchSheet(context, initialQuery: initialQuery);
  }

  @override
  List<String> get tables => const [tableTiktokSubscription];

  @override
  String get groupMembershipPrefix => '$id:';

  @override
  String get subscriptionTable => tableTiktokSubscription;

  @override
  Subscription subscriptionFromMap(Map<String, Object?> row) =>
      PluginAccountSubscription(id, row);

  @override
  bool owns(Subscription subscription) =>
      subscription is PluginAccountSubscription && subscription.pluginId == id;

  @override
  Widget Function() destinationFor(Subscription subscription) =>
      () => TikTokProfileScreen(handle: subscription.screenName);

  @override
  Future<void> reloadFromDatabase(BuildContext context) => context.read<TikTokFollowsStore>().load();

  @override
  Future<void> unfollow(BuildContext context, Subscription subscription) =>
      context.read<TikTokFollowsStore>().unfollow(subscription.screenName);

  @override
  Future<List<InterleavedItem>> interleavedPosts(BuildContext context, List<String> ids) =>
      loadTikTokGroupPosts(context, ids);

  @override
  List<PluginBackupSection> get backupSections => [
    PluginBackupSection(
      jsonKey: 'tiktokSubscriptions',
      table: tableTiktokSubscription,
      category: BackupCategory.tiktokSubscriptions,
      fromMap: TikTokFollow.fromMap,
    ),
  ];

  @override
  Future<void> resetPreferences(BasePrefService prefs) async {
    await prefs.set(optionPluginTiktokCookies, '');
    await prefs.set(optionPluginTiktokDeviceId, '');
    await prefs.set(optionPluginTiktokSearchHistory, '[]');
    await prefs.set(optionPluginTiktokLikedPosts, '[]');
    await prefs.set(optionPluginTiktokPreferEmbed, false);
  }

  @override
  Future<void> forgetLoadedData(BuildContext context) async {
    final follows = context.read<TikTokFollowsStore>();
    final likes = context.read<TikTokLikesStore>();
    final history = context.read<TikTokSearchHistoryStore>();
    await Future.wait([follows.load(), likes.load(), history.load()]);
  }
}
