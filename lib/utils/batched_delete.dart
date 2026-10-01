import 'package:sqflite/sqflite.dart';

/// The pause between batches. Android's sqflite runs every statement of every connection on one worker thread, so
/// a delete that never yields keeps each read in the app waiting until it is done.
const Duration batchedDeleteYield = Duration(milliseconds: 50);

/// Deletes the rows of [table] matching [where] a few at a time, so a large purge never holds the single database
/// worker for more than one short statement. Returns the number of rows deleted.
Future<int> deleteRowsInBatches(
  DatabaseExecutor database,
  String table, {
  required String where,
  List<Object?> arguments = const [],
  int batchSize = 20,
}) async {
  var total = 0;
  while (true) {
    final deleted = await database.rawDelete(
      'DELETE FROM $table WHERE rowid IN (SELECT rowid FROM $table WHERE $where LIMIT ?)',
      [...arguments, batchSize],
    );
    total += deleted;
    if (deleted < batchSize) return total;
    await Future<void>.delayed(batchedDeleteYield);
  }
}
