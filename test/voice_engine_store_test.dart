import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:path/path.dart' as p;
import 'package:xta/downloads/download_transfer.dart';
import 'package:xta/speech/voice_archive.dart';
import 'package:xta/speech/voice_download_store.dart';
import 'package:xta/speech/voice_engine.dart';
import 'package:xta/speech/voice_engine_archive.dart';
import 'package:xta/speech/voice_engine_store.dart';

import 'support/voice_engine_fixtures.dart';
import 'support/voice_fixtures.dart';

final _own = Uri.parse('https://github.com/Aimdi/XTA/releases/download/aimdi189/engine.tar.xz');
final _latest = Uri.parse('https://github.com/Aimdi/XTA/releases/latest/download/engine.tar.xz');

/// docs/specs/downloadable-voice-engine.md.
void main() {
  late Directory temp;
  late Directory root;
  late Directory staging;

  setUp(() async {
    temp = await Directory.systemTemp.createTemp('xta-engine-store-');
    root = Directory(p.join(temp.path, 'voice-engine'));
    staging = Directory(p.join(temp.path, 'staging'));
  });

  tearDown(() => temp.delete(recursive: true));

  VoiceEngineStore engine(Map<Uri, Object> answers, {List<Uri>? requested}) =>
      androidEngine(root, staging, servingEngine(answers, requested: requested), urls: [_own, _latest]);

  test('the engine that came with the app needs nothing downloaded', () async {
    final bundled = VoiceEngineStore(bundled: true);

    expect(bundled.usable, isTrue);
    expect(await bundled.download(), isTrue);
    expect(bundled.loadFrom, isNull);
  });

  test('on Android a voice cannot read until the engine is downloaded', () async {
    final requested = <Uri>[];
    final store = engine({_own: engineArchive()}, requested: requested);
    await store.ensureLoaded();

    expect(store.usable, isFalse);
    expect(requested, isEmpty, reason: 'nothing is fetched without a tap');

    expect(await store.download(), isTrue);

    expect(requested, [_own]);
    final ready = store.state as VoiceReady;
    expect(ready.bytes, fakeEnginePackage().installedBytes);
    expect(store.loadFrom, voiceEngineDirectory(root.path, fakeEnginePackage()));
    expect(store.usable, isTrue);
  });

  test('a release that does not answer falls back to the latest one', () async {
    final requested = <Uri>[];
    final store = engine({_own: 404, _latest: engineArchive()}, requested: requested);

    expect(await store.download(), isTrue);

    expect(requested, [_own, _latest]);
  });

  test('a file that does not match its pin is refused, and the next source tried', () async {
    final bad = engineArchive({...fakeEngineFiles, 'libonnxruntime.so': List<int>.filled(4096, 9)});
    final store = engine({_own: bad, _latest: engineArchive()});

    expect(await store.download(), isTrue);

    final elsewhere = Directory(p.join(temp.path, 'elsewhere'));
    final refused = androidEngine(elsewhere, staging, servingEngine({_own: bad, _latest: bad}), urls: [_own, _latest]);
    expect(await refused.download(), isFalse);
    expect(voiceEngineFilesPresent(elsewhere.path, fakeEnginePackage()), isFalse);
    expect(refused.state, isA<VoiceFailed>().having((failed) => failed.reason, 'reason', VoiceFailure.checksum));
  });

  test('when no source answers, the download fails', () async {
    final store = engine({_own: 500, _latest: 404});

    expect(await store.download(), isFalse);

    expect(store.state, isA<VoiceFailed>().having((failed) => failed.reason, 'reason', VoiceFailure.network));
    expect(store.usable, isFalse);
  });

  test('an engine already on the device is found without a download, and can be deleted', () async {
    expect(await engine({_own: engineArchive()}).download(), isTrue);
    final requested = <Uri>[];
    final later = engine({}, requested: requested);

    await later.ensureLoaded();

    expect(later.state, isA<VoiceReady>());
    expect(requested, isEmpty);

    await later.delete();

    expect(later.state, isA<VoiceAbsent>());
    expect(Directory(voiceEngineDirectory(root.path, fakeEnginePackage())).existsSync(), isFalse);
  });

  test('a processor with no published engine cannot download one', () async {
    final none = VoiceEngineStore(root: () async => root);

    expect(none.available, isFalse);
    expect(await none.download(), isFalse);
    expect(none.usable, isFalse);
  });

  group('a voice', () {
    late Directory voices;

    setUp(() => voices = Directory(p.join(temp.path, 'voices')));

    final archive = voiceArchive(
      files: under({'voice.onnx': 'model', 'tokens.txt': 'a 1', 'espeak-ng-data/phontab': 'phonemes'}),
    );
    final voice = testVoice(archive);

    VoiceDownloadStore downloads(VoiceEngineStore engine, Map<Uri, Object> answers, {List<Uri>? requested}) {
      final client = servingEngine(answers, requested: requested);
      return VoiceDownloadStore(
        catalog: [voice],
        root: () async => voices,
        transfer: (save) =>
            DownloadTransfer(clientFactory: () => client, temporaryDirectory: () async => staging, save: save).call,
        install: (archive, voice, root) async => installVoiceArchive(archive, voice, root),
        engine: engine,
      );
    }

    test('brings the engine first when it is not on the device', () async {
      final requested = <Uri>[];
      final store = downloads(engine({_own: engineArchive()}), {voice.url: archive}, requested: requested);

      await store.download(voice);

      expect(requested, [voice.url]);
      expect(store.engine.state, isA<VoiceReady>());
      expect(store.installOf(voice.id), isA<VoiceReady>());
      expect((await store.installedFor('de'))?.voice, voice);
    });

    test('is not downloaded when the engine cannot be, and fails as the engine did', () async {
      final requested = <Uri>[];
      final store = downloads(engine({_own: 404, _latest: 404}), {voice.url: archive}, requested: requested);

      await store.download(voice);

      expect(requested, isEmpty, reason: 'the voice itself was never fetched');
      expect(store.installOf(voice.id), isA<VoiceFailed>());
    });

    test('on the device without the engine does not read, until the engine is downloaded', () async {
      await downloads(VoiceEngineStore(bundled: true), {voice.url: archive}).download(voice);
      final store = downloads(engine({_own: engineArchive()}), {});
      await store.ensureLoaded();

      expect(store.installOf(voice.id), isA<VoiceReady>());
      expect(await store.installedFor('de'), isNull);

      await store.engine.download();

      expect((await store.installedFor('de'))?.voice, voice);
    });

    test('cancelled while the engine downloads cancels the engine too', () async {
      final engineRequested = <Uri>[];
      late VoiceDownloadStore store;
      final slow = MockClient.streaming((request, _) async {
        engineRequested.add(request.url);
        store.cancel(voice.id);
        return http.StreamedResponse(Stream.value(engineArchive()), 200);
      });
      store = downloads(androidEngine(root, staging, slow, urls: [_own]), {voice.url: archive});

      await store.download(voice);

      expect(engineRequested, [_own]);
      expect(store.engine.state, isA<VoiceAbsent>());
      expect(store.installOf(voice.id), isA<VoiceAbsent>());
    });
  });
}
