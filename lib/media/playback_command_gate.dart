import 'dart:async';

/// A timed-out native command cannot be cancelled. Keep it quarantined until it
/// settles, so a late source/seek cannot mutate a subsequently opened source.
class PlaybackCommandGate {
  final Future<void> Function() onAbandonedSettled;
  Future<void>? _pending;

  PlaybackCommandGate({required this.onAbandonedSettled});

  bool get busy => _pending != null;
  Future<void> get whenIdle => _pending ?? Future.value();

  Future<void> run(
    Future<void> Function() command, {
    required Future<void> cancelled,
    required Duration timeout,
  }) async {
    if (busy) throw StateError('A previous native playback command is still pending');
    final result = Completer<void>();
    final idle = Completer<void>();
    _pending = idle.future;
    var abandoned = false;
    void abandon(Object error) {
      if (result.isCompleted) return;
      abandoned = true;
      result.completeError(error);
    }

    final timer = Timer(timeout, () => abandon(TimeoutException('Native playback command timed out')));
    cancelled.then((_) => abandon(const PlaybackCommandCancelled()));
    Future<void> settle([Object? error, StackTrace? stack]) async {
      if (abandoned) {
        try {
          await onAbandonedSettled();
        } catch (_) {
          /* Keep disposal safe after the command settles. */
        }
      }
      _pending = null;
      idle.complete();
      if (result.isCompleted) return;
      if (error == null) {
        result.complete();
      } else {
        result.completeError(error, stack);
      }
    }

    Future<void>.sync(command).then((_) => settle(), onError: (Object error, StackTrace stack) => settle(error, stack));
    try {
      await result.future;
    } finally {
      timer.cancel();
    }
  }
}

class PlaybackCommandCancelled implements Exception {
  const PlaybackCommandCancelled();
}
