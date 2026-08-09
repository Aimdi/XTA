import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/home_screen.dart';
import 'package:xta/plugins/mastodon/mastodon_screen.dart';
import 'package:xta/plugins/mastodon/mastodon_settings.dart';
import 'package:xta/plugins/mastodon/mastodon_store.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/plugins/plugin_backup.dart';
import 'package:xta/settings/backup_category.dart';
import 'package:xta/plugins/mastodon/mastodon_interleaved.dart';
import 'package:xta/plugins/subscription_source.dart';
import 'package:xta/tweet/interleaved_items.dart';
import 'package:xta/plugins/mastodon/mastodon_profile_screen.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_category.dart';

/// Account-free Fediverse reading through a home Mastodon-compatible instance.
///
/// See docs/specs/mastodon-plugin.md.
class MastodonPlugin extends XtaPlugin with SubscriptionSource {
  MastodonPlugin();

  @override
  String get id => pluginIdMastodon;

  @override
  String get enabledPrefKey => optionPluginMastodonEnabled;

  @override
  String? get homeTabPrefKey => optionPluginMastodonShowTab;

  @override
  IconData get icon => Icons.public;

  @override
  PluginCategory get category => PluginCategory.social;

  @override
  Color get brandColor => const Color(0xFF6364FF);

  @override
  String title(BuildContext context) => L10n.of(context).plugin_mastodon_title;

  @override
  String description(BuildContext context) => L10n.of(context).plugin_mastodon_description;

  @override
  NavigationPage homePage(BuildContext context) {
    return NavigationPage(
      pluginIdMastodon,
      (c) => L10n.of(c).plugin_mastodon_title,
      const Icon(Icons.public_outlined),
      const Icon(Icons.public),
    );
  }

  @override
  Widget homeScreen({required ScrollController scrollController}) {
    return MastodonScreen(scrollController: scrollController);
  }

  @override
  Widget? settingsScreen(BuildContext context) => const MastodonSettingsScreen();

  @override
  List<String> get tables => const [tableMastodonSubscription];

  @override
  String get subscriptionTable => tableMastodonSubscription;

  @override
  Subscription subscriptionFromMap(Map<String, Object?> row) => MastodonSubscription.fromMap(row);

  @override
  bool owns(Subscription subscription) => subscription is MastodonSubscription;

  @override
  Widget Function()? destinationFor(Subscription subscription) =>
      () => MastodonProfileScreen(acct: subscription.id);

  @override
  Future<void> reloadFromDatabase(BuildContext context) => context.read<MastodonAccountsStore>().load();

  @override
  Future<void> unfollow(BuildContext context, Subscription subscription) =>
      context.read<MastodonAccountsStore>().remove(subscription.id);

  @override
  bool inHomeFeed(BuildContext context) => fediverseInHomeFeed(PrefService.of(context, listen: false));

  @override
  List<String> homeFeedIds(BuildContext context) => fediverseHomeIds(context);

  @override
  Future<List<InterleavedItem>> interleavedPosts(BuildContext context, List<String> ids) =>
      loadMastodonInterleaved(context, ids);

  @override
  List<PluginBackupSection> get backupSections => [
    PluginBackupSection(
      jsonKey: 'mastodonSubscriptions',
      table: tableMastodonSubscription,
      category: BackupCategory.mastodon,
      fromMap: MastodonSubscription.fromMap,
    ),
  ];

  @override
  Future<void> resetPreferences(BasePrefService prefs) async {
    await prefs.set(optionPluginMastodonInstance, '');
  }

  @override
  Future<void> forgetLoadedData(BuildContext context) async {
    final accounts = context.read<MastodonAccountsStore>();
    final feed = context.read<MastodonFeedStore>();
    await accounts.load();
    await feed.refresh();
  }
}
