import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_file_dialog/flutter_file_dialog.dart';
import 'package:http/http.dart' as http;
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:xta/downloads/download_entry.dart';
import 'package:xta/utils/download_directory.dart';

class DownloadCancelled implements Exception {
  const DownloadCancelled();
}

class DownloadCancellation {
  bool cancelled = false;
  final List<void Function()> _listeners = [];
  void check() {
    if (cancelled) throw const DownloadCancelled();
  }

  void onCancel(void Function() callback) {
    if (cancelled) {
      callback();
    } else {
      _listeners.add(callback);
    }
  }

  void cancel() {
    if (cancelled) return;
    cancelled = true;
    for (final listener in _listeners) {
      listener();
    }
    _listeners.clear();
  }
}

typedef DownloadProgress = void Function(int received, int? total);
typedef DownloadPhase = void Function(DownloadStatus status);
typedef DownloadRunner =
    Future<String?> Function(
      DownloadEntry entry,
      DownloadCancellation cancellation,
      DownloadProgress progress,
      DownloadPhase phase,
    );
typedef SaveStagedDownload =
    Future<String?> Function(DownloadEntry entry, File file, DownloadCancellation cancellation);

class DownloadTransfer {
  final http.Client Function() clientFactory;
  final Future<Directory> Function() temporaryDirectory;
  final SaveStagedDownload save;

  DownloadTransfer({
    http.Client Function()? clientFactory,
    Future<Directory> Function()? temporaryDirectory,
    SaveStagedDownload? save,
  }) : clientFactory = clientFactory ?? http.Client.new,
       temporaryDirectory = temporaryDirectory ?? getApplicationSupportDirectory,
       save = save ?? _save;

  static Future<void> clearInterruptedFiles() async {
    final root = await getApplicationSupportDirectory();
    final directory = Directory(p.join(root.path, 'xta-download-staging'));
    if (!await directory.exists()) return;
    final cutoff = DateTime.now().subtract(const Duration(days: 7));
    await for (final file in directory.list()) {
      if (file is File && (await file.lastModified()).isBefore(cutoff)) await file.delete();
    }
  }

  static Future<void> discardEntry(DownloadEntry entry) async {
    final root = await getApplicationSupportDirectory();
    final path = p.join(root.path, 'xta-download-staging', '${safeDownloadName(entry.id)}.part');
    for (final file in [File(path), File('$path.json'), File('$path.json.tmp')]) {
      if (await file.exists()) await file.delete();
    }
  }

  Future<String?> call(
    DownloadEntry entry,
    DownloadCancellation cancellation,
    DownloadProgress progress,
    DownloadPhase phase,
  ) async {
    final root = await temporaryDirectory();
    cancellation.check();
    final directory = await Directory(p.join(root.path, 'xta-download-staging')).create(recursive: true);
    final file = File(p.join(directory.path, '${safeDownloadName(entry.id)}.part'));
    final metadata = File('${file.path}.json');
    final client = clientFactory();
    cancellation.onCancel(client.close);
    var finished = false;
    try {
      await _receive(client, entry.uri, file, metadata, cancellation, progress);
      cancellation.check();
      phase(entry.treeUri == null ? DownloadStatus.choosingLocation : DownloadStatus.saving);
      final destination = await save(entry, file, cancellation);
      cancellation.check();
      finished = true;
      return destination;
    } finally {
      client.close();
      if (finished || cancellation.cancelled || await _validator(entry.uri, file, metadata) == null) {
        await _discard(file, metadata);
      }
    }
  }

  Future<void> _discard(File file, File metadata) async {
    for (final item in [file, metadata, File('${metadata.path}.tmp')]) {
      if (await item.exists()) await item.delete();
    }
  }

  String? _strongEtag(String? value) => value != null && RegExp(r'^"[^"\r\n]*"$').hasMatch(value) ? value : null;

  Future<String?> _validator(Uri uri, File file, File metadata) async {
    try {
      final data = jsonDecode(await metadata.readAsString());
      if (data is Map && data['url'] == uri.toString() && await file.exists()) {
        return _strongEtag(data['etag'] is String ? data['etag'] as String : null);
      }
    } catch (_) {}
    return null;
  }

  Future<void> _receive(
    http.Client client,
    Uri uri,
    File file,
    File metadata,
    DownloadCancellation cancellation,
    DownloadProgress progress,
  ) async {
    var validator = await _validator(uri, file, metadata);
    var offset = validator == null ? 0 : await file.length();
    if (offset == 0) await _discard(file, metadata);
    for (var attempt = 0; attempt < 2; attempt++) {
      cancellation.check();
      final request = http.Request('GET', uri)..headers['accept-encoding'] = 'identity';
      if (offset > 0) request.headers.addAll({'range': 'bytes=$offset-', 'if-range': validator!});
      final response = await client.send(request).timeout(const Duration(seconds: 45));
      cancellation.check();
      if (response.statusCode == 416 && offset > 0) {
        await response.stream.listen(null).cancel();
        await _discard(file, metadata);
        offset = 0;
        validator = null;
        continue;
      }
      if (response.statusCode != 200 && response.statusCode != 206) {
        await response.stream.listen(null).cancel();
        throw HttpException('HTTP ${response.statusCode}', uri: uri);
      }
      var total = response.contentLength;
      int? expectedEnd;
      final tag = _strongEtag(response.headers['etag']);
      if (response.statusCode == 200) {
        offset = 0; // Range was ignored or If-Range detected a changed file.
      } else {
        final range = RegExp(r'^bytes (\d+)-(\d+)/(\d+)$').firstMatch(response.headers['content-range'] ?? '');
        if (range == null ||
            int.parse(range[1]!) != offset ||
            int.parse(range[2]!) < offset ||
            int.parse(range[2]!) >= int.parse(range[3]!) ||
            (total != null && total != int.parse(range[2]!) - offset + 1) ||
            (offset > 0 && response.headers.containsKey('etag') && tag != validator)) {
          await response.stream.listen(null).cancel();
          await _discard(file, metadata);
          throw const HttpException('Invalid resumed download');
        }
        total = int.parse(range[3]!);
        expectedEnd = int.parse(range[2]!) + 1;
      }
      final encoding = response.headers['content-encoding'];
      if (encoding != null && encoding != 'identity') {
        await response.stream.listen(null).cancel();
        await _discard(file, metadata);
        throw const HttpException('Unexpected download encoding');
      }
      final output = await file.open(mode: offset == 0 ? FileMode.write : FileMode.append);
      var received = offset;
      try {
        final pending = File('${metadata.path}.tmp');
        await pending.writeAsString(
          jsonEncode({'url': uri.toString(), 'etag': tag ?? (offset > 0 ? validator : null)}),
          flush: true,
        );
        await pending.rename(metadata.path);
        progress(received, total);
        await for (final chunk in response.stream.timeout(const Duration(seconds: 45))) {
          cancellation.check();
          if ((expectedEnd ?? total) != null && received + chunk.length > (expectedEnd ?? total)!)
            throw const HttpException('Download exceeds declared length');
          await output.writeFrom(chunk);
          received += chunk.length;
          progress(received, total);
        }
        cancellation.check();
        if (total != null && total != received) throw const HttpException('Incomplete download');
        await output.flush();
      } finally {
        await output.close();
      }
      return;
    }
    throw const HttpException('Unable to resume download');
  }

  static Future<String?> _save(DownloadEntry entry, File file, DownloadCancellation cancellation) async {
    cancellation.check();
    if (entry.treeUri == null) {
      return FlutterFileDialog.saveFile(
        params: SaveFileDialogParams(
          fileName: entry.fileName,
          sourceFilePath: file.path,
          mimeTypesFilter: [mimeTypeFor(entry.fileName)],
        ),
      );
    }
    cancellation.onCancel(() {
      unawaited(DownloadDirectory.cancelSave(entry.id).catchError((Object _) {}));
    });
    final result = await DownloadDirectory.saveFile(
      treeUri: entry.treeUri!,
      fileName: entry.fileName,
      sourcePath: file.path,
      operationId: entry.id,
    );
    if (cancellation.cancelled && result != null) {
      await DownloadDirectory.deleteDocument(result);
    }
    cancellation.check();
    if (result == null) throw const FileSystemException('No saved document');
    return result;
  }
}
