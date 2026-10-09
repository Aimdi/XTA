import 'dart:async';

import 'package:xta/speech/tts_engines.dart';
import 'package:xta/speech/voice_download_store.dart';

/// Turns text into a playable audio file with one loaded voice.
abstract interface class VoiceSynthesizer {
  /// Speaks [text] at [speed] (1 is the voice's own pace) into an audio file
  /// and returns its path.
  Future<String> synthesize(String text, {required double speed});

  Future<void> dispose();
}

/// Plays one synthesised clip at a time.
abstract interface class ClipPlayer {
  /// Plays the file at [path]; completes when it ends, fails or is stopped.
  Future<UtteranceOutcome> play(String path);

  Future<void> stop();
}

typedef SynthesizerLoader = Future<VoiceSynthesizer> Function(InstalledVoice);

/// sherpa-onnx's speed for the reader's speech rate.
///
/// The rate slider is flutter_tts's scale, on which Android plays 0.5 at
/// normal speed; sherpa's 1.0 is the voice's own pace.
double sherpaSpeedForRate(double rate) => (rate * 2).clamp(0.5, 2.0);

/// Reads aloud with a voice downloaded to the device (sherpa-onnx).
///
/// It only offers to read when the reader left offline voices on and a
/// downloaded voice speaks the article's language; otherwise [prepare] says
/// no and the speech store falls back to the system engine. The same happens
/// when the voice fails to load.
///
/// Synthesis runs ahead of playback: while one chunk plays, the next one
/// ([upcoming]) is being synthesised, so sentences follow without a gap.
class OfflineSpeechEngine implements SpeechEngine, SpeechLookahead {
  final Future<InstalledVoice?> Function(String? language) _voiceFor;
  final bool Function() _enabled;
  final String? Function() _appLanguage;
  final SynthesizerLoader _load;
  final ClipPlayer _player;
  final Future<void> Function(String path) _discard;

  String? _voiceId;
  Future<VoiceSynthesizer>? _synthesizer;
  double _speed = 1;

  /// Clips asked for but not yet played, in reading order.
  final _queue = <({String text, Future<String?> clip})>[];

  /// Bumped by [stop], so a clip that finishes synthesising afterwards is
  /// not played.
  var _epoch = 0;

  OfflineSpeechEngine({
    required this._voiceFor,
    required this._enabled,
    required this._load,
    required this._player,
    required this._discard,
    String? Function()? appLanguage,
  }) : _appLanguage = appLanguage ?? (() => null);

  /// One sentence or two: short enough that the first one is ready quickly.
  @override
  int get maxChunkChars => 300;

  @override
  Future<bool> prepare(TtsChoice choice, {String? textLanguage}) async {
    if (!_enabled()) return false;
    final installed = await _voiceFor(textLanguage ?? _appLanguage());
    if (installed == null) return false;
    _speed = sherpaSpeedForRate(choice.rate);
    try {
      await _loadVoice(installed);
      return true;
    } catch (_) {
      await _unload();
      return false;
    }
  }

  Future<void> _loadVoice(InstalledVoice installed) async {
    if (_voiceId != installed.voice.id) {
      await _unload();
      _voiceId = installed.voice.id;
      _synthesizer = _load(installed);
    }
    await _synthesizer;
  }

  Future<void> _unload() async {
    final synthesizer = _synthesizer;
    _voiceId = null;
    _synthesizer = null;
    if (synthesizer == null) return;
    try {
      await (await synthesizer).dispose();
    } catch (_) {}
  }

  @override
  void upcoming(String chunk) {
    if (_synthesizer == null) return;
    _queue.add((text: chunk, clip: _synthesize(chunk)));
  }

  @override
  Future<UtteranceOutcome> say(String chunk) async {
    if (_synthesizer == null) return UtteranceOutcome.failed;
    final epoch = _epoch;
    final path = await _clipFor(chunk);
    if (epoch != _epoch) {
      if (path != null) unawaited(_discard(path));
      return UtteranceOutcome.cancelled;
    }
    if (path == null) return UtteranceOutcome.failed;
    try {
      return await _player.play(path);
    } finally {
      unawaited(_discard(path));
    }
  }

  /// The clip for [chunk]: the one already on its way if it was announced,
  /// else a new one. Clips announced for something else are dropped.
  Future<String?> _clipFor(String chunk) {
    while (_queue.isNotEmpty) {
      final next = _queue.removeAt(0);
      if (next.text == chunk) return next.clip;
      _dropClip(next.clip);
    }
    return _synthesize(chunk);
  }

  Future<String?> _synthesize(String chunk) async {
    try {
      final synthesizer = await _synthesizer!;
      return await synthesizer.synthesize(chunk, speed: _speed);
    } catch (_) {
      return null;
    }
  }

  void _dropClip(Future<String?> clip) =>
      unawaited(clip.then((path) => path == null ? null : _discard(path)));

  @override
  Future<void> stop() async {
    _epoch++;
    while (_queue.isNotEmpty) {
      _dropClip(_queue.removeAt(0).clip);
    }
    await _player.stop();
  }
}
