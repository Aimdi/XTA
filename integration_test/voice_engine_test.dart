import 'dart:ffi';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:xta/speech/offline_voice_catalog.dart';
import 'package:xta/speech/sherpa_voice_synthesizer.dart';
import 'package:xta/speech/voice_download_store.dart';
import 'package:xta/speech/voice_engine.dart';
import 'package:xta/speech/voice_engine_store.dart';

/// Where the voice-engine job of .github/workflows/emulator-qa.yml serves the engine archives and a voice, as the
/// emulator sees the host.
const _server = String.fromEnvironment('XTA_VOICE_TEST_SERVER');

/// The catalog's English voice, served by [_server].
OfflineVoice _servedEnglish() {
  final voice = offlineVoiceById('en-libritts-r')!;
  return OfflineVoice(
    id: voice.id,
    name: voice.name,
    locale: voice.locale,
    url: Uri.parse('$_server/${voice.archiveRoot}.tar.bz2'),
    archiveBytes: voice.archiveBytes,
    sha256: voice.sha256,
    licence: voice.licence,
    kind: voice.kind,
    archiveRoot: voice.archiveRoot,
    model: voice.model,
  );
}

/// docs/specs/downloadable-voice-engine.md, on Android itself.
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('the engine is not in the APK, downloads, loads from app storage and reads a sentence', (_) async {
    expect(_server, isNotEmpty, reason: 'run with --dart-define=XTA_VOICE_TEST_SERVER');
    expect(() => DynamicLibrary.open('libonnxruntime.so'), throwsArgumentError);
    expect(() => DynamicLibrary.open('libsherpa-onnx-c-api.so'), throwsArgumentError);

    final support = (await getApplicationSupportDirectory()).path;
    final engine = VoiceEngineStore(
      package: voiceEngineForAbi(Abi.current()),
      root: () async => Directory(p.join(support, 'test-voice-engine')),
      urls: (package) => [Uri.parse('$_server/${package.archiveName}')],
    );
    final voice = _servedEnglish();
    final voices = VoiceDownloadStore(
      catalog: [voice],
      root: () async => Directory(p.join(support, 'test-voices')),
      engine: engine,
    );

    await voices.download(voice);

    expect(engine.state, isA<VoiceReady>());
    expect(voices.installOf(voice.id), isA<VoiceReady>());
    final installed = await voices.installedFor('en');
    expect(installed, isNotNull);

    final synthesizer = await SherpaVoiceSynthesizer.load(
      installed!,
      clips: Directory(p.join(support, 'test-clips')),
      engine: engine.loadFrom,
    );
    final clip = await synthesizer.synthesize('The engine was downloaded, and it reads this sentence.', speed: 1);
    await synthesizer.dispose();

    expect(File(clip).lengthSync(), greaterThan(16000), reason: 'a second of speech at 16 kHz or more');
  });
}
