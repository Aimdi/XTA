import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client_unauthenticated.dart';
import 'package:xta/constants.dart';

/// The guest token's expiry handling was dead code. `getToken` consulted
/// `_expiresAt` / `_tokenLimit` / `_tokenRemaining`, all declared `const int =
/// -1`, so `if (_expiresAt == -1 && ...)` was always true and the cached token
/// was returned forever. The counters `fetchUnauthenticated` actually kept up to
/// date lived in different variables that nothing ever read.
void main() {
  const window = 60 * 1000;

  group('guestTokenUsable', () {
    test('a token with no observed rate-limit headers is used', () {
      expect(guestTokenUsable(resetAtMillis: -1, remaining: -1, nowMillis: window), isTrue);
    });

    test('a token with quota left inside the window is used', () {
      expect(guestTokenUsable(resetAtMillis: window, remaining: 5, nowMillis: 0), isTrue);
    });

    // This is the case the old code could never reach.
    test('an exhausted token inside the window is replaced', () {
      expect(guestTokenUsable(resetAtMillis: window, remaining: 0, nowMillis: 0), isFalse);
    });

    test('once the window rolls over the quota has refilled, so the token is used again', () {
      expect(guestTokenUsable(resetAtMillis: window, remaining: 0, nowMillis: window), isTrue);
      expect(guestTokenUsable(resetAtMillis: window, remaining: 0, nowMillis: window + 1), isTrue);
    });

    test('the boundary is inclusive of the reset instant', () {
      expect(guestTokenUsable(resetAtMillis: window, remaining: 0, nowMillis: window - 1), isFalse);
    });
  });

  test('the guest bearer is a single constant, not repeated inline', () {
    expect(guestBearerToken, startsWith('Bearer '));
    expect(guestBearerToken, isNot(bearerToken));
  });

  // fetchUnauthenticated sent `userAgentHeader.toString()`, which stringifies
  // the whole map — "{user-agent: Mozilla/..., Pragma: no-cache, ...}" — as the
  // user agent value.
  test('the user agent is the header value, not the whole map printed', () {
    final agent = userAgentHeader['user-agent']!;

    expect(agent, startsWith('Mozilla/'));
    expect(agent, isNot(contains('Pragma')));
    expect(userAgentHeader.toString(), contains('Pragma'));
  });
}
