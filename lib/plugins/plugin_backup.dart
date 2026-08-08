/// A table a plugin owns, as it appears in a backup file.
///
/// Every plugin already declares the tables it owns so that uninstall and
/// footprint cannot disagree about what belongs to it. The backup ignored that
/// and wrote the same names out again by hand, in three more files — which is
/// how followed stocks, followed Threads accounts, Reddit upvotes and Threads
/// likes each went unbacked-up for a while. Nothing there is on a server; this
/// file was their only copy.
///
/// Declaring the section here means adding a plugin cannot forget the backup.
library;

import 'package:xta/database/entities.dart';
import 'package:xta/settings/backup_category.dart';

class PluginBackupSection {
  /// The key this section has always had in the backup document.
  ///
  /// Load-bearing: a reader's existing file is only importable while the name
  /// stays put, so renaming one is a deliberate break, never a tidy-up.
  final String jsonKey;

  /// Where the rows are restored to.
  final String table;

  /// What the import preview calls these rows.
  final BackupCategory category;

  /// A stored row, read into something that can be written back out.
  final ToMappable Function(Map<String, Object?> row) fromMap;

  const PluginBackupSection({
    required this.jsonKey,
    required this.table,
    required this.category,
    required this.fromMap,
  });
}
