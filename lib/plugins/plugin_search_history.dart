import 'dart:convert';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';

const pluginSearchHistoryCap = 20;

List<String> readPluginSearchHistory(BasePrefService prefs, String key) {
  final raw = prefs.get<String>(key) ?? '[]';
  try {
    final decoded = jsonDecode(raw);
    if (decoded is List) {
      return [for (final item in decoded.whereType<String>()) item];
    }
  } catch (_) {}
  return const [];
}

String _sameText(String query) => query;

/// Puts [query] first. [identity] decides which older entries it replaces;
/// by default only the identical text.
Future<void> rememberPluginSearch(
  BasePrefService prefs,
  String key,
  String query, {
  String Function(String query) identity = _sameText,
}) async {
  final value = query.trim();
  if (value.isEmpty) return;
  final id = identity(value);
  final older = readPluginSearchHistory(prefs, key);
  final next = [
    value,
    ...older.where((item) => identity(item) != id),
  ].take(pluginSearchHistoryCap).toList();
  await prefs.set(key, jsonEncode(next));
}

Future<void> forgetPluginSearch(
  BasePrefService prefs,
  String key,
  String query,
) async {
  final history = readPluginSearchHistory(prefs, key);
  await prefs.set(key, jsonEncode([...history.where((item) => item != query)]));
}

Future<void> clearPluginSearchHistory(BasePrefService prefs, String key) async {
  await prefs.set(key, '[]');
}

/// Recent searches kept under [key], for a screen that shows them as they change.
class PluginSearchHistoryStore extends Store<List<String>> {
  final BasePrefService prefs;
  final String key;
  final String Function(String query) identity;

  PluginSearchHistoryStore(this.prefs, this.key, {this.identity = _sameText})
    : super(readPluginSearchHistory(prefs, key));

  void load() => update(readPluginSearchHistory(prefs, key));

  Future<void> remember(String query) async {
    await rememberPluginSearch(prefs, key, query, identity: identity);
    load();
  }

  Future<void> forget(String query) async {
    await forgetPluginSearch(prefs, key, query);
    load();
  }

  Future<void> clear() async {
    await clearPluginSearchHistory(prefs, key);
    load();
  }
}
