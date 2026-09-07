import 'dart:async';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/media/continuity_player.dart';
import 'package:xta/media/playback_command_gate.dart';
import 'package:xta/media/playback_restore.dart';

enum VideoSwitchResult { restored, restarted, cancelled, failed }

class VideoSourceState {
  final String url;
  final bool changing;
  final bool failed;
  const VideoSourceState(this.url, {this.changing = false, this.failed = false});
}

/// One source mutation at a time per pooled player, shared by inline/fullscreen.
class VideoSourceStore extends Store<VideoSourceState> {
  final ContinuityPlayer player;
  final bool Function() isDisposed;
  final Duration readyTimeout;
  final Duration seekTimeout;
  final Duration commandTimeout;
  late final _commands = PlaybackCommandGate(onAbandonedSettled: player.pause);
  Future<void> _operations = Future.value();
  Future<void>? _release;
  Completer<void>? _cancel;
  PlaybackFrame? _origin;
  String? _originUrl;
  bool _resumeAllowed = true;
  int _generation = 0;
  bool _closed = false;

  VideoSourceStore(
    this.player,
    String url, {
    required this.isDisposed,
    this.readyTimeout = const Duration(seconds: 5),
    this.seekTimeout = const Duration(seconds: 2),
    this.commandTimeout = const Duration(seconds: 8),
  }) : super(VideoSourceState(url));

  Future<VideoSwitchResult> change(String url) {
    if (_closed || isDisposed()) return Future.value(VideoSwitchResult.cancelled);
    if (state.url == url && !state.changing && !state.failed) return Future.value(VideoSwitchResult.restored);
    if (_origin == null) {
      _originUrl = state.url;
      _resumeAllowed = true;
    }
    _origin ??= player.frame;
    _cancel?.complete();
    final cancel = _cancel = Completer<void>();
    final generation = ++_generation;
    update(VideoSourceState(url, changing: true));
    final operation = _operations.then((_) => _change(url, generation, cancel));
    _operations = operation.then<void>((_) {});
    return operation;
  }

  bool _current(int generation) => !_closed && !isDisposed() && generation == _generation;

  bool get nativeCommandPending => _commands.busy;
  Future<void> get whenNativeIdle => _release ?? _commands.whenIdle;

  Future<void> _command(int generation, Completer<void> cancel, Future<void> Function() action) {
    if (!_current(generation)) return Future.error(const PlaybackCommandCancelled());
    return _commands.run(action, cancelled: cancel.future, timeout: commandTimeout);
  }

  Future<VideoSwitchResult> _change(String url, int generation, Completer<void> cancel) async {
    if (!_current(generation)) return VideoSwitchResult.cancelled;
    final origin = _origin!;
    try {
      await _command(generation, cancel, () => player.open(url, start: origin.completed ? Duration.zero : origin.position));
      if (!_current(generation)) return VideoSwitchResult.cancelled;
      final restored = await _restore(origin, generation, cancel);
      if (!_current(generation)) return VideoSwitchResult.cancelled;
      if (!restored) await _command(generation, cancel, () => player.open(url));
      if (!_current(generation)) return VideoSwitchResult.cancelled;
      await _settings(origin, generation, cancel);
      if (!_current(generation)) return VideoSwitchResult.cancelled;
      return restored ? VideoSwitchResult.restored : VideoSwitchResult.restarted;
    } catch (_) {
      if (!_current(generation)) return VideoSwitchResult.cancelled;
      if (_commands.busy) {
        update(VideoSourceState(state.url, failed: true));
      } else {
      await _recover(origin, generation, cancel);
      }
      return VideoSwitchResult.failed;
    } finally {
      if (_current(generation)) {
        if (!state.failed) { _origin = null; _originUrl = null; }
        update(VideoSourceState(state.url, failed: state.failed));
      }
    }
  }

  Future<void> _recover(PlaybackFrame origin, int generation, Completer<void> cancel) async {
    final previous = _originUrl!;
    try {
      await _command(generation, cancel, () => player.open(previous, start: origin.completed ? Duration.zero : origin.position));
      if (!_current(generation)) return;
      await _restore(origin, generation, cancel);
      if (!_current(generation)) return;
      await _settings(origin, generation, cancel);
      if (_current(generation)) update(VideoSourceState(previous));
    } catch (_) {
      if (_current(generation)) update(VideoSourceState(state.url, failed: true));
    }
  }

  Future<bool> _restore(PlaybackFrame origin, int generation, Completer<void> cancel) async {
    try {
      return await restorePlaybackPosition(
        player,
        origin.completed ? Duration.zero : origin.position,
        cancelled: cancel.future,
        isCurrent: () => _current(generation),
        commands: _commands,
        readyTimeout: readyTimeout,
        seekTimeout: seekTimeout,
      );
    } catch (_) {
      return false;
    }
  }

  Future<void> _settings(PlaybackFrame origin, int generation, Completer<void> cancel) async {
    // media_kit preserves these across open. Read them again so a mute or speed
    // adjustment made during loading is not replaced by the older snapshot.
    final volume = player.frame.volume;
    if (volume.isFinite && volume >= 0 && volume <= 100) {
      await _command(generation, cancel, () => player.setVolume(volume));
    }
    if (!_current(generation)) return;
    final rate = player.frame.rate;
    if (rate.isFinite && rate >= 0.25 && rate <= 4) {
      await _command(generation, cancel, () => player.setRate(rate));
    }
    if (_current(generation) && _resumeAllowed && origin.playing && !origin.completed) {
      await _command(generation, cancel, player.play);
    }
  }

  /// Hidden videos and another active player must keep this source paused.
  void suppressResume() => _resumeAllowed = false;

  /// Synchronous cancellation runs before pool disposal can touch the player.
  void cancel() {
    if (_closed) return;
    _closed = true;
    _generation++;
    _cancel?.complete();
  }

  @override
  Future<void> destroy() async {
    cancel();
    await _operations;
    _release = _commands.whenIdle.then((_) => player.dispose());
    if (_commands.busy) {
      unawaited(_release!.catchError((Object _) {}));
    } else {
      await _release;
    }
    return super.destroy();
  }
}
