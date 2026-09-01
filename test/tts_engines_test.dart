import 'package:flutter_test/flutter_test.dart';
import 'package:xta/speech/tts_engines.dart';

void main() {
  group('isSherpaEngine', () {
    test('recognises the official package and any sherpa id', () {
      expect(isSherpaEngine(sherpaOnnxTtsEngine), isTrue);
      expect(isSherpaEngine('com.example.sherpa.tts'), isTrue);
      expect(isSherpaEngine('com.google.android.tts'), isFalse);
      expect(isSherpaEngine(null), isFalse);
      expect(isSherpaEngine(''), isFalse);
    });

    test('recognises Next-gen Kaldi and k2fsa package ids', () {
      expect(isSherpaEngine('TTS Engine: Next-gen Kaldi'), isTrue);
      expect(isSherpaEngine('com.k2fsa.other'), isTrue);
      expect(isSherpaEngine('com.google.android.tts'), isFalse);
    });
  });

  group('pickInstalledSherpaEngine', () {
    test('prefers the official package over a friendly alias', () {
      expect(
        pickInstalledSherpaEngine([
          'com.google.android.tts',
          'org.example.sherpa',
          sherpaOnnxTtsEngine,
        ]),
        sherpaOnnxTtsEngine,
      );
    });

    test('falls back to any sherpa-named engine', () {
      expect(
        pickInstalledSherpaEngine([
          'com.google.android.tts',
          'app.sherpa.onnx',
        ]),
        'app.sherpa.onnx',
      );
    });

    test('is null when Sherpa is not installed', () {
      expect(pickInstalledSherpaEngine(['com.google.android.tts']), isNull);
      expect(sherpaIsInstalled(const []), isFalse);
    });
  });

  group('resolveSherpaEngineId', () {
    test('uses the installed package when the platform listed one', () {
      expect(
        resolveSherpaEngineId(['org.woheller69.sherpa']),
        'org.woheller69.sherpa',
      );
    });

    test('keeps the official id so setEngine still has something to try', () {
      expect(resolveSherpaEngineId(const []), sherpaOnnxTtsEngine);
    });
  });

  group('nonSherpaEngines', () {
    test('drops Sherpa so the picker does not list it twice', () {
      expect(
        nonSherpaEngines([
          sherpaOnnxTtsEngine,
          'com.google.android.tts',
          'app.sherpa',
        ]),
        ['com.google.android.tts'],
      );
    });
  });

  group('ttsFailureActionFor', () {
    test('offers Sherpa when the broken path was the system voice', () {
      expect(ttsFailureActionFor(null), TtsFailureAction.useSherpa);
      expect(
        ttsFailureActionFor('com.google.android.tts'),
        TtsFailureAction.useSherpa,
      );
    });

    test('offers install when Sherpa was already chosen', () {
      expect(
        ttsFailureActionFor(sherpaOnnxTtsEngine),
        TtsFailureAction.installSherpa,
      );
    });
  });

  group('languageTagForShortLocale', () {
    test('maps short locales to a BCP 47 tag', () {
      expect(languageTagForShortLocale('de'), 'de-DE');
      expect(languageTagForShortLocale('en'), 'en-EN');
      expect(languageTagForShortLocale('zh'), 'zh-CN');
      expect(languageTagForShortLocale('en_GB'), 'en-GB');
      expect(languageTagForShortLocale('de-DE'), 'de-DE');
    });
  });

  group('speakLanguageCandidates', () {
    test('tries the chosen voice, then the app, then English', () {
      expect(
        speakLanguageCandidates(voiceLocale: 'en-US', appLocale: 'de-DE'),
        ['en-US', 'en', 'de-DE', 'de'],
      );
    });

    test('does not insist on German when the app is German', () {
      final tags = speakLanguageCandidates(appLocale: 'de-DE');
      expect(tags.first, 'de-DE');
      expect(tags, containsAll(['de', 'en-US', 'en']));
    });
  });

  group('ttsLanguageAvailable', () {
    test('treats Android LANG_AVAILABLE and flutter_tts bools as success', () {
      expect(ttsLanguageAvailable(true), isTrue);
      expect(ttsLanguageAvailable(0), isTrue);
      expect(ttsLanguageAvailable(1), isTrue);
      expect(ttsLanguageAvailable(2), isTrue);
      expect(ttsLanguageAvailable(-1), isFalse);
      expect(ttsLanguageAvailable(-2), isFalse);
      expect(ttsLanguageAvailable(false), isFalse);
      expect(ttsLanguageAvailable(null), isFalse);
    });
  });

  group('mergeTtsEngines', () {
    test('adds the system default when getEngines hid it', () {
      expect(
        mergeTtsEngines(['com.google.android.tts'], sherpaOnnxTtsEngine),
        ['com.google.android.tts', sherpaOnnxTtsEngine],
      );
      expect(sherpaIsInstalled(mergeTtsEngines(const [], sherpaOnnxTtsEngine)), isTrue);
    });
  });

  group('pickListedSpeakLanguage', () {
    test('skips de-DE when the engine only has English', () async {
      final picked = await pickListedSpeakLanguage(
        candidates: speakLanguageCandidates(appLocale: 'de-DE'),
        installed: const ['en-US'],
        available: (tag) async => tag.startsWith('en'),
      );
      expect(picked, 'en-US');
    });

    test('uses the first installed language when nothing matches', () async {
      final picked = await pickListedSpeakLanguage(
        candidates: const ['de-DE'],
        installed: const ['eng'],
        available: (_) async => false,
      );
      expect(picked, 'eng');
    });
  });
}
