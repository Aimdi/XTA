import 'package:xta/utils/local_json_store.dart';

class MemoryJsonStore implements JsonStore {
  final values = <String, Object?>{};
  @override
  Future<Object?> read(String key) async => values[key];
  @override
  Future<void> write(String key, Object? value) async {
    values[key] = value;
  }

  @override
  Future<void> remove(String key) async {
    values.remove(key);
  }

  @override
  Future<Map<String, Object?>> readPrefix(String prefix) async => {
    for (final entry in values.entries)
      if (entry.key.startsWith(prefix)) entry.key: entry.value,
  };
}
