import 'package:sqflite/sqflite.dart';

/// Reads a plugin subscription table, or nothing when the table is not there.
///
/// Group and subscription reloads ask every [subscriptionSources] table on
/// launch — including `rss_subscription` added in 58. A database whose
/// migration did not run (or failed and was swallowed) used to throw here and
/// take startup down with it.
Future<List<Map<String, Object?>>> querySourceTable(
  DatabaseExecutor database,
  String table, {
  String? sql,
  List<Object?>? arguments,
}) async {
  try {
    return sql == null
        ? await database.query(table)
        : await database.rawQuery(sql, arguments);
  } catch (error) {
    if (!isMissingSourceTable(error, table)) rethrow;
    return const [];
  }
}

/// Writes that need the table, or nothing when it was never created.
Future<T?> mutateSourceTable<T>(
  String table,
  Future<T> Function() action,
) async {
  try {
    return await action();
  } catch (error) {
    if (!isMissingSourceTable(error, table)) rethrow;
    return null;
  }
}

bool isMissingSourceTable(Object error, String table) {
  final text = error.toString().toLowerCase();
  return text.contains('no such table') && text.contains(table.toLowerCase());
}
