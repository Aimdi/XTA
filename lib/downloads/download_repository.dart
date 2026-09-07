import 'dart:convert';
import 'dart:io';

import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:xta/downloads/download_entry.dart';

abstract interface class DownloadHistory {
  Future<List<DownloadEntry>> read();
  Future<void> write(List<DownloadEntry> entries);
}

class FileDownloadHistory implements DownloadHistory {
  final Future<Directory> Function() directory;
  FileDownloadHistory({Future<Directory> Function()? directory})
    : directory =
          directory ?? (() async => Directory(p.join((await getApplicationSupportDirectory()).path, 'downloads')));

  Future<File> _file() async {
    final root = await directory();
    await root.create(recursive: true);
    return File(p.join(root.path, 'history-v1.json'));
  }

  @override
  Future<List<DownloadEntry>> read() async {
    final file = await _file();
    if (!await file.exists()) return [];
    if (await file.length() > 2000000) return [];
    try {
      final value = jsonDecode(await file.readAsString());
      if (value is! Map || value['version'] != 1 || value['entries'] is! List) return [];
      final ids = <String>{};
      return (value['entries'] as List)
          .take(200)
          .map(DownloadEntry.fromJson)
          .whereType<DownloadEntry>()
          .where((entry) => ids.add(entry.id))
          .toList();
    } on FormatException {
      return [];
    }
  }

  @override
  Future<void> write(List<DownloadEntry> entries) async {
    final file = await _file();
    final encoded = jsonEncode({'version': 1, 'entries': entries.take(200).map((entry) => entry.toJson()).toList()});
    if (utf8.encode(encoded).length > 2000000) throw const FileSystemException('Download history exceeds limit');
    final temporary = File('${file.path}.tmp');
    await temporary.writeAsString(encoded, flush: true);
    await temporary.rename(file.path);
  }
}
