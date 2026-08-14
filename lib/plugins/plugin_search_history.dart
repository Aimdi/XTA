import 'dart:convert';

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

Future<void> rememberPluginSearch(
  BasePrefService prefs,
  String key,
  String query,
) async {
  final value = query.trim();
  if (value.isEmpty) return;
  final next = [
    value,
    ...readPluginSearchHistory(prefs, key).where((item) => item != value),
  ].take(pluginSearchHistoryCap).toList();
  await prefs.set(key, jsonEncode(next));
}

Future<void> clearPluginSearchHistory(BasePrefService prefs, String key) async {
  await prefs.set(key, '[]');
}
