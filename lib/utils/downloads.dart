import 'dart:async';

import 'package:dart_twitter_api/twitter_api.dart' show Media;
import 'package:flutter/material.dart';

import 'package:xta/client/client.dart';
import 'package:xta/plugins/mastodon/mastodon_archive.dart';
import 'package:xta/saved/saved_media.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/downloads/download_destination.dart';
import 'package:xta/downloads/download_entry.dart';
import 'package:xta/downloads/download_store.dart';
import 'package:xta/utils/download_directory.dart';
import 'package:http/http.dart' as http;
import 'package:path/path.dart' as p;
import 'package:pref/pref.dart';

/// Downloads every photo of a saved post straight to the configured download
/// folder, for folders with auto-download enabled. Silent-by-design: it never
/// prompts, so it needs a fixed download folder or background saving; when
/// downloads ask instead it just tells the user where to change that.
/// Context-free (takes a [messenger]) since the save sheet that triggers it
/// has already been dismissed.
Future<void> autoDownloadTweetPhotos({
  required Map<String, dynamic> content,
  required BasePrefService prefs,
  required ScaffoldMessengerState messenger,
  required String downloadingLabel,
  required String doneLabel,
  required String needFolderLabel,
}) async {
  String username;
  List<Media> photos;
  try {
    final mastodon = mastodonPostFromArchive(content);
    username = mastodon?.acct.split('@').first ?? TweetWithCard.fromJson(content).user?.screenName ?? 'xta';
    final media = mediaOfSavedContent(content);
    photos = media.where((m) => m.type == 'photo' && m.mediaUrlHttps != null).toList();
  } catch (_) {
    return;
  }
  if (photos.isEmpty) {
    return;
  }

  final destination = DownloadDestination.fromPrefs(prefs);
  if (destination.asks) {
    if (messenger.mounted) messenger.showSnackBar(SnackBar(content: Text(needFolderLabel)));
    return;
  }

  if (messenger.mounted) messenger.showSnackBar(workingSnackBar(downloadingLabel));
  final requests = photos.map((media) {
    final uri = originalDownloadUri(Uri.parse(media.mediaUrlHttps!));
    return DownloadRequest(uri: uri,
      fileName: '$username-${p.basename(uri.path).replaceFirst(RegExp(r':orig$'), '')}',
      treeUri: destination.treeUri, background: destination.background);
  }).toList();
  final result = await DownloadStore.shared.enqueueBatch(requests);
  if (!messenger.mounted) return;
  messenger.hideCurrentSnackBar(reason: SnackBarClosedReason.hide);
  messenger.showSnackBar(SnackBar(content: Text(L10n.current.downloads_batch_result(result.saved, result.total))));
}

/// Queues [uri] where the download setting says; true once the file is saved.
/// [onSuccess] reports a save to a folder the user chose; a background save
/// names its folder instead. [subfolder] applies to the configured folder and
/// to background saves; a file picked by the reader goes where they chose.
Future<bool> downloadUriToPickedFile(BuildContext context, Uri uri, String fileName,
    {required BasePrefService prefs, required Function() onStart, required Function() onSuccess,
    String? subfolder}) async {
  final messenger = ScaffoldMessenger.of(context);
  final l10n = L10n.of(context);
  try {
    onStart();
    final destination = DownloadDestination.fromPrefs(prefs);
    final result = await DownloadStore.shared.enqueue(uri: uri, fileName: fileName,
      treeUri: destination.treeUri, background: destination.background, subfolder: subfolder);
    final saved = result.status == DownloadStatus.completed;
    if (messenger.mounted) messenger.hideCurrentSnackBar();
    if (!context.mounted) return saved;
    final folder = sharedFolderOf(result);
    if (saved && folder != null) {
      messenger.showSnackBar(_savedToFolderSnackBar(messenger, l10n, result, folder));
    } else if (saved) {
      onSuccess();
    } else if (result.status == DownloadStatus.failed) {
      messenger.showSnackBar(SnackBar(content: Text(l10n.downloads_retry_hint)));
    }
    return saved;
  } catch (_) {
    if (messenger.mounted) {
      messenger.hideCurrentSnackBar();
      messenger.showSnackBar(SnackBar(content: Text(l10n.downloads_failed)));
    }
    return false;
  }
}

/// The shared folder a finished background download went to, or null when it
/// went where the user picked: Android 9 and older ask instead, and only a
/// MediaStore item lives under the `media` authority.
String? sharedFolderOf(DownloadEntry entry) {
  final saved = Uri.tryParse(entry.savedUri ?? '');
  if (!entry.background || saved?.authority != 'media') return null;
  final folder = SharedDownloadFolder.of(entry.fileName).relativePath;
  return entry.subfolder == null ? folder : '$folder/${entry.subfolder}';
}

SnackBar _savedToFolderSnackBar(ScaffoldMessengerState messenger, L10n l10n, DownloadEntry entry, String folder) =>
  SnackBar(
    content: Text(l10n.download_saved_to(folder)),
    action: SnackBarAction(
      label: l10n.downloads_open_file,
      onPressed: () => unawaited(DownloadDirectory.openDocument(entry.savedUri!, entry.fileName).catchError((Object _) {
        if (messenger.mounted) messenger.showSnackBar(SnackBar(content: Text(l10n.downloads_open_failed)));
      })),
    ),
  );

class UnableToSaveMedia {
  final Uri uri;
  final Object e;

  UnableToSaveMedia(this.uri, this.e);

  @override
  String toString() {
    return 'Unable to save the media {uri: $uri, e: $e}';
  }
}

Future downloadFile(BuildContext context, Uri uri) async {
  var response = await http.get(uri);
  if (response.statusCode == 200) {
    return response.bodyBytes;
  }

  if (context.mounted) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Text(
        L10n.of(context).unable_to_save_the_media_twitter_returned_a_status_of_response_statusCode(response.statusCode),
      ),
    ));
  }

  return null;
}
