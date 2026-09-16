import 'dart:convert';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:sqflite/sqflite.dart';
import 'package:xta/database/entities.dart';

class PendingUndo {
  final Future<bool> Function() _restore;
  final DateTime expiresAt;
  bool _used = false;
  PendingUndo(this._restore) : expiresAt = DateTime.now().add(const Duration(seconds: 10));
  Future<bool> restore() async {
    if (_used || DateTime.now().isAfter(expiresAt)) return false;
    _used = true;
    return _restore();
  }
}

class UndoStore extends Store<PendingUndo?> {
  static final shared = UndoStore();
  UndoStore() : super(null);
  void offer(PendingUndo? action) {
    if (action != null) update(action, force: true);
  }

  void clear() => update(null, force: true);
}

class UndoSlice {
  final String table;
  final String where;
  final List<Object?> args;
  const UndoSlice(this.table, this.where, this.args);
  Future<List<Map<String, Object?>>> read(DatabaseExecutor db) => db.query(table, where: where, whereArgs: args);
}

String _canonical(List<Map<String, Object?>> rows) => jsonEncode(
  (rows.map((row) {
    final keys = row.keys.toList()..sort();
    return jsonEncode({for (final key in keys) key: row[key]});
  }).toList()..sort()),
);

/// Record and change the selected rows in one transaction. Undo refuses to
/// overwrite a later edit, and never recreates a deleted parent group.
Future<PendingUndo?> changeWithUndo(
  Database db,
  List<UndoSlice> slices,
  Future<void> Function(Transaction) change, {
  Future<void> Function()? afterRestore,
}) async {
  final before = <List<Map<String, Object?>>>[];
  final after = <List<Map<String, Object?>>>[];
  await db.transaction((txn) async {
    for (final slice in slices) {
      before.add(await slice.read(txn));
    }
    await change(txn);
    for (final slice in slices) {
      after.add(await slice.read(txn));
    }
  });
  return undoFromSnapshots(db, slices, before, after, afterRestore: afterRestore);
}

PendingUndo? undoFromSnapshots(
  Database db,
  List<UndoSlice> slices,
  List<List<Map<String, Object?>>> before,
  List<List<Map<String, Object?>>> after, {
  Future<void> Function()? afterRestore,
}) {
  if (List.generate(slices.length, (i) => _canonical(before[i]) == _canonical(after[i])).every((e) => e)) return null;
  return PendingUndo(() async {
    final restored = await db.transaction((txn) async {
      for (var i = 0; i < slices.length; i++) {
        if (_canonical(await slices[i].read(txn)) != _canonical(after[i])) return false;
      }
      for (var i = 0; i < slices.length; i++) {
        final slice = slices[i];
        await txn.delete(slice.table, where: slice.where, whereArgs: slice.args);
        for (final row in before[i]) {
          if (row['group_id'] case final Object group) {
            if ((await txn.query(tableSubscriptionGroup, columns: ['id'], where: 'id = ?', whereArgs: [group])).isEmpty)
              continue;
          }
          await txn.insert(slice.table, row, conflictAlgorithm: ConflictAlgorithm.ignore);
        }
      }
      return true;
    });
    if (restored) await afterRestore?.call();
    return restored;
  });
}
