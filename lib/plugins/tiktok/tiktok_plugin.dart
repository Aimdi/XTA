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
import 'package:xta/plugins/tiktok/tiktok_settings.dart';
import 'package:xta/plugins/tiktok/tiktok_store.dart';
import 'package:xta/settings/backup_category.dart';

/// Private guest TikTok plugin. No account; follows stay on this device.
///
/// See docs/specs/tiktok-plugin.md.
class TikTokPlugin extends XtaPlugin {
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
  List<String> get tables => const [tableTiktokSubscription];

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
