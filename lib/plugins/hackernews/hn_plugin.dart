import 'package:sqflite/sqflite.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/plugins/plugin_account_subscription.dart';
import 'package:xta/plugins/subscription_source.dart';
import 'package:xta/plugins/hackernews/hn_group.dart';
import 'package:xta/plugins/hackernews/hn_user_screen.dart';
import 'package:xta/tweet/interleaved_items.dart';
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

class HackerNewsPlugin extends XtaPlugin with SubscriptionSource {
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
  String get subscriptionTable => optionPluginHnFollows;

  @override
  String? get subscriptionPreferenceKey => optionPluginHnFollows;

  @override
  Subscription subscriptionFromMap(Map<String, Object?> row) => PluginAccountSubscription(id, row);

  @override
  Future<List<Subscription>> readSubscriptions(DatabaseExecutor database, {BasePrefService? prefs}) async =>
      prefs == null ? const [] : readHnSubscriptions(prefs);

  @override
  bool owns(Subscription subscription) =>
      subscription is PluginAccountSubscription && subscription.pluginId == id;

  @override
  Widget Function() destinationFor(Subscription subscription) => () => HnUserScreen(userId: subscription.screenName);

  @override
  String subtitleFor(Subscription subscription) => subscription.screenName;

  @override
  Future<void> reloadFromDatabase(BuildContext context) => context.read<HnFollowsStore>().load();

  @override
  Future<void> unfollow(BuildContext context, Subscription subscription) async {
    final follows = context.read<HnFollowsStore>();
    await follows.load();
    if (follows.isFollowing(subscription.screenName)) await follows.toggle(subscription.screenName);
  }

  @override
  Future<List<InterleavedItem>> interleavedPosts(BuildContext context, List<String> ids) => loadHnGroupPosts(context, ids);

  @override
  Future<void> resetPreferences(BasePrefService prefs) async {
    await prefs.set(optionPluginHnLikedPosts, '[]');
    await prefs.set(optionPluginHnSavedPosts, '[]');
    await prefs.set(optionPluginHnFollows, '[]');
    final database = await Repository.writable();
    await database.delete(tableSubscriptionGroupMember,
      where: 'profile_id LIKE ?', whereArgs: ['$pluginIdHackerNews:%']);
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
