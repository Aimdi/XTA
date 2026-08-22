import 'dart:convert';

import 'package:pref/pref.dart';

/// A string list from prefs, or null when the key was never set.
///
/// `pref`'s [BasePrefService.getStringList] throws when the stored value is a
/// JSON string or a `List<dynamic>` — both show up after a backup restore.
/// Startup reads several of these keys (home pages, the feed strip, recents)
/// outside `Store.execute`, so a type mismatch used to take the process down.
List<String>? stringListPref(BasePrefService prefs, String key) {
  try {
    final value = prefs.getStringList(key);
    if (value != null) return List<String>.from(value);
  } catch (_) {
    // Fall through to the raw value.
  }
  return stringListFrom(prefs.get<dynamic>(key));
}

List<String>? stringListFrom(Object? raw) {
  if (raw == null) return null;
  if (raw is List) {
    return [
      for (final item in raw)
        if (item is String) item,
    ];
  }
  if (raw is String) {
    if (raw.isEmpty) return const [];
    try {
      final decoded = jsonDecode(raw);
      if (decoded is List) {
        return [
          for (final item in decoded)
            if (item is String) item,
        ];
      }
    } catch (_) {
      return raw
          .split(',')
          .map((e) => e.trim())
          .where((e) => e.isNotEmpty)
          .toList();
    }
  }
  return null;
}
