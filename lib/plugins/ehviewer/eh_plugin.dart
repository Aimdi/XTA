import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/home_screen.dart';
import 'package:xta/plugins/ehviewer/eh_screen.dart';
import 'package:xta/plugins/ehviewer/eh_search_screen.dart';
import 'package:xta/plugins/ehviewer/eh_settings.dart';
import 'package:xta/plugins/ehviewer/eh_store.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_backup.dart';
import 'package:xta/plugins/plugin_category.dart';
import 'package:xta/settings/backup_category.dart';

/// Private E-Hentai / ExHentai gallery browser inspired by EhViewer.
///
/// See docs/specs/ehviewer-plugin.md.
class EhViewerPlugin extends XtaPlugin {
  EhViewerPlugin();

  @override
  String get id => pluginIdEhViewer;

  @override
  String get enabledPrefKey => optionPluginEhEnabled;

  @override
  String? get homeTabPrefKey => optionPluginEhShowTab;

  @override
  bool get isPrivate => true;

  @override
  IconData get icon => Icons.collections_bookmark_outlined;

  @override
  PluginCategory get category => PluginCategory.art;

  @override
  Color get brandColor => const Color(0xFF5C6BC0);

  @override
  String title(BuildContext context) => L10n.of(context).plugin_eh_title;

  @override
  String description(BuildContext context) =>
      L10n.of(context).plugin_eh_description;

  @override
  NavigationPage homePage(BuildContext context) {
    return NavigationPage(
      pluginIdEhViewer,
      (c) => L10n.of(c).plugin_eh_title,
      const Icon(Icons.collections_bookmark_outlined),
      const Icon(Icons.collections_bookmark),
    );
  }

  @override
  Widget homeScreen({required ScrollController scrollController}) {
    return EhScreen(scrollController: scrollController);
  }

  @override
  Widget? settingsScreen(BuildContext context) => const EhSettingsScreen();

  @override
  bool get supportsSearch => true;

  @override
  Future<void> openSearch(BuildContext context, {String? initialQuery}) {
    return Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => EhSearchScreen(initialQuery: initialQuery),
      ),
    );
  }

  @override
  List<String> get tables => const [tableEhFavorite, tableEhHistory];

  @override
  List<PluginBackupSection> get backupSections => [
    PluginBackupSection(
      jsonKey: 'ehFavorites',
      table: tableEhFavorite,
      category: BackupCategory.ehFavorites,
      fromMap: (row) => EhFavorite.fromMap(row),
    ),
    PluginBackupSection(
      jsonKey: 'ehHistory',
      table: tableEhHistory,
      category: BackupCategory.ehHistory,
      fromMap: (row) => EhHistoryEntry.fromMap(row),
    ),
  ];

  @override
  Future<void> resetPreferences(BasePrefService prefs) async {
    await prefs.set(optionPluginEhCookies, '');
    await prefs.set(optionPluginEhUseExhentai, false);
    await prefs.set(optionPluginEhCategories, '');
    await prefs.set(optionPluginEhSearchHistory, '[]');
    await prefs.set(optionPluginEhPreferJapanese, true);
    await prefs.set(optionPluginEhKeepScreenOn, true);
  }

  @override
  Future<void> forgetLoadedData(BuildContext context) async {
    final favorites = context.read<EhFavoritesStore>();
    final history = context.read<EhHistoryStore>();
    await favorites.load();
    await history.load();
  }
}
