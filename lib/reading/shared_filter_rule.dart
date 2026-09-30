import 'dart:convert';
import 'dart:math';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/reading/reader_preference_writes.dart';

const sharedFilterPreferenceKey = 'reading.filters.v1';
const sharedFilterMaxRules = 100;
const sharedFilterMaxPattern = 256;

enum SharedFilterAction { hide, fold }

/// Where a rule applies: the reader's timelines, or results they searched for.
enum SharedFilterScope { timelines, search }

enum SharedFilterProblem { empty, tooLong, invalidPattern, noScope }

class SharedFilterRule {
  final String id;
  final String pattern;
  final bool regex;
  final bool caseSensitive;
  final SharedFilterAction action;
  final Set<SharedFilterScope> scopes;
  final DateTime? until;
  final bool enabled;

  const SharedFilterRule({
    required this.id,
    required this.pattern,
    this.regex = false,
    this.caseSensitive = false,
    this.action = SharedFilterAction.hide,
    this.scopes = const {SharedFilterScope.timelines, SharedFilterScope.search},
    this.until,
    this.enabled = true,
  });

  bool activeAt(DateTime now) => enabled && (until == null || until!.isAfter(now));

  bool expiredAt(DateTime now) => until != null && !until!.isAfter(now);

  SharedFilterProblem? get problem {
    if (pattern.trim().isEmpty) return SharedFilterProblem.empty;
    if (pattern.length > sharedFilterMaxPattern) return SharedFilterProblem.tooLong;
    if (scopes.isEmpty) return SharedFilterProblem.noScope;
    if (regex) {
      try {
        // Compiling is safe on the UI isolate; only matching can run away.
        RegExp(pattern, caseSensitive: caseSensitive, unicode: true);
      } on FormatException {
        return SharedFilterProblem.invalidPattern;
      }
    }
    return null;
  }

  SharedFilterRule copyWith({
    String? pattern,
    bool? regex,
    bool? caseSensitive,
    SharedFilterAction? action,
    Set<SharedFilterScope>? scopes,
    DateTime? Function()? until,
    bool? enabled,
  }) => SharedFilterRule(
    id: id,
    pattern: pattern ?? this.pattern,
    regex: regex ?? this.regex,
    caseSensitive: caseSensitive ?? this.caseSensitive,
    action: action ?? this.action,
    scopes: scopes ?? this.scopes,
    until: until == null ? this.until : until(),
    enabled: enabled ?? this.enabled,
  );

  Map<String, Object> toJson() => {
    'id': id,
    'pattern': pattern,
    if (regex) 'regex': true,
    if (caseSensitive) 'case': true,
    'action': action.name,
    'scopes': [for (final scope in scopes) scope.name],
    if (until != null) 'until': until!.toUtc().toIso8601String(),
    if (!enabled) 'enabled': false,
  };

  static SharedFilterRule? fromJson(Object? raw) {
    if (raw is! Map || raw['id'] is! String || raw['pattern'] is! String) return null;
    final action = SharedFilterAction.values.where((value) => value.name == raw['action']).firstOrNull;
    final scopes = {
      if (raw['scopes'] is List)
        for (final name in raw['scopes'] as List) ...SharedFilterScope.values.where((scope) => scope.name == name),
    };
    final until = raw['until'] is String ? DateTime.tryParse(raw['until'] as String)?.toLocal() : null;
    final rule = SharedFilterRule(
      id: raw['id'] as String,
      pattern: raw['pattern'] as String,
      regex: raw['regex'] == true,
      caseSensitive: raw['case'] == true,
      action: action ?? SharedFilterAction.hide,
      scopes: scopes,
      until: until,
      enabled: raw['enabled'] != false,
    );
    return rule.problem == null ? rule : null;
  }
}

String newSharedFilterId() =>
    '${DateTime.now().microsecondsSinceEpoch.toRadixString(36)}${Random().nextInt(1 << 20).toRadixString(36)}';

List<SharedFilterRule> _readRules(BasePrefService prefs) {
  try {
    final raw = prefs.get<String>(sharedFilterPreferenceKey);
    if (raw == null || raw.length > 200000) return const [];
    final json = jsonDecode(raw);
    if (json is! Map || json['version'] != 1 || json['rules'] is! List) return const [];
    final ids = <String>{};
    return List.unmodifiable(
      (json['rules'] as List)
          .map(SharedFilterRule.fromJson)
          .nonNulls
          .where((rule) => ids.add(rule.id))
          .take(sharedFilterMaxRules),
    );
  } catch (_) {
    return const [];
  }
}

/// The reader's filters, in order, shared by every source. Saves report failures honestly.
class SharedFilterStore extends Store<List<SharedFilterRule>> {
  static final _instances = Expando<SharedFilterStore>();
  final BasePrefService prefs;
  bool _closed = false;

  SharedFilterStore(this.prefs) : super(_readRules(prefs));

  static SharedFilterStore forPrefs(BasePrefService prefs) => _instances[prefs] ??= SharedFilterStore(prefs);

  Future<bool> save(SharedFilterRule rule) => _modify((rules) {
    if (rule.problem != null) return null;
    final index = rules.indexWhere((old) => old.id == rule.id);
    if (index < 0) return rules.length >= sharedFilterMaxRules ? null : [...rules, rule];
    return [...rules]..[index] = rule;
  });

  Future<bool> remove(String id) => _modify((rules) => rules.where((rule) => rule.id != id).toList());

  Future<bool> setEnabled(String id, bool enabled) =>
      _modify((rules) => [for (final rule in rules) rule.id == id ? rule.copyWith(enabled: enabled) : rule]);

  Future<bool> _modify(List<SharedFilterRule>? Function(List<SharedFilterRule> rules) edit) {
    if (_closed) return Future.value(false);
    return ReaderPreferenceWrites.enqueue(prefs, () async {
      if (_closed) return false;
      final next = edit(state);
      if (next == null) return false;
      final payload = jsonEncode({
        'version': 1,
        'rules': [for (final rule in next) rule.toJson()],
      });
      if (!await ReaderPreferenceWrites.putString(prefs, sharedFilterPreferenceKey, payload) || _closed) return false;
      update(List.unmodifiable(next));
      return true;
    });
  }

  @override
  Future<void> destroy() async {
    _closed = true;
    if (identical(_instances[prefs], this)) _instances[prefs] = null;
    await ReaderPreferenceWrites.drain(prefs);
    await super.destroy();
  }
}
