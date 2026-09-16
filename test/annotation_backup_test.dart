import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/settings/annotation_backup.dart';
import 'package:xta/settings/backup_data.dart';
import 'package:xta/utils/local_json_store.dart';

class MemoryJsonStore implements JsonStore {
  final Map<String, Object?> data = {};
  @override
  Future<Object?> read(String key) async => data[key];
  @override
  Future<void> write(String key, Object? value) async {
    data[key] = value;
  }

  @override
  Future<void> remove(String key) async {
    data.remove(key);
  }

  @override
  Future<Map<String, Object?>> readPrefix(String prefix) async =>
      Map.fromEntries(data.entries.where((e) => e.key.startsWith(prefix)));
}

void main() {
  test('JSON backup restores annotation text, tags and extraction exactly', () async {
    final source = MemoryJsonStore();
    source.data['archive-note:article-1'] = {
      'highlights': {'Quotation': 'My note'},
      'tags': ['science', 'read'],
      'extracted': 'Image text',
    };
    source.data['profile:id:1'] = {'private': 'cache'};
    final encoded = jsonEncode(SettingsData(archiveAnnotations: await collectAnnotations(storage: source)).toJson());
    final decoded = SettingsData.fromJson(jsonDecode(encoded));
    expect(backupCounts(decoded)[BackupCategory.archiveAnnotations], 1);
    expect(encoded, isNot(contains('private')));
    final destination = MemoryJsonStore();
    await restoreAnnotations(decoded.archiveAnnotations, storage: destination);
    expect(destination.data, {'archive-note:article-1': source.data['archive-note:article-1']});
  });
  test('restore preserves current edits and merges additional highlights and tags', () async {
    final destination = MemoryJsonStore();
    destination.data['archive-note:1'] = {
      'highlights': {'quote': 'new edit'},
      'tags': ['current'],
      'extracted': 'current text',
    };
    await restoreAnnotations({
      'archive-note:1': {
        'highlights': {'quote': 'old edit', 'other': 'another note'},
        'tags': ['imported'],
        'extracted': 'old text',
      },
    }, storage: destination);
    final restored = destination.data['archive-note:1'] as Map;
    expect(restored['highlights'], {'quote': 'new edit', 'other': 'another note'});
    expect(restored['tags'], containsAll(['current', 'imported']));
    expect(restored['extracted'], 'current text');
  });
  test('legacy backups and unrelated sidecar keys cannot erase existing data', () async {
    final destination = MemoryJsonStore();
    destination.data['archive-note:1'] = {
      'tags': ['keep'],
    };
    await restoreAnnotations(SettingsData.fromJson({}).archiveAnnotations, storage: destination);
    await restoreAnnotations({
      'profile:id:1': {'user': 'injected'},
      'archive-note:invalid': 7,
    }, storage: destination);
    expect(destination.data, {
      'archive-note:1': {
        'tags': ['keep'],
      },
    });
  });
}
