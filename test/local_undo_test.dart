import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/utils/local_undo.dart';

void main() {
  late Database db;
  setUp(() async {
    sqfliteFfiInit();
    db = await databaseFactoryFfi.openDatabase(inMemoryDatabasePath);
    await db.execute('CREATE TABLE people (id TEXT PRIMARY KEY, name TEXT, in_feed INTEGER)');
    await db.execute('CREATE TABLE $tableSubscriptionGroup (id TEXT PRIMARY KEY)');
    await db.execute('CREATE TABLE memberships (group_id TEXT, profile_id TEXT, PRIMARY KEY(group_id, profile_id))');
    await db.insert('people', {'id': 'a', 'name': 'Original name', 'in_feed': 0});
    await db.insert(tableSubscriptionGroup, {'id': 'g'});
    await db.insert('memberships', {'group_id': 'g', 'profile_id': 'a'});
  });
  tearDown(() => db.close());
  Future<PendingUndo?> remove() => changeWithUndo(
    db,
    [
      const UndoSlice('people', 'id = ?', ['a']),
      const UndoSlice('memberships', 'profile_id = ?', ['a']),
    ],
    (txn) async {
      await txn.delete('people', where: 'id = ?', whereArgs: ['a']);
      await txn.delete('memberships', where: 'profile_id = ?', whereArgs: ['a']);
    },
  );
  test('undo restores exact subscription metadata and memberships once', () async {
    final undo = (await remove())!;
    expect(await undo.restore(), isTrue);
    expect(await db.query('people'), [
      {'id': 'a', 'name': 'Original name', 'in_feed': 0},
    ]);
    expect(await db.query('memberships'), [
      {'group_id': 'g', 'profile_id': 'a'},
    ]);
    expect(await undo.restore(), isFalse);
  });
  test('undo refuses to overwrite a subsequent refollow', () async {
    final undo = (await remove())!;
    await db.insert('people', {'id': 'a', 'name': 'New name', 'in_feed': 1});
    expect(await undo.restore(), isFalse);
    expect((await db.query('people')).single['name'], 'New name');
  });
  test('undo does not resurrect deleted groups or disturb unrelated people', () async {
    final undo = (await remove())!;
    await db.delete(tableSubscriptionGroup);
    await db.insert('people', {'id': 'b', 'name': 'Unrelated', 'in_feed': 1});
    expect(await undo.restore(), isTrue);
    expect(await db.query('memberships'), isEmpty);
    expect(await db.query('people'), hasLength(2));
  });
  test('moving between groups can be undone without changing other members', () async {
    await db.insert(tableSubscriptionGroup, {'id': 'h'});
    final undo = await changeWithUndo(
      db,
      [
        const UndoSlice('memberships', 'profile_id = ?', ['a']),
      ],
      (txn) async {
        await txn.update('memberships', {'group_id': 'h'}, where: 'profile_id = ?', whereArgs: ['a']);
      },
    );
    await db.insert('memberships', {'group_id': 'h', 'profile_id': 'b'});
    expect(await undo!.restore(), isTrue);
    expect(
      await db.query('memberships'),
      containsAll([
        {'group_id': 'g', 'profile_id': 'a'},
        {'group_id': 'h', 'profile_id': 'b'},
      ]),
    );
  });
}
