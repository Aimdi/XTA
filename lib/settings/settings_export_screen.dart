import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_file_dialog/flutter_file_dialog.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:intl/intl.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:quax/client/accounts.dart';
import 'package:quax/database/entities.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/group/group_model.dart';
import 'package:quax/saved/liked_tweet_model.dart';
import 'package:quax/saved/saved_tweet_folder_model.dart';
import 'package:quax/saved/saved_tweet_model.dart';
import 'package:quax/settings/_data.dart';
import 'package:quax/settings/settings_chrome.dart';
import 'package:quax/settings/settings_view_store.dart';
import 'package:quax/subscriptions/users_model.dart';
import 'package:quax/utils/crash_reporter.dart';

class SettingsExportScreen extends StatefulWidget {
  const SettingsExportScreen({super.key});

  @override
  State<SettingsExportScreen> createState() => _SettingsExportScreenState();
}

class _SettingsExportScreenState extends State<SettingsExportScreen> {
  late final SettingsExportStore _viewStore;

  @override
  void initState() {
    super.initState();
    _viewStore = SettingsExportStore();
  }

  @override
  void dispose() {
    _viewStore.destroy();
    super.dispose();
  }

  Future<void> _export(SettingsExportState selection) async {
    _viewStore.setBusy(true);
    try {
      final groupModel = context.read<GroupsModel>();
      final folderModel = context.read<SavedTweetFolderModel>();
      final likedModel = context.read<LikedTweetModel>();
      final subscriptionsModel = context.read<SubscriptionsModel>();
      final savedModel = context.read<SavedTweetModel>();
      final prefs = PrefService.of(context, listen: false);

      await groupModel.reloadGroups();
      await subscriptionsModel.reloadSubscriptions();
      await savedModel.listSavedTweets();
      await folderModel.listFolders();
      await likedModel.listLikedTweets();

      final selected = selection.selected;
      final subscriptions =
          selected.contains(SettingsExportOption.subscriptions)
          ? subscriptionsModel.state
          : null;
      final accounts = selected.contains(SettingsExportOption.accounts)
          ? await getAccounts()
          : null;
      final data = SettingsData(
        settings: selected.contains(SettingsExportOption.settings)
            ? prefsMapWithoutSecrets(prefs.toMap())
            : null,
        searchSubscriptions: subscriptions
            ?.whereType<SearchSubscription>()
            .toList(),
        userSubscriptions: subscriptions
            ?.whereType<UserSubscription>()
            .toList(),
        subscriptionGroups: selected.contains(SettingsExportOption.groups)
            ? groupModel.state
            : null,
        subscriptionGroupMembers:
            selected.contains(SettingsExportOption.groupMembers)
            ? await groupModel.listGroupMembers()
            : null,
        tweets: selected.contains(SettingsExportOption.tweets)
            ? savedModel.state
            : null,
        savedTweetFolders: selected.contains(SettingsExportOption.savedFolders)
            ? folderModel.state
            : null,
        likedTweets: selected.contains(SettingsExportOption.likedTweets)
            ? likedModel.state
            : null,
        accounts: accounts,
      );

      final exportData = jsonEncode(data.toJson());
      final fileName =
          'quax-${DateFormat('yyyy-MM-dd').format(DateTime.now())}.json';
      final path = await FlutterFileDialog.saveFile(
        params: SaveFileDialogParams(
          fileName: fileName,
          data: Uint8List.fromList(utf8.encode(exportData)),
        ),
      );
      if (path != null && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(L10n.of(context).data_exported_to_fileName(fileName)),
          ),
        );
      }
    } finally {
      if (mounted) _viewStore.setBusy(false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<SettingsExportStore, SettingsExportState>(
      store: _viewStore,
      onState: (_, state) => SettingsPageScaffold(
        title: l10n.export,
        floatingActionButton: state.selected.isEmpty
            ? null
            : FloatingActionButton.extended(
                icon: state.busy
                    ? const SizedBox.square(
                        dimension: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.save_outlined),
                label: Text(l10n.export),
                onPressed: state.busy ? null : () => _export(state),
              ),
        body: SettingsList(
          children: [
            SettingsSection(
              children: [
                _option(
                  state,
                  SettingsExportOption.settings,
                  l10n.export_settings,
                ),
                _option(
                  state,
                  SettingsExportOption.subscriptions,
                  l10n.export_subscriptions,
                ),
                _option(
                  state,
                  SettingsExportOption.groups,
                  l10n.export_subscription_groups,
                ),
                _option(
                  state,
                  SettingsExportOption.groupMembers,
                  l10n.export_subscription_group_members,
                  enabled: state.canIncludeGroupMembers,
                ),
                _option(state, SettingsExportOption.tweets, l10n.export_tweets),
                _option(
                  state,
                  SettingsExportOption.savedFolders,
                  l10n.export_saved_folders,
                ),
                _option(
                  state,
                  SettingsExportOption.likedTweets,
                  l10n.export_liked_posts,
                ),
                _option(
                  state,
                  SettingsExportOption.accounts,
                  l10n.export_accounts,
                  description: l10n.export_accounts_details,
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _option(
    SettingsExportState state,
    SettingsExportOption option,
    String title, {
    String? description,
    bool enabled = true,
  }) {
    return SettingsCheckboxRow(
      title: title,
      description: description,
      value: state.includes(option),
      onChanged: enabled ? (_) => _viewStore.toggle(option) : null,
    );
  }
}
