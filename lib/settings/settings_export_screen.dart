import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_file_dialog/flutter_file_dialog.dart';
import 'package:xta/client/accounts.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/saved/liked_tweet_model.dart';
import 'package:xta/saved/saved_tweet_folder_model.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/settings/_data.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/utils/crash_reporter.dart';
import 'package:xta/settings/backup_data.dart';
import 'package:xta/settings/backup_rows.dart';
import 'package:intl/intl.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';

class SettingsExportScreen extends StatefulWidget {
  const SettingsExportScreen({super.key});

  @override
  State<SettingsExportScreen> createState() => _SettingsExportScreenState();
}

class _SettingsExportScreenState extends State<SettingsExportScreen> {
  bool _exportSettings = false;
  bool _exportSubscriptions = false;
  bool _exportSubscriptionGroups = false;
  bool _exportSubscriptionGroupMembers = false;
  bool _exportTweets = false;
  bool _exportSavedFolders = false;
  bool _exportLikedTweets = false;
  bool _exportFilters = false;
  bool _exportReadPositions = false;
  bool _exportAccounts = false;

  void toggleExportSubscriptionGroupMembersIfRequired() {
    if (_exportSubscriptionGroupMembers && (!_exportSubscriptions || !_exportSubscriptionGroups)) {
      setState(() {
        _exportSubscriptionGroupMembers = false;
      });
    }
  }

  void toggleExportSettings() {
    setState(() {
      _exportSettings = !_exportSettings;
    });
  }

  void toggleExportSubscriptions() {
    setState(() {
      _exportSubscriptions = !_exportSubscriptions;
    });

    toggleExportSubscriptionGroupMembersIfRequired();
  }

  void toggleExportSubscriptionGroups() {
    setState(() {
      _exportSubscriptionGroups = !_exportSubscriptionGroups;
    });

    toggleExportSubscriptionGroupMembersIfRequired();
  }

  void toggleExportSubscriptionGroupMembers() {
    setState(() {
      _exportSubscriptionGroupMembers = !_exportSubscriptionGroupMembers;
    });
  }

  void toggleExportTweets() {
    setState(() {
      _exportTweets = !_exportTweets;
    });
  }

  void toggleExportSavedFolders() {
    setState(() {
      _exportSavedFolders = !_exportSavedFolders;
    });
  }

  void toggleExportLikedTweets() {
    setState(() {
      _exportLikedTweets = !_exportLikedTweets;
    });
  }

  void toggleExportFilters() {
    setState(() {
      _exportFilters = !_exportFilters;
    });
  }

  void toggleExportReadPositions() {
    setState(() {
      _exportReadPositions = !_exportReadPositions;
    });
  }

  void toggleExportAccounts() {
    setState(() {
      _exportAccounts = !_exportAccounts;
    });
  }

  bool noExportOptionSelected() {
    return !(_exportSettings ||
        _exportSubscriptions ||
        _exportSubscriptionGroups ||
        _exportSubscriptionGroupMembers ||
        _exportTweets ||
        _exportSavedFolders ||
        _exportLikedTweets ||
        _exportFilters ||
        _exportReadPositions ||
        _exportAccounts);
  }

  /// Users and saved searches are both subscriptions, so one choice covers the
  /// two tables they live in.
  List<T>? _subscriptionsOf<T extends Subscription>(List<Subscription> all) {
    return _exportSubscriptions ? all.whereType<T>().toList() : null;
  }

  Future<SettingsData> _collect() async {
    var groupModel = context.read<GroupsModel>();
    var subscriptionsModel = context.read<SubscriptionsModel>();
    var savedTweetModel = context.read<SavedTweetModel>();
    var savedTweetFolderModel = context.read<SavedTweetFolderModel>();
    var likedTweetModel = context.read<LikedTweetModel>();
    var prefs = PrefService.of(context);

    await groupModel.reloadGroups();
    await subscriptionsModel.reloadSubscriptions();
    await savedTweetModel.listSavedTweets();
    await savedTweetFolderModel.listFolders();
    await likedTweetModel.listLikedTweets();

    var subscriptions = subscriptionsModel.state;

    return SettingsData(
      exportedAt: DateTime.now(),
      appVersion: await appVersionLabel(),
      settings: _exportSettings ? prefsMapWithoutSecrets(prefs.toMap()) : null,
      searchSubscriptions: _subscriptionsOf<SearchSubscription>(subscriptions),
      userSubscriptions: _subscriptionsOf<UserSubscription>(subscriptions),
      // Every plugin's rows, not the two this screen used to name: followed
      // stocks, Threads, Bluesky and Fediverse accounts, and device-only
      // upvotes and likes were all silently left out of a chosen export.
      pluginRows: _exportSubscriptions ? await readPluginRows() : null,
      subscriptionGroups: _exportSubscriptionGroups ? groupModel.state : null,
      subscriptionGroupMembers: _exportSubscriptionGroupMembers ? await groupModel.listGroupMembers() : null,
      searchGroupMembers: _exportSubscriptionGroupMembers ? await readSearchGroupMembers() : null,
      tweets: _exportTweets ? savedTweetModel.state : null,
      savedTweetFolders: _exportSavedFolders ? savedTweetFolderModel.state : null,
      likedTweets: _exportLikedTweets ? likedTweetModel.state : null,
      retweetFilters: _exportFilters ? await readRetweetFilters() : null,
      replyFilters: _exportFilters ? await readReplyFilters() : null,
      feedReadPositions: _exportReadPositions ? await readFeedReadPositions() : null,
      accounts: _exportAccounts ? await getAccounts() : null,
      profileNotes: _exportFilters ? await readProfileNotes() : null,
      antennas: _exportFilters ? await readAntennas() : null,
    );
  }

  Future<void> _export() async {
    var data = await _collect();
    var exportData = jsonEncode(data.toJson());

    var dateFormat = DateFormat('yyyy-MM-dd');
    var fileName = 'xta-${dateFormat.format(DateTime.now())}.json';

    var path = await FlutterFileDialog.saveFile(
        params: SaveFileDialogParams(fileName: fileName, data: Uint8List.fromList(utf8.encode(exportData))));

    if (path != null && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            L10n.of(context).data_exported_to_fileName(fileName),
          ),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(L10n.of(context).export),
      ),
      floatingActionButton: noExportOptionSelected()
          ? null
          : FloatingActionButton(
              onPressed: _export,
              child: const Icon(Icons.save),
            ),
      body: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Expanded(
              child: SingleChildScrollView(
                  child: Column(
            children: [
              CheckboxListTile(
                  value: _exportSettings,
                  title: Text(L10n.of(context).export_settings),
                  onChanged: (v) => toggleExportSettings()),
              CheckboxListTile(
                  value: _exportSubscriptions,
                  title: Text(L10n.of(context).export_subscriptions),
                  onChanged: (v) => toggleExportSubscriptions()),
              CheckboxListTile(
                  value: _exportSubscriptionGroups,
                  title: Text(L10n.of(context).export_subscription_groups),
                  onChanged: (v) => toggleExportSubscriptionGroups()),
              CheckboxListTile(
                  value: _exportSubscriptionGroupMembers,
                  title: Text(L10n.of(context).export_subscription_group_members),
                  onChanged: _exportSubscriptions && _exportSubscriptionGroups
                      ? (v) => toggleExportSubscriptionGroupMembers()
                      : null),
              CheckboxListTile(
                  value: _exportTweets,
                  title: Text(L10n.of(context).export_tweets),
                  onChanged: (v) => toggleExportTweets()),
              CheckboxListTile(
                  value: _exportSavedFolders,
                  title: Text(L10n.of(context).export_saved_folders),
                  onChanged: (v) => toggleExportSavedFolders()),
              CheckboxListTile(
                  value: _exportLikedTweets,
                  title: Text(L10n.of(context).export_liked_posts),
                  onChanged: (v) => toggleExportLikedTweets()),
              CheckboxListTile(
                  value: _exportFilters,
                  title: Text(L10n.of(context).export_feed_filters),
                  onChanged: (v) => toggleExportFilters()),
              CheckboxListTile(
                  value: _exportReadPositions,
                  title: Text(L10n.of(context).export_reading_positions),
                  onChanged: (v) => toggleExportReadPositions()),
              CheckboxListTile(
                  value: _exportAccounts,
                  title: Text(L10n.of(context).export_accounts),
                  subtitle: Text(L10n.of(context).export_accounts_details),
                  onChanged: (v) => toggleExportAccounts()),
            ],
          ))),
        ],
      ),
    );
  }
}
