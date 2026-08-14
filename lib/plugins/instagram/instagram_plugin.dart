import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/home_screen.dart';
import 'package:xta/plugins/instagram/instagram_screen.dart';
import 'package:xta/plugins/instagram/instagram_settings.dart';
import 'package:xta/plugins/instagram/instagram_store.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_backup.dart';
import 'package:xta/plugins/plugin_category.dart';
import 'package:xta/settings/backup_category.dart';

/// Private Instagram plugin. Guest first; cookies optional. Follows stay here.
class InstagramPlugin extends XtaPlugin {
  InstagramPlugin();

  @override
  String get id => pluginIdInstagram;

  @override
  String get enabledPrefKey => optionPluginInstagramEnabled;

  @override
  String? get homeTabPrefKey => optionPluginInstagramShowTab;

  @override
  bool get isPrivate => true;

  @override
  IconData get icon => Icons.camera_alt_outlined;

  @override
  PluginCategory get category => PluginCategory.social;

  @override
  Color get brandColor => const Color(0xFFE1306C);

  @override
  String title(BuildContext context) => L10n.of(context).plugin_instagram_title;

  @override
  String description(BuildContext context) =>
      L10n.of(context).plugin_instagram_description;

  @override
  NavigationPage homePage(BuildContext context) {
    return NavigationPage(
      pluginIdInstagram,
      (c) => L10n.of(c).plugin_instagram_title,
      const Icon(Icons.camera_alt_outlined),
      const Icon(Icons.camera_alt),
    );
  }

  @override
  Widget homeScreen({required ScrollController scrollController}) {
    return InstagramScreen(scrollController: scrollController);
  }

  @override
  Widget? settingsScreen(BuildContext context) =>
      const InstagramSettingsScreen();

  @override
  List<String> get tables => const [tableInstagramSubscription];

  @override
  List<PluginBackupSection> get backupSections => [
    PluginBackupSection(
      jsonKey: 'instagramSubscriptions',
      table: tableInstagramSubscription,
      category: BackupCategory.instagramSubscriptions,
      fromMap: InstagramFollow.fromMap,
    ),
  ];

  @override
  Future<void> resetPreferences(BasePrefService prefs) async {
    await prefs.set(optionPluginInstagramCookies, '');
    await prefs.set(optionPluginInstagramSearchHistory, '[]');
    await prefs.set(optionPluginInstagramLikedPosts, '[]');
  }

  @override
  Future<void> forgetLoadedData(BuildContext context) async {
    final follows = context.read<InstagramFollowsStore>();
    final likes = context.read<InstagramLikesStore>();
    final history = context.read<InstagramSearchHistoryStore>();
    await Future.wait([follows.load(), likes.load(), history.load()]);
  }
}
