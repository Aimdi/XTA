import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/deepmarks/deepmarks_settings_screen.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_category.dart';

/// Saves links to Deepmarks, the Nostr-backed bookmarking service. Like the
/// Karakeep plugin this adds an action rather than a feed, plus a settings
/// screen for the API key and the signing key.
class DeepmarksPlugin extends XtaPlugin {
  DeepmarksPlugin();

  @override
  String get id => pluginIdDeepmarks;

  @override
  String get enabledPrefKey => optionPluginDeepmarksEnabled;

  @override
  IconData get icon => Icons.hub;

  @override
  PluginCategory get category => PluginCategory.bookmarks;

  /// Nostr-adjacent purple — Deepmarks is bookmarking on that network.
  @override
  Color get brandColor => const Color(0xFF8B5CF6);

  @override
  String title(BuildContext context) => L10n.of(context).plugin_deepmarks_title;

  @override
  String description(BuildContext context) => L10n.of(context).plugin_deepmarks_description;

  @override
  Widget? settingsScreen(BuildContext context) => const DeepmarksSettingsScreen();

  @override
  Future<void> resetPreferences(BasePrefService prefs) async {
    await prefs.set(optionPluginDeepmarksApiBase, '');
    await prefs.set(optionPluginDeepmarksApiKey, '');
    // The signing key is the reader's Nostr identity. Nothing about it should
    // outlive the plugin that asked for it.
    await prefs.set(optionPluginDeepmarksSecretKey, '');
  }
}
