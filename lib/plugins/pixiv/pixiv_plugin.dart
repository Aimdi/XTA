import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/home_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_store.dart';
import 'package:xta/plugins/pixiv/pixiv_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_search_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_category.dart';

/// Private Pixiv gallery — following, ranking, bookmarks, search.
///
/// Inspired by pixez-flutter's approach; code is original.
/// See docs/specs/pixiv-plugin.md.
class PixivPlugin extends XtaPlugin {
  PixivPlugin();

  @override
  String get id => pluginIdPixiv;

  @override
  String get enabledPrefKey => optionPluginPixivEnabled;

  @override
  String? get homeTabPrefKey => optionPluginPixivShowTab;

  @override
  bool get isPrivate => true;

  @override
  IconData get icon => Icons.brush;

  @override
  PluginCategory get category => PluginCategory.art;

  @override
  Color get brandColor => const Color(0xFF0096FA);

  @override
  String title(BuildContext context) => L10n.of(context).plugin_pixiv_title;

  @override
  String description(BuildContext context) =>
      L10n.of(context).plugin_pixiv_description;

  @override
  NavigationPage homePage(BuildContext context) {
    return NavigationPage(
      pluginIdPixiv,
      (c) => L10n.of(c).plugin_pixiv_title,
      const Icon(Icons.brush_outlined),
      const Icon(Icons.brush),
    );
  }

  @override
  Widget homeScreen({required ScrollController scrollController}) {
    return PixivScreen(scrollController: scrollController);
  }

  @override
  Widget? settingsScreen(BuildContext context) => const PixivSettingsScreen();

  @override
  bool get supportsSearch => true;

  @override
  Future<void> openSearch(BuildContext context, {String? initialQuery}) {
    return Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => PixivSearchScreen(initialQuery: initialQuery),
      ),
    );
  }

  @override
  Future<void> resetPreferences(BasePrefService prefs) async {
    await prefs.set(optionPluginPixivRefreshToken, '');
    await prefs.set(optionPluginPixivAccessToken, '');
    await prefs.set(optionPluginPixivAccessExpiresAt, '');
    await prefs.set(optionPluginPixivUserId, 0);
    await prefs.set(optionPluginPixivShowR18, false);
    await prefs.set(optionPluginPixivMutedAuthors, '[]');
    await prefs.set(optionPluginPixivMutedTags, '[]');
    await prefs.set(optionPluginPixivMutedIllusts, '[]');
    await prefs.set(optionPluginPixivSearchHistory, '[]');
  }

  @override
  Future<void> forgetLoadedData(BuildContext context) async {
    context.read<PixivFeedStore>().update(const []);
    context.read<PixivBookmarkStore>().update(const {});
  }
}
