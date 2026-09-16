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
  final _keys = <String>{};
  final _changedDuringIndex = <String>{};
  final _maintenance = <String, Future<void>>{};
  final _maintenanceDirty = <String>{};
  Future<void>? _index;
  bool _indexed = false;

  Future<void> _ensureIndex() => _index ??= _scanKeys();

  Future<void> _scanKeys() async {
    try {
      final dir = await directory();
      if (!await dir.exists()) {
        _indexed = true;
        return;
      }
      await for (final file in dir.list()) {
        if (file is! File || !file.path.endsWith('.json')) continue;
        try {
          final row = jsonDecode(await file.readAsString());
          if (row is Map && row['key'] is String && !_changedDuringIndex.contains(row['key'])) {
            _keys.add(row['key'] as String);
          }
        } catch (_) {
          /* A damaged sidecar does not hide other records. */
        }
      }
      _indexed = true;
    } catch (_) {
      _index = null;
    } finally {
      _changedDuringIndex.clear();
    }
  }

  Future<void> trimCache(String prefix, int limit) {
    final running = _maintenance[prefix];
    if (running != null) {
      _maintenanceDirty.add(prefix);
      return running;
    }
    late final Future<void> task;
    task = _trimUntilClean(prefix, limit).whenComplete(() {
      if (identical(_maintenance[prefix], task)) _maintenance.remove(prefix);
    });
    _maintenance[prefix] = task;
    return task;
  }

  Future<void> _trimUntilClean(String prefix, int limit) async {
    do {
      _maintenanceDirty.remove(prefix);
      await _trimCache(prefix, limit);
    } while (_maintenanceDirty.remove(prefix));
  }

  Future<void> _trimCache(String prefix, int limit) async {
    await _ensureIndex();
    if (_keys.where((key) => key.startsWith(prefix)).length <= limit) return;
    await _pruneJsonCache(this, prefix, limit);
  }

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
  Future<void> write(String key, Object? value) {
    if (!_indexed) _changedDuringIndex.add(key);
    _keys.add(key);
    return _enqueue(key, () async {
      final file = await _file(key);
      final pending = File('${file.path}.tmp');
      await pending.writeAsString(jsonEncode({'key': key, 'value': value}), flush: true);
      await pending.rename(file.path);
    });
  }

  @override
  Future<void> remove(String key) {
    if (!_indexed) _changedDuringIndex.add(key);
    _keys.remove(key);
    return _enqueue(key, () async {
      final file = await _file(key);
      if (await file.exists()) await file.delete();
    });
  }

  @override
  Future<Map<String, Object?>> readPrefix(String prefix) async {
    await _ensureIndex();
    final keys = _keys.where((key) => key.startsWith(prefix)).toList();
    final values = await Future.wait(keys.map(read));
    return {for (var index = 0; index < keys.length; index++) keys[index]: values[index]};
  }
}

Future<void> pruneJsonCache(JsonStore storage, String prefix, int limit) =>
    storage is LocalJsonStore ? storage.trimCache(prefix, limit) : _pruneJsonCache(storage, prefix, limit);

Future<void> _pruneJsonCache(JsonStore storage, String prefix, int limit) async {
  final records = await storage.readPrefix(prefix);
  if (records.length <= limit) return;
  final keys = records.keys.toList()
    ..sort((a, b) => '${(records[a] as Map?)?['at']}'.compareTo('${(records[b] as Map?)?['at']}'));
  for (final key in keys.take(records.length - limit)) {
    await storage.remove(key);
  }
}
