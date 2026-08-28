import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_file_dialog/flutter_file_dialog.dart';
import 'package:xta/client/accounts.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/import_data_model.dart';
import 'package:xta/saved/liked_tweet_model.dart';
import 'package:xta/saved/local_post_model.dart';
import 'package:xta/saved/saved_tweet_folder_model.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/settings/sync_screen.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/utils/crash_reporter.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/settings/backup_data.dart';
import 'package:xta/settings/backup_rows.dart';
import 'package:xta/settings/import_preview.dart';
import 'package:logging/logging.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';

Future<void> _importFromFile(BuildContext context, File file) async {
  await importSettingsJson(context, file.readAsStringSync());
}

/// Applies an exported backup document, once the reader has seen what is in it.
/// Shared by the file import and the WebDAV restore so a restore can never
/// diverge from what a file does.
Future<void> importSettingsJson(BuildContext context, String json) async {
  var data = _parseBackup(json);
  if (data == null) {
    _notify(context, L10n.of(context).unable_to_import);
    return;
  }

  if (!isSupportedBackupVersion(data.formatVersion)) {
    _notify(context, L10n.of(context).import_unsupported_version);
    return;
  }

  if (backupCounts(data).isEmpty) {
    _notify(context, L10n.of(context).unable_to_import);
    return;
  }

  var choice = await showImportPreview(context, data);
  if (choice != null && context.mounted) {
    await _applyBackup(context, data, choice);
  }
}

/// Null for anything that is not a backup document. Nothing is applied from a
/// file we could not read whole.
SettingsData? _parseBackup(String json) {
  try {
    var content = jsonDecode(json);

    return content is Map<String, dynamic> ? SettingsData.fromJson(content) : null;
  } catch (e) {
    return null;
  }
}

void _notify(BuildContext context, String message) {
  ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
}

Future<void> _applyBackup(BuildContext context, SettingsData data, ImportChoice choice) async {
  var importModel = context.read<ImportDataModel>();
  var groupModel = context.read<GroupsModel>();
  var prefs = PrefService.of(context);

  var settings = data.settings;
  if (settings != null) {
    prefs.fromMap(settings);
  }

  await importModel.importData(backupTables(data, includeReadPositions: choice.includeReadPositions));
  await groupModel.reloadGroups();

  if (context.mounted) {
    await _reloadAfterImport(context);
  }

  if (context.mounted) {
    _notify(context, L10n.of(context).data_imported_successfully);
  }
}

/// Every store holding a table the import may have just replaced.
Future<void> _reloadAfterImport(BuildContext context) async {
  var subscriptions = context.read<SubscriptionsModel>();
  var folders = context.read<SavedTweetFolderModel>();
  var likedTweets = context.read<LikedTweetModel>();
  // Every source, not the two this used to name: a restored list of followed
  // Threads, Bluesky, Fediverse or stock accounts stayed invisible until the
  // app was restarted.
  final sources = subscriptionSources;

  await subscriptions.reloadSubscriptions();
  await folders.listFolders();
  await likedTweets.listLikedTweets();
  if (context.mounted) {
    await context.read<LocalPostModel>().listLocalPosts();
  }
  for (final source in sources) {
    if (!context.mounted) return;
    await source.reloadFromDatabase(context);
  }
}

/// The whole backup payload as JSON, for callers that write it somewhere other
/// than a file. Accounts are opt-in because they carry X session tokens.
Future<String> exportSettingsJson(BuildContext context, {required bool includeAccounts}) async {
  var data = await collectBackup(context, includeAccounts: includeAccounts);

  return jsonEncode(data.toJson());
}

/// Every backed-up table, so a WebDAV upload carries exactly what a manual
/// export of everything would.
Future<SettingsData> collectBackup(BuildContext context, {required bool includeAccounts}) async {
  var groupModel = context.read<GroupsModel>();
  var subscriptionsModel = context.read<SubscriptionsModel>();
  var prefs = PrefService.of(context, listen: false);

  var saved = await collectSavedPosts(context);
  await subscriptionsModel.reloadSubscriptions();
  var subscriptions = subscriptionsModel.state;

  return SettingsData(
    exportedAt: DateTime.now(),
    appVersion: await appVersionLabel(),
    settings: prefsMapWithoutSecrets(prefs.toMap()),
    searchSubscriptions: subscriptions.whereType<SearchSubscription>().toList(),
    userSubscriptions: subscriptions.whereType<UserSubscription>().toList(),
    // Read from the plugins rather than named here: a plugin's rows are often
    // the only copy in existence, and a list that has to be extended by hand is
    // how several of them went unsaved.
    pluginRows: await readPluginRows(),
    subscriptionGroups: groupModel.state,
    subscriptionGroupMembers: await groupModel.listGroupMembers(),
    searchGroupMembers: await readSearchGroupMembers(),
    tweets: saved.tweets,
    savedTweetFolders: saved.folders,
    likedTweets: saved.liked,
    retweetFilters: await readRetweetFilters(),
    replyFilters: await readReplyFilters(),
    feedReadPositions: await readFeedReadPositions(),
    accounts: includeAccounts ? await getAccounts() : null,
    profileNotes: await readProfileNotes(),
    antennas: await readAntennas(),
    localPosts: await readLocalPosts(),
  );
}

/// The saved side of a backup, refreshed from the database first so an export
/// never writes a stale list.
Future<({List<SavedTweet> tweets, List<SavedTweetFolder> folders, List<LikedTweet> liked})> collectSavedPosts(
  BuildContext context,
) async {
  var tweets = context.read<SavedTweetModel>();
  var folders = context.read<SavedTweetFolderModel>();
  var liked = context.read<LikedTweetModel>();

  await tweets.listSavedTweets();
  await folders.listFolders();
  await liked.listLikedTweets();

  return (tweets: tweets.state, folders: folders.state, liked: liked.state);
}

/// The build that wrote a file, so its reader can tell where it came from.
Future<String> appVersionLabel() async {
  var info = await PackageInfo.fromPlatform();

  return '${info.version}+${info.buildNumber}';
}

Future<void> importBackup(BuildContext context) async {
  var path = await FlutterFileDialog.pickFile(params: const OpenFileDialogParams());
  if (path != null && context.mounted) {
    await _importFromFile(context, File(path));
  }
}

class SettingsDataFragment extends StatelessWidget {
  static final log = Logger('SettingsDataFragment');

  const SettingsDataFragment({super.key});

  @override
  Widget build(BuildContext context) {
    return Column(children: [
      PrefLabel(
        leading: const Icon(Icons.import_export),
        title: Text(L10n.of(context).import),
        subtitle: Text(L10n.of(context).import_data_from_another_device),
        onTap: () => importBackup(context),
      ),
      PrefLabel(
        leading: const Icon(Icons.save),
        title: Text(L10n.of(context).export),
        subtitle: Text(L10n.of(context).export_your_data),
        onTap: () => Navigator.pushNamed(context, routeSettingsExport),
      ),
      PrefLabel(
        leading: const Icon(Icons.cloud_sync_outlined),
        title: Text(L10n.of(context).sync),
        subtitle: Text(L10n.of(context).sync_description),
        onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const SyncScreen())),
      ),
    ]);
  }
}
