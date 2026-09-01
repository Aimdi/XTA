import 'package:dart_twitter_api/twitter_api.dart' show Media;
import 'package:http/http.dart' as http;
import 'package:logging/logging.dart';
import 'package:pref/pref.dart';
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/immich/immich_client.dart';
import 'package:xta/plugins/immich/immich_media.dart';
import 'package:xta/plugins/immich/immich_upload_log.dart';

final _log = Logger('ImmichUploader');

/// How an upload run ended, so the caller can say so without knowing the API.
enum ImmichUploadStatus { nothingToDo, notConfigured, done, failed }

class ImmichUploadReport {
  final ImmichUploadStatus status;

  /// Assets Immich did not already have.
  final int uploaded;

  /// Files it recognised by checksum. Not a failure — the library has them.
  final int duplicates;

  final int failures;

  const ImmichUploadReport(this.status, {this.uploaded = 0, this.duplicates = 0, this.failures = 0});
}

/// Sends the media of one saved post to Immich.
///
/// Only ever writes: it uploads what the reader has already chosen to keep and
/// asks the server nothing about the rest of their library.
class ImmichUploader {
  final ImmichClient client;
  final ImmichUploadLog log;
  final http.Client httpClient;

  ImmichUploader({
    required this.client,
    ImmichUploadLog? log,
    http.Client? httpClient,
  })  : log = log ?? ImmichUploadLog(),
        httpClient = httpClient ?? http.Client();

  /// Uploads the photos and videos of [content], filing them into an album
  /// named [albumName] when one is asked for.
  Future<ImmichUploadReport> uploadTweetMedia({
    required Map<String, dynamic> content,
    required BasePrefService prefs,
    String? albumName,
  }) async {
    final baseUrl = prefs.get<String>(optionPluginImmichServerUrl) ?? '';
    final apiKey = prefs.get<String>(optionPluginImmichApiKey) ?? '';
    if (parseImmichBaseUrl(baseUrl) == null || apiKey.trim().isEmpty) {
      return const ImmichUploadReport(ImmichUploadStatus.notConfigured);
    }

    final candidates = uploadableMedia(
      mediaCandidates(_mediaOf(content)),
      includeVideos: prefs.get<bool>(optionPluginImmichIncludeVideos) ?? true,
    );
    if (candidates.isEmpty) {
      return const ImmichUploadReport(ImmichUploadStatus.nothingToDo);
    }

    final pending = await log.pending(candidates.map((m) => m.id));
    final todo = candidates.where((m) => pending.contains(m.id)).toList();
    if (todo.isEmpty) {
      return const ImmichUploadReport(ImmichUploadStatus.nothingToDo);
    }

    final assetIds = <String>[];
    var uploaded = 0;
    var duplicates = 0;
    var failures = 0;

    for (final media in todo) {
      final result = await _sendOne(media, baseUrl: baseUrl, apiKey: apiKey);
      switch (result?.outcome) {
        case ImmichUploadOutcome.created:
          uploaded++;
        case ImmichUploadOutcome.duplicate:
          duplicates++;
        case null:
          failures++;
      }
      final assetId = result?.assetId;
      if (assetId != null) {
        assetIds.add(assetId);
      }
    }

    await _fileInAlbum(albumName, baseUrl: baseUrl, apiKey: apiKey, assetIds: assetIds);

    if (uploaded == 0 && duplicates == 0) {
      return ImmichUploadReport(ImmichUploadStatus.failed, failures: failures);
    }
    return ImmichUploadReport(
      ImmichUploadStatus.done,
      uploaded: uploaded,
      duplicates: duplicates,
      failures: failures,
    );
  }

  /// Downloads one file and hands it over, recording it so it is not sent again.
  /// Returns null when either half failed; the caller counts that and carries on
  /// to the next file rather than abandoning the post.
  Future<ImmichUploadResult?> _sendOne(
    UploadableMedia media, {
    required String baseUrl,
    required String apiKey,
  }) async {
    try {
      final response = await httpClient.get(Uri.parse(media.url));
      if (response.statusCode != 200) {
        _log.warning('Could not fetch ${media.url}: HTTP ${response.statusCode}');
        return null;
      }

      final result = await client.upload(
        baseUrl: baseUrl,
        apiKey: apiKey,
        bytes: response.bodyBytes,
        fileName: media.fileName,
        deviceAssetId: media.id,
        createdAt: DateTime.now(),
      );

      await log.record(media.id, assetId: result.assetId);
      return result;
    } catch (e) {
      _log.warning('Could not upload ${media.url}: $e');
      return null;
    }
  }

  Future<void> _fileInAlbum(
    String? albumName, {
    required String baseUrl,
    required String apiKey,
    required List<String> assetIds,
  }) async {
    final name = albumName?.trim();
    if (name == null || name.isEmpty || assetIds.isEmpty) {
      return;
    }

    final albumId = await client.ensureAlbum(baseUrl: baseUrl, apiKey: apiKey, name: name);
    if (albumId == null) {
      // The assets are in the library; not being in an album is worth a line in
      // the log and nothing more.
      _log.warning('Uploaded to Immich but could not resolve the album "$name"');
      return;
    }
    await client.addToAlbum(baseUrl: baseUrl, apiKey: apiKey, albumId: albumId, assetIds: assetIds);
  }

  List<Media> _mediaOf(Map<String, dynamic> content) {
    try {
      final tweet = TweetWithCard.fromJson(content);
      return tweet.extendedEntities?.media ?? tweet.entities?.media ?? const <Media>[];
    } catch (e) {
      _log.warning('Could not read the media of a saved post: $e');
      return const [];
    }
  }
}
