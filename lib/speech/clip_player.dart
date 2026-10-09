import 'dart:async';

import 'package:xta/media/continuity_player.dart';
import 'package:xta/speech/offline_speech_engine.dart';
import 'package:xta/speech/tts_engines.dart';

/// Plays synthesised clips through the same media_kit player port the
/// podcast player uses.
///
/// The player is made on first use, so nothing loads libmpv until a
/// downloaded voice actually speaks.
class ContinuityClipPlayer implements ClipPlayer {
  final ContinuityPlayer Function() _create;

  /// A clip that has not started by then counts as failed.
  final Duration startTimeout;

  ContinuityPlayer? _player;
  Completer<UtteranceOutcome>? _pending;

  ContinuityClipPlayer({ContinuityPlayer Function()? create, this.startTimeout = const Duration(seconds: 10)})
    : _create = create ?? MediaKitContinuityPlayer.createPodcast;

  @override
  Future<UtteranceOutcome> play(String path) async {
    _settle(UtteranceOutcome.cancelled);
    final player = _player ??= _create();
    final pending = _pending = Completer<UtteranceOutcome>();
    var started = false;
    final watchdog = Timer(startTimeout, () {
      if (!started) _settle(UtteranceOutcome.failed, only: pending);
    });
    final changes = player.changes.listen((_) {
      final frame = player.frame;
      started = started || frame.playing;
      if (frame.failed) _settle(UtteranceOutcome.failed, only: pending);
      if (started && frame.completed) {
        _settle(UtteranceOutcome.completed, only: pending);
      }
    });
    unawaited(
      _start(player, path).then((ok) {
        if (!ok) _settle(UtteranceOutcome.failed, only: pending);
      }),
    );
    final outcome = await pending.future;
    watchdog.cancel();
    await changes.cancel();
    return outcome;
  }

  /// Not awaited by [play]: a player that never answers is the watchdog's
  /// business, and must not keep the reading waiting.
  Future<bool> _start(ContinuityPlayer player, String path) async {
    try {
      await player.open(path);
      await player.play();
      return true;
    } catch (_) {
      return false;
    }
  }

  /// Ends the clip being waited on — or, given [only], only if it is that one.
  void _settle(UtteranceOutcome outcome, {Completer<UtteranceOutcome>? only}) {
    final pending = _pending;
    if (pending == null || (only != null && !identical(pending, only))) return;
    _pending = null;
    if (!pending.isCompleted) pending.complete(outcome);
  }

  @override
  Future<void> stop() async {
    _settle(UtteranceOutcome.cancelled);
    try {
      await _player?.stop();
    } catch (_) {}
  }
}
