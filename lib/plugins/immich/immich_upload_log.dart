import 'package:logging/logging.dart';
import 'package:sqflite/sqflite.dart' show ConflictAlgorithm;
import 'package:xta/database/repository.dart';

final _log = Logger('ImmichUploadLog');

/// What has already been sent to Immich.
///
/// Immich rejects a second copy of the same bytes by checksum, so this is not
/// what keeps the library clean — it is what keeps the phone from uploading a
/// file again to be told so. Re-filing a post between folders is common, and a
/// video is not a cheap thing to send twice.
class ImmichUploadLog {
  /// The media ids of [ids] that have not been uploaded yet.
  Future<Set<String>> pending(Iterable<String> ids) async {
    final wanted = ids.toSet();
    if (wanted.isEmpty) {
      return const {};
    }

    try {
      final database = await Repository.readOnly();
      final placeholders = List.filled(wanted.length, '?').join(',');
      final rows = await database.query(
        tableImmichUpload,
        columns: ['media_id'],
        where: 'media_id IN ($placeholders)',
        whereArgs: wanted.toList(),
      );
      final already = rows.map((r) => r['media_id'] as String).toSet();
      return wanted.difference(already);
    } catch (e) {
      // A table that is not there yet, or a database busy elsewhere, must not
      // stop an upload — at worst Immich answers "duplicate".
      _log.warning('Could not read the Immich upload log: $e');
      return wanted;
    }
  }

  /// Records that [mediaId] is on the server, so it is not sent again.
  Future<void> record(String mediaId, {String? assetId}) async {
    try {
      final database = await Repository.writable();
      await database.insert(
        tableImmichUpload,
        {'media_id': mediaId, 'asset_id': assetId},
        conflictAlgorithm: ConflictAlgorithm.replace,
      );
    } catch (e) {
      _log.warning('Could not record an Immich upload: $e');
    }
  }
}
