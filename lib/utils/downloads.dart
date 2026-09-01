import 'package:dart_twitter_api/twitter_api.dart' show Media;
import 'package:flutter/material.dart';
import 'package:flutter_file_dialog/flutter_file_dialog.dart';

import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/utils/download_directory.dart';
import 'package:http/http.dart' as http;
import 'package:path/path.dart' as p;
import 'package:pref/pref.dart';

/// Downloads every photo of a saved post straight to the configured download
/// folder, for folders with auto-download enabled. Silent-by-design: it never
/// prompts, so it needs a fixed download folder; without one it just tells the
/// user where to set it. Context-free (takes a [messenger]) since the save
/// sheet that triggers it has already been dismissed.
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
    final tweet = TweetWithCard.fromJson(content);
    username = tweet.user?.screenName ?? 'xta';
    final media = tweet.extendedEntities?.media ?? tweet.entities?.media ?? const <Media>[];
    photos = media.where((m) => m.type == 'photo' && m.mediaUrlHttps != null).toList();
  } catch (_) {
    return;
  }
  if (photos.isEmpty) {
    return;
  }

  final downloadType = prefs.get(optionDownloadType);
  final treeUri = prefs.get<String>(optionDownloadTreeUri) ?? '';
  if (downloadType == optionDownloadTypeAsk || treeUri.isEmpty) {
    messenger.showSnackBar(SnackBar(content: Text(needFolderLabel)));
    return;
  }

  messenger.showSnackBar(workingSnackBar(downloadingLabel));
  var saved = 0;
  Object? failure;
  for (final media in photos) {
    try {
      final response = await http.get(Uri.parse('${media.mediaUrlHttps}:orig'));
      if (response.statusCode != 200) {
        continue;
      }
      final fileName = '$username-${p.basename(media.mediaUrlHttps!)}'.split('?')[0];
      await DownloadDirectory.save(treeUri: treeUri, fileName: fileName, bytes: response.bodyBytes);
      saved++;
    } catch (e) {
      failure ??= e;
    }
  }

  messenger.hideCurrentSnackBar(reason: SnackBarClosedReason.hide);
  if (saved > 0) {
    messenger.showSnackBar(SnackBar(content: Text(doneLabel)));
  } else if (failure != null) {
    // Silent failure is what made this hard to diagnose; say what to do.
    messenger.showSnackBar(SnackBar(content: Text(needFolderLabel)));
  }
}

Future<void> downloadUriToPickedFile(BuildContext context, Uri uri, String fileName,
    {required BasePrefService prefs, required Function() onStart, required Function() onSuccess}) async {
  var sanitizedFilename = fileName.split("?")[0];

  try {
    onStart();
    var responseTask = downloadFile(context, uri);

    var response = await responseTask;
    if (response == null) {
      return;
    }

    final downloadType = prefs.get(optionDownloadType);
    final treeUri = prefs.get<String>(optionDownloadTreeUri) ?? '';

    // Ask every time, or fall back to asking when no folder is usable yet — a
    // folder chosen by an older build cannot be written to any more.
    if (downloadType == optionDownloadTypeAsk || treeUri.isEmpty) {
      var fileInfo =
          await FlutterFileDialog.saveFile(params: SaveFileDialogParams(fileName: sanitizedFilename, data: response));
      if (fileInfo == null) {
        return;
      }

      onSuccess();
      return;
    }

    // Write through the document tree the user granted, which is the only way
    // to reach shared storage on Android 11 and later.
    await DownloadDirectory.save(treeUri: treeUri, fileName: sanitizedFilename, bytes: response);

    onSuccess();
  } catch (e) {
    if (context.mounted) {
      showSnackBar(context, icon: '🙊', message: e.toString());
    }
  }
}

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
