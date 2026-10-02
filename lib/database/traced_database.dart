import 'dart:async';

import 'package:sqflite/sqflite.dart';
import 'package:xta/utils/read_activity.dart';

/// How long one statement may run before the connection counts as stalled.
const Duration databaseStallThreshold = Duration(seconds: 10);

/// A connection whose every statement is timed into the read log, named by its verb and table only, so a report
/// says which statement the app waited for. [onStall] fires once when a statement outlives [databaseStallThreshold].
class TracedDatabase implements Database {
  final Database inner;
  final String name;
  final void Function(TracedDatabase database)? onStall;
  bool _stalled = false;

  TracedDatabase(this.inner, this.name, {this.onStall});

  bool get stalled => _stalled;

  /// "SELECT subscription", "DELETE feed_group_chunk": identifiers only, never arguments or values.
  static String describe(String sql) {
    final words = sql.trim().split(RegExp(r'\s+'));
    if (words.isEmpty || words.first.isEmpty) return 'SQL';
    final verb = words.first.toUpperCase();
    final anchors = {'FROM', 'INTO', 'UPDATE', 'TABLE', 'INDEX', 'PRAGMA'};
    for (var index = 0; index < words.length - 1; index++) {
      if (!anchors.contains(words[index].toUpperCase())) continue;
      var next = index + 1;
      if (words[next].toUpperCase() == 'IF') {
        next += next + 1 < words.length && words[next + 1].toUpperCase() == 'NOT' ? 3 : 2;
      }
      if (next >= words.length) break;
      final target = words[next].replaceAll(RegExp(r'[^A-Za-z0-9_]'), '');
      if (target.isNotEmpty) return '$verb $target';
    }
    return verb;
  }

  Future<T> _trace<T>(String what, Future<T> Function() run) {
    var done = false;
    final watchdog = Timer(databaseStallThreshold, () {
      if (done || _stalled) return;
      _stalled = true;
      onStall?.call(this);
    });
    return ReadActivityLog.shared.trace('$name ${describe(what)}', run).whenComplete(() {
      done = true;
      watchdog.cancel();
    });
  }

  @override
  Future<void> execute(String sql, [List<Object?>? arguments]) => _trace(sql, () => inner.execute(sql, arguments));

  @override
  Future<int> rawInsert(String sql, [List<Object?>? arguments]) => _trace(sql, () => inner.rawInsert(sql, arguments));

  @override
  Future<int> insert(
    String table,
    Map<String, Object?> values, {
    String? nullColumnHack,
    ConflictAlgorithm? conflictAlgorithm,
  }) => _trace(
    'INSERT INTO $table',
    () => inner.insert(table, values, nullColumnHack: nullColumnHack, conflictAlgorithm: conflictAlgorithm),
  );

  @override
  Future<List<Map<String, Object?>>> query(
    String table, {
    bool? distinct,
    List<String>? columns,
    String? where,
    List<Object?>? whereArgs,
    String? groupBy,
    String? having,
    String? orderBy,
    int? limit,
    int? offset,
  }) => _trace(
    'SELECT FROM $table',
    () => inner.query(
      table,
      distinct: distinct,
      columns: columns,
      where: where,
      whereArgs: whereArgs,
      groupBy: groupBy,
      having: having,
      orderBy: orderBy,
      limit: limit,
      offset: offset,
    ),
  );

  @override
  Future<List<Map<String, Object?>>> rawQuery(String sql, [List<Object?>? arguments]) =>
      _trace(sql, () => inner.rawQuery(sql, arguments));

  @override
  Future<QueryCursor> rawQueryCursor(String sql, List<Object?>? arguments, {int? bufferSize}) =>
      inner.rawQueryCursor(sql, arguments, bufferSize: bufferSize);

  @override
  Future<QueryCursor> queryCursor(
    String table, {
    bool? distinct,
    List<String>? columns,
    String? where,
    List<Object?>? whereArgs,
    String? groupBy,
    String? having,
    String? orderBy,
    int? limit,
    int? offset,
    int? bufferSize,
  }) => inner.queryCursor(
    table,
    distinct: distinct,
    columns: columns,
    where: where,
    whereArgs: whereArgs,
    groupBy: groupBy,
    having: having,
    orderBy: orderBy,
    limit: limit,
    offset: offset,
    bufferSize: bufferSize,
  );

  @override
  Future<int> rawUpdate(String sql, [List<Object?>? arguments]) => _trace(sql, () => inner.rawUpdate(sql, arguments));

  @override
  Future<int> update(
    String table,
    Map<String, Object?> values, {
    String? where,
    List<Object?>? whereArgs,
    ConflictAlgorithm? conflictAlgorithm,
  }) => _trace(
    'UPDATE $table',
    () => inner.update(table, values, where: where, whereArgs: whereArgs, conflictAlgorithm: conflictAlgorithm),
  );

  @override
  Future<int> rawDelete(String sql, [List<Object?>? arguments]) => _trace(sql, () => inner.rawDelete(sql, arguments));

  @override
  Future<int> delete(String table, {String? where, List<Object?>? whereArgs}) =>
      _trace('DELETE FROM $table', () => inner.delete(table, where: where, whereArgs: whereArgs));

  @override
  Batch batch() => inner.batch();

  @override
  Database get database => inner.database;

  @override
  String get path => inner.path;

  @override
  Future<void> close() => inner.close();

  @override
  Future<T> transaction<T>(Future<T> Function(Transaction txn) action, {bool? exclusive}) =>
      _trace('TRANSACTION', () => inner.transaction(action, exclusive: exclusive));

  @override
  Future<T> readTransaction<T>(Future<T> Function(Transaction txn) action) =>
      _trace('READ TRANSACTION', () => inner.readTransaction(action));

  @override
  bool get isOpen => inner.isOpen;

  @override
  // ignore: deprecated_member_use
  Future<T> devInvokeMethod<T>(String method, [Object? arguments]) => inner.devInvokeMethod(method, arguments);

  @override
  // ignore: deprecated_member_use
  Future<T> devInvokeSqlMethod<T>(String method, String sql, [List<Object?>? arguments]) =>
      inner.devInvokeSqlMethod(method, sql, arguments);
}
