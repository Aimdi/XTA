import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:xta/database/repository.dart';

/// The 7-day purge bounds how *old* a cached timeline gets, not how many there
/// are. A row is written for every thread opened and every profile visited, and
/// none is read again once the reader has moved on — so a week of ordinary
/// reading left thousands of pages of JSON in the database with nothing to
/// remove them.
Future<void> _seed(Database database, int count) async {
  final batch = database.batch();
  for (var i = 0; i < count; i++) {
    batch.insert(tableTimelineCache, {
      'key': 'thread:$i',
      'response': '{"chains":[]}',
      // Ascending, so the highest i is the newest.
      'created_at': DateTime.utc(2026, 1, 1).add(Duration(minutes: i)).toIso8601String(),
    });
  }
  await batch.commit(noResult: true);
}

Future<List<String>> _keys(Database database) async {
  final rows = await database.query(tableTimelineCache, columns: ['key']);

  return rows.map((row) => row['key'] as String).toList();
}

void main() {
  late Database database;

  setUpAll(() async {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
    final dir = await Directory.systemTemp.createTemp('xta_timeline_trim_test');
    await databaseFactory.setDatabasesPath(dir.path);
    await Repository().migrate();
    database = await Repository.writable();
  });

  setUp(() async {
    await database.delete(tableTimelineCache);
  });

  test('keeps the newest entries and drops the rest', () async {
    await _seed(database, 25);

    await Repository.trimTimelineCache(database, keep: 10);

    final keys = await _keys(database);
    expect(keys, hasLength(10));
    expect(keys, contains('thread:24'), reason: 'the newest must survive');
    expect(keys, isNot(contains('thread:14')), reason: 'the 15th-newest must not');
  });

  test('does nothing when the table is already under the cap', () async {
    await _seed(database, 4);

    await Repository.trimTimelineCache(database, keep: 10);

    expect(await _keys(database), hasLength(4));
  });

  test('an empty table is not a special case', () async {
    await Repository.trimTimelineCache(database, keep: 10);

    expect(await _keys(database), isEmpty);
  });
}
