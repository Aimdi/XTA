import 'dart:typed_data';

import 'package:flutter/services.dart';
import 'package:path/path.dart' as p;
import 'package:xta/downloads/download_entry.dart';
import 'package:xta/utils/desktop.dart';
import 'package:xta/utils/desktop_download_directory.dart';

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
    if (isDesktop) return DesktopDownloadDirectory.pick();
    return _channel.invokeMethod<String>('pickDownloadDirectory');
  }

  /// Whether [treeUri] is still writable — the folder can be deleted, or the
  /// grant revoked, long after it was chosen.
  static Future<bool> hasAccess(String? treeUri) async {
    if (treeUri == null || treeUri.isEmpty) {
      return false;
    }
    if (isDesktop) return DesktopDownloadDirectory.hasAccess(treeUri);
    final granted = await _channel.invokeMethod<bool>('hasDownloadDirectoryAccess', {'treeUri': treeUri});
    return granted ?? false;
  }

  /// Writes [bytes] into the chosen folder. Returns the saved document's URI.
  static Future<String?> save({
    required String treeUri,
    required String fileName,
    required Uint8List bytes,
  }) async {
    if (isDesktop) return DesktopDownloadDirectory.saveBytes(folder: treeUri, fileName: fileName, bytes: bytes);
    return _channel.invokeMethod<String>('saveToDownloadDirectory', {
      'treeUri': treeUri,
      'fileName': fileName,
      'mimeType': mimeTypeFor(fileName),
      'bytes': bytes,
    });
  }

  /// Copies a staged file without sending its contents through the platform
  /// channel, into [subfolder] inside the chosen folder when there is one
  /// (made when missing, reused after).
  static Future<String?> saveFile({required String treeUri, required String fileName,
      required String sourcePath, required String operationId, String? subfolder}) => isDesktop
    ? DesktopDownloadDirectory.saveFile(folder: treeUri, fileName: fileName, sourcePath: sourcePath,
        subfolder: subfolder == null ? null : safeDownloadFolder(subfolder))
    : _channel.invokeMethod<String>('saveFileToDownloadDirectory', {
      'treeUri': treeUri, 'fileName': fileName, 'mimeType': mimeTypeFor(fileName),
      'sourcePath': sourcePath, 'operationId': operationId, ..._subfolderArgument(subfolder),
    });

  /// Whether files can be saved into the shared Pictures, Movies and Download
  /// folders without a picker: MediaStore takes them from Android 10 on.
  static Future<bool> canSaveToSharedStorage() async {
    if (isDesktop) return true;
    try {
      return await _channel.invokeMethod<bool>('canSaveToSharedStorage') ?? false;
    } catch (_) {
      return false;
    }
  }

  /// Copies a staged file into the shared folder for its type (see
  /// [SharedDownloadFolder]), in [subfolder] below it when one is named, where
  /// the gallery sees it at once. Returns the new item's URI, or null when the
  /// operation was cancelled.
  static Future<String?> saveFileToSharedStorage({required String fileName,
      required String sourcePath, required String operationId, String? subfolder}) {
    final folder = SharedDownloadFolder.of(fileName);
    final below = subfolder == null ? null : safeDownloadFolder(subfolder);
    final relativePath = below == null ? folder.relativePath : '${folder.relativePath}/$below';
    if (isDesktop) {
      return DesktopDownloadDirectory.saveFileToSharedStorage(
        collection: folder.collection, relativePath: relativePath, fileName: fileName, sourcePath: sourcePath);
    }
    return _channel.invokeMethod<String>('saveFileToSharedStorage', {
      'fileName': fileName, 'mimeType': mimeTypeFor(fileName),
      'collection': folder.collection,
      'relativePath': relativePath,
      'sourcePath': sourcePath, 'operationId': operationId,
    });
  }

  static Map<String, String> _subfolderArgument(String? subfolder) {
    final folder = subfolder == null ? null : safeDownloadFolder(subfolder);
    return folder == null ? const {} : {'subfolder': folder};
  }

  // A desktop save is one local copy, with nothing to call off part-way.
  static Future<void> cancelSave(String operationId) async => isDesktop ? null :
    _channel.invokeMethod<void>('cancelDownloadSave', {'operationId': operationId});

  static Future<void> deleteDocument(String documentUri) => isDesktop
    ? DesktopDownloadDirectory.delete(documentUri)
    : _channel.invokeMethod<void>('deleteDownloadedDocument', {'documentUri': documentUri});

  static Future<void> openDocument(String documentUri, String fileName) => isDesktop
    ? DesktopDownloadDirectory.open(documentUri)
    : _channel.invokeMethod<void>('openDownloadedDocument', {
      'documentUri': documentUri, 'mimeType': mimeTypeFor(fileName),
    });

  /// A readable folder name for the settings row: a tree URI ends in a document
  /// id like `primary:Pictures/XTA`, which is the part worth showing.
  static String displayName(String treeUri) {
    // A desktop folder is a plain path, shown whole.
    if (treeUri.startsWith('/')) return treeUri;
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

/// Where a download saved without a picker lands: the folder the gallery or
/// file manager already shows for its type, with an XTA subfolder.
enum SharedDownloadFolder {
  pictures('images', 'Pictures/XTA'),
  movies('video', 'Movies/XTA'),
  downloads('downloads', 'Download/XTA');

  /// The MediaStore collection, as the platform side names it.
  final String collection;
  final String relativePath;

  const SharedDownloadFolder(this.collection, this.relativePath);

  static SharedDownloadFolder of(String fileName) {
    final mimeType = mimeTypeFor(fileName);
    if (mimeType.startsWith('image/')) return pictures;
    if (mimeType.startsWith('video/')) return movies;
    return downloads;
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
    case '.zip':
      return 'application/zip';
    default:
      return 'application/octet-stream';
  }
}
