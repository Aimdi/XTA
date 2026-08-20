import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/home_screen.dart';
import 'package:xta/plugins/hackernews/hn_screen.dart';
import 'package:xta/plugins/hackernews/hn_search_sheet.dart';
import 'package:xta/plugins/hackernews/hn_settings.dart';
import 'package:xta/plugins/hackernews/hn_store.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_category.dart';

/// Guest Hacker News reader. No login, vote, comment, or submit.
const hackerNewsBrand = Color(0xFFFF6600);

class HackerNewsPlugin extends XtaPlugin {
  HackerNewsPlugin();

  @override
  String get id => pluginIdHackerNews;

  @override
  String get enabledPrefKey => optionPluginHnEnabled;

  @override
  String? get homeTabPrefKey => optionPluginHnShowTab;

  @override
  IconData get icon => Icons.forum_outlined;

  @override
  PluginCategory get category => PluginCategory.communities;

  @override
  Color get brandColor => hackerNewsBrand;

  @override
  String title(BuildContext context) => L10n.of(context).plugin_hn_title;

  @override
  String description(BuildContext context) =>
      L10n.of(context).plugin_hn_description;

  @override
  NavigationPage homePage(BuildContext context) {
    return NavigationPage(
      pluginIdHackerNews,
      (c) => L10n.of(c).plugin_hn_title,
      const Icon(Icons.forum_outlined),
      const Icon(Icons.forum),
    );
  }

  @override
  Widget homeScreen({required ScrollController scrollController}) {
    return HnScreen(scrollController: scrollController);
  }

  @override
  Widget? settingsScreen(BuildContext context) => const HnSettingsScreen();

  @override
  bool get supportsSearch => true;

  @override
  Future<void> openSearch(BuildContext context, {String? initialQuery}) {
    return showHnSearchSheet(context, initialQuery: initialQuery);
  }

  @override
  Future<void> resetPreferences(BasePrefService prefs) async {
    await prefs.set(optionPluginHnLikedPosts, '[]');
    await prefs.set(optionPluginHnSavedPosts, '[]');
    await prefs.set(optionPluginHnFollows, '[]');
    await prefs.set(optionPluginHnSearchHistory, '[]');
  }

  @override
  Future<void> forgetLoadedData(BuildContext context) async {
    final likes = context.read<HnLikesStore>();
    final saved = context.read<HnSavedStore>();
    final follows = context.read<HnFollowsStore>();
    final history = context.read<HnSearchHistoryStore>();
    await Future.wait([
      likes.load(),
      saved.load(),
      follows.load(),
      history.load(),
    ]);
  }
}
