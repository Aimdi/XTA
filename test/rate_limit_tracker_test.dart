import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/rate_limit_tracker.dart';

void main() {
  const account = 'acct-1';
  const endpoint = '/SearchTimeline';
  const otherEndpoint = '/TweetDetail';

  tearDown(() {
    RateLimitTracker.clear(account, endpoint);
    RateLimitTracker.clear(account, otherEndpoint);
    RateLimitTracker.clear('acct-2', endpoint);
  });

  group('RateLimitTracker', () {
    test('is not limited when nothing has been flagged', () {
      final now = DateTime.utc(2026, 7, 20, 12);
      expect(RateLimitTracker.isLimited(account, endpoint, now), isFalse);
    });

    test('is limited before the reset time', () {
      final now = DateTime.utc(2026, 7, 20, 12);
      final resetAt = now.add(const Duration(minutes: 15));
      RateLimitTracker.flag(account, endpoint, resetAt);
      expect(RateLimitTracker.isLimited(account, endpoint, now), isTrue);
    });

    test('is not limited at or after the reset time', () {
      final resetAt = DateTime.utc(2026, 7, 20, 12, 15);
      RateLimitTracker.flag(account, endpoint, resetAt);
      expect(RateLimitTracker.isLimited(account, endpoint, resetAt), isFalse);
      expect(
        RateLimitTracker.isLimited(account, endpoint, resetAt.add(const Duration(seconds: 1))),
        isFalse,
      );
    });

    test('rate limits are per endpoint, not per account globally', () {
      final now = DateTime.utc(2026, 7, 20, 12);
      RateLimitTracker.flag(account, endpoint, now.add(const Duration(minutes: 15)));
      expect(RateLimitTracker.isLimited(account, endpoint, now), isTrue);
      expect(RateLimitTracker.isLimited(account, otherEndpoint, now), isFalse);
    });

    test('rate limits are per account for the same endpoint', () {
      final now = DateTime.utc(2026, 7, 20, 12);
      RateLimitTracker.flag(account, endpoint, now.add(const Duration(minutes: 15)));
      expect(RateLimitTracker.isLimited(account, endpoint, now), isTrue);
      expect(RateLimitTracker.isLimited('acct-2', endpoint, now), isFalse);
    });

    test('clear removes the limit for that account and endpoint', () {
      final now = DateTime.utc(2026, 7, 20, 12);
      RateLimitTracker.flag(account, endpoint, now.add(const Duration(minutes: 15)));
      RateLimitTracker.clear(account, endpoint);
      expect(RateLimitTracker.isLimited(account, endpoint, now), isFalse);
    });
  });

  // Ported from QuaX commit 6dca796.
  group('RateLimitTracker quota', () {
    final now = DateTime.utc(2026, 7, 20, 12);
    final reset = now.add(const Duration(minutes: 15));
    Map<String, String> headers(int remaining, [DateTime? resetAt]) => {
      'x-rate-limit-remaining': '$remaining',
      'x-rate-limit-reset': '${(resetAt ?? reset).millisecondsSinceEpoch ~/ 1000}',
    };

    test('an answer with quota left keeps the account available', () {
      RateLimitTracker.record(account, endpoint, headers(10), now);
      expect(RateLimitTracker.isLimited(account, endpoint, now), isFalse);
    });

    test('a spent quota sets the account aside until the reset', () {
      RateLimitTracker.record(account, endpoint, headers(0), now);
      expect(RateLimitTracker.isLimited(account, endpoint, now), isTrue);
      final active = RateLimitTracker.activeFor(account, now);
      expect(active.keys, [endpoint]);
      expect(active[endpoint]!.isAtSameMomentAs(reset), isTrue, reason: 'the wait the error card counts down');
      expect(RateLimitTracker.isLimited(account, endpoint, reset), isFalse);
    });

    test('requests sent count the quota down', () {
      RateLimitTracker.record(account, endpoint, headers(2), now);
      RateLimitTracker.consume(account, endpoint, now);
      expect(RateLimitTracker.isLimited(account, endpoint, now), isFalse);
      RateLimitTracker.consume(account, endpoint, now);
      expect(RateLimitTracker.isLimited(account, endpoint, now), isTrue);
      expect(RateLimitTracker.isLimited(account, otherEndpoint, now), isFalse, reason: 'quotas are per endpoint');
    });

    test('an answer written before requests in flight does not hand their credits back', () {
      RateLimitTracker.record(account, endpoint, headers(2), now);
      RateLimitTracker.consume(account, endpoint, now);
      RateLimitTracker.consume(account, endpoint, now);
      RateLimitTracker.record(account, endpoint, headers(1), now);
      expect(RateLimitTracker.isLimited(account, endpoint, now), isTrue);
    });

    test('a new window replaces the count', () {
      RateLimitTracker.record(account, endpoint, headers(0), now);
      final next = reset.add(const Duration(minutes: 15));
      RateLimitTracker.record(account, endpoint, headers(50, next), now);
      expect(RateLimitTracker.isLimited(account, endpoint, now), isFalse);
    });

    test('an unknown quota is never counted down', () {
      RateLimitTracker.consume(account, endpoint, now);
      expect(RateLimitTracker.isLimited(account, endpoint, now), isFalse);
    });

    test('an answer without quota headers lifts a flag, as a success always did', () {
      RateLimitTracker.flag(account, endpoint, reset);
      RateLimitTracker.record(account, endpoint, const {}, now);
      expect(RateLimitTracker.isLimited(account, endpoint, now), isFalse);
    });

    test('malformed quota headers are ignored', () {
      RateLimitTracker.record(account, endpoint, {'x-rate-limit-remaining': 'x', 'x-rate-limit-reset': '1'}, now);
      RateLimitTracker.record(account, otherEndpoint, {'x-rate-limit-remaining': '0', 'x-rate-limit-reset': '-5'}, now);
      expect(RateLimitTracker.isLimited(account, endpoint, now), isFalse);
      expect(RateLimitTracker.isLimited(account, otherEndpoint, now), isFalse);
    });
  });
}
