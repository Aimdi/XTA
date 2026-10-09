import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:intl/intl.dart';
import 'package:xta/speech/tts_engines.dart';
import 'package:xta/speech/tts_settings.dart';

/// Reads aloud with whatever text-to-speech engine Android has installed.
///
/// flutter_tts can leave a `speak` call waiting forever on Android: when the
/// engine refuses an utterance, reports an error, or is not bound at all, the
/// plugin parks the call and never answers it. Awaiting that future is what
/// made reading aloud in Substack sit on "Reading aloud" in silence, with no
/// hint that anything was wrong. So completion is not awaited from the
/// platform call here: each utterance is followed through the start, done and
/// error callbacks, and one that has not started within [startTimeout] counts
/// as failed — which the store can report.
class SystemSpeechEngine implements SpeechEngine {
  static const _channel = MethodChannel('flutter_tts');

  final FlutterTts _tts;
  final Duration startTimeout;

  /// The utterance being spoken, if any.
  Completer<UtteranceOutcome>? _pending;
  var _started = false;
  Timer? _watchdog;

  /// The engine package this plugin instance is bound to, once XTA bound one.
  /// Null means whatever the system default was when the plugin started.
  String? _boundEngine;

  SystemSpeechEngine(this._tts, {this.startTimeout = defaultStartTimeout}) {
    _tts.setStartHandler(() => _started = true);
    _tts.setCompletionHandler(() => _settle(UtteranceOutcome.completed));
    _tts.setErrorHandler((_) => _settle(UtteranceOutcome.failed));
    // A cancel before this utterance started is the echo of the stop that
    // ended the previous one; only a cancel mid-utterance is about this one.
    _tts.setCancelHandler(() {
      if (_started) _settle(UtteranceOutcome.cancelled);
    });
  }

  /// Long enough for an engine that loads a voice model on first use.
  static const defaultStartTimeout = Duration(seconds: 20);

  FlutterTts get tts => _tts;

  @override
  int get maxChunkChars => 3500;

  @override
  Future<bool> prepare(TtsChoice choice, {String? textLanguage}) async {
    _claimCallbacks();
    await _tts.awaitSpeakCompletion(false);
    if (!await _hasAnyEngine()) return false;
    if (!await _bindEngine(choice)) return false;

    final language = await pickSpeakLanguage(
      _tts,
      textLanguage: textLanguage,
      voiceLocale: choice.voiceLocale,
      appLocale: languageTagForShortLocale(Intl.shortLocale(Intl.getCurrentLocale())),
    );
    await _applyLanguageAndVoice(choice, language);
    await _quietly(() => _tts.setVolume(1));
    await _quietly(() => _tts.setSpeechRate(choice.rate));
    return true;
  }

  @override
  Future<UtteranceOutcome> say(String chunk) {
    _settle(UtteranceOutcome.cancelled);
    final pending = _pending = Completer<UtteranceOutcome>();
    _started = false;
    _watchdog = Timer(startTimeout, () {
      if (_started) return;
      _settle(UtteranceOutcome.failed, only: pending);
      unawaited(_quietly(_tts.stop));
    });
    unawaited(
      _queue(chunk).then((queued) {
        if (!queued) _settle(UtteranceOutcome.failed, only: pending);
      }),
    );
    return pending.future;
  }

  @override
  Future<void> stop() async {
    _settle(UtteranceOutcome.cancelled);
    await _quietly(_tts.stop);
  }

  /// Ends the pending utterance with [outcome] — or, given [only], ends it
  /// only if it is still that one.
  void _settle(UtteranceOutcome outcome, {Completer<UtteranceOutcome>? only}) {
    if (only != null && !identical(_pending, only)) return;
    _watchdog?.cancel();
    _watchdog = null;
    final pending = _pending;
    _pending = null;
    if (pending != null && !pending.isCompleted) pending.complete(outcome);
  }

  /// Every `FlutterTts()` takes over the one platform channel's callbacks,
  /// and the settings screens used to make their own — after which this
  /// engine never heard an utterance finish. Take them back before speaking.
  void _claimCallbacks() => _channel.setMethodCallHandler(_tts.platformCallHandler);

  /// Queues [chunk]. A plugin that parks the call never answers; the start
  /// watchdog covers that.
  Future<bool> _queue(String chunk) async {
    try {
      return !speakRefused(await _tts.speak(chunk, focus: true));
    } catch (_) {
      return false;
    }
  }

  /// An Android phone without any engine (common on de-Googled systems)
  /// would otherwise accept the reading and never say a word.
  Future<bool> _hasAnyEngine() async {
    final engines = await _quietly(() => _tts.getEngines);
    final listed = engines is List && engines.whereType<String>().isNotEmpty;
    return listed || await readDefaultEngine(_tts) != null;
  }

  /// Binds the chosen engine — only when it differs from the bound one.
  ///
  /// flutter_tts builds a new platform TextToSpeech on every `setEngine` and
  /// never shuts the old one down, so doing it on every Listen leaked a
  /// service connection each time and made the engine start cold.
  Future<bool> _bindEngine(TtsChoice choice) async {
    final target = await resolveBoundEngine(_tts, choice.engine);
    final current = _boundEngine ?? await readDefaultEngine(_tts);
    if (target == null || target == current) return true;
    try {
      await _tts.setEngine(target);
      _boundEngine = target;
      // setLanguage right after setEngine is a no-op on some Android builds
      // until the new engine has answered something.
      await _quietly(() => _tts.getLanguages);
      return true;
    } catch (_) {
      return !isSherpaEngine(choice.engine);
    }
  }

  /// The language first, then the voice: Android's setLanguage picks that
  /// language's default voice, so the other order threw the chosen voice
  /// away. A voice in another language than the text is left out.
  Future<void> _applyLanguageAndVoice(TtsChoice choice, String? language) async {
    if (language != null) {
      await _quietly(() => _tts.setLanguage(language));
    }
    if (choice.hasVoice && voiceSuitsLanguage(choice.voiceLocale!, language)) {
      await _quietly(() => _tts.setVoice({'name': choice.voiceName!, 'locale': choice.voiceLocale!}));
    }
  }
}

/// flutter_tts answers 1 for a queued utterance and 0 (or false) for one the
/// engine turned away.
bool speakRefused(dynamic result) => result == 0 || result == false;

/// True when a voice for [voiceLocale] can read text in [language].
bool voiceSuitsLanguage(String voiceLocale, String? language) {
  if (language == null) return true;
  String base(String tag) => tag.replaceAll('_', '-').split('-').first.toLowerCase();
  return base(voiceLocale) == base(language);
}

Future<T?> _quietly<T>(Future<T> Function() call) async {
  try {
    return await call();
  } catch (_) {
    return null;
  }
}
