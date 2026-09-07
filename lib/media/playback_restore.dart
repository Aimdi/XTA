import 'dart:async';

import 'package:xta/media/continuity_player.dart';

Duration playbackResumeTarget(Duration position, Duration duration) {
  if (position <= Duration.zero || duration <= Duration.zero) return Duration.zero;
  final ceiling = duration - const Duration(milliseconds: 500);
  if (ceiling <= Duration.zero) return Duration.zero;
  return position > ceiling ? ceiling : position;
}

/// Source open accepts an initial demux position, but native readiness arrives
/// later. Check the observed position and seek only after a duration is known.
Future<bool> restorePlaybackPosition(
  ContinuityPlayer player,
  Duration position, {
  required Future<void> cancelled,
  required bool Function() isCurrent,
  Duration readyTimeout = const Duration(seconds: 5),
  Duration seekTimeout = const Duration(seconds: 2),
}) async {
  if (!isCurrent()) return false;
  if (position <= Duration.zero) return true;
  final ready = await waitForPlayback(
    player, () => player.frame.failed || player.frame.duration > Duration.zero,
    cancelled: cancelled, timeout: readyTimeout,
  );
  if (!ready || !isCurrent() || player.frame.failed) return false;
  final target = playbackResumeTarget(position, player.frame.duration);
  bool reached() => (player.frame.position - target).abs() <= const Duration(seconds: 1);
  if (reached()) return true;
  await player.seek(target);
  if (!isCurrent()) return false;
  return waitForPlayback(player, reached, cancelled: cancelled, timeout: seekTimeout);
}

Future<bool> waitForPlayback(
  ContinuityPlayer player,
  bool Function() ready, {
  required Future<void> cancelled,
  required Duration timeout,
}) async {
  if (ready()) return true;
  final result = Completer<bool>();
  void check() { if (!result.isCompleted && ready()) result.complete(true); }
  final subscription = player.changes.listen((_) => check());
  final timer = Timer(timeout, () { if (!result.isCompleted) result.complete(false); });
  cancelled.then((_) { if (!result.isCompleted) result.complete(false); });
  check();
  try {
    return await result.future;
  } finally {
    timer.cancel();
    await subscription.cancel();
  }
}
