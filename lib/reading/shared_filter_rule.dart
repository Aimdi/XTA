import 'package:pref/pref.dart';
import 'package:xta/reading/reader_preference_list.dart';

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

/// The reader's filters, in order, shared by every source. Saves report failures honestly.
class SharedFilterStore extends ReaderPreferenceListStore<SharedFilterRule> {
  static final _instances = Expando<SharedFilterStore>();

  SharedFilterStore(BasePrefService prefs)
    : super(
        prefs,
        sharedFilterPreferenceKey,
        'rules',
        readReaderPreferenceList(
          prefs,
          sharedFilterPreferenceKey,
          'rules',
          max: sharedFilterMaxRules,
          decode: SharedFilterRule.fromJson,
          idOf: (rule) => rule.id,
        ),
      );

  static SharedFilterStore forPrefs(BasePrefService prefs) => _instances[prefs] ??= SharedFilterStore(prefs);

  @override
  Map<String, Object?> encodeItem(SharedFilterRule item) => item.toJson();

  Future<bool> save(SharedFilterRule rule) => modify((rules) {
    if (rule.problem != null) return null;
    final index = rules.indexWhere((old) => old.id == rule.id);
    if (index < 0) return rules.length >= sharedFilterMaxRules ? null : [...rules, rule];
    return [...rules]..[index] = rule;
  });

  Future<bool> remove(String id) => modify((rules) => rules.where((rule) => rule.id != id).toList());

  Future<bool> setEnabled(String id, bool enabled) =>
      modify((rules) => [for (final rule in rules) rule.id == id ? rule.copyWith(enabled: enabled) : rule]);

  @override
  Future<void> destroy() async {
    if (identical(_instances[prefs], this)) _instances[prefs] = null;
    await super.destroy();
  }
}
