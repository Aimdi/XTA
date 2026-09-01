import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/karakeep/karakeep_settings_screen.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_category.dart';

/// Sends links to a self-hosted Karakeep instance. No home tab: the plugin adds
/// a save action where links already are, plus its own settings screen.
class KarakeepPlugin extends XtaPlugin {
  KarakeepPlugin();

  @override
  String get id => pluginIdKarakeep;

  @override
  String get enabledPrefKey => optionPluginKarakeepEnabled;

  @override
  IconData get icon => Icons.bookmark_add;

  @override
  PluginCategory get category => PluginCategory.bookmarks;

  @override
  Color get brandColor => const Color(0xFF0F766E);

  @override
  String title(BuildContext context) => L10n.of(context).plugin_karakeep_title;

  @override
  String description(BuildContext context) => L10n.of(context).plugin_karakeep_description;

  @override
  Widget? settingsScreen(BuildContext context) => const KarakeepSettingsScreen();

  @override
  Future<void> resetPreferences(BasePrefService prefs) async {
    await prefs.set(optionPluginKarakeepServerUrl, '');
    await prefs.set(optionPluginKarakeepApiKey, '');
  }
}
