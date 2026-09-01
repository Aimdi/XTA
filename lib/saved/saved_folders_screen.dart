import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:quax/constants.dart';
import 'package:quax/database/entities.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/saved/folder_picker.dart';
import 'package:quax/saved/saved_chrome.dart';
import 'package:quax/saved/saved_tab_order.dart';
import 'package:quax/saved/saved_tweet_folder_model.dart';
import 'package:quax/saved/saved_tweet_model.dart';
import 'package:quax/saved/saved_view_store.dart';
import 'package:quax/tweet/tweet_chrome.dart';
import 'package:quax/ui/errors.dart';
import 'package:quax/ui/reader_chrome.dart';

class SavedFoldersScreen extends StatefulWidget {
  const SavedFoldersScreen({super.key});

  @override
  State<SavedFoldersScreen> createState() => _SavedFoldersScreenState();
}

class _SavedFoldersScreenState extends State<SavedFoldersScreen> {
  late final SavedFolderManagementStore _viewStore;

  @override
  void initState() {
    super.initState();
    _viewStore = SavedFolderManagementStore();
    context.read<SavedTweetFolderModel>().listFolders();
    context.read<SavedTweetModel>().listSavedTweets();
  }

  SavedTweetFolderModel get _folderModel =>
      context.read<SavedTweetFolderModel>();

  @override
  void dispose() {
    _viewStore.destroy();
    super.dispose();
  }

  int _countIn(String folderId) => context
      .read<SavedTweetModel>()
      .state
      .where((e) => e.folderId == folderId)
      .length;

  Future<void> _rename(SavedTweetFolder folder) async {
    await showCreateFolderDialog(context, _folderModel, existing: folder);
  }

  Future<void> _confirmDelete(SavedTweetFolder folder) async {
    await showDeleteFolderDialog(context, _folderModel, folder);
  }

  Future<void> _onReorder(
    List<String> tokens,
    int oldIndex,
    int newIndex,
  ) async {
    var reordered = [...tokens];
    reordered.insert(newIndex, reordered.removeAt(oldIndex));

    await PrefService.of(
      context,
      listen: false,
    ).set(optionSavedTabOrder, jsonEncode(reordered));
    if (mounted) _viewStore.refresh();
  }

  Widget _dragHandle(int index) {
    return ReorderableDragStartListener(
      index: index,
      child: const SizedBox(
        width: kTweetTouchTarget,
        height: kTweetTouchTarget,
        child: Icon(Icons.drag_handle),
      ),
    );
  }

  Widget _tabRow(String token, List<SavedTweetFolder> folders, int index) {
    if (token == savedTabAll) {
      return _builtInRow(
        token,
        L10n.of(context).all,
        optionSavedShowAllTab,
        index,
      );
    }
    if (token == savedTabUnfiled) {
      return _builtInRow(
        token,
        L10n.of(context).unfiled,
        optionSavedShowUnfiledTab,
        index,
      );
    }
    if (token == savedTabFavorites) {
      return _builtInRow(
        token,
        L10n.of(context).favorites,
        optionSavedShowFavoritesTab,
        index,
      );
    }

    var folder = folders.firstWhere((f) => f.id == token);
    return ListTile(
      key: ValueKey(token),
      minTileHeight: kTweetTouchTarget,
      contentPadding: const EdgeInsets.symmetric(
        horizontal: kTweetHorizontalPadding,
      ),
      leading: const Icon(Icons.folder_outlined),
      title: Text(folder.name),
      subtitle: Text(L10n.of(context).folder_post_count(_countIn(folder.id))),
      onTap: () => _rename(folder),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          IconButton(
            icon: const Icon(Icons.delete_outline),
            tooltip: L10n.of(context).delete,
            onPressed: () => _confirmDelete(folder),
          ),
          _dragHandle(index),
        ],
      ),
    );
  }

  /// A built-in tab ("All" / "Unfiled") — not deletable, but its visibility in the
  /// Saved tab's folder strip can be toggled.
  Widget _builtInRow(String token, String label, String prefKey, int index) {
    var prefs = PrefService.of(context, listen: false);
    var visible = prefs.get<bool>(prefKey) ?? true;

    return ListTile(
      key: ValueKey(token),
      minTileHeight: kTweetTouchTarget,
      contentPadding: const EdgeInsets.symmetric(
        horizontal: kTweetHorizontalPadding,
      ),
      leading: Icon(
        token == savedTabFavorites
            ? Icons.favorite_border
            : Icons.bookmarks_outlined,
      ),
      title: Text(label),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          IconButton(
            icon: Icon(visible ? Icons.visibility : Icons.visibility_off),
            tooltip: visible ? L10n.of(context).hide : L10n.of(context).show,
            onPressed: () async {
              await prefs.set(prefKey, !visible);
              if (mounted) _viewStore.refresh();
            },
          ),
          _dragHandle(index),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return XtaSystemBars(
      child: Scaffold(
        appBar: AppBar(
          surfaceTintColor: Colors.transparent,
          elevation: 0,
          scrolledUnderElevation: 0,
          title: Text(L10n.of(context).manage_folders),
          actions: [
            IconButton(
              icon: const Icon(Icons.create_new_folder_outlined),
              tooltip: L10n.of(context).create_new_folder,
              onPressed: () => showCreateFolderDialog(context, _folderModel),
            ),
          ],
        ),
        body: ScopedBuilder<SavedFolderManagementStore, int>(
          store: _viewStore,
          onState: (_, __) =>
              ScopedBuilder<SavedTweetFolderModel, List<SavedTweetFolder>>(
                store: _folderModel,
                onLoading: (_) => const SavedFolderListSkeleton(),
                onError: (_, error) => FullPageErrorWidget(
                  error: error,
                  stackTrace: null,
                  prefix: L10n.of(context).unable_to_load_the_tweets,
                  onRetry: _folderModel.listFolders,
                ),
                onState: (context, folders) {
                  var tokens = orderedSavedTabs(
                    folders,
                    PrefService.of(
                      context,
                      listen: false,
                    ).get(optionSavedTabOrder),
                  );
                  return SafeArea(
                    top: false,
                    child: ReorderableListView.builder(
                      buildDefaultDragHandles: false,
                      itemCount: tokens.length,
                      onReorderItem: (oldIndex, newIndex) =>
                          _onReorder(tokens, oldIndex, newIndex),
                      itemBuilder: (context, index) => Column(
                        key: ValueKey(tokens[index]),
                        children: [
                          _tabRow(tokens[index], folders, index),
                          tweetHairlineDivider(context),
                        ],
                      ),
                    ),
                  );
                },
              ),
        ),
      ),
    );
  }
}
