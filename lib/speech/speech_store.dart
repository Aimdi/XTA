import 'package:audio_session/audio_session.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/media/xta_audio_handler.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:intl/intl.dart';
import 'package:xta/speech/tts_engines.dart';
import 'package:xta/speech/tts_settings.dart';

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
  final FlutterTts _tts;

  /// Bumped every time a reading starts or is stopped.
  ///
  /// Long text is spoken in pieces, because the platform silently truncates a
  /// very long utterance, and the loop feeding those pieces is what has to be
  /// called off. It checks this between chunks, so a loop belonging to an
  /// abandoned reading stops rather than talking over its successor.
  int _generation = 0;

  SpeechStore({FlutterTts? tts})
    : _tts = tts ?? FlutterTts(),
      super(SpeechPlayback.idle) {
    _tts.awaitSpeakCompletion(true);
    _tts.setCancelHandler(_finished);
    _tts.setErrorHandler((_) => _finished());
  }

  /// Exposed for the voice picker, which has to ask the platform what it can
  /// speak with — and there is only one engine, this one.
  FlutterTts get tts => _tts;

  void _finished() {
    audioHandler?.clearSession();
    update(SpeechPlayback.idle);
  }

  /// Reads [text] aloud, replacing whatever was being read.
  ///
  /// Returns false when there was nothing to say or the engine refused to
  /// start — callers can nudge the reader toward voice settings.
  Future<bool> speak({
    required String title,
    required String text,
    required TtsChoice choice,
  }) async {
    await stop();

    final chunks = chunkForSpeech(text);
    if (chunks.isEmpty) {
      return false;
    }

    try {
      // Shared instance keeps Android from dropping the utterance when the
      // activity is paused or another media session briefly takes focus.
      await _tts.setSharedInstance(true);
    } catch (_) {
      // Desktop / older engines may not expose this.
    }

    if (!await _applyVoice(choice)) {
      return false;
    }

    await _prepareSpeechAudio();

    final generation = ++_generation;
    // Bind a lockscreen stop control, but do not mark the media session
    // playing: that requests exclusive AUDIOFOCUS_GAIN and mutes Sherpa,
    // which speaks from another process.
    audioHandler?.bindSession(
      title: title,
      binding: (
        onPlay: null,
        onPause: null,
        onStop: () => stop(),
        onSeek: null,
      ),
    );
    update(SpeechPlayback(title: title, speaking: true));

    try {
      for (final chunk in chunks) {
        if (generation != _generation) {
          return true;
        }
        final result = await _tts.speak(chunk, focus: true);
        if (result == 0 || result == false) {
          // Platform reported failure to queue the utterance.
          if (generation == _generation) {
            _finished();
          }
          return false;
        }
      }
    } catch (_) {
      if (generation == _generation) {
        _finished();
      }
      return false;
    }

    if (generation == _generation) {
      _finished();
    }
    return true;
  }

  Future<void> stop() async {
    _generation++;
    audioHandler?.clearSession();
    if (state.speaking) {
      update(SpeechPlayback.idle);
    }
    await _tts.stop();
  }

  /// The reader's chosen engine and voice, falling back to a language the
  /// bound engine actually speaks — never insist on de-DE when the voice is
  /// English-only Sherpa / Next-gen Kaldi.
  Future<bool> _applyVoice(TtsChoice choice) async {
    final applied = await applyTtsChoice(_tts, choice);
    if (isSherpaEngine(choice.engine) && !applied) {
      return false;
    }

    final language = await pickSpeakLanguage(
      _tts,
      voiceLocale: choice.voiceLocale,
      appLocale: languageTagForShortLocale(
        Intl.shortLocale(Intl.getCurrentLocale()),
      ),
    );
    if (language != null) {
      try {
        await _tts.setLanguage(language);
      } catch (_) {}
    }
    await _tts.setSpeechRate(choice.rate);
    return true;
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
