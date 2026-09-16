import 'dart:async';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/tweet/video_controller_pool.dart';

class _Video extends Fake implements PooledVideo {
  int disposals = 0;
  final Completer<void>? stopping;
  _Video({this.stopping});
  @override
  bool get isDisposed => disposals > 0;
  @override
  void suppressQualityResume() {}
  @override
  Future<void> dispose() async {
    disposals++;
    await stopping?.future;
  }
}

Future<void> _settle() => Future<void>.delayed(Duration.zero);

void main() {
  test('eviction retains capacity until the native player is actually disposed', () async {
    final stopped = Completer<void>();
    final first = _Video(stopping: stopped);
    final pool = VideoControllerPool(maxSize: 1);
    await pool.acquire('a', () async => first);
    pool.release('a');
    await expectLater(pool.acquire('b', () async => _Video()), throwsA(isA<VideoPoolFullException>()));
    expect(first.disposals, 1);
    expect(pool.canAcquire('b'), isFalse);
    stopped.complete();
    await _settle();
    await pool.acquire('b', () async => _Video());
    expect(pool.contains('b'), isTrue);
  });

  test('abandoned startup cannot allocate more decoders and late completion is disposed', () async {
    final pending = Completer<PooledVideo>();
    final pool = VideoControllerPool(maxSize: 1);
    final acquisition = pool.acquire('a', () => pending.future);
    pool.release('a', acquisition: acquisition);
    pool.discardIfUnused('a', acquisition);
    var creates = 0;
    for (var attempt = 0; attempt < 3; attempt++) {
      await expectLater(
        pool.acquire('a', () async {
          creates++;
          return _Video();
        }),
        throwsA(isA<VideoPoolFullException>()),
      );
    }
    expect(creates, 0);
    final late = _Video();
    pending.complete(late);
    await acquisition;
    await _settle();
    expect(late.disposals, 1);
    expect(pool.canAcquire('a'), isTrue);
  });

  test('a late release cannot release a replacement with the same key', () async {
    final pool = VideoControllerPool(maxSize: 1);
    final old = pool.acquire('a', () async => _Video());
    await old;
    pool.release('a', acquisition: old);
    pool.discardIfUnused('a', old);
    await _settle();
    final replacement = _Video();
    await pool.acquire('a', () async => replacement);
    pool.release('a', acquisition: old);
    pool.discardIfUnused('a', old);
    pool.releaseUnused();
    expect(pool.contains('a'), isTrue);
    expect(replacement.disposals, 0);
    await expectLater(pool.acquire('b', () async => _Video()), throwsA(isA<VideoPoolFullException>()));
  });

  test('reattaching to a cached key shares one acquisition', () async {
    final pool = VideoControllerPool(maxSize: 1);
    final first = pool.acquire('a', () async => _Video());
    await first;
    pool.release('a');
    final second = pool.acquire('a', () async => throw StateError('must reuse'));
    expect(identical(first, second), isTrue);
    await second;
    pool.release('a');
    pool.releaseUnused();
    await _settle();
  });
}
