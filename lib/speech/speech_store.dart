import 'package:audio_session/audio_session.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/media/xta_audio_handler.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:xta/speech/tts_engines.dart';
import 'package:xta/speech/system_speech_engine.dart';
import 'package:xta/speech/text_language.dart';

/// What is being read aloud, if anything.
class SpeechPlayback {
  /// The title of what is being read, for the bar to name it.
  final String? title;

  final bool speaking;

  const SpeechPlayback({this.title, this.speaking = false});

  static const idle = SpeechPlayback();

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is SpeechPlayback &&
          other.title == title &&
          other.speaking == speaking;

  @override
  int get hashCode => Object.hash(title, speaking);
}

/// Reading aloud, owned by the app rather than by the screen that started it.
///
/// It used to live in the reader's [State], so closing the article stopped the
/// voice mid-sentence — which is not what "read this to me" means. Here it
/// outlives the screen: leave the article, go anywhere, and it keeps reading
/// until it finishes or you stop it.
///
/// Leaving the *app* is a different matter. Android keeps speaking while XTA
/// is in the background, but there is no media notification behind this and no
/// foreground service, so the system is free to reclaim the process.
class SpeechStore extends Store<SpeechPlayback> {
  final SystemSpeechEngine _system;

  /// Who can read aloud, best first. The system engine is last: it is what is
  /// left when nothing better is ready.
  final List<SpeechEngine> _engines;

  /// Bumped every time a reading starts or is stopped.
  ///
  /// Long text is spoken in pieces, because the platform silently truncates a
  /// very long utterance, and the loop feeding those pieces is what has to be
  /// called off. It checks this between chunks, so a loop belonging to an
  /// abandoned reading stops rather than talking over its successor.
  int _generation = 0;

  /// How long an engine may take to get ready before it counts as broken.
  final Duration prepareTimeout;

  SpeechStore({
    FlutterTts? tts,
    List<SpeechEngine> preferred = const [],
    Duration startTimeout = SystemSpeechEngine.defaultStartTimeout,
    Duration prepareTimeout = const Duration(seconds: 20),
  }) : this._(
         SystemSpeechEngine(tts ?? FlutterTts(), startTimeout: startTimeout),
         preferred,
         prepareTimeout,
       );

  SpeechStore._(
    SystemSpeechEngine system,
    List<SpeechEngine> preferred,
    this.prepareTimeout,
  ) : _system = system,
      _engines = [...preferred, system],
      super(SpeechPlayback.idle);

  /// Exposed for the voice picker, which has to ask the platform what it can
  /// speak with. Use this one rather than a new `FlutterTts()`: every new
  /// instance takes the platform callbacks away from the last.
  FlutterTts get tts => _system.tts;

  void _finished() {
    audioHandler?.clearSession();
    if (state.speaking) {
      update(SpeechPlayback.idle);
    }
  }

  /// Reads [text] aloud, replacing whatever was being read.
  ///
  /// Returns false when there was nothing to say or no engine would speak —
  /// callers can nudge the reader toward voice settings. Completes when the
  /// reading ends; it never waits on an engine that went quiet.
  Future<bool> speak({
    required String title,
    required String text,
    required TtsChoice choice,
  }) async {
    await stop();
    if (text.trim().isEmpty) return false;

    final generation = _generation;
    final language = detectTextLanguage(text);
    final engine = await _prepare(choice, language);
    if (generation != _generation) return true;
    if (engine == null) return false;

    await _prepareSpeechAudio();
    _bindLockscreenStop(title);
    update(SpeechPlayback(title: title, speaking: true));

    final chunks = chunkForSpeech(text, maxChars: engine.maxChunkChars);
    final read = await _readChunks(
      engine,
      chunks,
      retry: () => engine.prepare(choice, textLanguage: language),
      generation: generation,
    );
    if (generation == _generation) _finished();
    return read;
  }

  /// The first engine that is ready to read, or null when none is.
  Future<SpeechEngine?> _prepare(TtsChoice choice, String? language) async {
    for (final engine in _engines) {
      final ready = await engine
          .prepare(choice, textLanguage: language)
          .timeout(prepareTimeout, onTimeout: () => false)
          .catchError((_) => false);
      if (ready) return engine;
    }
    return null;
  }

  /// Speaks [chunks] in order. False when the engine failed; a reading that
  /// was stopped or superseded is not a failure.
  Future<bool> _readChunks(
    SpeechEngine engine,
    List<String> chunks, {
    required Future<bool> Function() retry,
    required int generation,
  }) async {
    for (var i = 0; i < chunks.length; i++) {
      if (generation != _generation) return true;
      final saying = engine.say(chunks[i]);
      // After say, so the chunk being read is prepared before the next one.
      if (engine is SpeechLookahead && i + 1 < chunks.length) {
        (engine as SpeechLookahead).upcoming(chunks[i + 1]);
      }
      var outcome = await saying;
      if (outcome == UtteranceOutcome.failed &&
          i == 0 &&
          generation == _generation) {
        // Engines sometimes refuse the first utterance after binding.
        await retry();
        outcome = await engine.say(chunks[i]);
      }
      if (outcome == UtteranceOutcome.cancelled) return true;
      if (outcome == UtteranceOutcome.failed) {
        return generation != _generation;
      }
    }
    return true;
  }

  /// Binds a lockscreen stop control, but does not mark the media session
  /// playing: that requests exclusive AUDIOFOCUS_GAIN and mutes Sherpa,
  /// which speaks from another process.
  void _bindLockscreenStop(String title) {
    audioHandler?.bindSession(
      title: title,
      binding: (
        onPlay: null,
        onPause: null,
        onStop: () => stop(),
        onSeek: null,
      ),
    );
  }

  Future<void> stop() async {
    _generation++;
    audioHandler?.clearSession();
    if (state.speaking) {
      update(SpeechPlayback.idle);
    }
    for (final engine in _engines) {
      await engine.stop();
    }
  }

  /// Drop exclusive media focus so a third-party engine (Sherpa) can be heard.
  Future<void> _prepareSpeechAudio() async {
    try {
      final session = await AudioSession.instance;
      await session.configure(AudioSessionConfiguration.speech());
      await session.setActive(true);
    } catch (_) {
      // Tests and desktop have no session; speaking still works.
    }
  }
}

/// The rest of [full] starting at [needle] (a paragraph the reader held).
///
/// If the needle is not in the article, the needle itself is what to speak —
/// the web view already sent the remainder from that block.
String textFromHere(String full, String needle) {
  final clip = needle.trim();
  final hay = full.trim();
  if (clip.isEmpty) return hay;
  if (hay.isEmpty) return clip;

  var i = hay.indexOf(clip);
  if (i < 0) {
    final line = clip.split(RegExp(r'\n+')).first.trim();
    if (line.length >= 12) i = hay.indexOf(line);
  }
  if (i < 0) {
    final short = clip.length > 48 ? clip.substring(0, 48) : clip;
    i = hay.indexOf(short);
  }
  if (i <= 0) return i == 0 ? hay : clip;
  return hay.substring(i);
}

/// Splits text into utterances the platform will actually finish.
///
/// Sentence boundaries first, so a break never lands mid-word; a single
/// sentence longer than the limit is cut where it has to be.
List<String> chunkForSpeech(String text, {int maxChars = 3500}) {
  final trimmed = text.trim();
  if (trimmed.isEmpty) {
    return const [];
  }
  if (trimmed.length <= maxChars) {
    return [trimmed];
  }

  final chunks = <String>[];
  final buffer = StringBuffer();

  for (final sentence in trimmed.split(RegExp(r'(?<=[.!?。！？])\s+'))) {
    if (sentence.length > maxChars) {
      if (buffer.isNotEmpty) {
        chunks.add(buffer.toString());
        buffer.clear();
      }
      for (var i = 0; i < sentence.length; i += maxChars) {
        chunks.add(
          sentence.substring(i, (i + maxChars).clamp(0, sentence.length)),
        );
      }
      continue;
    }

    if (buffer.length + sentence.length + 1 > maxChars) {
      chunks.add(buffer.toString());
      buffer.clear();
    }
    if (buffer.isNotEmpty) {
      buffer.write(' ');
    }
    buffer.write(sentence);
  }

  if (buffer.isNotEmpty) {
    chunks.add(buffer.toString());
  }

  return chunks;
}
