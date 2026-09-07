import 'dart:async';
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
  void check() { if (cancelled) throw const DownloadCancelled(); }
  void onCancel(void Function() callback) {
    if (cancelled) { callback(); } else { _listeners.add(callback); }
  }
  void cancel() {
    if (cancelled) return;
    cancelled = true;
    for (final listener in _listeners) { listener(); }
    _listeners.clear();
  }
}

typedef DownloadProgress = void Function(int received, int? total);
typedef DownloadPhase = void Function(DownloadStatus status);
typedef DownloadRunner = Future<String?> Function(DownloadEntry entry, DownloadCancellation cancellation,
  DownloadProgress progress, DownloadPhase phase);
typedef SaveStagedDownload = Future<String?> Function(DownloadEntry entry, File file, DownloadCancellation cancellation);

class DownloadTransfer {
  final http.Client Function() clientFactory;
  final Future<Directory> Function() temporaryDirectory;
  final SaveStagedDownload save;

  DownloadTransfer({http.Client Function()? clientFactory, Future<Directory> Function()? temporaryDirectory,
      SaveStagedDownload? save}) : clientFactory = clientFactory ?? http.Client.new,
      temporaryDirectory = temporaryDirectory ?? getTemporaryDirectory, save = save ?? _save;

  static Future<void> clearInterruptedFiles() async {
    final root = await getTemporaryDirectory();
    final directory = Directory(p.join(root.path, 'xta-download-staging'));
    if (await directory.exists()) await directory.delete(recursive: true);
  }

  Future<String?> call(DownloadEntry entry, DownloadCancellation cancellation,
      DownloadProgress progress, DownloadPhase phase) async {
    final root = await temporaryDirectory();
    cancellation.check();
    final directory = await Directory(p.join(root.path, 'xta-download-staging')).create(recursive: true);
    final file = File(p.join(directory.path, '${safeDownloadName(entry.id)}-${safeDownloadName(entry.fileName)}'));
    final client = clientFactory();
    cancellation.onCancel(client.close);
    try {
      await _receive(client, entry.uri, file, cancellation, progress);
      cancellation.check();
      phase(entry.treeUri == null ? DownloadStatus.choosingLocation : DownloadStatus.saving);
      final destination = await save(entry, file, cancellation);
      cancellation.check();
      return destination;
    } finally {
      client.close();
      if (await file.exists()) await file.delete();
    }
  }

  Future<void> _receive(http.Client client, Uri uri, File file, DownloadCancellation cancellation,
      DownloadProgress progress) async {
    final response = await client.send(http.Request('GET', uri)).timeout(const Duration(seconds: 45));
    cancellation.check();
    if (response.statusCode != 200) throw HttpException('HTTP ${response.statusCode}', uri: uri);
    final total = response.contentLength;
    final output = await file.open(mode: FileMode.write);
    var received = 0;
    try {
      progress(received, total);
      await for (final chunk in response.stream.timeout(const Duration(seconds: 45))) {
        cancellation.check();
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
  }

  static Future<String?> _save(DownloadEntry entry, File file, DownloadCancellation cancellation) async {
    cancellation.check();
    if (entry.treeUri == null) {
      return FlutterFileDialog.saveFile(params: SaveFileDialogParams(fileName: entry.fileName, sourceFilePath: file.path,
        mimeTypesFilter: [mimeTypeFor(entry.fileName)]));
    }
    cancellation.onCancel(() { unawaited(DownloadDirectory.cancelSave(entry.id).catchError((Object _) {})); });
    final result = await DownloadDirectory.saveFile(treeUri: entry.treeUri!, fileName: entry.fileName,
      sourcePath: file.path, operationId: entry.id);
    if (cancellation.cancelled && result != null) {
      await DownloadDirectory.deleteDocument(result);
    }
    cancellation.check();
    if (result == null) throw const FileSystemException('No saved document');
    return result;
  }
}
