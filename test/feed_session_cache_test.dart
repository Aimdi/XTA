import 'package:flutter_test/flutter_test.dart';
import 'package:xta/group/feed_session_cache.dart';

void main() {
  group('FeedSessionCache', () {
    test('the same key keeps the same controller', () {
      final cache = FeedSessionCache();

      expect(
        identical(
          cache.getOrCreateController('a'),
          cache.getOrCreateController('a'),
        ),
        isTrue,
      );
    });

    test('stops growing once it is full', () {
      // Each entry holds every page of tweets its feed loaded, so an unbounded
      // map is a session-long leak for anyone who browses several groups.
      final cache = FeedSessionCache(maxEntries: 3);

      for (var i = 0; i < 10; i++) {
        cache.getOrCreateController('group$i');
      }

      expect(cache.length, 3);
    });

    test('drops the least recently used, not the oldest created', () {
      final cache = FeedSessionCache(maxEntries: 2);

      final a = cache.getOrCreateController('a');
      cache.saveOffset('a', 100);
      cache.getOrCreateController('b');

      // Touching 'a' should make 'b' the eviction candidate.
      expect(identical(cache.getOrCreateController('a'), a), isTrue);
      cache.getOrCreateController('c');

      expect(
        identical(cache.getOrCreateController('a'), a),
        isTrue,
        reason: 'a was used most recently',
      );
      expect(cache.readOffset('a'), 100);
      expect(
        cache.readOffset('b'),
        isNull,
        reason: 'b was the least recently used',
      );
    });

    test('an evicted key loses its offset and media filter too', () {
      final cache = FeedSessionCache(maxEntries: 1);

      cache.getOrCreateController('a');
      cache.saveOffset('a', 42);
      cache.saveMediaOnly('a', true);

      cache.getOrCreateController('b');

      expect(cache.readOffset('a'), isNull);
      expect(cache.readMediaOnly('a'), isFalse);
    });

    test('evict drops one key so the next get is a fresh controller', () {
      final cache = FeedSessionCache();
      final first = cache.getOrCreateController('home--1');
      cache.saveOffset('home--1', 80);
      cache.saveMediaOnly('home--1', true);

      cache.evict('home--1');

      expect(cache.length, 0);
      expect(cache.readOffset('home--1'), isNull);
      expect(cache.readMediaOnly('home--1'), isFalse);
      expect(identical(cache.getOrCreateController('home--1'), first), isFalse);
    });

    test('invalidateAll empties it', () {
      final cache = FeedSessionCache();

      cache.getOrCreateController('a');
      cache.saveOffset('a', 1);
      cache.invalidateAll();

      expect(cache.length, 0);
      expect(cache.readOffset('a'), isNull);
    });
  });
}
