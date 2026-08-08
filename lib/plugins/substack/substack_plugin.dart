import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/home_screen.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/plugins/plugin_backup.dart';
import 'package:xta/settings/backup_category.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_category.dart';
import 'package:xta/plugins/substack/substack_screen.dart';
import 'package:xta/plugins/substack/substack_store.dart';

class SubstackPlugin extends XtaPlugin {
  SubstackPlugin();

  @override
  String get id => pluginIdSubstack;

  @override
  String get enabledPrefKey => optionPluginSubstackEnabled;

  @override
  String? get homeTabPrefKey => optionPluginSubstackShowTab;

  @override
  IconData get icon => Icons.newspaper;

  @override
  PluginCategory get category => PluginCategory.newsletters;

  @override
  Color get brandColor => const Color(0xFFFF6719);

  @override
  String title(BuildContext context) => L10n.of(context).plugin_substack_title;

  @override
  String description(BuildContext context) => L10n.of(context).plugin_substack_description;

  @override
  NavigationPage homePage(BuildContext context) {
    return NavigationPage(
      pluginIdSubstack,
      (c) => L10n.of(c).plugin_substack_title,
      const Icon(Icons.newspaper_outlined),
      const Icon(Icons.newspaper),
    );
  }

  @override
  Widget homeScreen({required ScrollController scrollController}) {
    return SubstackScreen(scrollController: scrollController);
  }

  @override
  List<String> get tables => const [tableSubstackSubscription];

  @override
  List<PluginBackupSection> get backupSections => [
    PluginBackupSection(
      jsonKey: 'substackSubscriptions',
      table: tableSubstackSubscription,
      category: BackupCategory.substack,
      fromMap: SubstackSubscription.fromMap,
    ),
  ];

  @override
  Future<void> resetPreferences(BasePrefService prefs) async {
    await prefs.set(optionPluginSubstackPublications, '[]');
    await prefs.set(optionPluginSubstackReadIds, '[]');
    await prefs.set(optionPluginSubstackLikedPosts, '[]');
    await prefs.set(optionPluginSubstackSavedPosts, '[]');
  }

  @override
  Future<void> forgetLoadedData(BuildContext context) async {
    await context.read<SubstackPublicationsStore>().load();
    if (context.mounted) {
      await context.read<SubstackReadStore>().load();
    }
    if (context.mounted) {
      await context.read<SubstackLikesStore>().load();
    }
    if (context.mounted) {
      await context.read<SubstackSavedStore>().load();
    }
  }
}
