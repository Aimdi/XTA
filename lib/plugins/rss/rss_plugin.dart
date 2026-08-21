import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/home_screen.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_backup.dart';
import 'package:xta/plugins/plugin_category.dart';
import 'package:xta/plugins/rss/rss_feed_screen.dart';
import 'package:xta/plugins/rss/rss_interleaved.dart';
import 'package:xta/plugins/rss/rss_screen.dart';
import 'package:xta/plugins/rss/rss_settings.dart';
import 'package:xta/plugins/rss/rss_store.dart';
import 'package:xta/plugins/source_tables.dart';
import 'package:xta/plugins/subscription_source.dart';
import 'package:xta/settings/backup_category.dart';
import 'package:xta/tweet/interleaved_items.dart';

const rssBrand = Color(0xFFEE802F);

class RssPlugin extends XtaPlugin with SubscriptionSource {
  RssPlugin();

  @override
  String get id => pluginIdRss;

  @override
  String get enabledPrefKey => optionPluginRssEnabled;

  @override
  String? get homeTabPrefKey => optionPluginRssShowTab;

  @override
  IconData get icon => Icons.rss_feed;

  @override
  PluginCategory get category => PluginCategory.newsletters;

  @override
  Color get brandColor => rssBrand;

  @override
  String title(BuildContext context) => L10n.of(context).plugin_rss_title;

  @override
  String description(BuildContext context) =>
      L10n.of(context).plugin_rss_description;

  @override
  NavigationPage homePage(BuildContext context) {
    return NavigationPage(
      pluginIdRss,
      (c) => L10n.of(c).plugin_rss_title,
      const Icon(Icons.rss_feed_outlined),
      const Icon(Icons.rss_feed),
    );
  }

  @override
  Widget homeScreen({required ScrollController scrollController}) {
    return RssScreen(scrollController: scrollController);
  }

  @override
  Widget? settingsScreen(BuildContext context) => const RssSettingsScreen();

  @override
  List<String> get tables => const [tableRssSubscription];

  @override
  String get subscriptionTable => tableRssSubscription;

  @override
  Subscription subscriptionFromMap(Map<String, Object?> row) =>
      RssSubscription.fromMap(row);

  @override
  bool owns(Subscription subscription) => subscription is RssSubscription;

  @override
  String subtitleFor(Subscription subscription) {
    final url =
        (subscription as RssSubscription).siteUrl ?? subscription.feedUrl;
    return Uri.tryParse(url)?.host ?? url;
  }

  @override
  Widget Function()? destinationFor(Subscription subscription) =>
      () => RssFeedScreen(feed: feedOf(subscription as RssSubscription));

  @override
  Future<void> reloadFromDatabase(BuildContext context) =>
      context.read<RssFeedsStore>().load();

  @override
  Future<void> unfollow(BuildContext context, Subscription subscription) =>
      context.read<RssFeedsStore>().remove(subscription.id);

  @override
  bool inHomeFeed(BuildContext context) =>
      rssInHomeFeed(PrefService.of(context, listen: false));

  @override
  List<String> homeFeedIds(BuildContext context) => rssHomeIds(context);

  @override
  Future<List<InterleavedItem>> interleavedPosts(
    BuildContext context,
    List<String> ids,
  ) async {
    final database = await Repository.readOnly();
    final rows = await querySourceTable(database, tableRssSubscription);
    final feeds = rows
        .map(RssSubscription.fromMap)
        .where((feed) => ids.contains(feed.id))
        .toList(growable: false);

    if (!context.mounted) {
      return const [];
    }
    return loadRssInterleaved(context, feeds);
  }

  @override
  List<PluginBackupSection> get backupSections => [
    PluginBackupSection(
      jsonKey: 'rssSubscriptions',
      table: tableRssSubscription,
      category: BackupCategory.rss,
      fromMap: RssSubscription.fromMap,
    ),
  ];

  @override
  Future<void> resetPreferences(BasePrefService prefs) async {
    await prefs.set(optionPluginRssFeeds, '[]');
    await prefs.set(optionPluginRssReadIds, '[]');
    await prefs.set(optionPluginRssTags, '{}');
    await prefs.set(optionPluginRssInHomeFeed, false);
  }

  @override
  Future<void> forgetLoadedData(BuildContext context) async {
    await context.read<RssFeedsStore>().load();
    if (context.mounted) {
      await context.read<RssReadStore>().load();
    }
    if (context.mounted) {
      await context.read<RssTagsStore>().load();
    }
  }
}
