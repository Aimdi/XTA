import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/group/custom_feed_rules.dart';
import 'package:xta/reading/shared_filter_engine.dart';
import 'package:xta/reading/shared_filter_rule.dart';
import 'package:xta/reading/shared_filter_worker.dart';

import 'support/reader_tools_harness.dart';

SharedFilterRule _rule(
  String id,
  String pattern, {
  bool regex = false,
  bool caseSensitive = false,
  SharedFilterAction action = SharedFilterAction.hide,
  Set<SharedFilterScope> scopes = const {SharedFilterScope.timelines, SharedFilterScope.search},
  DateTime? until,
}) => SharedFilterRule(
  id: id,
  pattern: pattern,
  regex: regex,
  caseSensitive: caseSensitive,
  action: action,
  scopes: scopes,
  until: until,
);

/// Answers pattern checks in Dart on this isolate, with a gate to hold them.
class _FakeEvaluator implements SharedFilterRegexEvaluator {
  final batches = <List<String>>[];
  Completer<void>? gate;
  bool fail = false;

  @override
  Future<List<List<int>>> evaluate(List<SharedFilterPattern> patterns, List<String> texts) async {
    batches.add(texts);
    await gate?.future;
    if (fail) throw const SharedFilterWorkerFailure('deadline');
    return [
      for (final text in texts)
        [
          for (var index = 0; index < patterns.length; index++)
            if (RegExp(patterns[index].pattern, caseSensitive: patterns[index].caseSensitive).hasMatch(text)) index,
        ],
    ];
  }

  @override
  Future<void> close() async {}
}

Future<SharedFilterEngine> _engine(List<SharedFilterRule> rules, {_FakeEvaluator? evaluator, DateTime? now}) async {
  final store = SharedFilterStore(PrefServiceCache());
  for (final rule in rules) {
    expect(await store.save(rule), isTrue);
  }
  return SharedFilterEngine(store, evaluator: () => evaluator ?? _FakeEvaluator(), clock: () => now ?? DateTime.now());
}

Future<void> _settle() => Future<void>.delayed(Duration.zero).then((_) => Future<void>.delayed(Duration.zero));

void main() {
  group('rules', () {
    test('explain what is wrong with a draft', () {
      expect(_rule('a', '  ').problem, SharedFilterProblem.empty);
      expect(_rule('a', 'x' * 257).problem, SharedFilterProblem.tooLong);
      expect(_rule('a', '(unclosed', regex: true).problem, SharedFilterProblem.invalidPattern);
      expect(_rule('a', 'ok', scopes: const {}).problem, SharedFilterProblem.noScope);
      expect(_rule('a', r'\bnft\b', regex: true).problem, isNull);
    });

    test('survive storage; malformed, invalid and duplicate rules are dropped', () async {
      final prefs = PrefServiceCache(
        cache: {
          sharedFilterPreferenceKey: jsonEncode({
            'version': 1,
            'rules': [
              _rule('a', 'crypto', action: SharedFilterAction.fold).toJson(),
              _rule('a', 'duplicate').toJson(),
              {
                'id': 'b',
                'pattern': '(',
                'regex': true,
                'action': 'hide',
                'scopes': ['search'],
              },
              'nonsense',
              _rule('c', 'Sale', caseSensitive: true, scopes: const {SharedFilterScope.search}).toJson(),
            ],
          }),
        },
      );
      final store = SharedFilterStore(prefs);
      expect(store.state.map((rule) => rule.id), ['a', 'c']);
      expect(store.state.first.action, SharedFilterAction.fold);
      expect(store.state.last.caseSensitive, isTrue);
      expect(store.state.last.scopes, {SharedFilterScope.search});
      await store.destroy();
    });

    test('saves in order, edits in place and refuses more than the limit', () async {
      final prefs = PrefServiceCache();
      final store = SharedFilterStore(prefs);
      for (var i = 0; i < sharedFilterMaxRules; i++) {
        await store.save(_rule('$i', 'word$i'));
      }
      expect(await store.save(_rule('extra', 'one too many')), isFalse);
      expect(await store.save(_rule('5', 'renamed')), isTrue);
      expect(store.state[5].pattern, 'renamed');
      expect(await store.setEnabled('6', false), isTrue);
      expect(await store.remove('0'), isTrue);
      final restarted = SharedFilterStore(prefs);
      expect(restarted.state.first.id, '1');
      expect(restarted.state.firstWhere((rule) => rule.id == '6').enabled, isFalse);
      await store.destroy();
      await restarted.destroy();
    });

    test('a refused or throwing save keeps the rules that were saved', () async {
      final prefs = GatedPrefs();
      final store = SharedFilterStore(prefs);
      expect(await store.save(_rule('a', 'kept')), isTrue);
      prefs.rejectWrites = true;
      expect(await store.save(_rule('b', 'lost')), isFalse);
      prefs.rejectWrites = false;
      prefs.throwWrites = true;
      expect(await store.remove('a'), isFalse);
      expect(store.state.map((rule) => rule.id), ['a']);
      await store.destroy();
    });
  });

  test('keywords match whole single words, phrases anywhere, and case only when asked', () {
    expect(MutedTermMatcher('cat').matches('A cat sat'), isTrue);
    expect(MutedTermMatcher('cat').matches('category'), isFalse);
    expect(MutedTermMatcher('black friday').matches('The BLACK FRIDAY sale'), isTrue);
    expect(MutedTermMatcher('Apple', caseSensitive: true).matches('apple pie'), isFalse);
    expect(MutedTermMatcher('Apple', caseSensitive: true).matches('Apple pie'), isTrue);
    expect(textMatchesMutedTerm('Straße gesperrt', 'straße'), isTrue);
  });

  group('engine', () {
    test('hides and folds by keyword, per scope, with hide winning over fold', () async {
      final engine = await _engine([
        _rule('fold', 'spoiler', action: SharedFilterAction.fold),
        _rule('hide', 'crypto', scopes: const {SharedFilterScope.timelines}),
      ]);
      final timelines = SharedFilterScope.timelines;
      expect(engine.verdict('big crypto spoiler', timelines).hidden, isTrue);
      expect(engine.verdict('a spoiler ahead', timelines).folded, isTrue);
      expect(engine.verdict('a spoiler ahead', timelines).reason, 'spoiler');
      expect(engine.verdict('crypto news', SharedFilterScope.search).hidden, isFalse);
      expect(engine.verdict('nothing to see', timelines), same(SharedFilterVerdict.show));
      await engine.destroy();
    });

    test('expired rules stop applying without a timer', () async {
      var now = DateTime(2026, 9, 30, 12);
      final store = SharedFilterStore(PrefServiceCache());
      await store.save(_rule('soon', 'match', until: DateTime(2026, 9, 30, 13)));
      final engine = SharedFilterEngine(store, evaluator: _FakeEvaluator.new, clock: () => now);
      expect(engine.verdict('match', SharedFilterScope.timelines).hidden, isTrue);
      now = DateTime(2026, 9, 30, 14);
      expect(engine.verdict('match', SharedFilterScope.timelines).hidden, isFalse);
      expect(engine.active, isFalse);
      await engine.destroy();
    });

    test('patterns answer off the UI isolate: pending until checked, then cached', () async {
      final evaluator = _FakeEvaluator()..gate = Completer();
      final engine = await _engine([_rule('re', r'\b(nft|web3)\b', regex: true)], evaluator: evaluator);
      final revisions = <int>[];
      engine.observer(onState: revisions.add);
      expect(engine.verdict('new web3 drop', SharedFilterScope.timelines).pending, isTrue);
      expect(engine.verdict('plain post', SharedFilterScope.timelines).pending, isTrue);
      await _settle();
      evaluator.gate!.complete();
      await _settle();
      expect(revisions, isNotEmpty);
      expect(engine.verdict('new web3 drop', SharedFilterScope.timelines).hidden, isTrue);
      expect(engine.verdict('plain post', SharedFilterScope.timelines), same(SharedFilterVerdict.show));
      expect(evaluator.batches.single, ['new web3 drop', 'plain post']);
      await engine.destroy();
    });

    test('a keyword hide needs no pattern check', () async {
      final evaluator = _FakeEvaluator();
      final engine = await _engine([_rule('k', 'crypto'), _rule('re', 'x+y', regex: true)], evaluator: evaluator);
      expect(engine.verdict('crypto', SharedFilterScope.timelines).hidden, isTrue);
      await _settle();
      expect(evaluator.batches, isEmpty);
      await engine.destroy();
    });

    test('a pattern that cannot be checked hides nothing and pauses patterns until resumed', () async {
      final evaluator = _FakeEvaluator()..fail = true;
      final engine = await _engine([_rule('re', 'boom', regex: true), _rule('k', 'keyword')], evaluator: evaluator);
      expect(engine.verdict('boom', SharedFilterScope.timelines).pending, isTrue);
      await _settle();
      expect(engine.patternsPaused, isTrue);
      expect(engine.verdict('boom', SharedFilterScope.timelines).hidden, isFalse);
      expect(engine.verdict('keyword', SharedFilterScope.timelines).hidden, isTrue);
      evaluator.fail = false;
      engine.resumePatterns();
      expect(engine.verdict('boom', SharedFilterScope.timelines).pending, isTrue);
      await _settle();
      expect(engine.verdict('boom', SharedFilterScope.timelines).hidden, isTrue);
      await engine.destroy();
    });

    test('editing rules drops cached verdicts at once', () async {
      final engine = await _engine([_rule('a', 'first')]);
      expect(engine.verdict('first second', SharedFilterScope.timelines).hidden, isTrue);
      await engine.rules.save(_rule('a', 'third'));
      expect(engine.verdict('first second', SharedFilterScope.timelines).hidden, isFalse);
      await engine.rules.setEnabled('a', false);
      expect(engine.active, isFalse);
      await engine.destroy();
    });
  });

  group('isolate worker', () {
    test('matches patterns and survives a runaway one by replacing its isolate', () async {
      final worker = IsolateRegexEvaluator(deadline: const Duration(milliseconds: 250));
      final matches = await worker.evaluate(
        const [SharedFilterPattern('web3', false), SharedFilterPattern('NFT', true)],
        ['Web3 and nft', 'NFT only', 'none'],
      );
      expect(matches, [
        [0],
        [1],
        <int>[],
      ]);
      final started = DateTime.now();
      await expectLater(
        worker.evaluate(const [SharedFilterPattern(r'^(a+)+$', false)], ['${'a' * 40}!']),
        throwsA(isA<SharedFilterWorkerFailure>()),
      );
      expect(DateTime.now().difference(started), lessThan(const Duration(seconds: 3)));
      final after = await worker.evaluate(const [SharedFilterPattern('ok', false)], ['ok']);
      expect(after, [
        [0],
      ]);
      await worker.close();
    });
  });
}
