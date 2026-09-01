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
}
