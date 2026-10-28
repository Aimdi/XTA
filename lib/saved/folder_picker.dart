import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/immich/immich_client.dart';
import 'package:xta/plugins/immich/immich_uploader.dart';
import 'package:xta/saved/save_sheet.dart';
import 'package:xta/saved/saved_tweet_folder_model.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/ui/action_feedback.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/utils/downloads.dart';
import 'package:xta/utils/iterables.dart';

/// Where a plain tap on the bookmark files a post, or null for unfiled.
///
/// Null whenever the reader has not asked for a folder to be remembered, which
/// is the old behaviour: everything lands unfiled until they say otherwise.
String? rememberedSaveFolder(BasePrefService prefs) {
  if (prefs.get<bool>(optionSavedStickyFolderEnabled) != true) {
    return null;
  }
  final id = prefs.get<String>(optionSavedStickyFolderId) ?? '';
  return id.isEmpty ? null : id;
}

/// Notes [folderId] as where the next save goes, if remembering is switched on.
Future<void> rememberSaveFolder(BasePrefService prefs, String? folderId) async {
  if (prefs.get<bool>(optionSavedStickyFolderEnabled) == true) {
    await prefs.set(optionSavedStickyFolderId, folderId ?? '');
  }
}

/// Files a post in [folderId], then does whatever that folder asks for: download
/// its photos, send its media to Immich, or both.
///
/// Shared by the sheet and by a plain tap on the bookmark. The plain tap used
/// to insert the row itself and skip the download, so a folder set to
/// auto-download only did so when the post arrived through the sheet.
Future<void> fileSavedTweet(
  BuildContext context, {
  required String tweetId,
  required String? userId,
  required Map<String, dynamic> content,
  required String? folderId,
}) async {
  final savedModel = context.read<SavedTweetModel>();
  final folderModel = context.read<SavedTweetFolderModel>();
  final prefs = PrefService.of(context, listen: false);
  final messenger = ScaffoldMessenger.of(context);
  // Read defensively: not every screen that files a post is guaranteed to sit
  // under the provider, and a bookmark must still be saved if it does not.
  ImmichClient? immichClient;
  try {
    immichClient = context.read<ImmichClient>();
  } on ProviderNotFoundException {
    immichClient = null;
  }

  // Read before the await: the labels come from a context that may be gone by
  // the time the download starts.
  final downloadingLabel = L10n.of(context).downloading_media;
  final doneLabel = L10n.of(context).successfully_saved_the_media;
  final needFolderLabel = L10n.of(context).set_a_download_folder_to_auto_download;
  final immichLabels = _ImmichLabels.of(context);

  final folder = folderId == null ? null : folderModel.state.firstWhereOrNull((f) => f.id == folderId);

  if (savedModel.isSaved(tweetId)) {
    await savedModel.setFolder(tweetId, folderId);
  } else {
    await savedModel.saveTweet(tweetId, userId, content, folderId: folderId);
  }

  if (folder?.autoDownload ?? false) {
    await autoDownloadTweetPhotos(
      content: content,
      prefs: prefs,
      messenger: messenger,
      downloadingLabel: downloadingLabel,
      doneLabel: doneLabel,
      needFolderLabel: needFolderLabel,
    );
  }

  if ((folder?.autoUpload ?? false) && prefs.get(optionPluginImmichEnabled) == true && immichClient != null) {
    await _uploadToImmich(
      client: immichClient,
      content: content,
      prefs: prefs,
      messenger: messenger,
      folderName: folder!.name,
      labels: immichLabels,
    );
  }
}

/// The strings the Immich run may need, read while there is still a context.
class _ImmichLabels {
  final String uploading;
  final String done;
  final String failed;
  final String notConfigured;

  const _ImmichLabels(this.uploading, this.done, this.failed, this.notConfigured);

  factory _ImmichLabels.of(BuildContext context) {
    final l10n = L10n.of(context);
    return _ImmichLabels(
      l10n.plugin_immich_uploading,
      l10n.plugin_immich_upload_done,
      l10n.plugin_immich_upload_failed,
      l10n.plugin_immich_not_configured,
    );
  }
}

Future<void> _uploadToImmich({
  required ImmichClient client,
  required Map<String, dynamic> content,
  required BasePrefService prefs,
  required ScaffoldMessengerState messenger,
  required String folderName,
  required _ImmichLabels labels,
}) async {
  final albumPerFolder = prefs.get<bool>(optionPluginImmichAlbumPerFolder) ?? true;

  messenger.showSnackBar(workingSnackBar(labels.uploading));
  final report = await ImmichUploader(client: client).uploadTweetMedia(
    content: content,
    prefs: prefs,
    albumName: albumPerFolder ? folderName : null,
  );
  messenger.hideCurrentSnackBar(reason: SnackBarClosedReason.hide);

  // Nothing to do covers both a post with no media and one already uploaded;
  // neither is worth interrupting the reader for.
  final message = switch (report.status) {
    ImmichUploadStatus.nothingToDo => null,
    ImmichUploadStatus.notConfigured => labels.notConfigured,
    ImmichUploadStatus.done => labels.done,
    ImmichUploadStatus.failed => labels.failed,
  };
  if (message != null) {
    messenger.showSnackBar(SnackBar(content: Text(message)));
  }
}

/// Opens the "save to folder" sheet for a post: a tap on a folder files the
/// post there, saving it first if needed.
Future<void> showSaveToFolderSheet(BuildContext context,
    {required String tweetId, String? userId, required Map<String, dynamic> content}) async {
  var savedModel = context.read<SavedTweetModel>();
  var folderModel = context.read<SavedTweetFolderModel>();
  lightHaptic(context);

  await folderModel.listFolders();
  if (!context.mounted) {
    return;
  }

  await showModalBottomSheet(
    context: context,
    showDragHandle: true,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (_) => SaveToFolderSheet(
      tweetId: tweetId,
      savedModel: savedModel,
      folderModel: folderModel,
      file: (context, folderId) async {
        final prefs = PrefService.of(context, listen: false);
        await fileSavedTweet(context, tweetId: tweetId, userId: userId, content: content, folderId: folderId);
        // The pick is what gets remembered, so the next plain tap lands here too.
        await rememberSaveFolder(prefs, folderId);
      },
    ),
  );
}

Future<SavedTweetFolder?> showCreateFolderDialog(BuildContext context, SavedTweetFolderModel folderModel,
    {SavedTweetFolder? existing}) {
  return showDialog<SavedTweetFolder>(
    context: context,
    builder: (_) => _EditFolderDialog(folderModel: folderModel, existing: existing),
  );
}

/// Confirms deletion of [folder]; on confirm, deletes it (its posts return to
/// "unfiled") and reloads the saved list. Returns true if it was deleted.
Future<bool> showDeleteFolderDialog(
    BuildContext context, SavedTweetFolderModel folderModel, SavedTweetFolder folder) async {
  var savedModel = context.read<SavedTweetModel>();

  var confirmed = await showDialog<bool>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(L10n.of(context).delete_folder),
      content: Text(L10n.of(context).delete_folder_description),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context, false), child: Text(L10n.of(context).cancel)),
        TextButton(onPressed: () => Navigator.pop(context, true), child: Text(L10n.of(context).delete)),
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
  late bool _autoDownload;
  late bool _autoUpload;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.existing?.name ?? '');
    _autoDownload = widget.existing?.autoDownload ?? false;
    _autoUpload = widget.existing?.autoUpload ?? false;
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    var name = _controller.text.trim();
    if (name.isEmpty) {
      return;
    }

    var existing = widget.existing;
    if (existing == null) {
      var folder = await widget.folderModel
          .createFolder(name, autoDownload: _autoDownload, autoUpload: _autoUpload);
      if (mounted) Navigator.pop(context, folder);
    } else {
      await widget.folderModel
          .updateFolder(existing.id, name, autoDownload: _autoDownload, autoUpload: _autoUpload);
      if (mounted) {
        Navigator.pop(context, existing.copyWith(name: name, autoDownload: _autoDownload, autoUpload: _autoUpload));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(widget.existing == null ? L10n.of(context).create_new_folder : L10n.of(context).edit_folder),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          TextField(
            controller: _controller,
            autofocus: true,
            textCapitalization: TextCapitalization.sentences,
            decoration: InputDecoration(hintText: L10n.of(context).folder_name),
            onSubmitted: (_) => _submit(),
          ),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: Text(L10n.of(context).auto_download_images),
            subtitle: Text(L10n.of(context).auto_download_images_description),
            value: _autoDownload,
            onChanged: (value) => setState(() => _autoDownload = value),
          ),
          // Only offered where it can do something. A switch for a server the
          // reader has not set up reads as a broken feature rather than an
          // optional one.
          if (PrefService.of(context).get(optionPluginImmichEnabled) == true)
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(L10n.of(context).auto_upload_immich),
              subtitle: Text(L10n.of(context).auto_upload_immich_description),
              value: _autoUpload,
              onChanged: (value) => setState(() => _autoUpload = value),
            ),
        ],
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: Text(L10n.of(context).cancel)),
        TextButton(
            onPressed: _submit,
            child: Text(widget.existing == null ? L10n.of(context).create : L10n.of(context).save)),
      ],
    );
  }
}
