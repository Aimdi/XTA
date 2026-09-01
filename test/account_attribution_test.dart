import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/account_selector.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';

Account _account(String id, {DateTime? lastNotFoundAt}) =>
    Account(id: id, authHeader: '{}', screenName: id, lastNotFoundAt: lastNotFoundAt);

/// X answers 404 for a rotated GraphQL query id and for a stale
/// `x-client-transaction-id` exactly as it does for a broken sign-in, and the
/// transport used to blame the account for all three. One change on X's side
/// therefore marked healthy accounts as "authentication broken" for six hours
/// and told the reader their accounts had stopped working — sending them off to
/// re-add accounts that were fine.
///
/// The evidence that an account really is at fault is a contrast: it was
/// refused where another account succeeded. `isNotFoundFlagged` is what records
/// that verdict, so these pin its meaning.
void main() {
  final now = DateTime.utc(2026, 7, 25, 12);

  group('isNotFoundFlagged', () {
    test('an account that has never been refused is not flagged', () {
      expect(isNotFoundFlagged(_account('a'), now), isFalse);
    });

    test('an account refused within the cooldown is flagged', () {
      expect(isNotFoundFlagged(_account('a', lastNotFoundAt: now.subtract(const Duration(hours: 1))), now), isTrue);
    });

    test('the flag lapses once the cooldown has passed', () {
      final past = now.subtract(notFoundCooldown + const Duration(minutes: 1));

      expect(isNotFoundFlagged(_account('a', lastNotFoundAt: past), now), isFalse);
    });

    test('the flag lasts exactly the configured cooldown', () {
      expect(isNotFoundFlagged(_account('a', lastNotFoundAt: now.subtract(notFoundCooldown)), now), isFalse);
      expect(
        isNotFoundFlagged(
          _account('a', lastNotFoundAt: now.subtract(notFoundCooldown - const Duration(minutes: 1))),
          now,
        ),
        isTrue,
      );
    });
  });

  group('the selector agrees with the flag', () {
    test('a flagged account is deprioritised but still reachable as a fallback', () {
      final flagged = _account('flagged', lastNotFoundAt: now.subtract(const Duration(hours: 1)));
      final healthy = _account('healthy');

      final picked = AccountSelector([flagged, healthy], now).pick(exclude: <String>{});
      expect(picked?.id, 'healthy');

      // Never a hard block: a request is always attempted while any account is
      // left, so an error only ever comes from a real response.
      final fallback = AccountSelector([flagged], now).pick(exclude: <String>{});
      expect(fallback?.id, 'flagged');
    });

    test('when every account is flagged one is still tried', () {
      final accounts = [
        _account('a', lastNotFoundAt: now.subtract(const Duration(hours: 1))),
        _account('b', lastNotFoundAt: now.subtract(const Duration(hours: 1))),
      ];

      expect(AccountSelector(accounts, now).pick(exclude: <String>{}), isNotNull);
    });
  });
}
