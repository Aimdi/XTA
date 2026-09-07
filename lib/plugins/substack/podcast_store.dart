import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/media/continuity_player.dart';
import 'package:xta/media/playback_restore.dart';
import 'package:xta/media/podcast_checkpoint.dart';
import 'package:xta/media/xta_audio_handler.dart';

class PodcastPlayback {
  final String? url;
  final String title;
  final bool playing;
  final bool loading;
  final bool failed;
  final Duration position;
  final Duration duration;

  const PodcastPlayback({this.url, this.title = '', this.playing = false,
    this.loading = false, this.failed = false,
    this.position = Duration.zero, this.duration = Duration.zero});

  bool get active => url != null;

  PodcastPlayback copyWith({bool? playing, bool? loading, bool? failed,
    Duration? position, Duration? duration}) => PodcastPlayback(
    url: url, title: title, playing: playing ?? this.playing,
    loading: loading ?? this.loading, failed: failed ?? this.failed,
    position: position ?? this.position, duration: duration ?? this.duration,
  );
}

/// One lazy native player app-wide. Cold restoration is local and always paused.
class PodcastStore extends Store<PodcastPlayback> with WidgetsBindingObserver {
  final BasePrefService? prefs;
  final ContinuityPlayer Function() createPlayer;
  final bool observeLifecycle;
  final Duration readyTimeout;
  final Duration seekTimeout;
  ContinuityPlayer? _playerOrNull;
  StreamSubscription<void>? _subscription;
  Future<void> _operations = Future.value();
  Future<void> _writes = Future.value();
  Completer<void>? _cancel;
  Timer? _timer;
  int _generation = 0;
  String? _loadedUrl;
  bool _receiving = false;
  bool _wantsPlay = false;
  bool _closed = false;

  PodcastStore({this.prefs, ContinuityPlayer Function()? createPlayer,
    this.observeLifecycle = true,
    this.readyTimeout = const Duration(seconds: 5),
    this.seekTimeout = const Duration(seconds: 2),
  }) : createPlayer = createPlayer ?? MediaKitContinuityPlayer.createPodcast,
       super(const PodcastPlayback()) {
    _restore();
    if (observeLifecycle) WidgetsBinding.instance.addObserver(this);
  }

  void _restore() {
    try {
      final raw = prefs?.getKeys().contains(podcastCheckpointPreference) == true
        ? prefs?.get<String>(podcastCheckpointPreference) : null;
      final checkpoint = PodcastCheckpoint.decode(raw);
      if (checkpoint != null) update(PodcastPlayback(url: checkpoint.url,
        title: checkpoint.title, position: checkpoint.position, duration: checkpoint.duration));
    } catch (_) { /* A damaged preference must not prevent startup. */ }
  }

  ContinuityPlayer get _player {
    if (_playerOrNull != null) return _playerOrNull!;
    final player = _playerOrNull = createPlayer();
    _subscription = player.changes.listen((_) => _receive());
    return player;
  }

  void _receive() {
    if (_closed || !_receiving || !state.active || _loadedUrl != state.url) return;
    final frame = _player.frame;
    if (frame.failed) { _failed(); return; }
    if (frame.completed) { unawaited(stop()); return; }
    final wasPlaying = state.playing;
    update(state.copyWith(playing: frame.playing, position: frame.position,
      duration: frame.duration > Duration.zero ? frame.duration : state.duration));
    _session();
    if (wasPlaying && !frame.playing) { unawaited(flush()); } else { _schedule(); }
  }

  Future<void> toggle({required String url, required String title}) async {
    if (_closed || !PodcastCheckpoint.validUrl(url)) return;
    if (state.url == url && (_loadedUrl == url || state.loading)) {
      _wantsPlay = state.loading ? !_wantsPlay : !state.playing;
      if (!state.loading) await (_wantsPlay ? _player.play() : _player.pause());
      return;
    }
    final position = state.url == url ? state.position : Duration.zero;
    final duration = state.url == url ? state.duration : Duration.zero;
    _wantsPlay = true;
    _receiving = false;
    _cancel?.complete();
    final cancel = _cancel = Completer<void>();
    final generation = ++_generation;
    update(PodcastPlayback(url: url, title: title, position: position, duration: duration, loading: true));
    _schedule();
    _operations = _operations.then((_) => _open(url, position, generation, cancel));
    await _operations;
  }

  bool _current(int generation) => !_closed && _generation == generation;

  Future<void> _open(String url, Duration position, int generation, Completer<void> cancel) async {
    if (!_current(generation)) return;
    try {
      await _player.open(url, start: position);
      if (!_current(generation)) return;
      final restored = await _restorePosition(position, generation, cancel);
      if (!_current(generation)) return;
      if (!restored) { _failed(); return; }
      _loadedUrl = url;
      _receiving = true;
      update(state.copyWith(loading: false, failed: false));
      _bind();
      _receive();
      if (_current(generation) && !state.failed && _wantsPlay) await _player.play();
    } catch (_) {
      if (!_current(generation)) return;
      _failed();
    }
  }

  void _failed() {
    _loadedUrl = null;
    _receiving = false;
    update(state.copyWith(loading: false, playing: false, failed: true));
    audioHandler?.clearSession();
    unawaited(flush());
  }

  Future<bool> _restorePosition(Duration position, int generation, Completer<void> cancel) async {
    try {
      return await restorePlaybackPosition(_player, position,
        cancelled: cancel.future, isCurrent: () => _current(generation),
        readyTimeout: readyTimeout, seekTimeout: seekTimeout);
    } catch (_) { return false; }
  }

  void _bind() => audioHandler?.bindSession(title: state.title, binding: (
    onPlay: () { _wantsPlay = true; if (!_closed) unawaited(_player.play()); },
    onPause: () { _wantsPlay = false; if (!_closed) unawaited(_player.pause()); },
    onStop: () => unawaited(stop()), onSeek: (position) => unawaited(seek(position)),
  ));

  void _session() => audioHandler?.updateSession(
    playing: state.playing, position: state.position, duration: state.duration);

  Future<void> seek(Duration position) async {
    if (_closed || !state.active || state.loading) return;
    final generation = _generation;
    final target = playbackResumeTarget(position, state.duration);
    if (_loadedUrl == state.url) await _player.seek(target);
    if (!_current(generation)) return;
    update(state.copyWith(position: target));
    await flush();
  }

  Future<void> stop() async {
    if (_closed) return;
    _generation++;
    _cancel?.complete();
    _cancel = null;
    _receiving = false;
    _wantsPlay = false;
    _loadedUrl = null;
    update(const PodcastPlayback());
    audioHandler?.clearSession();
    final pending = _operations;
    _operations = pending.then((_) async { await _playerOrNull?.stop(); }).catchError((Object _) {});
    await flush();
    await _operations;
  }

  void _schedule() {
    if (prefs == null || _closed) return;
    // A trailing debounce alone would never write during continuous playback.
    _timer ??= Timer(const Duration(seconds: 2), () { _timer = null; unawaited(flush()); });
  }

  Future<void> flush() {
    _timer?.cancel();
    _timer = null;
    final current = state;
    final raw = current.url == null ? '' : PodcastCheckpoint(url: current.url!,
      title: current.title, position: current.position, duration: current.duration).encode();
    _writes = _writes.then((_) async { await prefs?.set(podcastCheckpointPreference, raw); })
      .catchError((Object _) {});
    return _writes;
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state != AppLifecycleState.resumed && !_closed) unawaited(flush());
  }

  @override
  Future<void> destroy() async {
    if (_closed) return;
    _closed = true;
    _generation++;
    _cancel?.complete();
    if (observeLifecycle) WidgetsBinding.instance.removeObserver(this);
    await flush();
    await _operations;
    await _subscription?.cancel();
    await _playerOrNull?.dispose();
    return super.destroy();
  }
}
