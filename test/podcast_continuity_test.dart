import 'dart:async';
import 'dart:convert';

import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/media/podcast_checkpoint.dart';
import 'package:xta/plugins/substack/podcast_store.dart';

import 'support/continuity_player_fake.dart';

const _url = 'https://publication.example/episode.mp3';
const _nextUrl = 'https://publication.example/episode-2.mp3';
const _position = Duration(minutes: 8, seconds: 12);
const _duration = Duration(minutes: 30);

String _checkpoint() => const PodcastCheckpoint(url: _url, title: 'Episode one',
  position: _position, duration: _duration).encode();

PodcastStore _store(BasePrefService prefs, FakeContinuityPlayer player) => PodcastStore(
  prefs: prefs, createPlayer: () => player, observeLifecycle: false,
  readyTimeout: const Duration(milliseconds: 20), seekTimeout: const Duration(milliseconds: 20));

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('cold restore shows paused position without creating a player', () async {
    final prefs = PrefServiceCache(defaults: {podcastCheckpointPreference: _checkpoint()});
    var creations = 0;
    final player = FakeContinuityPlayer();
    final store = PodcastStore(prefs: prefs, observeLifecycle: false,
      createPlayer: () { creations++; return player; });
    expect(store.state.url, _url);
    expect(store.state.position, _position);
    expect(store.state.playing, isFalse);
    expect(creations, 0);
    expect(player.opens, isEmpty);
    await store.toggle(url: _url, title: 'Episode one');
    expect(creations, 1);
    expect(player.opens.single.start, _position);
    expect(store.state.position, _position);
    expect(store.state.playing, isTrue);
    await store.destroy();
  });

  test('background and closing persist the current episode paused on restart', () async {
    final prefs = PrefServiceCache();
    final player = FakeContinuityPlayer();
    final store = _store(prefs, player);
    await store.toggle(url: _url, title: 'Episode one');
    player.emit(position: _position);
    store.didChangeAppLifecycleState(AppLifecycleState.paused);
    await store.flush();
    expect(PodcastCheckpoint.decode(prefs.get(podcastCheckpointPreference))!.position, _position);
    await store.destroy();
    final restored = _store(prefs, FakeContinuityPlayer());
    expect(restored.state.position, _position);
    expect(restored.state.playing, isFalse);
    await restored.destroy();
  });

  testWidgets('continuous playback writes periodically instead of starving a debounce', (tester) async {
    final prefs = PrefServiceCache();
    final player = FakeContinuityPlayer();
    final store = _store(prefs, player);
    await store.toggle(url: _url, title: 'Episode one');
    for (var i = 1; i <= 5; i++) {
      player.emit(position: Duration(seconds: i));
      await tester.pump(const Duration(milliseconds: 500));
    }
    final saved = PodcastCheckpoint.decode(prefs.get(podcastCheckpointPreference));
    expect(saved, isNotNull);
    expect(saved!.position, greaterThan(Duration.zero));
    await store.destroy();
  });

  test('completion clears progress and does not resurrect on restart', () async {
    final prefs = PrefServiceCache();
    final player = FakeContinuityPlayer();
    final store = _store(prefs, player);
    await store.toggle(url: _url, title: 'Episode one');
    player.emit(position: _duration, completed: true);
    await store.flush();
    expect(store.state.active, isFalse);
    await store.destroy();
    expect(PodcastCheckpoint.decode(prefs.get(podcastCheckpointPreference)), isNull);
    final restored = _store(prefs, FakeContinuityPlayer());
    expect(restored.state.active, isFalse);
    await restored.destroy();
  });

  test('a changed episode receives no previous episode seek or playing event', () async {
    final prefs = PrefServiceCache(defaults: {podcastCheckpointPreference: _checkpoint()});
    final player = FakeContinuityPlayer();
    final gate = Completer<void>();
    player.beforeOpen = (url) async { if (url == _url) await gate.future; };
    final store = _store(prefs, player);
    final first = store.toggle(url: _url, title: 'Episode one');
    await Future<void>.delayed(Duration.zero);
    final second = store.toggle(url: _nextUrl, title: 'Episode two');
    player.emit(position: const Duration(minutes: 17), completed: true);
    expect(store.state.url, _nextUrl);
    expect(store.state.position, Duration.zero);
    gate.complete();
    await Future.wait([first, second]);
    expect(player.opens.map((item) => item.url), [_url, _nextUrl]);
    expect(player.commands.where((command) => command.startsWith('seek:')), isEmpty);
    expect(store.state.url, _nextUrl);
    expect(store.state.position, Duration.zero);
    expect(store.state.playing, isTrue);
    await store.destroy();
  });

  test('stop during open cancels resume and clears queued checkpoints', () async {
    final prefs = PrefServiceCache(defaults: {podcastCheckpointPreference: _checkpoint()});
    final player = FakeContinuityPlayer();
    final gate = Completer<void>();
    player.beforeOpen = (_) => gate.future;
    final store = _store(prefs, player);
    final opening = store.toggle(url: _url, title: 'Episode one');
    await Future<void>.delayed(Duration.zero);
    final stopping = store.stop();
    gate.complete();
    await Future.wait([opening, stopping]);
    expect(store.state.active, isFalse);
    expect(player.commands, isNot(contains('play')));
    expect(PodcastCheckpoint.decode(prefs.get(podcastCheckpointPreference)), isNull);
    await store.destroy();
  });

  test('a late old write cannot replace the final stop checkpoint', () async {
    final prefs = _DelayedPrefs();
    final player = FakeContinuityPlayer();
    final store = _store(prefs, player);
    await store.toggle(url: _url, title: 'Episode one');
    player.emit(position: _position);
    final saving = store.flush();
    await Future<void>.delayed(Duration.zero);
    final stopping = store.stop();
    await Future<void>.delayed(Duration.zero);
    expect(prefs.started, 1);
    prefs.firstWrite.complete();
    await Future.wait([saving, stopping]);
    expect(PodcastCheckpoint.decode(prefs.get(podcastCheckpointPreference)), isNull);
    await store.destroy();
  });

  test('failed loading preserves checkpoint and allows an explicit retry', () async {
    final prefs = PrefServiceCache(defaults: {podcastCheckpointPreference: _checkpoint()});
    final player = FakeContinuityPlayer()..beforeOpen = (_) => Future.error(StateError('network'));
    final store = _store(prefs, player);
    await store.toggle(url: _url, title: 'Episode one');
    expect(store.state.failed, isTrue);
    expect(store.state.position, _position);
    expect(store.state.playing, isFalse);
    player.beforeOpen = null;
    await store.toggle(url: _url, title: 'Episode one');
    expect(store.state.failed, isFalse);
    expect(store.state.playing, isTrue);
    await store.destroy();
  });

  test('restored slider can be moved without initializing playback', () async {
    final prefs = PrefServiceCache(defaults: {podcastCheckpointPreference: _checkpoint()});
    final player = FakeContinuityPlayer();
    final store = _store(prefs, player);
    await store.seek(const Duration(minutes: 12));
    expect(player.commands, isEmpty);
    await store.toggle(url: _url, title: 'Episode one');
    expect(player.opens.single.start, const Duration(minutes: 12));
    await store.destroy();
  });

  test('unrestorable podcast stays paused and keeps the checkpoint for retry', () async {
    final prefs = PrefServiceCache(defaults: {podcastCheckpointPreference: _checkpoint()});
    final player = FakeContinuityPlayer()..honorStart = false..honorSeek = false;
    final store = _store(prefs, player);
    await store.toggle(url: _url, title: 'Episode one');
    expect(store.state.failed, isTrue);
    expect(store.state.position, _position);
    expect(player.opens.length, 1);
    expect(player.commands, isNot(contains('play')));
    await store.destroy();
    expect(PodcastCheckpoint.decode(prefs.get(podcastCheckpointPreference))!.position, _position);
  });

  test('malformed, oversized, unsafe and completed records are ignored', () {
    final base = jsonDecode(_checkpoint()) as Map<String, dynamic>;
    for (final value in [
      '{broken', 'x' * 16385, '[]',
      jsonEncode({...base, 'version': 5}),
      jsonEncode({...base, 'url': 'file:///secret.mp3'}),
      jsonEncode({...base, 'position': -1}),
      jsonEncode({...base, 'position': _duration.inMilliseconds}),
      jsonEncode({...base, 'duration': const Duration(days: 8).inMilliseconds}),
      jsonEncode({...base, 'title': 'x' * 513}),
    ]) {
      expect(PodcastCheckpoint.decode(value), isNull);
    }
    expect(PodcastCheckpoint.decode(_checkpoint())!.title, 'Episode one');
  });
}

class _DelayedPrefs extends PrefServiceCache {
  final firstWrite = Completer<void>();
  int started = 0;

  @override
  Future<bool> put<T>(String key, T value) async {
    started++;
    if (started == 1) await firstWrite.future;
    return super.put(key, value);
  }
}
