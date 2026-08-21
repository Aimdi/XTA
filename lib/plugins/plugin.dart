import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/home/home_screen.dart';
import 'package:xta/plugins/plugin_backup.dart';
import 'package:xta/plugins/plugin_category.dart';
import 'package:xta/plugins/plugin_storage.dart';

/// Built-in XTA plugin descriptor. Plugins are read-oriented feature packs;
/// they must not add X posting / compose capabilities.
abstract class XtaPlugin {
  String get id;
  String get enabledPrefKey;
  IconData get icon;

  /// Store grouping (social, communities, newsletters, art, …).
  PluginCategory get category;

  /// Accent for the store / tab mark — the colour people associate with the
  /// service, not a logo asset.
  Color get brandColor;

  /// When true, the public catalogue never offers this plugin; the store only
  /// surfaces it after “Show private plugins” or once it is already installed.
  bool get isPrivate => false;

  String title(BuildContext context);
  String description(BuildContext context);

  bool isEnabled(BasePrefService prefs) => prefs.get(enabledPrefKey) == true;

  Future<void> setEnabled(BasePrefService prefs, bool enabled) async {
    await prefs.set(enabledPrefKey, enabled);
  }

  /// Preference controlling whether this plugin occupies a home tab, for
  /// plugins whose feature is reachable elsewhere too. Null means the tab is
  /// not optional.
  String? get homeTabPrefKey => null;

  /// Whether the plugin should currently take up a home tab.
  bool showsHomeTab(BasePrefService prefs) {
    final key = homeTabPrefKey;
    return key == null || prefs.get(key) != false;
  }

  /// Optional home tab when the plugin is enabled.
  NavigationPage? homePage(BuildContext context) => null;

  /// Root screen for the home tab (used by [HomeScreen]).
  Widget? homeScreen({required ScrollController scrollController}) => null;

  /// Whether this plugin can be pinned next to Following / For you.
  ///
  /// Defaults to plugins that already expose a home tab — helpers without a
  /// timeline (Karakeep, Immich, …) leave [homeTabPrefKey] null and stay out.
  bool get supportsFeedStrip => homeTabPrefKey != null;

  /// Body for a feed-strip pin. Defaults to the bottom-nav [homeScreen].
  Widget? feedStripScreen({required ScrollController scrollController}) =>
      homeScreen(scrollController: scrollController);

  /// Optional configuration screen, reached from the plugin store row.
  Widget? settingsScreen(BuildContext context) => null;

  /// Tables whose rows are this plugin's, and named caches it fills.
  ///
  /// Declared rather than deleted by hand so that [footprint] and [uninstall]
  /// cannot disagree about what the plugin owns.
  List<String> get tables => const [];
  List<String> get caches => const [];

  /// Which of those tables belong in a backup, and under what name.
  ///
  /// A plugin's rows are usually the only copy in existence — nothing tells
  /// Reddit about a device-local upvote — so leaving one out of a backup loses
  /// it for good. Declaring it here means the backup reads the plugin rather
  /// than a list somebody has to remember to extend.
  List<PluginBackupSection> get backupSections => const [];

  /// Returns this plugin's own settings to their defaults.
  ///
  /// Written by each plugin rather than derived from a list of keys: `pref`
  /// stores a value for every key it has seen, so a reset is a typed write and
  /// not a deletion.
  Future<void> resetPreferences(BasePrefService prefs) async {}

  /// What the plugin is currently keeping on the device.
  Future<PluginFootprint> footprint() =>
      pluginFootprint(tables: tables, caches: caches);

  /// Switches the plugin off and deletes everything it saved.
  ///
  /// Turning a plugin off used to leave its subscriptions, its cache and its
  /// credentials sitting there — which is not what anyone means by removing
  /// something.
  Future<void> uninstall(BuildContext context) async {
    final prefs = PrefService.of(context, listen: false);

    await erasePluginStorage(tables: tables, caches: caches);
    await resetPreferences(prefs);
    await setEnabled(prefs, false);

    final pinned = prefs.getStringList(optionHomeFeedStripPlugins);
    if (pinned != null && pinned.contains(id)) {
      await prefs.set(
        optionHomeFeedStripPlugins,
        pinned.where((e) => e != id).toList(),
      );
    }
    final seeded = prefs.getStringList(optionSeededStripPlugins);
    if (seeded != null && seeded.contains(id)) {
      await prefs.set(
        optionSeededStripPlugins,
        seeded.where((e) => e != id).toList(),
      );
    }

    final tab = homeTabPrefKey;
    if (tab != null) {
      // Installing it again should offer the tab, as a first install does.
      await prefs.set(tab, true);
    }

    if (context.mounted) {
      await forgetLoadedData(context);
    }
  }

  /// Empties what the plugin is holding in memory.
  ///
  /// The stores outlive the screens, so without this, installing again in the
  /// same session brings back a list that has just been deleted.
  Future<void> forgetLoadedData(BuildContext context) async {}

  /// Whether the Search tab can hand a query to this plugin.
  bool get supportsSearch => false;

  /// Opens this plugin's search UI, optionally with a query already filled in.
  Future<void> openSearch(BuildContext context, {String? initialQuery}) async {}
}
