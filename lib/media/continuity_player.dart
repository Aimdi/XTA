import 'dart:async';

import 'package:media_kit/media_kit.dart' as mk;

class PlaybackFrame {
  final Duration position;
  final Duration duration;
  final bool playing;
  final bool completed;
  final bool failed;
  final double volume;
  final double rate;

  const PlaybackFrame({
    this.position = Duration.zero,
    this.duration = Duration.zero,
    this.playing = false,
    this.completed = false,
    this.failed = false,
    this.volume = 100,
    this.rate = 1,
  });
}

/// Small playback port: continuity policies can be verified without native mpv.
abstract class ContinuityPlayer {
  PlaybackFrame get frame;
  Stream<void> get changes;
  Future<void> open(String url, {Duration start = Duration.zero});
  Future<void> play();
  Future<void> pause();
  Future<void> seek(Duration position);
  Future<void> setVolume(double value);
  Future<void> setRate(double value);
  Future<void> stop();
  Future<void> dispose();
}

class MediaKitContinuityPlayer implements ContinuityPlayer {
  final mk.Player player;
  final Map<String, String>? httpHeaders;
  final bool ownsPlayer;
  final _changes = StreamController<void>.broadcast(sync: true);
  final List<StreamSubscription<dynamic>> _subscriptions = [];
  bool _failed = false;

  MediaKitContinuityPlayer(this.player, {this.httpHeaders, this.ownsPlayer = false}) {
    for (final stream in <Stream<dynamic>>[
      player.stream.position,
      player.stream.duration,
      player.stream.playing,
      player.stream.completed,
    ]) {
      _subscriptions.add(stream.listen((_) => _changes.add(null)));
    }
    _subscriptions.add(player.stream.error.listen((_) {
      _failed = true;
      _changes.add(null);
    }));
  }

  static ContinuityPlayer createPodcast() {
    mk.MediaKit.ensureInitialized();
    return MediaKitContinuityPlayer(mk.Player(), ownsPlayer: true);
  }

  @override
  PlaybackFrame get frame => PlaybackFrame(
    position: player.state.position,
    duration: player.state.duration,
    playing: player.state.playing,
    completed: player.state.completed,
    failed: _failed,
    volume: player.state.volume,
    rate: player.state.rate,
  );

  @override
  Stream<void> get changes => _changes.stream;

  @override
  Future<void> open(String url, {Duration start = Duration.zero}) {
    _failed = false;
    return player.open(mk.Media(url, start: start, httpHeaders: httpHeaders), play: false);
  }

  @override
  Future<void> play() => player.play();
  @override
  Future<void> pause() => player.pause();
  @override
  Future<void> seek(Duration position) => player.seek(position);
  @override
  Future<void> setVolume(double value) => player.setVolume(value);
  @override
  Future<void> setRate(double value) => player.setRate(value);
  @override
  Future<void> stop() => player.stop();

  @override
  Future<void> dispose() async {
    for (final subscription in _subscriptions) {
      await subscription.cancel();
    }
    await _changes.close();
    if (ownsPlayer) {
      await player.stop();
      await Future<void>.delayed(const Duration(milliseconds: 250));
      await player.dispose();
    }
  }
}
