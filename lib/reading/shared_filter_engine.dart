import 'dart:async';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/group/custom_feed_rules.dart';
import 'package:xta/reading/shared_filter_rule.dart';
import 'package:xta/reading/shared_filter_worker.dart';

const sharedFilterMaxText = 32768;
const sharedFilterBatch = 64;
const sharedFilterCacheLimit = 4096;

/// What one text gets under the reader's filters. A pending text waits for its pattern check and is not shown yet.
class SharedFilterVerdict {
  final SharedFilterAction? action;
  final String? reason;
  final bool pending;
  const SharedFilterVerdict._(this.action, this.reason, this.pending);

  static const show = SharedFilterVerdict._(null, null, false);
  static const waiting = SharedFilterVerdict._(null, null, true);

  factory SharedFilterVerdict.matched(SharedFilterRule rule) => SharedFilterVerdict._(rule.action, rule.pattern, false);

  bool get hidden => pending || action == SharedFilterAction.hide;
  bool get folded => !pending && action == SharedFilterAction.fold;
}

class _Compiled {
  final SharedFilterRule rule;
  final MutedTermMatcher? keyword;
  _Compiled(this.rule)
    : keyword = rule.regex ? null : MutedTermMatcher(rule.pattern, caseSensitive: rule.caseSensitive);
}

/// Applies the shared filters. Keywords are checked at once; patterns run on one background worker, and the
/// state (a revision) changes whenever an answer arrives or the rules change, so projections can rebuild.
class SharedFilterEngine extends Store<int> {
  static final _instances = Expando<SharedFilterEngine>();
  final SharedFilterStore rules;
  final SharedFilterRegexEvaluator Function() _evaluatorFactory;
  final DateTime Function() clock;
  SharedFilterRegexEvaluator? _evaluator;
  List<_Compiled> _active = const [];
  DateTime? _nextExpiry;
  final _verdicts = {for (final scope in SharedFilterScope.values) scope: <String, SharedFilterVerdict>{}};
  final _patternMatches = <String, Set<String>>{};
  final _pending = <String>{};
  bool _dispatching = false;
  bool _patternsPaused = false;
  int _generation = 0;
  bool _closed = false;
  late final Disposer _disposeRules;

  SharedFilterEngine(this.rules, {SharedFilterRegexEvaluator Function()? evaluator, DateTime Function()? clock})
    : _evaluatorFactory = evaluator ?? IsolateRegexEvaluator.new,
      clock = clock ?? DateTime.now,
      super(0) {
    _compile();
    _disposeRules = rules.observer(onState: (_) => _rulesChanged());
  }

  static SharedFilterEngine forPrefs(BasePrefService prefs) =>
      _instances[prefs] ??= SharedFilterEngine(SharedFilterStore.forPrefs(prefs));

  /// Whether pattern checks were paused because one ran too long; matching posts are shown meanwhile.
  bool get patternsPaused => _patternsPaused;

  bool get active {
    _expire();
    return _active.isNotEmpty;
  }

  void _compile() {
    final now = clock();
    _active = [
      for (final rule in rules.state)
        if (rule.activeAt(now)) _Compiled(rule),
    ];
    final expiries = [
      for (final compiled in _active)
        if (compiled.rule.until != null) compiled.rule.until!,
    ]..sort();
    _nextExpiry = expiries.firstOrNull;
    for (final cache in _verdicts.values) {
      cache.clear();
    }
  }

  /// Expired rules stop applying at the next check, without a timer.
  void _expire() {
    final next = _nextExpiry;
    if (next != null && !clock().isBefore(next)) _compile();
  }

  void _rulesChanged() {
    ++_generation;
    _patternMatches.clear();
    _pending.clear();
    _patternsPaused = false;
    _compile();
    _publish();
  }

  void _publish() {
    if (!_closed) update(state + 1);
  }

  SharedFilterVerdict verdict(String text, SharedFilterScope scope) {
    _expire();
    if (_active.isEmpty) return SharedFilterVerdict.show;
    final bounded = text.length > sharedFilterMaxText ? text.substring(0, sharedFilterMaxText) : text;
    final cache = _verdicts[scope]!;
    final cached = cache[bounded];
    if (cached != null) return cached;
    final verdict = _evaluate(bounded, scope);
    if (!verdict.pending) {
      if (cache.length >= sharedFilterCacheLimit) cache.remove(cache.keys.first);
      cache[bounded] = verdict;
    }
    return verdict;
  }

  SharedFilterVerdict _evaluate(String text, SharedFilterScope scope) {
    final matches = _patternMatches[text];
    SharedFilterRule? fold;
    var waiting = false;
    for (final compiled in _active) {
      final rule = compiled.rule;
      if (!rule.scopes.contains(scope)) continue;
      final keyword = compiled.keyword;
      final bool matched;
      if (keyword != null) {
        matched = keyword.matches(text);
      } else if (_patternsPaused) {
        matched = false;
      } else if (matches == null) {
        waiting = true;
        continue;
      } else {
        matched = matches.contains(rule.id);
      }
      if (!matched) continue;
      if (rule.action == SharedFilterAction.hide) return SharedFilterVerdict.matched(rule);
      fold ??= rule;
    }
    if (waiting) {
      _request(text);
      return SharedFilterVerdict.waiting;
    }
    return fold == null ? SharedFilterVerdict.show : SharedFilterVerdict.matched(fold);
  }

  void _request(String text) {
    _pending.add(text);
    if (!_dispatching) scheduleMicrotask(_dispatch);
  }

  Future<void> _dispatch() async {
    if (_dispatching || _closed) return;
    _dispatching = true;
    try {
      while (_pending.isNotEmpty && !_closed && !_patternsPaused) {
        await _checkBatch();
      }
    } finally {
      _dispatching = false;
    }
  }

  Future<void> _checkBatch() async {
    final generation = _generation;
    final patterns = [
      for (final compiled in _active)
        if (compiled.keyword == null) compiled.rule,
    ];
    if (patterns.isEmpty) {
      _pending.clear();
      return;
    }
    final batch = _pending.take(sharedFilterBatch).toList();
    try {
      final results = await (_evaluator ??= _evaluatorFactory()).evaluate([
        for (final rule in patterns) SharedFilterPattern(rule.pattern, rule.caseSensitive),
      ], batch);
      if (generation != _generation || _closed) return;
      for (var index = 0; index < batch.length; index++) {
        if (_patternMatches.length >= sharedFilterCacheLimit) _patternMatches.remove(_patternMatches.keys.first);
        _patternMatches[batch[index]] = {for (final matched in results[index]) patterns[matched].id};
        _pending.remove(batch[index]);
      }
    } catch (_) {
      if (generation != _generation || _closed) return;
      // Fail open: a pattern that cannot be checked hides nothing, and the reader is told.
      _patternsPaused = true;
      _pending.clear();
      for (final cache in _verdicts.values) {
        cache.clear();
      }
    }
    _publish();
  }

  /// Checks patterns again after they were paused, with a fresh worker.
  void resumePatterns() {
    if (!_patternsPaused || _closed) return;
    _patternsPaused = false;
    _patternMatches.clear();
    for (final cache in _verdicts.values) {
      cache.clear();
    }
    _publish();
  }

  @override
  Future<void> destroy() async {
    _closed = true;
    if (identical(_instances[rules.prefs], this)) _instances[rules.prefs] = null;
    await _disposeRules();
    await _evaluator?.close();
    await super.destroy();
  }
}
