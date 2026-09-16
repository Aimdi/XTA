import 'dart:convert';
import 'dart:io';
import 'package:crypto/crypto.dart';
import 'package:path_provider/path_provider.dart';

abstract interface class JsonStore {
  Future<Object?> read(String key);
  Future<void> write(String key, Object? value);
  Future<void> remove(String key);
  Future<Map<String, Object?>> readPrefix(String prefix);
}

/// Small local sidecars, independently of the app's database schema.
class LocalJsonStore implements JsonStore {
  static final shared = LocalJsonStore();
  final Future<Directory> Function() directory;
  final _writes = <String, Future<void>>{};
  LocalJsonStore({Future<Directory> Function()? directory}) : directory = directory ?? _directory;
  static Future<Directory> _directory() async =>
      Directory('${(await getApplicationSupportDirectory()).path}/reader-state');
  Future<File> _file(String key) async {
    final dir = await directory();
    await dir.create(recursive: true);
    return File('${dir.path}/${sha256.convert(utf8.encode(key))}.json');
  }

  Future<void> _enqueue(String key, Future<void> Function() work) {
    final prior = _writes[key] ?? Future<void>.value();
    final next = prior.then((_) => work(), onError: (Object _) => work());
    _writes[key] = next;
    next.then((_) {
      if (identical(_writes[key], next)) _writes.remove(key);
    }, onError: (Object _) {});
    return next;
  }

  @override
  Future<Object?> read(String key) async {
    try {
      await _writes[key];
      final value = jsonDecode(await (await _file(key)).readAsString());
      return value is Map && value['key'] == key ? value['value'] : null;
    } catch (_) {
      return null;
    }
  }

  @override
  Future<void> write(String key, Object? value) => _enqueue(key, () async {
    final file = await _file(key);
    final pending = File('${file.path}.tmp');
    await pending.writeAsString(jsonEncode({'key': key, 'value': value}), flush: true);
    await pending.rename(file.path);
  });
  @override
  Future<void> remove(String key) => _enqueue(key, () async {
    final file = await _file(key);
    if (await file.exists()) await file.delete();
  });
  @override
  Future<Map<String, Object?>> readPrefix(String prefix) async {
    final result = <String, Object?>{};
    try {
      await Future.wait(_writes.values.toList());
      final dir = await directory();
      if (!await dir.exists()) return result;
      await for (final file in dir.list()) {
        if (file is! File || !file.path.endsWith('.json')) continue;
        try {
          final row = jsonDecode(await file.readAsString());
          if (row is Map && row['key'] is String && (row['key'] as String).startsWith(prefix)) {
            result[row['key'] as String] = row['value'];
          }
        } catch (_) {
          /* One damaged sidecar does not hide the others. */
        }
      }
    } catch (_) {
      /* Empty on first launch or unavailable storage. */
    }
    return result;
  }
}
