import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/account_selector.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';

Account _account(String id, {DateTime? lastNotFoundAt}) => Account(
      id: id,
      authHeader: '{}',
      screenName: id,
      lastNotFoundAt: lastNotFoundAt,
    );

void main() {
  final now = DateTime.utc(2026, 7, 20, 12);

  group('AccountSelector.pick', () {
    test('returns null when every account is excluded', () {
      final selector = AccountSelector([_account('a'), _account('b')], now);
      expect(selector.pick(exclude: {'a', 'b'}), isNull);
    });

    test('returns the only remaining account', () {
      final selector = AccountSelector([_account('a'), _account('b')], now);
      expect(selector.pick(exclude: {'a'})?.id, 'b');
    });

    test('prefers healthy accounts over not-found-flagged ones', () {
      final flagged = _account('bad', lastNotFoundAt: now.subtract(const Duration(hours: 1)));
      final healthy = _account('good');
      final selector = AccountSelector([flagged, healthy], now);

      final picks = {for (var i = 0; i < 40; i++) selector.pick(exclude: <String>{})!.id};
      expect(picks, {'good'});
    });

    test('treats expired not-found cooldown as healthy', () {
      final recovered = _account(
        'recovered',
        lastNotFoundAt: now.subtract(notFoundCooldown).subtract(const Duration(minutes: 1)),
      );
      final selector = AccountSelector([recovered], now);
      expect(selector.pick(exclude: <String>{})?.id, 'recovered');
    });

    test('still within not-found cooldown is flagged', () {
      final flagged = _account(
        'flagged',
        lastNotFoundAt: now.subtract(notFoundCooldown).add(const Duration(minutes: 1)),
      );
      final healthy = _account('ok');
      final selector = AccountSelector([flagged, healthy], now);
      final picks = {for (var i = 0; i < 40; i++) selector.pick(exclude: <String>{})!.id};
      expect(picks, {'ok'});
    });

    test('falls back to flagged accounts when none are healthy', () {
      final a = _account('a', lastNotFoundAt: now);
      final b = _account('b', lastNotFoundAt: now);
      final selector = AccountSelector([a, b], now, isRateLimited: (_) => true);

      final pick = selector.pick(exclude: <String>{});
      expect(pick, isNotNull);
      expect({'a', 'b'}, contains(pick!.id));
    });

    test('excludes rate-limited accounts from the healthy pool', () {
      final limited = _account('limited');
      final free = _account('free');
      final selector = AccountSelector(
        [limited, free],
        now,
        isRateLimited: (account) => account.id == 'limited',
      );

      final picks = {for (var i = 0; i < 40; i++) selector.pick(exclude: <String>{})!.id};
      expect(picks, {'free'});
    });

    test('falls back to rate-limited accounts when all remaining are limited', () {
      final a = _account('a');
      final selector = AccountSelector([a], now, isRateLimited: (_) => true);
      expect(selector.pick(exclude: <String>{})?.id, 'a');
    });
  });

  group('AccountSelector.pick, least used first', () {
    Set<String> picks(AccountSelector selector) => {for (var i = 0; i < 60; i++) selector.pick(exclude: <String>{})!.id};

    test('takes the account with the most requests left', () {
      final quota = {'low': 2, 'high': 140, 'mid': 50};
      final selector = AccountSelector([
        _account('low'),
        _account('high'),
        _account('mid'),
      ], now, remainingQuota: (a) => quota[a.id]);
      expect(picks(selector), {'high'});
    });

    test('an account with no quota known ties with the best one instead of beating it', () {
      final quota = {'best': 140, 'low': 3};
      final selector = AccountSelector([
        _account('best'),
        _account('low'),
        _account('unknown'),
      ], now, remainingQuota: (a) => quota[a.id]);
      expect(picks(selector), {'best', 'unknown'});
    });

    // aimdi176: an account answering 401/403 never reports a quota. Counted as
    // fresh it outranked every other account and took every request.
    test('an account that never reports a quota does not take every request', () {
      final quota = {'a': 120, 'b': 120};
      final selector = AccountSelector([
        _account('a'),
        _account('b'),
        _account('failing'),
      ], now, remainingQuota: (a) => quota[a.id]);
      final counts = <String, int>{};
      for (var i = 0; i < 300; i++) {
        counts.update(selector.pick(exclude: <String>{})!.id, (n) => n + 1, ifAbsent: () => 1);
      }
      expect(counts['failing'], lessThan(150));
      expect(counts.keys, containsAll(['a', 'b']));
    });

    test('a burst goes to the account with the fewest requests in flight', () {
      final busy = {'a': 2, 'b': 0, 'c': 1};
      final quota = {'a': 140, 'b': 10, 'c': 140};
      final selector = AccountSelector(
        [_account('a'), _account('b'), _account('c')],
        now,
        remainingQuota: (a) => quota[a.id],
        inFlight: (a) => busy[a.id]!,
      );
      expect(picks(selector), {'b'});
    });

    test('equal accounts still share the load', () {
      final selector = AccountSelector([_account('a'), _account('b')], now, remainingQuota: (_) => 30);
      expect(picks(selector), {'a', 'b'});
    });

    test('a healthy account beats a flagged one with more quota', () {
      final flagged = _account('flagged', lastNotFoundAt: now.subtract(const Duration(hours: 1)));
      final quota = {'flagged': 140, 'ok': 3};
      final selector = AccountSelector([flagged, _account('ok')], now, remainingQuota: (a) => quota[a.id]);
      expect(picks(selector), {'ok'});
    });

    test('an excluded account is skipped however much it has left', () {
      final quota = {'tried': 140, 'next': 5};
      final selector = AccountSelector([_account('tried'), _account('next')], now, remainingQuota: (a) => quota[a.id]);
      expect(selector.pick(exclude: {'tried'})?.id, 'next');
    });
  });
}
