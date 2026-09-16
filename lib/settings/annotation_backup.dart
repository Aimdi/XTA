import 'package:xta/archive/archive_notes.dart';
import 'package:xta/utils/local_json_store.dart';

/// Only durable article annotations, never cached profiles or request data.
Map<String, Object?> parseAnnotationBackup(Object? raw) {
  if (raw is! Map) return {};
  return {
    for (final entry in raw.entries)
      if (entry.key is String && (entry.key as String).startsWith('archive-note:') && entry.value is Map)
        entry.key as String: ArchiveAnnotation.parse(entry.value).toJson(),
  };
}

Future<Map<String, Object?>> collectAnnotations({JsonStore? storage}) async =>
    parseAnnotationBackup(await (storage ?? LocalJsonStore.shared).readPrefix('archive-note:'));

/// Import adds missing notes/tags; current edits win conflicting note text.
Future<void> restoreAnnotations(Map<String, Object?>? annotations, {JsonStore? storage}) async {
  final store = storage ?? LocalJsonStore.shared;
  for (final entry in parseAnnotationBackup(annotations).entries) {
    final incoming = ArchiveAnnotation.parse(entry.value);
    final current = ArchiveAnnotation.parse(await store.read(entry.key));
    await store.write(
      entry.key,
      ArchiveAnnotation(
        highlights: {...incoming.highlights, ...current.highlights},
        tags: {...incoming.tags, ...current.tags},
        extracted: current.extracted.isNotEmpty ? current.extracted : incoming.extracted,
      ).toJson(),
    );
  }
}
