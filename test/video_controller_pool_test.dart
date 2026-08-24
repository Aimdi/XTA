import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/tweet/video_controller_pool.dart';

/// Never completes — we only need the pool map and refcounts, not a Player.
Future<PooledVideo> _pending() => Completer<PooledVideo>().future;

void main() {
  group('VideoControllerPool.acquire', () {
    test('at capacity, an unused entry is evicted for a new key', () {
      final pool = VideoControllerPool(maxSize: 1);
      pool.acquire('a', _pending);
      pool.release('a');

      expect(pool.canAcquire('b'), isTrue);
      pool.acquire('b', _pending);

      expect(pool.contains('a'), isFalse);
      expect(pool.contains('b'), isTrue);
    });

    test('at capacity with every player in use, a new key fails', () async {
      final pool = VideoControllerPool(maxSize: 1);
      pool.acquire('a', _pending);

      expect(pool.canAcquire('b'), isFalse);
      await expectLater(
        pool.acquire('b', _pending),
        throwsA(isA<VideoPoolFullException>()),
      );
      expect(pool.contains('a'), isTrue);
    });

    test('reattaching to a cached key does not evict it', () {
      final pool = VideoControllerPool(maxSize: 1);
      final first = pool.acquire('a', _pending);
      pool.release('a');
      final second = pool.acquire('a', _pending);

      expect(identical(first, second), isTrue);
      expect(pool.contains('a'), isTrue);
    });

    test('canAcquire agrees with acquire when an unused entry can be dropped', () {
      final pool = VideoControllerPool(maxSize: 2);
      pool.acquire('a', _pending);
      pool.acquire('b', _pending);
      pool.release('a');

      expect(pool.canAcquire('c'), isTrue);
      pool.acquire('c', _pending);
      expect(pool.contains('a'), isFalse);
      expect(pool.contains('b'), isTrue);
      expect(pool.contains('c'), isTrue);
    });
  });
}
