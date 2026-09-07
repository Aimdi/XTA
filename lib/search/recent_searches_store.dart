import 'dart:convert';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';

const recentSearchesPreference = 'search.recent_by_network.v1';

class RecentSearchesStore extends Store<Map<String, List<String>>> {
  final BasePrefService prefs;
  Future<void> _writes = Future.value();
  RecentSearchesStore(this.prefs) : super(const {}) { update(_read()); }

  Map<String, List<String>> _read() {
    try {
      if (!prefs.getKeys().contains(recentSearchesPreference)) return {};
      final raw = prefs.get<String>(recentSearchesPreference);
      if (raw == null || raw.length > 100000) return {};
      final decoded = jsonDecode(raw);
      if (decoded is! Map) return {};
      return {for (final entry in decoded.entries.take(24))
        if (entry.key is String && entry.value is List)
          entry.key as String: (entry.value as List).whereType<String>().where((q) => q.isNotEmpty && q.length <= 200).take(10).toList()};
    } catch (_) { return {}; }
  }

  Future<void> remember(String scope, String value) {
    final query = value.trim();
    if (query.isEmpty || query.length > 200) return Future.value();
    return _change(scope, (previous) => [query,
      ...previous.where((old) => old.toLowerCase() != query.toLowerCase())].take(10).toList());
  }

  Future<void> remove(String scope, String query) => _change(scope,
    (previous) => previous.where((old) => old != query).toList());

  Future<void> _change(String scope, List<String> Function(List<String>) apply) {
    _writes = _writes.then((_) async {
      final current = {...state, ..._read()};
      final next = {...current, scope: apply(current[scope] ?? const [])};
      update(next);
      await prefs.set(recentSearchesPreference, jsonEncode(next));
    }).catchError((Object _) {});
    return _writes;
  }

  @override
  Future<void> destroy() async { await _writes; await super.destroy(); }
}
