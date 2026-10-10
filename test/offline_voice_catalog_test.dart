import 'package:flutter_test/flutter_test.dart';
import 'package:xta/speech/offline_speech_engine.dart';
import 'package:xta/speech/offline_voice_catalog.dart';
import 'package:xta/speech/sherpa_voice_synthesizer.dart';
import 'package:xta/speech/voice_archive.dart';

/// Kokoro as it would be catalogued; not offered yet, but loadable.
final kokoro = OfflineVoice(
  id: 'en-kokoro',
  name: 'Kokoro',
  locale: 'en_US',
  url: Uri.https('github.com', '/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-en-v0_19.tar.bz2'),
  archiveBytes: 103248205,
  sha256: 'c9f0dd393615805b0bab050c340834d5e684e732aec91c0e860cd30e982c08bd',
  licence: 'Apache-2.0',
  kind: VoiceModelKind.kokoro,
  archiveRoot: 'kokoro-int8-en-v0_19',
  model: 'model.int8.onnx',
  voices: 'voices.bin',
  preference: 1,
);

void main() {
  group('catalog', () {
    test('ids are unique and usable as folder names', () {
      final ids = offlineVoiceCatalog.map((voice) => voice.id).toList();
      expect(ids.toSet(), hasLength(ids.length));
      for (final id in ids) {
        expect(id, matches(RegExp(r'^[a-z0-9-]+$')));
      }
    });

    test('every voice is pinned to k2-fsa tts-models over https', () {
      for (final voice in offlineVoiceCatalog) {
        expect(voice.url.scheme, 'https');
        expect(voice.url.host, 'github.com');
        expect(
          voice.url.path,
          '/k2-fsa/sherpa-onnx/releases/download/tts-models/'
          '${voice.archiveRoot}.tar.bz2',
        );
      }
    });

    test('every voice has a confirmed checksum and size', () {
      for (final voice in offlineVoiceCatalog) {
        expect(voice.sha256, matches(RegExp(r'^[0-9a-f]{64}$')));
        expect(voice.archiveBytes, greaterThan(1 << 20));
      }
    });

    test('names its model files and licence', () {
      for (final voice in offlineVoiceCatalog) {
        expect(voice.model, endsWith('.onnx'));
        expect(voice.requiredFiles, contains('tokens.txt'));
        expect(voice.requiredFiles, contains('espeak-ng-data/phontab'));
        expect(voice.licence, isNotEmpty);
        expect(voice.voices != null, voice.kind == VoiceModelKind.kokoro, reason: 'only Kokoro has speaker embeddings');
      }
    });

    test('offers German and English', () {
      final languages = offlineVoiceCatalog.map((voice) => voice.language);
      expect(languages, containsAll(['de', 'en']));
    });

    test('reads German with the neutral Thorsten speaker', () {
      expect(offlineVoiceById('de-thorsten-emotional')!.speakerId, 4);
    });
  });

  group('picking a voice', () {
    final german = offlineVoiceById('de-thorsten-emotional')!;
    final libritts = offlineVoiceById('en-libritts-r')!;

    test('matches the language whatever the region', () {
      expect(pickOfflineVoice([german, libritts], 'de'), german);
      expect(pickOfflineVoice([german, libritts], 'en-GB'), libritts);
      expect(pickOfflineVoice([german, libritts], 'de_AT'), german);
    });

    test('finds nothing for a language no voice speaks', () {
      expect(pickOfflineVoice([german, libritts], 'fr'), isNull);
      expect(pickOfflineVoice([german], null), isNull);
      expect(pickOfflineVoice(const [], 'de'), isNull);
    });

    test('prefers the better voice when two speak the language', () {
      expect(pickOfflineVoice([libritts, kokoro], 'en'), kokoro);
      expect(pickOfflineVoice([kokoro, libritts], 'en'), kokoro);
    });
  });

  group('archive entry paths', () {
    test('strip the archive folder', () {
      expect(voiceEntryPath('root/tokens.txt', 'root'), 'tokens.txt');
      expect(voiceEntryPath('root/espeak/phontab', 'root'), 'espeak/phontab');
      expect(voiceEntryPath('root/', 'root'), '');
      expect(voiceEntryPath('./root/a.onnx', 'root'), 'a.onnx');
    });

    test('refuse anything outside it', () {
      expect(voiceEntryPath('/etc/passwd', 'root'), isNull);
      expect(voiceEntryPath('root/../../evil', 'root'), isNull);
      expect(voiceEntryPath('other/tokens.txt', 'root'), isNull);
      expect(voiceEntryPath('rootkit/x', 'root'), isNull);
    });
  });

  test('speech rate maps onto sherpa speed', () {
    expect(sherpaSpeedForRate(0.5), 1.0);
    expect(sherpaSpeedForRate(0.45), closeTo(0.9, 1e-9));
    expect(sherpaSpeedForRate(0.2), 0.5);
    expect(sherpaSpeedForRate(1.0), 2.0);
  });

  group('sherpa configuration', () {
    test('loads VITS voices with their espeak-ng data', () {
      final voice = offlineVoiceById('de-thorsten-emotional')!;
      final config = sherpaConfigFor((voice: voice, path: '/v/de'));
      expect(config.model.vits.model, '/v/de/de_DE-thorsten_emotional-medium.onnx');
      expect(config.model.vits.tokens, '/v/de/tokens.txt');
      expect(config.model.vits.dataDir, '/v/de/espeak-ng-data');
      expect(config.model.kokoro.model, isEmpty);
    });

    test('loads Kokoro with its speaker embeddings', () {
      final config = sherpaConfigFor((voice: kokoro, path: '/v/k'));
      expect(config.model.kokoro.model, '/v/k/model.int8.onnx');
      expect(config.model.kokoro.voices, '/v/k/voices.bin');
      expect(config.model.kokoro.dataDir, '/v/k/espeak-ng-data');
      expect(config.model.vits.model, isEmpty);
    });
  });
}
