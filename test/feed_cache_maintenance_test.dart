import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/database_facts.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/group/feed_cache.dart';

/// One chunk row holds a page of tweet JSON. Every load appended rows that nothing read back, and the weekly
/// cleanup then deleted a week of them in one statement, holding the single database worker for minutes.
void main() {
  setUpAll(() {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
  });

  late Database db;

  setUp(() async {
    final path = '${Directory.systemTemp.path}/xta_chunks_${DateTime.now().microsecondsSinceEpoch}.db';
    final plan = buildMigrationPlan();
    db = await openDatabase(path, version: databaseVersion, onCreate: plan.call, onUpgrade: plan.call);
    addTearDown(() async {
      await db.close();
      final file = File(path);
      if (await file.exists()) await file.delete();
    });
  });

  Future<void> insertRows(String hash, int count) async {
    for (var index = 0; index < count; index++) {
      await db.insert(tableFeedGroupChunk, {
        'cursor_id': index,
        'hash': hash,
        'response': '[]',
        'created_at': '2026-09-${(index + 1).toString().padLeft(2, '0')} 12:00:00',
      });
    }
  }

  Future<List<int>> cursorsOf(String hash) async => [
    for (final row in await db.query(tableFeedGroupChunk, where: 'hash = ?', whereArgs: [hash], orderBy: 'cursor_id'))
      row['cursor_id'] as int,
  ];

  test('pruning keeps only the newest rows a read ever takes', () async {
    await insertRows('a', 12);
    await insertRows('b', 3);

    expect(await pruneChunkRows(db, 'a'), 12 - maxCachedChunkRows);

    expect(await cursorsOf('a'), List.generate(maxCachedChunkRows, (i) => 12 - maxCachedChunkRows + i));
    expect(await cursorsOf('b'), [0, 1, 2]);
  });

  test('batched deletion removes every matching row in several small statements and nothing else', () async {
    await insertRows('a', 11);
    await insertRows('b', 2);

    final deleted = await deleteChunkRowsInBatches(db, where: 'hash = ?', arguments: ['a'], batchSize: 4);

    expect(deleted, 11);
    expect(await cursorsOf('a'), isEmpty);
    expect(await cursorsOf('b'), [0, 1]);
  });

  test('batched deletion by age matches the weekly cleanup predicate', () async {
    await insertRows('a', 5);
    await db.insert(tableFeedGroupChunk, {'cursor_id': 99, 'hash': 'a', 'response': '[]'});

    final deleted = await deleteChunkRowsInBatches(db, where: "created_at <= date('now', '-7 day')", batchSize: 2);

    expect(deleted, 5);
    expect(await cursorsOf('a'), [99]);
  });

  test('the launch bound leaves a small cache alone', () async {
    await insertRows('a', 6);

    expect(await Repository.boundFeedCache(db, maxRows: 10), isFalse);
    expect(await cursorsOf('a'), hasLength(6));
  });

  test('the launch bound empties an oversized cache and restores its indexes', () async {
    await insertRows('a', 12);
    await db.execute('DROP INDEX IF EXISTS idx_feed_group_chunk_hash');

    expect(await Repository.boundFeedCache(db, maxRows: 10), isTrue);

    expect(await cursorsOf('a'), isEmpty);
    final indexes = await db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = ?", [
      tableFeedGroupChunk,
    ]);
    expect(indexes.map((row) => row['name']), contains('idx_feed_group_chunk_hash'));
  });

  test('database facts name the size, the chunk rows and the chunk indexes', () async {
    await insertRows('a', 3);

    await DatabaseFacts.collect(db, feedCacheTruncated: true);

    expect(DatabaseFacts.summary, contains('3 chunk rows'));
    expect(DatabaseFacts.summary, contains('idx_feed_group_chunk_created'));
    expect(DatabaseFacts.summary, contains('emptied at launch'));
  });
}
