import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/home_screen.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/settings/backup_rows.dart';
import 'package:xta/plugins/plugin_backup.dart';
import 'package:xta/settings/backup_category.dart';
import 'package:xta/plugins/threads/threads_interleaved.dart';
import 'package:xta/plugins/subscription_source.dart';
import 'package:xta/tweet/interleaved_items.dart';
import 'package:xta/plugins/threads/threads_profile_screen.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_category.dart';
import 'package:xta/plugins/threads/threads_screen.dart';
import 'package:xta/plugins/threads/threads_settings.dart';
import 'package:xta/plugins/threads/threads_store.dart';

class ThreadsPlugin extends XtaPlugin with SubscriptionSource {
  ThreadsPlugin();

  @override
  String get id => pluginIdThreads;

  @override
  String get enabledPrefKey => optionPluginThreadsEnabled;

  @override
  String? get homeTabPrefKey => optionPluginThreadsShowTab;

  @override
  IconData get icon => Icons.alternate_email;

  @override
  PluginCategory get category => PluginCategory.social;

  @override
  Color get brandColor => const Color(0xFF101010);

  @override
  String title(BuildContext context) => L10n.of(context).plugin_threads_title;

  @override
  String description(BuildContext context) => L10n.of(context).plugin_threads_description;

  @override
  NavigationPage homePage(BuildContext context) {
    return NavigationPage(
      pluginIdThreads,
      (c) => L10n.of(c).plugin_threads_title,
      const Icon(Icons.alternate_email_outlined),
      const Icon(Icons.alternate_email),
    );
  }

  @override
  Widget homeScreen({required ScrollController scrollController}) {
    return ThreadsScreen(scrollController: scrollController);
  }

  @override
  Widget? settingsScreen(BuildContext context) => const ThreadsSettingsScreen();

  @override
  List<String> get tables => const [
    tableThreadsSubscription,
    tableThreadsLocalLike,
  ];

  @override
  String get subscriptionTable => tableThreadsSubscription;

  @override
  Subscription subscriptionFromMap(Map<String, Object?> row) => ThreadsSubscription.fromMap(row);

  @override
  bool owns(Subscription subscription) => subscription is ThreadsSubscription;

  @override
  Widget Function()? destinationFor(Subscription subscription) =>
      () => ThreadsProfileScreen(username: subscription.id);

  @override
  Future<void> reloadFromDatabase(BuildContext context) => context.read<ThreadsAccountsStore>().load();

  @override
  Future<void> unfollow(BuildContext context, Subscription subscription) =>
      context.read<ThreadsAccountsStore>().remove(subscription.id);

  @override
  Future<List<InterleavedItem>> interleavedPosts(BuildContext context, List<String> ids) =>
      loadThreadsInterleaved(context, ids);

  @override
  bool inHomeFeed(BuildContext context) => threadsInHomeFeed(PrefService.of(context, listen: false));

  @override
  List<String> homeFeedIds(BuildContext context) => threadsHomeHandles(context);

  @override
  List<PluginBackupSection> get backupSections => [
    PluginBackupSection(
      jsonKey: 'threadsSubscriptions',
      table: tableThreadsSubscription,
      category: BackupCategory.threads,
      fromMap: ThreadsSubscription.fromMap,
    ),
    PluginBackupSection(
      jsonKey: 'threadsLocalLikes',
      table: tableThreadsLocalLike,
      category: BackupCategory.threadsLikes,
      fromMap: ThreadsLocalLike.fromMap,
    ),
  ];

  @override
  Future<void> resetPreferences(BasePrefService prefs) async {
    await prefs.set(optionPluginThreadsInstance, '');
    await prefs.set(optionPluginThreadsApiBase, kThreadsApiDefaultBase);
    await prefs.set(optionPluginThreadsApiToken, '');
    await prefs.set(optionPluginThreadsDirectCookies, '');
    await prefs.set(optionPluginThreadsDirectBearer, '');
    await prefs.set(optionPluginThreadsDirectDeviceId, '');
    await prefs.set(optionPluginThreadsLikedPosts, '[]');
    await prefs.set(optionPluginThreadsGuestLsd, '');
    await prefs.set(optionPluginThreadsGuestLsdAt, '');
  }

  @override
  Future<void> forgetLoadedData(BuildContext context) async {
    await context.read<ThreadsAccountsStore>().load();
  }
}
