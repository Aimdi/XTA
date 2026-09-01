import 'package:flutter_triple/flutter_triple.dart';
import 'package:logging/logging.dart';
import 'package:sqflite/sqflite.dart';
import 'package:uuid/uuid.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';

class AntennaModel extends Store<List<Antenna>> {
  static final log = Logger('AntennaModel');

  AntennaModel() : super([]);

  Future<void> listAntennas() async {
    log.info('Listing antennas');

    await execute(() async {
      final database = await Repository.readOnly();
      return (await database.query(
        tableAntenna,
        orderBy: 'created_at DESC',
      )).map(Antenna.fromMap).toList(growable: false);
    });
  }

  Future<Antenna> saveAntenna({
    String? id,
    required String name,
    required List<String> includeTerms,
    List<String> excludeTerms = const [],
    String scope = 'search',
    DateTime? createdAt,
  }) async {
    final database = await Repository.writable();
    Antenna? existing;
    if (id != null) {
      for (final row in state) {
        if (row.id == id) {
          existing = row;
          break;
        }
      }
    }
    final antenna = Antenna(
      id: id ?? const Uuid().v4(),
      name: name.trim(),
      includeTerms: includeTerms,
      excludeTerms: excludeTerms,
      scope: scope == 'following' ? 'following' : 'search',
      createdAt: createdAt ?? existing?.createdAt ?? DateTime.now(),
    );

    await database.insert(tableAntenna, antenna.toMap(), conflictAlgorithm: ConflictAlgorithm.replace);

    final next = id == null ? [antenna, ...state] : state.map((e) => e.id == id ? antenna : e).toList(growable: false);
    update(next, force: true);
    return antenna;
  }

  Future<void> deleteAntenna(String id) async {
    final database = await Repository.writable();
    await database.delete(tableAntenna, where: 'id = ?', whereArgs: [id]);
    update(state.where((e) => e.id != id).toList(growable: false), force: true);
  }
}
