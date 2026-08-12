import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/home_screen.dart';
import 'package:xta/plugins/booru/booru_engines.dart';
import 'package:xta/plugins/booru/booru_interleaved.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_screen.dart';
import 'package:xta/plugins/booru/booru_search_screen.dart';
import 'package:xta/plugins/booru/booru_settings.dart';
import 'package:xta/plugins/booru/booru_store.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_backup.dart';
import 'package:xta/plugins/plugin_category.dart';
import 'package:xta/plugins/subscription_source.dart';
import 'package:xta/settings/backup_category.dart';
import 'package:xta/tweet/interleaved_items.dart';

/// Multi-booru gallery inspired by Boorusama — read-only, original code.
///
/// See docs/specs/booru-plugin.md.
class BooruPlugin extends XtaPlugin with SubscriptionSource {
  BooruPlugin();

  @override
  String get id => pluginIdBooru;

  @override
  String get enabledPrefKey => optionPluginBooruEnabled;

  @override
  String? get homeTabPrefKey => optionPluginBooruShowTab;

  @override
  IconData get icon => Icons.photo_library_outlined;

  @override
  PluginCategory get category => PluginCategory.art;

  @override
  Color get brandColor => const Color(0xFF7E57C2);

  @override
  String title(BuildContext context) => L10n.of(context).plugin_booru_title;

  @override
  String description(BuildContext context) =>
      L10n.of(context).plugin_booru_description;

  @override
  NavigationPage homePage(BuildContext context) {
    return NavigationPage(
      pluginIdBooru,
      (c) => L10n.of(c).plugin_booru_title,
      const Icon(Icons.photo_library_outlined),
      const Icon(Icons.photo_library),
    );
  }

  @override
  Widget homeScreen({required ScrollController scrollController}) {
    return BooruScreen(scrollController: scrollController);
  }

  @override
  Widget? settingsScreen(BuildContext context) => const BooruSettingsScreen();

  @override
  List<String> get tables => const [tableBooruSubscription];

  @override
  String get subscriptionTable => tableBooruSubscription;

  @override
  Subscription subscriptionFromMap(Map<String, Object?> row) =>
      BooruSubscription.fromMap(row);

  @override
  bool owns(Subscription subscription) => subscription is BooruSubscription;

  @override
  String subtitleFor(Subscription subscription) => 'tag:${subscription.name}';

  @override
  GroupMemberPreview previewOf(Subscription subscription) =>
      GroupMemberPreview(id: subscription.id, name: subscription.name);

  @override
  Widget avatarFor(Subscription subscription, {double size = 40}) {
    return CircleAvatar(
      radius: size / 2,
      child: Icon(Icons.sell_outlined, size: size * 0.5),
    );
  }

  @override
  Widget Function()? destinationFor(Subscription subscription) =>
      () => BooruSearchScreen(initialQuery: subscription.name);

  @override
  Future<void> reloadFromDatabase(BuildContext context) =>
      context.read<BooruTagsStore>().load();

  @override
  Future<void> unfollow(BuildContext context, Subscription subscription) =>
      context.read<BooruTagsStore>().remove(subscription.name);

  @override
  Future<List<InterleavedItem>> interleavedPosts(
    BuildContext context,
    List<String> ids,
  ) => loadBooruInterleaved(context, ids);

  @override
  bool inHomeFeed(BuildContext context) =>
      booruInHomeFeed(PrefService.of(context, listen: false));

  @override
  List<String> homeFeedIds(BuildContext context) => booruHomeTags(context);

  @override
  List<PluginBackupSection> get backupSections => [
    PluginBackupSection(
      jsonKey: 'booruSubscriptions',
      table: tableBooruSubscription,
      category: BackupCategory.booruTags,
      fromMap: BooruSubscription.fromMap,
    ),
  ];

  @override
  Future<void> resetPreferences(BasePrefService prefs) async {
    await prefs.set(optionPluginBooruEngine, BooruEngine.danbooru.id);
    await prefs.set(optionPluginBooruHost, booruPresets.first.host);
    await prefs.set(optionPluginBooruPreset, booruPresets.first.id);
    await prefs.set(optionPluginBooruLogin, '');
    await prefs.set(optionPluginBooruApiKey, '');
    await prefs.set(optionPluginBooruMaxRating, BooruRating.general.code);
    await prefs.set(optionPluginBooruInHomeFeed, false);
    await prefs.set(optionPluginBooruSearchHistory, '[]');
    await prefs.set(optionPluginBooruMutedTags, '[]');
    await prefs.set(optionPluginBooruCustomSites, '[]');
  }

  @override
  Future<void> forgetLoadedData(BuildContext context) async {
    final tags = context.read<BooruTagsStore>();
    final mute = context.read<BooruMuteStore>();
    await tags.load();
    await mute.load();
  }
}
