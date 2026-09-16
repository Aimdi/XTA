import 'dart:async';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/tweet/video_controller_pool.dart';
import 'package:xta/tweet/video_memory_observer.dart';

class FixtureVideo extends Fake implements PooledVideo {
  int disposals = 0;
  @override
  bool get isDisposed => disposals > 0;
  @override
  void suppressQualityResume() {}
  @override
  Future<void> dispose() async {
    disposals++;
  }
}

void main() {
  final binding = TestWidgetsFlutterBinding.ensureInitialized();
  test('system memory pressure releases idle players and preserves attached players', () async {
    final pool = VideoControllerPool();
    final observer = VideoMemoryObserver(pool);
    addTearDown(observer.dispose);
    final idle = FixtureVideo(), active = FixtureVideo();
    await pool.acquire('idle', () async => idle);
    await pool.acquire('active', () async => active);
    pool.release('idle');
    binding.handleMemoryPressure();
    await Future<void>.delayed(Duration.zero);
    expect(idle.disposals, 1);
    expect(active.disposals, 0);
    expect(pool.contains('active'), isTrue);
    pool.release('active');
    binding.handleMemoryPressure();
    await Future<void>.delayed(Duration.zero);
    expect(active.disposals, 1);
  });
  test('idle creation finishing after a memory warning is disposed once', () async {
    final pool = VideoControllerPool();
    final pending = Completer<PooledVideo>();
    final creation = pool.acquire('pending', () => pending.future);
    pool.release('pending');
    pool.releaseUnused();
    pool.releaseUnused();
    final video = FixtureVideo();
    pending.complete(video);
    await creation;
    await Future<void>.delayed(Duration.zero);
    expect(video.disposals, 1);
    expect(pool.contains('pending'), isFalse);
  });
  test('a visible token protects a released player until the view hides', () async {
    final pool = VideoControllerPool();
    final video = FixtureVideo();
    final token = Object();
    await pool.acquire('visible', () async => video);
    pool.markVisible('visible', token);
    pool.release('visible');
    pool.releaseUnused();
    expect(pool.contains('visible'), isTrue);
    pool.markHidden('visible', token);
    pool.releaseUnused();
    await Future<void>.delayed(Duration.zero);
    expect(video.disposals, 1);
  });
}
