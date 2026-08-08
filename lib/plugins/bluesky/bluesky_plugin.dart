import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/home_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_butterfly_icon.dart';
import 'package:xta/plugins/bluesky/bluesky_likes_store.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_settings.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/settings/backup_rows.dart';
import 'package:xta/plugins/plugin_backup.dart';
import 'package:xta/settings/backup_category.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_category.dart';

/// Account-free Bluesky reading: local follows, public AppView feeds.
///
/// Skylib (AGPL) inspired the approach — not the code. See
/// docs/specs/bluesky-plugin.md.
class BlueskyPlugin extends XtaPlugin {
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
  String description(BuildContext context) => L10n.of(context).plugin_bluesky_description;

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
  List<String> get tables => const [
        tableBlueskySubscription,
        tableBlueskyLocalLike,
      ];

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
  }

  @override
  Future<void> forgetLoadedData(BuildContext context) async {
    final accounts = context.read<BlueskyAccountsStore>();
    final likes = context.read<BlueskyLikesStore>();
    final feed = context.read<BlueskyFeedStore>();
    await accounts.load();
    await likes.load();
    await feed.refresh();
  }
}
