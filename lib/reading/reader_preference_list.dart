import 'dart:convert';
import 'dart:math';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/reading/reader_preference_writes.dart';

const readerPreferenceListMaxChars = 200000;

/// A fresh id for an item a reader tool saves.
String newReaderItemId() =>
    '${DateTime.now().microsecondsSinceEpoch.toRadixString(36)}${Random().nextInt(1 << 20).toRadixString(36)}';

/// The items of a versioned JSON list kept in one preference. Malformed, repeated and surplus entries are dropped,
/// and an unreadable or oversized value reads as empty.
List<T> readReaderPreferenceList<T extends Object>(
  BasePrefService prefs,
  String key,
  String field, {
  required int max,
  required T? Function(Object? raw) decode,
  required String Function(T item) idOf,
}) {
  try {
    final raw = prefs.get<String>(key);
    if (raw == null || raw.length > readerPreferenceListMaxChars) return const [];
    final json = jsonDecode(raw);
    if (json is! Map || json['version'] != 1 || json[field] is! List) return const [];
    final ids = <String>{};
    return List.unmodifiable((json[field] as List).map(decode).nonNulls.where((item) => ids.add(idOf(item))).take(max));
  } catch (_) {
    return const [];
  }
}

/// A reader tool's list of items in one preference. Changes run one at a time from the latest saved list, and a
/// change reports false, keeping the saved list, when it is refused or cannot be written.
abstract class ReaderPreferenceListStore<T extends Object> extends Store<List<T>> {
  final BasePrefService prefs;
  final String preferenceKey;
  final String field;
  bool _closed = false;

  ReaderPreferenceListStore(this.prefs, this.preferenceKey, this.field, List<T> saved) : super(saved);

  Map<String, Object?> encodeItem(T item);

  /// Saves what [edit] makes of the saved list; [edit] returns null to refuse the change.
  Future<bool> modify(List<T>? Function(List<T> items) edit) {
    if (_closed) return Future.value(false);
    return ReaderPreferenceWrites.enqueue(prefs, () async {
      if (_closed) return false;
      final next = edit(state);
      if (next == null) return false;
      final payload = jsonEncode({
        'version': 1,
        field: [for (final item in next) encodeItem(item)],
      });
      if (!await ReaderPreferenceWrites.putString(prefs, preferenceKey, payload) || _closed) return false;
      update(List.unmodifiable(next));
      return true;
    });
  }

  @override
  Future<void> destroy() async {
    _closed = true;
    await ReaderPreferenceWrites.drain(prefs);
    await super.destroy();
  }
}
