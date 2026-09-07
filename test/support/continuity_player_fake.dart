import 'dart:async';

import 'package:xta/media/continuity_player.dart';

class FakeContinuityPlayer implements ContinuityPlayer {
  final controller = StreamController<void>.broadcast(sync: true);
  final opens = <({String url, Duration start})>[];
  final commands = <String>[];
  bool honorStart = true;
  bool honorSeek = true;
  bool publishDuration = true;
  bool disposed = false;
  Future<void> Function(String url)? beforeOpen;
  Duration mediaDuration = const Duration(minutes: 30);
  @override
  PlaybackFrame frame;

  FakeContinuityPlayer({this.frame = const PlaybackFrame()});

  @override
  Stream<void> get changes => controller.stream;

  void emit({Duration? position, Duration? duration, bool? playing, bool? completed,
    bool? failed, double? volume, double? rate}) {
    frame = PlaybackFrame(position: position ?? frame.position, duration: duration ?? frame.duration,
      playing: playing ?? frame.playing, completed: completed ?? frame.completed,
      failed: failed ?? frame.failed, volume: volume ?? frame.volume, rate: rate ?? frame.rate);
    controller.add(null);
  }

  void record(String command) {
    if (disposed) throw StateError('Playback touched after disposal');
    commands.add(command);
  }

  @override
  Future<void> open(String url, {Duration start = Duration.zero}) async {
    record('open:$url');
    opens.add((url: url, start: start));
    await beforeOpen?.call(url);
    emit(position: honorStart ? start : Duration.zero,
      duration: publishDuration ? mediaDuration : Duration.zero,
      playing: false, completed: false, failed: false);
  }

  @override
  Future<void> play() async { record('play'); emit(playing: true); }
  @override
  Future<void> pause() async { record('pause'); emit(playing: false); }
  @override
  Future<void> seek(Duration position) async {
    record('seek:${position.inMilliseconds}');
    if (honorSeek) emit(position: position);
  }
  @override
  Future<void> setVolume(double value) async { record('volume:$value'); emit(volume: value); }
  @override
  Future<void> setRate(double value) async { record('rate:$value'); emit(rate: value); }
  @override
  Future<void> stop() async { record('stop'); emit(playing: false, position: Duration.zero); }
  @override
  Future<void> dispose() async { record('dispose'); disposed = true; await controller.close(); }
}
