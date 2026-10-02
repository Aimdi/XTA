import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:xta/database/traced_database.dart';
import 'package:xta/utils/read_activity.dart';

void main() {
  setUpAll(() {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
  });

  test('statements are described by verb and table only', () {
    expect(TracedDatabase.describe('SELECT * FROM accounts WHERE id = ?'), 'SELECT accounts');
    expect(
      TracedDatabase.describe("DELETE FROM feed_group_chunk WHERE rowid IN (SELECT rowid FROM x LIMIT ?)"),
      'DELETE feed_group_chunk',
    );
    expect(TracedDatabase.describe('INSERT OR REPLACE INTO timeline_cache (k) VALUES (?)'), 'INSERT timeline_cache');
    expect(TracedDatabase.describe('UPDATE subscription SET name = ? WHERE id = ?'), 'UPDATE subscription');
    expect(TracedDatabase.describe('CREATE INDEX IF NOT EXISTS idx_a ON t (c)'), 'CREATE idx_a');
    expect(TracedDatabase.describe('PRAGMA page_count'), 'PRAGMA page_count');
    expect(TracedDatabase.describe('BEGIN IMMEDIATE'), 'BEGIN');
  });

  test('a wrapped connection runs statements and leaves quick ones out of the log', () async {
    final inner = await openDatabase(inMemoryDatabasePath);
    final log = ReadActivityLog.shared;
    final before = log.snapshot().length;
    final database = TracedDatabase(inner, 'rw');
    await database.execute('CREATE TABLE t (id INTEGER PRIMARY KEY, v TEXT)');
    await database.insert('t', {'v': 'a'});
    expect(await database.query('t'), hasLength(1));
    expect((await database.rawQuery('SELECT COUNT(*) AS n FROM t')).single['n'], 1);
    expect(await database.update('t', {'v': 'b'}, where: 'id = ?', whereArgs: [1]), 1);
    expect(await database.delete('t'), 1);
    expect(await database.transaction((txn) => txn.insert('t', {'v': 'c'})), isPositive);
    expect(log.snapshot().length, before);
    expect(database.stalled, isFalse);
    await database.close();
  });
}
