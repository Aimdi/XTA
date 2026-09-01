import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/immich/immich_settings_screen.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_category.dart';

/// Sends the media of chosen bookmark folders to a self-hosted Immich instance.
/// No home tab: the plugin adds a per-folder switch where folders already are,
/// plus its own settings screen.
class ImmichPlugin extends XtaPlugin {
  ImmichPlugin();

  @override
  String get id => pluginIdImmich;

  @override
  String get enabledPrefKey => optionPluginImmichEnabled;

  @override
  IconData get icon => Icons.photo_library;

  @override
  PluginCategory get category => PluginCategory.media;

  @override
  Color get brandColor => const Color(0xFF4250AF);

  @override
  String title(BuildContext context) => L10n.of(context).plugin_immich_title;

  @override
  String description(BuildContext context) => L10n.of(context).plugin_immich_description;

  @override
  Widget? settingsScreen(BuildContext context) => const ImmichSettingsScreen();

  /// The record of what has been uploaded. Removing the plugin forgets it, so a
  /// later reinstall sends again rather than believing a server it no longer
  /// knows still has the files.
  @override
  List<String> get tables => const [tableImmichUpload];

  @override
  Future<void> resetPreferences(BasePrefService prefs) async {
    await prefs.set(optionPluginImmichServerUrl, '');
    await prefs.set(optionPluginImmichApiKey, '');
    await prefs.set(optionPluginImmichAlbumPerFolder, true);
    await prefs.set(optionPluginImmichIncludeVideos, true);
  }
}
