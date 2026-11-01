import 'dart:io';

import 'package:archive/archive.dart';
import 'package:crypto/crypto.dart';
import 'package:path/path.dart' as p;
import 'package:xta/speech/voice_archive.dart';
import 'package:xta/speech/voice_engine.dart';

/// Unpacks the engine archive at [archivePath] (tar.xz) into [voiceEngineDirectory] under [root], when every library
/// [package] pins is in it and matches its pin.
///
/// Synchronous and CPU-bound, so it runs in a background isolate. The libraries are checked before they are written,
/// into a staging folder renamed into place only once all of them are there, so a half-installed engine never looks
/// installed. Anything else in the archive is left out.
void installVoiceEngineArchive(String archivePath, VoiceEnginePackage package, String root) {
  final archive = _decode(archivePath);
  final target = Directory(voiceEngineDirectory(root, package));
  final staging = Directory('${target.path}.partial');
  try {
    if (staging.existsSync()) staging.deleteSync(recursive: true);
    staging.createSync(recursive: true);
    for (final library in package.libraries) {
      File(p.join(staging.path, library.name)).writeAsBytesSync(_pinnedBytes(archive, library), flush: true);
    }
    if (target.existsSync()) target.deleteSync(recursive: true);
    staging.renameSync(target.path);
  } finally {
    if (staging.existsSync()) staging.deleteSync(recursive: true);
  }
}

Archive _decode(String archivePath) {
  try {
    return TarDecoder().decodeBytes(XZDecoder().decodeBytes(File(archivePath).readAsBytesSync()));
  } on Exception catch (error) {
    throw VoiceArchiveInvalid('$error');
  }
}

/// [library]'s bytes out of [archive], when they are the pinned ones.
List<int> _pinnedBytes(Archive archive, VoiceEngineLibrary library) {
  final entry = archive.files.where((file) => file.isFile && p.basename(file.name) == library.name).firstOrNull;
  if (entry == null) throw VoiceArchiveInvalid('${library.name} is missing');
  final bytes = entry.content;
  if (bytes.length != library.bytes || sha256.convert(bytes).toString() != library.sha256) {
    throw const VoiceChecksumMismatch();
  }
  return bytes;
}

/// Whether the engine for [package] is installed under [root]: every library there, at its pinned size.
bool voiceEngineFilesPresent(String root, VoiceEnginePackage package) {
  final folder = voiceEngineDirectory(root, package);
  return package.libraries.every((library) {
    final file = File(p.join(folder, library.name));
    return file.existsSync() && file.lengthSync() == library.bytes;
  });
}
