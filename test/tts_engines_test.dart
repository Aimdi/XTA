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
}
