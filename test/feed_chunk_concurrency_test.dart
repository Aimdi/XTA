import 'package:flutter_test/flutter_test.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/group/feed_chunk_concurrency.dart';

final _now = DateTime.utc(2026, 10, 11, 12);

Account _account(String id, {bool flagged = false}) => Account(
  id: id,
  authHeader: '{}',
  screenName: id,
  lastNotFoundAt: flagged ? _now.subtract(const Duration(minutes: 1)) : null,
);

void main() {
  group('feedChunkConcurrencyFor', () {
    test('a guest or a single account keeps the old three', () {
      expect(feedChunkConcurrencyFor(0), 3);
      expect(feedChunkConcurrencyFor(1), 3);
    });

    test('each account adds about two searches, never more than six', () {
      expect(feedChunkConcurrencyFor(2), 4);
      expect(feedChunkConcurrencyFor(3), 6);
      expect(feedChunkConcurrencyFor(12), 6);
    });
  });

  group('usableSearchAccounts', () {
    test('counts accounts on for home feeds with a working sign-in', () {
      final accounts = [_account('a'), _account('b'), _account('c', flagged: true), _account('d')];
      expect(usableSearchAccounts(accounts, {'d'}, _now), 2);
    });

    test('with every account off for home feeds, all working ones count', () {
      final accounts = [_account('a'), _account('b'), _account('c', flagged: true)];
      expect(usableSearchAccounts(accounts, {'a', 'b', 'c'}, _now), 2);
    });

    test('no accounts means none usable', () {
      expect(usableSearchAccounts(const [], const {}, _now), 0);
    });
  });
}
