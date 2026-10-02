import 'package:sqflite/sqflite.dart';
import 'package:xta/database/repository.dart';

/// What the database looked like when the app started: size, chunk rows and the indexes on the chunk table.
/// Collected once after the schema work so a report can say it even when the database no longer answers.
class DatabaseFacts {
  static String summary = 'not collected yet';

  static Future<void> collect(Database database, {bool feedCacheTruncated = false}) async {
    try {
      summary = await _describe(database, feedCacheTruncated: feedCacheTruncated);
    } catch (error) {
      summary = 'failed (${error.runtimeType})';
    }
  }

  static Future<String> _describe(Database database, {required bool feedCacheTruncated}) async {
    Future<int> scalar(String sql) async => (await database.rawQuery(sql)).first.values.first as int? ?? 0;
    final bytes = await scalar('PRAGMA page_size') * await scalar('PRAGMA page_count');
    final chunkRows = await scalar('SELECT COUNT(*) FROM $tableFeedGroupChunk');
    final indexes = await database.rawQuery(
      "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = ? ORDER BY name",
      [tableFeedGroupChunk],
    );
    final names = indexes.map((row) => row['name']).join(', ');
    return 'file ${bytes ~/ 1048576} MB, $chunkRows chunk rows, chunk indexes: ${names.isEmpty ? 'none' : names}'
        '${feedCacheTruncated ? ', feed cache emptied at launch' : ''}';
  }
}
