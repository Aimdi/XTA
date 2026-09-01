import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_file_dialog/flutter_file_dialog.dart';
import 'package:quax/client/accounts.dart';
import 'package:quax/constants.dart';
import 'package:quax/database/entities.dart';
import 'package:quax/database/repository.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/group/group_model.dart';
import 'package:quax/import_data_model.dart';
import 'package:quax/saved/liked_tweet_model.dart';
import 'package:quax/saved/saved_tweet_folder_model.dart';
import 'package:quax/saved/saved_tweet_model.dart';
import 'package:quax/settings/sync_screen.dart';
import 'package:quax/settings/settings_chrome.dart';
import 'package:quax/tweet/tweet_chrome.dart';
import 'package:quax/subscriptions/users_model.dart';
import 'package:quax/utils/crash_reporter.dart';
import 'package:logging/logging.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';

class SettingsData {
  final Map<String, dynamic>? settings;
  final List<SearchSubscription>? searchSubscriptions;
  final List<UserSubscription>? userSubscriptions;
  final List<SubscriptionGroup>? subscriptionGroups;
  final List<SubscriptionGroupMember>? subscriptionGroupMembers;
  final List<SavedTweet>? tweets;
  final List<SavedTweetFolder>? savedTweetFolders;
  final List<LikedTweet>? likedTweets;
  final List<Account>? accounts;

  SettingsData({
    required this.settings,
    required this.searchSubscriptions,
    required this.userSubscriptions,
    required this.subscriptionGroups,
    required this.subscriptionGroupMembers,
    required this.tweets,
    required this.savedTweetFolders,
    required this.likedTweets,
    required this.accounts,
  });

  factory SettingsData.fromJson(Map<String, dynamic> json) {
    return SettingsData(
      settings: json['settings'],
      searchSubscriptions: json['searchSubscriptions'] != null
          ? (json['searchSubscriptions'] as List? ?? [])
                .map((e) => SearchSubscription.fromMap(e))
                .toList()
          : null,
      userSubscriptions: json['subscriptions'] != null
          ? (json['subscriptions'] as List? ?? [])
                .map((e) => UserSubscription.fromMap(e))
                .toList()
          : null,
      subscriptionGroups: json['subscriptionGroups'] != null
          ? (json['subscriptionGroups'] as List? ?? [])
                .map((e) => SubscriptionGroup.fromMap(e))
                .toList()
          : null,
      subscriptionGroupMembers: json['subscriptionGroupMembers'] != null
          ? (json['subscriptionGroupMembers'] as List? ?? [])
                .map((e) => SubscriptionGroupMember.fromMap(e))
                .toList()
          : null,
      tweets: json['tweets'] != null
          ? (json['tweets'] as List).map((e) => SavedTweet.fromMap(e)).toList()
          : null,
      savedTweetFolders: json['savedTweetFolders'] != null
          ? (json['savedTweetFolders'] as List? ?? [])
                .map((e) => SavedTweetFolder.fromMap(e))
                .toList()
          : null,
      likedTweets: json['likedTweets'] != null
          ? (json['likedTweets'] as List? ?? [])
                .map((e) => LikedTweet.fromMap(e))
                .toList()
          : null,
      accounts: json['accounts'] != null
          ? (json['accounts'] as List).map((e) => Account.fromMap(e)).toList()
          : null,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'settings': settings,
      'searchSubscriptions': searchSubscriptions
          ?.map((e) => e.toMap())
          .toList(),
      'subscriptions': userSubscriptions?.map((e) => e.toMap()).toList(),
      'subscriptionGroups': subscriptionGroups?.map((e) => e.toMap()).toList(),
      'subscriptionGroupMembers': subscriptionGroupMembers
          ?.map((e) => e.toMap())
          .toList(),
      'tweets': tweets?.map((e) => e.toMap()).toList(),
      'savedTweetFolders': savedTweetFolders?.map((e) => e.toMap()).toList(),
      'likedTweets': likedTweets?.map((e) => e.toMap()).toList(),
      'accounts': accounts?.map((e) => e.toMap()).toList(),
    };
  }
}

Future<void> _importFromFile(BuildContext context, File file) async {
  await importSettingsJson(context, file.readAsStringSync());
}

/// Applies an exported backup document. Shared by the file import and the
/// WebDAV restore so a restore can never diverge from what a file does.
Future<void> importSettingsJson(BuildContext context, String json) async {
  var content = jsonDecode(json);

  var importModel = context.read<ImportDataModel>();
  var groupModel = context.read<GroupsModel>();
  var prefs = PrefService.of(context);

  var data = SettingsData.fromJson(content);

  var settings = data.settings;
  if (settings != null) {
    prefs.fromMap(settings);
  }

  var dataToImport = <String, List<ToMappable>>{};

  var searchSubscriptions = data.searchSubscriptions;
  if (searchSubscriptions != null) {
    dataToImport[tableSearchSubscription] = searchSubscriptions;
  }

  var userSubscriptions = data.userSubscriptions;
  if (userSubscriptions != null) {
    dataToImport[tableSubscription] = userSubscriptions;
  }

  var subscriptionGroups = data.subscriptionGroups;
  if (subscriptionGroups != null) {
    dataToImport[tableSubscriptionGroup] = subscriptionGroups;
  }

  var subscriptionGroupMembers = data.subscriptionGroupMembers;
  if (subscriptionGroupMembers != null) {
    dataToImport[tableSubscriptionGroupMember] = subscriptionGroupMembers;
  }

  var tweets = data.tweets;
  if (tweets != null) {
    dataToImport[tableSavedTweet] = tweets;
  }

  var savedTweetFolders = data.savedTweetFolders;
  if (savedTweetFolders != null) {
    dataToImport[tableSavedTweetFolder] = savedTweetFolders;
  }

  var likedTweets = data.likedTweets;
  if (likedTweets != null) {
    dataToImport[tableLikedTweet] = likedTweets;
  }

  var accounts = data.accounts;
  if (accounts != null) {
    dataToImport[tableAccounts] = accounts;
  }

  await importModel.importData(dataToImport);
  await groupModel.reloadGroups();
  context.mounted
      ? await context.read<SubscriptionsModel>().reloadSubscriptions()
      : null;
  context.mounted
      ? await context.read<SavedTweetFolderModel>().listFolders()
      : null;
  context.mounted
      ? await context.read<LikedTweetModel>().listLikedTweets()
      : null;

  if (context.mounted) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(L10n.of(context).data_imported_successfully)),
    );
  }
}

/// The whole backup payload as JSON, for callers that write it somewhere other
/// than a file. Accounts are opt-in because they carry X session tokens.
Future<String> exportSettingsJson(
  BuildContext context, {
  required bool includeAccounts,
}) async {
  final groupModel = context.read<GroupsModel>();
  final subscriptionsModel = context.read<SubscriptionsModel>();
  final savedTweetModel = context.read<SavedTweetModel>();
  final savedTweetFolderModel = context.read<SavedTweetFolderModel>();
  final likedTweetModel = context.read<LikedTweetModel>();
  final prefs = PrefService.of(context, listen: false);

  await subscriptionsModel.reloadSubscriptions();
  await savedTweetModel.listSavedTweets();
  await savedTweetFolderModel.listFolders();
  await likedTweetModel.listLikedTweets();

  final subscriptions = subscriptionsModel.state;

  return jsonEncode(
    SettingsData(
      settings: prefsMapWithoutSecrets(prefs.toMap()),
      searchSubscriptions: subscriptions
          .whereType<SearchSubscription>()
          .toList(),
      userSubscriptions: subscriptions.whereType<UserSubscription>().toList(),
      subscriptionGroups: groupModel.state,
      subscriptionGroupMembers: await groupModel.listGroupMembers(),
      tweets: savedTweetModel.state,
      savedTweetFolders: savedTweetFolderModel.state,
      likedTweets: likedTweetModel.state,
      accounts: includeAccounts ? await getAccounts() : null,
    ).toJson(),
  );
}

Future<void> importBackup(BuildContext context) async {
  var path = await FlutterFileDialog.pickFile(
    params: const OpenFileDialogParams(),
  );
  if (path != null && context.mounted) {
    await _importFromFile(context, File(path));
  }
}

class SettingsDataFragment extends StatelessWidget {
  static final log = Logger('SettingsDataFragment');

  const SettingsDataFragment({super.key});

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        SettingsRow(
          icon: Icons.file_download_outlined,
          title: L10n.of(context).import,
          description: L10n.of(context).import_data_from_another_device,
          onTap: () => importBackup(context),
        ),
        tweetHairlineDivider(context),
        SettingsNavigationRow(
          icon: Icons.save_outlined,
          title: L10n.of(context).export,
          description: L10n.of(context).export_your_data,
          onTap: () => Navigator.pushNamed(context, routeSettingsExport),
        ),
        tweetHairlineDivider(context),
        SettingsNavigationRow(
          icon: Icons.cloud_sync_outlined,
          title: L10n.of(context).sync,
          description: L10n.of(context).sync_description,
          onTap: () => Navigator.push(
            context,
            MaterialPageRoute(builder: (_) => const SyncScreen()),
          ),
        ),
      ],
    );
  }
}
