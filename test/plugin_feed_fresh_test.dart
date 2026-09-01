import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/account_posts.dart';
import 'package:xta/plugins/plugin_feed_fresh.dart';

void main() {
  final now = DateTime.utc(2026, 8, 13, 12);

  test('a feed that has never been fetched is not fresh', () {
    expect(pluginFeedIsFresh(null, now: now), isFalse);
  });

  test('a feed fetched inside the TTL is reused', () {
    expect(
      pluginFeedIsFresh(
        now.subtract(const Duration(minutes: 3)),
        now: now,
        ttl: kAccountPostsCacheTtl,
      ),
      isTrue,
    );
  });

  test('a feed older than the TTL is fetched again', () {
    expect(
      pluginFeedIsFresh(
        now.subtract(const Duration(minutes: 11)),
        now: now,
        ttl: kAccountPostsCacheTtl,
      ),
      isFalse,
    );
  });

  test('a timestamp in the future is not treated as fresh', () {
    expect(
      pluginFeedIsFresh(now.add(const Duration(minutes: 1)), now: now),
      isFalse,
    );
  });
}
