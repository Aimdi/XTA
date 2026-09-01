import 'dart:typed_data';

import 'package:flutter/services.dart';
import 'package:path/path.dart' as p;

/// The folder media is auto-saved into, addressed as an Android document tree
/// rather than a filesystem path.
///
/// Writing to a shared-storage path with `File` stopped being allowed in
/// Android 11: the app can be told a folder's name without being given access
/// to it, which is why saving used to fail with
/// `PathAccessException … Operation not permitted, errno = 1` no matter which
/// permissions were granted. A document tree carries the grant with it.
class DownloadDirectory {
  static const MethodChannel _channel = MethodChannel('browser_resolver');

  /// Opens the system folder picker and keeps write access to the result.
  /// Returns the tree URI, or null when the user backed out.
  static Future<String?> pick() async {
    return _channel.invokeMethod<String>('pickDownloadDirectory');
  }

  /// Whether [treeUri] is still writable — the folder can be deleted, or the
  /// grant revoked, long after it was chosen.
  static Future<bool> hasAccess(String? treeUri) async {
    if (treeUri == null || treeUri.isEmpty) {
      return false;
    }
    final granted = await _channel.invokeMethod<bool>('hasDownloadDirectoryAccess', {'treeUri': treeUri});
    return granted ?? false;
  }

  /// Writes [bytes] into the chosen folder. Returns the saved document's URI.
  static Future<String?> save({
    required String treeUri,
    required String fileName,
    required Uint8List bytes,
  }) async {
    return _channel.invokeMethod<String>('saveToDownloadDirectory', {
      'treeUri': treeUri,
      'fileName': fileName,
      'mimeType': mimeTypeFor(fileName),
      'bytes': bytes,
    });
  }

  /// A readable folder name for the settings row: a tree URI ends in a document
  /// id like `primary:Pictures/XTA`, which is the part worth showing.
  static String displayName(String treeUri) {
    // The document id must be taken as a whole path segment before decoding:
    // it encodes its own separators (`primary%3APictures%2FXTA`), so decoding
    // first and splitting on "/" would throw away the parent folder.
    final uri = Uri.tryParse(treeUri);
    final documentId =
        uri != null && uri.pathSegments.isNotEmpty ? uri.pathSegments.last : Uri.decodeFull(treeUri);

    final withoutVolume = documentId.contains(':') ? documentId.split(':').last : documentId;
    return withoutVolume.isEmpty ? documentId : withoutVolume;
  }
}

/// Content type from the file's extension. Android stores this with the
/// document, and it decides whether the gallery shows the file at all.
String mimeTypeFor(String fileName) {
  switch (p.extension(fileName).toLowerCase()) {
    case '.jpg':
    case '.jpeg':
      return 'image/jpeg';
    case '.png':
      return 'image/png';
    case '.gif':
      return 'image/gif';
    case '.webp':
      return 'image/webp';
    case '.mp4':
      return 'video/mp4';
    case '.m4v':
      return 'video/x-m4v';
    case '.mov':
      return 'video/quicktime';
    case '.webm':
      return 'video/webm';
    case '.mp3':
      return 'audio/mpeg';
    case '.m4a':
      return 'audio/mp4';
    default:
      return 'application/octet-stream';
  }
}
