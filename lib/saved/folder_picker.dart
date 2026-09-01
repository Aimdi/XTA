import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:quax/database/entities.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/saved/saved_tweet_folder_model.dart';
import 'package:quax/saved/saved_tweet_model.dart';
import 'package:quax/saved/saved_view_store.dart';
import 'package:quax/tweet/tweet_chrome.dart';
import 'package:quax/utils/downloads.dart';
import 'package:quax/utils/iterables.dart';

/// Opens the "save to folder" bottom sheet for a post, saving it first if needed.
Future<void> showSaveToFolderSheet(
  BuildContext context, {
  required String tweetId,
  String? userId,
  required Map<String, dynamic> content,
}) async {
  var savedModel = context.read<SavedTweetModel>();
  var folderModel = context.read<SavedTweetFolderModel>();
  var messenger = ScaffoldMessenger.of(context);

  await folderModel.listFolders();
  await HapticFeedback.lightImpact();

  if (!context.mounted) {
    return;
  }

  await showModalBottomSheet(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: true,
    builder: (_) => _SaveToFolderSheet(
      tweetId: tweetId,
      userId: userId,
      content: content,
      savedModel: savedModel,
      folderModel: folderModel,
      messenger: messenger,
    ),
  );
}

class _SaveToFolderSheet extends StatelessWidget {
  final String tweetId;
  final String? userId;
  final Map<String, dynamic> content;
  final SavedTweetModel savedModel;
  final SavedTweetFolderModel folderModel;
  final ScaffoldMessengerState messenger;

  const _SaveToFolderSheet({
    required this.tweetId,
    required this.userId,
    required this.content,
    required this.savedModel,
    required this.folderModel,
    required this.messenger,
  });

  Future<void> _file(
    BuildContext context,
    String? folderId,
    String label,
  ) async {
    final autoDownload =
        folderId != null &&
        (folderModel.state
                .firstWhereOrNull((f) => f.id == folderId)
                ?.autoDownload ??
            false);
    final prefs = PrefService.of(context, listen: false);
    final downloadingLabel = L10n.of(context).downloading_media;
    final doneLabel = L10n.of(context).successfully_saved_the_media;
    final needFolderLabel = L10n.of(
      context,
    ).set_a_download_folder_to_auto_download;

    Navigator.pop(context);

    if (savedModel.isSaved(tweetId)) {
      await savedModel.setFolder(tweetId, folderId);
    } else {
      await savedModel.saveTweet(tweetId, userId, content, folderId: folderId);
    }

    messenger.showSnackBar(
      SnackBar(
        content: Text(L10n.current.saved_to_folder(label)),
        duration: const Duration(seconds: 3),
      ),
    );

    if (autoDownload) {
      await autoDownloadTweetPhotos(
        content: content,
        prefs: prefs,
        messenger: messenger,
        downloadingLabel: downloadingLabel,
        doneLabel: doneLabel,
        needFolderLabel: needFolderLabel,
      );
    }
  }

  Future<void> _createAndFile(BuildContext context) async {
    var folder = await showCreateFolderDialog(context, folderModel);
    if (folder != null && context.mounted) {
      await _file(context, folder.id, folder.name);
    }
  }

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<SavedTweetFolderModel, List<SavedTweetFolder>>(
      store: folderModel,
      onState: (context, folders) {
        var current = savedModel.folderOf(tweetId);

        return Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(
                kTweetHorizontalPadding,
                kTweetSpace1,
                kTweetHorizontalPadding,
                kTweetSpace3,
              ),
              child: Row(
                children: [
                  Icon(Icons.bookmark_add_outlined),
                  const SizedBox(width: kTweetSpace3),
                  Text(
                    L10n.of(context).save_to_folder,
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                ],
              ),
            ),
            // Scrolls rather than overflowing the sheet once there are more
            // folders than fit (ported from upstream ffea8688).
            Flexible(
              child: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _FolderTile(
                      label: L10n.of(context).unfiled,
                      selected: current == null,
                      onTap: () =>
                          _file(context, null, L10n.of(context).unfiled),
                    ),
                    ...folders.map(
                      (f) => _FolderTile(
                        label: f.name,
                        selected: current == f.id,
                        onTap: () => _file(context, f.id, f.name),
                      ),
                    ),
                  ],
                ),
              ),
            ),
            tweetHairlineDivider(context),
            ListTile(
              minTileHeight: kTweetTouchTarget,
              contentPadding: const EdgeInsets.symmetric(
                horizontal: kTweetHorizontalPadding,
              ),
              leading: const Icon(Icons.create_new_folder_outlined),
              title: Text(L10n.of(context).create_new_folder),
              onTap: () => _createAndFile(context),
            ),
          ],
        );
      },
    );
  }
}

class _FolderTile extends StatelessWidget {
  final String label;
  final bool selected;
  final VoidCallback onTap;

  const _FolderTile({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return ListTile(
      minTileHeight: kTweetTouchTarget,
      contentPadding: const EdgeInsets.symmetric(
        horizontal: kTweetHorizontalPadding,
      ),
      leading: Icon(selected ? Icons.folder : Icons.folder_outlined),
      title: Text(label),
      trailing: selected
          ? Icon(Icons.check, color: tweetAccentColor(context))
          : null,
      selected: selected,
      selectedColor: tweetPrimaryColor(context),
      selectedTileColor: tweetAccentColor(context).withValues(alpha: 0.08),
      onTap: onTap,
    );
  }
}

Future<SavedTweetFolder?> showCreateFolderDialog(
  BuildContext context,
  SavedTweetFolderModel folderModel, {
  SavedTweetFolder? existing,
}) {
  return showDialog<SavedTweetFolder>(
    context: context,
    builder: (_) =>
        _EditFolderDialog(folderModel: folderModel, existing: existing),
  );
}

/// Confirms deletion of [folder]; on confirm, deletes it (its posts return to
/// "unfiled") and reloads the saved list. Returns true if it was deleted.
Future<bool> showDeleteFolderDialog(
  BuildContext context,
  SavedTweetFolderModel folderModel,
  SavedTweetFolder folder,
) async {
  var savedModel = context.read<SavedTweetModel>();

  var confirmed = await showDialog<bool>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(L10n.of(context).delete_folder),
      content: Text(L10n.of(context).delete_folder_description),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context, false),
          child: Text(L10n.of(context).cancel),
        ),
        TextButton(
          onPressed: () => Navigator.pop(context, true),
          child: Text(L10n.of(context).delete),
        ),
      ],
    ),
  );

  if (confirmed != true) {
    return false;
  }

  await folderModel.deleteFolder(folder.id);
  await savedModel.listSavedTweets();
  return true;
}

class _EditFolderDialog extends StatefulWidget {
  final SavedTweetFolderModel folderModel;
  final SavedTweetFolder? existing;

  const _EditFolderDialog({required this.folderModel, this.existing});

  @override
  State<_EditFolderDialog> createState() => _EditFolderDialogState();
}

class _EditFolderDialogState extends State<_EditFolderDialog> {
  late final TextEditingController _controller;
  late final SavedFolderEditorStore _editorStore;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.existing?.name ?? '');
    _editorStore = SavedFolderEditorStore(
      widget.existing?.autoDownload ?? false,
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    _editorStore.destroy();
    super.dispose();
  }

  Future<void> _submit() async {
    var name = _controller.text.trim();
    if (name.isEmpty) {
      return;
    }

    var existing = widget.existing;
    if (existing == null) {
      var folder = await widget.folderModel.createFolder(
        name,
        autoDownload: _editorStore.state,
      );
      if (mounted) Navigator.pop(context, folder);
    } else {
      await widget.folderModel.updateFolder(
        existing.id,
        name,
        autoDownload: _editorStore.state,
      );
      if (mounted) {
        Navigator.pop(
          context,
          existing.copyWith(name: name, autoDownload: _editorStore.state),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<SavedFolderEditorStore, bool>(
      store: _editorStore,
      onState: (_, autoDownload) => AlertDialog(
        title: Text(
          widget.existing == null
              ? L10n.of(context).create_new_folder
              : L10n.of(context).edit_folder,
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: _controller,
              autofocus: true,
              textCapitalization: TextCapitalization.sentences,
              decoration: InputDecoration(
                hintText: L10n.of(context).folder_name,
              ),
              onSubmitted: (_) => _submit(),
            ),
            const SizedBox(height: kTweetSpace2),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(L10n.of(context).auto_download_images),
              subtitle: Text(L10n.of(context).auto_download_images_description),
              value: autoDownload,
              onChanged: _editorStore.setAutoDownload,
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text(L10n.of(context).cancel),
          ),
          TextButton(
            onPressed: _submit,
            child: Text(
              widget.existing == null
                  ? L10n.of(context).create
                  : L10n.of(context).save,
            ),
          ),
        ],
      ),
    );
  }
}
