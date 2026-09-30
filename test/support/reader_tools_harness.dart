import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:pref/pref.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/utils/local_json_store.dart';

/// A [JsonStore] kept in memory, with optional failing writes.
class MemoryJsonStore implements JsonStore {
  final values = <String, Object?>{};
  bool failWrites = false;
  int writes = 0;

  @override
  Future<Object?> read(String key) async => values[key];

  @override
  Future<void> write(String key, Object? value) async {
    writes++;
    if (failWrites) throw StateError('storage unavailable');
    values[key] = value;
  }

  @override
  Future<void> remove(String key) async => values.remove(key);

  @override
  Future<Map<String, Object?>> readPrefix(String prefix) async => {
    for (final entry in values.entries)
      if (entry.key.startsWith(prefix)) entry.key: entry.value,
  };
}

/// Preferences whose next writes can be refused, thrown or held.
class GatedPrefs extends PrefServiceCache {
  GatedPrefs({super.cache});
  bool rejectWrites = false;
  bool throwWrites = false;
  Completer<void>? gate;
  final written = <String>[];

  @override
  Future<bool> put<T>(String key, T val) async {
    await gate?.future;
    if (rejectWrites) return false;
    if (throwWrites) throw StateError('storage unavailable');
    written.add(key);
    return super.put(key, val);
  }
}

Widget readerToolsApp(BasePrefService prefs, Widget child, {Locale locale = const Locale('en')}) => PrefService(
  service: prefs,
  child: MaterialApp(
    locale: locale,
    localizationsDelegates: const [
      L10n.delegate,
      GlobalMaterialLocalizations.delegate,
      GlobalWidgetsLocalizations.delegate,
      GlobalCupertinoLocalizations.delegate,
    ],
    supportedLocales: L10n.delegate.supportedLocales,
    home: child,
  ),
);
