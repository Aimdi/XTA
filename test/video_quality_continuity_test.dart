import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/media/continuity_player.dart';
import 'package:xta/media/playback_restore.dart';
import 'package:xta/media/video_source_store.dart';

import 'support/continuity_player_fake.dart';

const _old = 'https://video.example/360.mp4';
const _high = 'https://video.example/1080.mp4';
const _medium = 'https://video.example/720.mp4';
const _position = Duration(seconds: 42);

FakeContinuityPlayer _player({bool playing = true}) => FakeContinuityPlayer(
  frame: PlaybackFrame(
    position: _position,
    duration: const Duration(minutes: 2),
    playing: playing,
    volume: 27,
    rate: 1.5,
  ),
)..mediaDuration = const Duration(minutes: 2);

VideoSourceStore _store(FakeContinuityPlayer player, {bool Function()? disposed}) => VideoSourceStore(
  player,
  _old,
  isDisposed: disposed ?? () => false,
  readyTimeout: const Duration(milliseconds: 20),
  seekTimeout: const Duration(milliseconds: 20),
  commandTimeout: const Duration(milliseconds: 20),
);

void main() {
  test('quality switch preserves position, volume, rate and active play intent', () async {
    final player = _player();
    final store = _store(player);
    expect(await store.change(_high), VideoSwitchResult.restored);
    expect(player.opens.single, (url: _high, start: _position));
    expect(player.frame.position, _position);
    expect(player.frame.playing, isTrue);
    expect(player.frame.volume, 27);
    expect(player.frame.rate, 1.5);
    expect(player.commands.where((command) => command.startsWith('seek:')), isEmpty);
    await store.destroy();
  });

  test('paused video remains paused after a quality switch', () async {
    final player = _player(playing: false);
    final store = _store(player);
    expect(await store.change(_high), VideoSwitchResult.restored);
    expect(player.frame.position, _position);
    expect(player.frame.playing, isFalse);
    expect(player.commands, isNot(contains('play')));
    await store.destroy();
  });

  test('if native start is ignored seek waits until new duration arrives', () async {
    final player = _player()
      ..honorStart = false
      ..publishDuration = false;
    final store = _store(player);
    final changing = store.change(_high);
    await Future<void>.delayed(Duration.zero);
    expect(player.commands.where((command) => command.startsWith('seek:')), isEmpty);
    player.emit(duration: const Duration(minutes: 2));
    expect(await changing, VideoSwitchResult.restored);
    expect(player.commands, contains('seek:42000'));
    expect(player.frame.position, _position);
    expect(player.commands.last, 'play');
    await store.destroy();
  });

  test('unacknowledged seek falls back once to a playable start', () async {
    final player = _player()
      ..honorStart = false
      ..honorSeek = false;
    final store = _store(player);
    expect(await store.change(_high), VideoSwitchResult.restarted);
    expect(player.opens.map((item) => item.start), [_position, Duration.zero]);
    expect(player.commands.where((command) => command.startsWith('seek:')).length, 1);
    expect(player.frame.position, Duration.zero);
    expect(player.frame.playing, isTrue);
    await store.destroy();
  });

  test('missing duration times out without seeking an unready decoder', () async {
    final player = _player()
      ..publishDuration = false
      ..honorStart = false;
    final store = _store(player);
    expect(await store.change(_high), VideoSwitchResult.restarted);
    expect(player.commands.where((command) => command.startsWith('seek:')), isEmpty);
    expect(player.opens.length, 2);
    await store.destroy();
  });

  test('rapid quality choices serialize native opens and latest choice wins', () async {
    final player = _player();
    final gate = Completer<void>();
    player.beforeOpen = (url) async {
      if (url == _high) await gate.future;
    };
    final store = _store(player);
    final first = store.change(_high);
    await Future<void>.delayed(Duration.zero);
    final second = store.change(_medium);
    expect(player.opens.length, 1);
    gate.complete();
    expect(await first, VideoSwitchResult.cancelled);
    expect(await second, VideoSwitchResult.restored);
    expect(player.opens.map((item) => item.url), [_high, _medium]);
    expect(player.opens.last.start, _position);
    expect(player.commands.where((command) => command == 'play').length, 1);
    expect(store.state.url, _medium);
    await store.destroy();
  });

  test('new choice cancels a duration wait rather than waiting for timeout', () async {
    final player = _player()..publishDuration = false;
    final store = VideoSourceStore(player, _old, isDisposed: () => false, readyTimeout: const Duration(minutes: 1));
    final first = store.change(_high);
    await Future<void>.delayed(Duration.zero);
    player.publishDuration = true;
    final second = store.change(_medium);
    expect(await first.timeout(const Duration(seconds: 1)), VideoSwitchResult.cancelled);
    expect(await second, VideoSwitchResult.restored);
    await store.destroy();
  });

  test('disposal cancels restoration before any seek or playback command', () async {
    final player = _player()..publishDuration = false;
    var disposed = false;
    final store = _store(player, disposed: () => disposed);
    final changing = store.change(_high);
    await Future<void>.delayed(Duration.zero);
    disposed = true;
    final closing = store.destroy();
    expect(await changing, VideoSwitchResult.cancelled);
    await closing;
    expect(player.commands, ['open:$_high', 'dispose']);
    expect(await store.change(_medium), VideoSwitchResult.cancelled);
  });

  test('hiding the video during restoration suppresses delayed automatic play', () async {
    final player = _player()..publishDuration = false;
    final store = _store(player);
    final changing = store.change(_high);
    await Future<void>.delayed(Duration.zero);
    store.suppressResume();
    player.emit(duration: const Duration(minutes: 2));
    expect(await changing, VideoSwitchResult.restored);
    expect(player.frame.playing, isFalse);
    expect(player.commands, isNot(contains('play')));
    await store.destroy();
  });

  test('mute and speed changes made while loading take precedence', () async {
    final player = _player()..publishDuration = false;
    final store = _store(player);
    final changing = store.change(_high);
    await Future<void>.delayed(Duration.zero);
    player.emit(volume: 0, rate: 2);
    player.emit(duration: const Duration(minutes: 2));
    expect(await changing, VideoSwitchResult.restored);
    expect(player.frame.volume, 0);
    expect(player.frame.rate, 2);
    expect(player.commands, isNot(contains('volume:27.0')));
    await store.destroy();
  });

  test('failed variant restores the original source and remains retryable', () async {
    final player = _player()
      ..beforeOpen = (url) async {
        if (url == _high) throw StateError('variant unavailable');
      };
    final store = _store(player);
    expect(await store.change(_high), VideoSwitchResult.failed);
    expect(store.state.url, _old);
    expect(player.opens.map((item) => item.url), [_high, _old]);
    expect(player.frame.position, _position);
    player.beforeOpen = null;
    expect(await store.change(_high), VideoSwitchResult.restored);
    expect(store.state.url, _high);
    await store.destroy();
  });

  test('completed videos start paused and invalid rate or volume is not applied', () async {
    final player = _player()..emit(completed: true, volume: double.nan, rate: double.infinity);
    final store = _store(player);
    expect(await store.change(_high), VideoSwitchResult.restored);
    expect(player.opens.single.start, Duration.zero);
    expect(player.commands.any((value) => value.startsWith('rate:') || value.startsWith('volume:')), isFalse);
    expect(player.commands, isNot(contains('play')));
    await store.destroy();
  });

  test('resume target stays within a shorter variant and does not seek EOF', () {
    expect(playbackResumeTarget(_position, const Duration(seconds: 30)), const Duration(milliseconds: 29500));
    expect(playbackResumeTarget(const Duration(seconds: -5), _position), Duration.zero);
    expect(playbackResumeTarget(_position, Duration.zero), Duration.zero);
  });

  test('stalled native seek fails promptly without overlapping a fallback open', () async {
    final gate = Completer<void>();
    final player = _player()
      ..honorStart = false
      ..beforeSeek = (_) => gate.future;
    final store = _store(player);
    expect(await store.change(_high).timeout(const Duration(seconds: 1)), VideoSwitchResult.failed);
    expect(store.nativeCommandPending, isTrue);
    expect(player.opens.length, 1);
    expect(player.commands, isNot(contains('play')));
    expect(await store.change(_medium), VideoSwitchResult.failed);
    expect(player.opens.length, 1);
    gate.complete();
    await store.whenNativeIdle;
    expect(player.frame.playing, isFalse);
    expect(await store.change(_medium), VideoSwitchResult.restored);
    expect(player.opens.last.start, _position);
    await store.destroy();
  });

  test('stalled native open fails promptly and disposal waits for its late completion', () async {
    final gate = Completer<void>();
    final player = _player()..beforeOpen = (_) => gate.future;
    final store = _store(player);
    expect(await store.change(_high).timeout(const Duration(seconds: 1)), VideoSwitchResult.failed);
    expect(store.nativeCommandPending, isTrue);
    await store.destroy().timeout(const Duration(seconds: 1));
    expect(player.disposed, isFalse);
    gate.complete();
    await store.whenNativeIdle;
    expect(player.disposed, isTrue);
    expect(player.commands, ['open:$_high', 'pause', 'dispose']);
  });

  test('latest choice unblocks when an uncancellable open is still stalled', () async {
    final gate = Completer<void>();
    final player = _player()..beforeOpen = (_) => gate.future;
    final store = VideoSourceStore(player, _old, isDisposed: () => false, commandTimeout: const Duration(milliseconds: 20));
    final first = store.change(_high);
    await Future<void>.delayed(Duration.zero);
    final latest = store.change(_medium);
    expect(await first.timeout(const Duration(seconds: 1)), VideoSwitchResult.cancelled);
    expect(await latest.timeout(const Duration(seconds: 1)), VideoSwitchResult.failed);
    expect(player.opens.map((open) => open.url), [_high]);
    expect(store.state.failed, isTrue);
    gate.complete();
    await store.whenNativeIdle;
    expect(player.frame.playing, isFalse);
    expect(player.commands, isNot(contains('play')));
    player.beforeOpen = null;
    expect(await store.change(_medium), VideoSwitchResult.restored);
    expect(player.opens.last.url, _medium);
    await store.destroy();
  });

  test('latest choice cannot receive the seek of an abandoned earlier source', () async {
    final gate = Completer<void>();
    final player = _player()
      ..honorStart = false
      ..beforeSeek = (_) => gate.future;
    final store = VideoSourceStore(
      player,
      _old,
      isDisposed: () => false,
      seekTimeout: const Duration(minutes: 1),
      commandTimeout: const Duration(milliseconds: 20),
    );
    final first = store.change(_high);
    await Future<void>.delayed(Duration.zero);
    final latest = store.change(_medium);
    expect(await first.timeout(const Duration(seconds: 1)), VideoSwitchResult.cancelled);
    expect(await latest.timeout(const Duration(seconds: 1)), VideoSwitchResult.failed);
    expect(player.opens.map((open) => open.url), [_high]);
    gate.complete();
    await store.whenNativeIdle;
    expect(player.commands, ['open:$_high', 'seek:42000', 'pause']);
    await store.destroy();
  });
}
