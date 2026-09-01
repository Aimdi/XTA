import 'dart:io';

import 'package:logging/logging.dart';
import 'package:path_provider/path_provider.dart';
import 'package:xta/database/repository.dart';
import 'package:sqflite/sqflite.dart';

final _log = Logger('PluginStorage');

/// What a plugin has put on this device: how many things it is keeping, and
/// how much of the disk its cache is holding.
///
/// The rows themselves are not measured. They live in one shared SQLite file
/// whose per-table size cannot be read out of it, and inventing a number would
/// be worse than counting what is actually there.
typedef PluginFootprint = ({int items, int bytes});

const PluginFootprint emptyFootprint = (items: 0, bytes: 0);

/// Reads what [tables] and [caches] currently hold.
///
/// Every step is allowed to fail: a table can predate the migration that made
/// it, and a cache directory Android emptied is simply not there.
Future<PluginFootprint> pluginFootprint({
  required List<String> tables,
  required List<String> caches,
}) async {
  final counts = await Future.wait(tables.map(_countRows));
  final sizes = await Future.wait(caches.map(_cacheBytes));

  return (
    items: counts.fold(0, (a, b) => a + b),
    bytes: sizes.fold(0, (a, b) => a + b),
  );
}

/// Deletes everything [tables] and [caches] hold, leaving the tables in place.
///
/// The tables stay because they belong to a migration; dropping one would make
/// the schema disagree with the version the database claims to be at. What the
/// reader means by "uninstall" is that their data is gone, and it is.
Future<void> erasePluginStorage({
  required List<String> tables,
  required List<String> caches,
}) async {
  for (final table in tables) {
    await _clearTable(table);
  }
  for (final cache in caches) {
    await _deleteCache(cache);
  }
}

Future<int> _countRows(String table) async {
  try {
    final database = await Repository.readOnly();
    return Sqflite.firstIntValue(await database.rawQuery('SELECT COUNT(*) FROM $table')) ?? 0;
  } catch (e) {
    _log.info('Nothing to count in $table: $e');
    return 0;
  }
}

Future<void> _clearTable(String table) async {
  try {
    final database = await Repository.writable();
    // Membership first: once the rows are gone there is nothing left to say
    // which group entries were pointing at them.
    await database.rawDelete(
      'DELETE FROM $tableSubscriptionGroupMember WHERE profile_id IN (SELECT id FROM $table)',
    );
    await database.delete(table);
  } catch (e) {
    _log.warning('Unable to clear $table: $e');
  }
}

/// FFCache keeps each named cache in its own directory under the app's
/// temporary directory, so it can be measured and removed without the cache
/// itself being open.
Future<Directory> _cacheDirectory(String name) async =>
    Directory('${(await getTemporaryDirectory()).path}/$name');

Future<int> _cacheBytes(String name) async {
  try {
    final directory = await _cacheDirectory(name);
    if (!await directory.exists()) {
      return 0;
    }

    var total = 0;
    await for (final entity in directory.list(recursive: true, followLinks: false)) {
      if (entity is File) {
        total += await entity.length();
      }
    }
    return total;
  } catch (e) {
    _log.info('Unable to measure the $name cache: $e');
    return 0;
  }
}

Future<void> _deleteCache(String name) async {
  try {
    final directory = await _cacheDirectory(name);
    if (await directory.exists()) {
      await directory.delete(recursive: true);
    }
  } catch (e) {
    _log.warning('Unable to delete the $name cache: $e');
  }
}

/// A size the way a file manager writes one. The units are SI symbols rather
/// than words, so they read the same in every locale.
String formatStorageSize(int bytes) {
  const units = ['B', 'kB', 'MB', 'GB'];

  var value = bytes.toDouble();
  var unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }

  // Bytes are whole things; anything larger has been divided into a fraction
  // worth one decimal.
  return unit == 0 ? '$bytes ${units[0]}' : '${value.toStringAsFixed(1)} ${units[unit]}';
}
