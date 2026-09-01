import 'package:flutter/material.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:logging/logging.dart';
import 'package:sqflite/sqflite.dart';

class ImportDataModel extends ChangeNotifier {
  static final log = Logger('HomeModel');

  Future importData(Map<String, List<ToMappable>> data) async {
    var database = await Repository.writable();

    for (var pair in data.entries) {
      await _importTable(database, pair.key, pair.value);
    }
  }

  /// One batch per table rather than one for the whole file: a table a failed
  /// migration never created used to cost the reader every other table in the
  /// backup, since a single rejected insert takes the whole commit with it.
  Future<void> _importTable(Database database, String table, List<ToMappable> rows) async {
    var batch = database.batch();

    for (var row in rows) {
      batch.insert(table, row.toMap(), conflictAlgorithm: ConflictAlgorithm.replace);
    }

    try {
      await batch.commit(noResult: true);
      log.info('Imported ${rows.length} rows into $table');
    } catch (e) {
      log.warning('Could not import into $table: $e');
    }
  }
}
