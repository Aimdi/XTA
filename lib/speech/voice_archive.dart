import 'dart:io';

import 'package:archive/archive_io.dart';
import 'package:crypto/crypto.dart';
import 'package:path/path.dart' as p;
import 'package:xta/speech/offline_voice_catalog.dart';

/// The downloaded archive is not the one the catalog pinned.
class VoiceChecksumMismatch implements Exception {
  const VoiceChecksumMismatch();
}

/// The archive is damaged, unsafe, or lacks a file the voice needs.
class VoiceArchiveInvalid implements Exception {
  final String reason;
  const VoiceArchiveInvalid(this.reason);

  @override
  String toString() => 'VoiceArchiveInvalid: $reason';
}

/// Where [voice] lives once installed under [root].
String voiceDirectory(String root, OfflineVoice voice) => p.join(root, voice.id);

/// Verifies [archivePath] and unpacks it to `<root>/<voice id>`.
///
/// Synchronous and CPU-bound, so it runs in a background isolate. Unpacks into
/// a staging folder and renames it into place only when every file the voice
/// needs is there, so a half-installed voice never looks installed.
void installVoiceArchive(String archivePath, OfflineVoice voice, String root) {
  final expected = voice.sha256;
  if (expected != null && sha256OfFile(archivePath) != expected) {
    throw const VoiceChecksumMismatch();
  }

  final staging = Directory(p.join(root, '.${voice.id}.partial'));
  final tar = File(p.join(root, '.${voice.id}.tar'));
  try {
    _recreate(staging);
    _decompress(archivePath, tar.path);
    _unpackTar(tar.path, voice.archiveRoot, staging.path);
    _requireFiles(staging.path, voice.requiredFiles);
    final target = Directory(voiceDirectory(root, voice));
    if (target.existsSync()) target.deleteSync(recursive: true);
    staging.renameSync(target.path);
  } finally {
    if (tar.existsSync()) tar.deleteSync();
    if (staging.existsSync()) staging.deleteSync(recursive: true);
  }
}

/// SHA-256 of a file as lower-case hex, read in pieces.
String sha256OfFile(String path) {
  final sink = _DigestSink();
  final input = sha256.startChunkedConversion(sink);
  final file = File(path).openSync();
  try {
    for (var chunk = file.readSync(1 << 20); chunk.isNotEmpty; chunk = file.readSync(1 << 20)) {
      input.add(chunk);
    }
  } finally {
    file.closeSync();
  }
  input.close();
  return sink.value.toString();
}

class _DigestSink implements Sink<Digest> {
  late Digest value;

  @override
  void add(Digest data) => value = data;

  @override
  void close() {}
}

void _recreate(Directory directory) {
  if (directory.existsSync()) directory.deleteSync(recursive: true);
  directory.createSync(recursive: true);
}

void _decompress(String archivePath, String tarPath) {
  final input = InputFileStream(archivePath);
  final output = OutputFileStream(tarPath);
  try {
    final ok = BZip2Decoder().decodeStream(input, output, verify: true);
    if (!ok) throw const VoiceArchiveInvalid('bzip2');
  } on VoiceArchiveInvalid {
    rethrow;
  } catch (_) {
    throw const VoiceArchiveInvalid('bzip2');
  } finally {
    input.closeSync();
    output.closeSync();
  }
}

void _unpackTar(String tarPath, String archiveRoot, String destination) {
  final input = InputFileStream(tarPath);
  try {
    final archive = TarDecoder().decodeStream(input);
    for (final entry in archive) {
      _extractEntry(entry, archiveRoot, destination);
    }
  } on VoiceArchiveInvalid {
    rethrow;
  } catch (_) {
    throw const VoiceArchiveInvalid('tar');
  } finally {
    input.closeSync();
  }
}

void _extractEntry(ArchiveFile entry, String archiveRoot, String destination) {
  final relative = voiceEntryPath(entry.name, archiveRoot);
  if (relative == null) throw VoiceArchiveInvalid('path ${entry.name}');
  if (relative.isEmpty) return;
  if (entry.isSymbolicLink) throw VoiceArchiveInvalid('link ${entry.name}');

  final target = p.join(destination, relative);
  if (entry.isDirectory) {
    Directory(target).createSync(recursive: true);
    return;
  }
  Directory(p.dirname(target)).createSync(recursive: true);
  final output = OutputFileStream(target);
  try {
    entry.writeContent(output);
  } finally {
    output.closeSync();
  }
}

/// [name] relative to [archiveRoot]: empty for the root folder itself, null
/// for anything outside it or trying to climb out (`..`, absolute paths).
String? voiceEntryPath(String name, String archiveRoot) {
  final normal = p.posix.normalize(name.replaceAll('\\', '/'));
  if (p.posix.isAbsolute(normal) || normal.split('/').contains('..')) {
    return null;
  }
  if (normal == archiveRoot) return '';
  if (!p.posix.isWithin(archiveRoot, normal)) return null;
  return p.posix.relative(normal, from: archiveRoot);
}

void _requireFiles(String directory, List<String> files) {
  for (final file in files) {
    final candidate = File(p.join(directory, file));
    if (!candidate.existsSync() || candidate.lengthSync() == 0) {
      throw VoiceArchiveInvalid('missing $file');
    }
  }
}

/// True when every file [voice] needs is in [directory].
bool voiceFilesPresent(String directory, OfflineVoice voice) =>
    voice.requiredFiles.every((file) => File(p.join(directory, file)).existsSync());

/// Bytes the files under [directory] take.
int directoryBytes(Directory directory) {
  if (!directory.existsSync()) return 0;
  return directory
      .listSync(recursive: true, followLinks: false)
      .whereType<File>()
      .fold(0, (sum, file) => sum + file.lengthSync());
}
