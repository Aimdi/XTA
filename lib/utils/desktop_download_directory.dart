import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:path/path.dart' as p;
import 'package:url_launcher/url_launcher.dart';
import 'package:xdg_directories/xdg_directories.dart' as xdg;

/// [DownloadDirectory] on the desktop, where a chosen folder is a plain path
/// and the "shared storage" is the reader's XDG Pictures, Videos and Downloads
/// folders. Every saved "document" is the path of the file written.
class DesktopDownloadDirectory {
  const DesktopDownloadDirectory._();

  static Future<String?> pick() => FilePicker.getDirectoryPath();

  static Future<bool> hasAccess(String folder) => Directory(folder).exists();

  static Future<String> saveBytes({required String folder, required String fileName, required List<int> bytes}) async {
    final target = await _freeTarget(folder, fileName);
    await target.writeAsBytes(bytes, flush: true);
    return target.path;
  }

  static Future<String> saveFile({
    required String folder,
    required String fileName,
    required String sourcePath,
    String? subfolder,
  }) async {
    final directory = subfolder == null ? folder : p.join(folder, subfolder);
    final target = await _freeTarget(directory, fileName);
    return (await File(sourcePath).copy(target.path)).path;
  }

  /// [relativePath] is the Android one, `Pictures/XTA/…`: its first segment
  /// names the shared folder, read from the reader's XDG user directories.
  static Future<String> saveFileToSharedStorage({
    required String collection,
    required String relativePath,
    required String fileName,
    required String sourcePath,
  }) {
    final segments = p.split(relativePath);
    final root = sharedFolderFor(collection, xdg.getUserDirectory, Platform.environment['HOME'] ?? '.');
    return saveFile(folder: p.joinAll([root, ...segments.skip(1)]), fileName: fileName, sourcePath: sourcePath);
  }

  static Future<void> delete(String path) async {
    final file = File(path);
    if (await file.exists()) await file.delete();
  }

  static Future<void> open(String path) => launchUrl(Uri.file(path));
}

/// The XDG user directory a MediaStore [collection] maps to, under [home]
/// when `xdg-user-dir` cannot say.
String sharedFolderFor(String collection, Directory? Function(String) userDirectory, String home) {
  final (name, fallback) = switch (collection) {
    'images' => ('PICTURES', 'Pictures'),
    'video' => ('VIDEOS', 'Videos'),
    _ => ('DOWNLOAD', 'Downloads'),
  };
  return userDirectory(name)?.path ?? p.join(home, fallback);
}

/// The first of `name.ext`, `name (1).ext`, … not yet taken in [directory],
/// as Android's document provider does: a save never overwrites.
Future<File> _freeTarget(String directory, String fileName) async {
  await Directory(directory).create(recursive: true);
  final base = p.basenameWithoutExtension(fileName);
  final extension = p.extension(fileName);
  for (var copy = 0; ; copy++) {
    final name = copy == 0 ? fileName : '$base ($copy)$extension';
    final file = File(p.join(directory, name));
    if (!await file.exists()) return file;
  }
}
