import 'package:sqflite/sqflite.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/plugins/plugin_account_subscription.dart';
import 'package:xta/plugins/subscription_source.dart';
import 'package:xta/plugins/pixiv/pixiv_group.dart';
import 'package:xta/plugins/pixiv/pixiv_group_store.dart';
import 'package:xta/plugins/pixiv/pixiv_user_screen.dart';
import 'package:xta/tweet/interleaved_items.dart';
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
class PixivPlugin extends XtaPlugin with SubscriptionSource {
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

  /// Logical storage key; Pixiv authors are carried by settings backup.
  @override
  String get subscriptionTable => optionPluginPixivGroupSubscriptions;

  @override
  String? get subscriptionPreferenceKey => optionPluginPixivGroupSubscriptions;

  @override
  Subscription subscriptionFromMap(Map<String, Object?> row) => PluginAccountSubscription(id, row);

  @override
  Future<List<Subscription>> readSubscriptions(DatabaseExecutor database, {BasePrefService? prefs}) async =>
      prefs == null ? const [] : readPixivGroupSubscriptions(prefs);

  @override
  bool owns(Subscription subscription) =>
      subscription is PluginAccountSubscription && subscription.pluginId == id;

  @override
  Widget Function() destinationFor(Subscription subscription) =>
      () => PixivUserScreen(userId: int.parse((subscription as PluginAccountSubscription).accountId));

  @override
  Future<void> reloadFromDatabase(BuildContext context) async {}

  @override
  Future<void> unfollow(BuildContext context, Subscription subscription) async {
    final store = PixivGroupSubscriptionsStore(PrefService.of(context, listen: false));
    try { await store.remove(subscription.id); } finally { store.destroy(); }
  }

  @override
  Future<List<InterleavedItem>> interleavedPosts(BuildContext context, List<String> ids) =>
      loadPixivGroupPosts(context, ids);

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
    final database = await Repository.writable();
    await database.delete(tableSubscriptionGroupMember,
      where: 'profile_id LIKE ?', whereArgs: ['$pluginIdPixiv:%']);
    await prefs.set(optionPluginPixivGroupSubscriptions, '[]');
  }

  @override
  Future<void> forgetLoadedData(BuildContext context) async {
    context.read<PixivFeedStore>().update(const []);
    context.read<PixivBookmarkStore>().update(const {});
  }
}
