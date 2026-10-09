import 'dart:async';
import 'dart:io';

import 'package:archive/archive.dart';
import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:path/path.dart' as p;
import 'package:xta/downloads/download_transfer.dart';
import 'package:xta/speech/offline_voice_catalog.dart';
import 'package:xta/speech/voice_archive.dart';
import 'package:xta/speech/voice_download_store.dart';

const _root = 'vits-test-voice';

/// A voice archive shaped like k2-fsa's: one folder holding the model, the
/// tokens and espeak-ng data.
List<int> voiceArchive({Map<String, String>? files}) {
  final archive = Archive()..add(ArchiveFile.directory('$_root/'));
  final contents =
      files ??
      {
        'voice.onnx': 'model',
        'tokens.txt': 'a 1',
        'espeak-ng-data/phontab': 'phonemes',
      };
  for (final entry in contents.entries) {
    archive.add(ArchiveFile.string(entry.key, entry.value));
  }
  return BZip2Encoder().encodeBytes(TarEncoder().encodeBytes(archive));
}

Map<String, String> under(Map<String, String> files) => {
  for (final entry in files.entries) '$_root/${entry.key}': entry.value,
};

OfflineVoice testVoice(List<int> archive, {String? checksum}) => OfflineVoice(
  id: 'xx-test',
  name: 'Test',
  locale: 'de_DE',
  url: Uri.https('github.com', '/k2-fsa/test.tar.bz2'),
  archiveBytes: archive.length,
  sha256: checksum ?? sha256.convert(archive).toString(),
  licence: 'CC0-1.0',
  kind: VoiceModelKind.vits,
  archiveRoot: _root,
  model: 'voice.onnx',
);

void main() {
  late Directory temp;
  late Directory voices;
  late Directory staging;

  setUp(() async {
    temp = await Directory.systemTemp.createTemp('xta-voices-');
    voices = Directory(p.join(temp.path, 'voices'));
    staging = Directory(p.join(temp.path, 'support'));
  });

  tearDown(() => temp.delete(recursive: true));

  VoiceDownloadStore store(OfflineVoice voice, http.Client client) =>
      VoiceDownloadStore(
        catalog: [voice],
        root: () async => voices,
        transfer: (save) => DownloadTransfer(
          clientFactory: () => client,
          temporaryDirectory: () async => staging,
          save: save,
        ).call,
        install: (archive, voice, root) async =>
            installVoiceArchive(archive, voice, root),
      );

  http.Client serving(List<int> bytes, {List<Uri>? requested}) =>
      MockClient.streaming((request, _) async {
        requested?.add(request.url);
        final halves = [
          bytes.sublist(0, bytes.length ~/ 2),
          bytes.sublist(bytes.length ~/ 2),
        ];
        return http.StreamedResponse(
          Stream.fromIterable(halves),
          200,
          contentLength: bytes.length,
        );
      });

  List<File> leftovers() => staging.existsSync()
      ? staging.listSync(recursive: true).whereType<File>().toList()
      : const [];

  test('starts with nothing and touches no network', () async {
    final requested = <Uri>[];
    final archive = voiceArchive(files: under(const {}));
    final voices = store(testVoice(archive), serving([], requested: requested));
    await voices.ensureLoaded();
    expect(voices.installOf('xx-test'), isA<VoiceAbsent>());
    expect(await voices.installedFor('de'), isNull);
    expect(requested, isEmpty);
  });

  test('downloads with progress, verifies and unpacks into place', () async {
    final archive = voiceArchive(
      files: under({
        'voice.onnx': 'model',
        'tokens.txt': 'a 1',
        'espeak-ng-data/phontab': 'phonemes',
      }),
    );
    final voice = testVoice(archive);
    final requested = <Uri>[];
    final downloads = store(voice, serving(archive, requested: requested));
    final seen = <VoiceInstall>[];
    downloads.observer(onState: (state) => seen.addAll([?state['xx-test']]));

    await downloads.download(voice);

    expect(requested, [voice.url]);
    final progress = seen.whereType<VoiceDownloading>().toList();
    expect(progress.first.received, 0);
    expect(progress.last.received, archive.length);
    expect(progress.last.fraction, 1);
    expect(seen.whereType<VoiceInstalling>(), isNotEmpty);
    final ready = downloads.installOf('xx-test') as VoiceReady;
    expect(ready.path, p.join(voices.path, 'xx-test'));
    expect(
      File(p.join(ready.path, 'espeak-ng-data', 'phontab')).readAsStringSync(),
      'phonemes',
    );
    expect(ready.bytes, greaterThan(0));
    expect((await downloads.installedFor('de-DE'))?.voice, voice);
    expect(await downloads.installedFor('en'), isNull);
    expect(leftovers(), isEmpty, reason: 'staged archive removed');
  });

  test('a download that does not match its checksum is rejected', () async {
    final archive = voiceArchive(
      files: under({
        'voice.onnx': 'model',
        'tokens.txt': 'a 1',
        'espeak-ng-data/phontab': 'phonemes',
      }),
    );
    final voice = testVoice(archive, checksum: '0' * 64);
    final downloads = store(voice, serving(archive));

    await downloads.download(voice);

    expect(
      downloads.installOf('xx-test'),
      isA<VoiceFailed>().having(
        (f) => f.reason,
        'reason',
        VoiceFailure.checksum,
      ),
    );
    expect(Directory(p.join(voices.path, 'xx-test')).existsSync(), isFalse);
    expect(voices.listSync(), isEmpty, reason: 'no staging folder left');
    expect(leftovers(), isEmpty, reason: 'mismatched file is not resumed');
  });

  test('an archive missing a file the voice needs is rejected', () async {
    final archive = voiceArchive(files: under({'voice.onnx': 'model'}));
    final voice = testVoice(archive);
    final downloads = store(voice, serving(archive));

    await downloads.download(voice);

    expect(
      downloads.installOf('xx-test'),
      isA<VoiceFailed>().having(
        (f) => f.reason,
        'reason',
        VoiceFailure.archive,
      ),
    );
    expect(voices.listSync(), isEmpty);
  });

  test('an archive reaching outside its folder is rejected', () async {
    final archive = voiceArchive(
      files: {
        ...under({
          'voice.onnx': 'model',
          'tokens.txt': 'a 1',
          'espeak-ng-data/phontab': 'phonemes',
        }),
        '$_root/../../escaped.txt': 'gotcha',
      },
    );
    final voice = testVoice(archive);
    final downloads = store(voice, serving(archive));

    await downloads.download(voice);

    expect(downloads.installOf('xx-test'), isA<VoiceFailed>());
    expect(File(p.join(temp.path, 'escaped.txt')).existsSync(), isFalse);
    expect(voices.listSync(), isEmpty);
  });

  test('a voice without a known checksum installs from a sound archive', () {
    final archive = voiceArchive(
      files: under({
        'voice.onnx': 'model',
        'tokens.txt': 'a 1',
        'espeak-ng-data/phontab': 'phonemes',
      }),
    );
    final file = File(p.join(temp.path, 'voice.tar.bz2'))
      ..writeAsBytesSync(archive);
    final unchecked = OfflineVoice(
      id: 'xx-unchecked',
      name: 'Test',
      locale: 'de_DE',
      url: Uri.https('github.com', '/x.tar.bz2'),
      archiveBytes: archive.length,
      sha256: null,
      licence: 'CC0-1.0',
      kind: VoiceModelKind.vits,
      archiveRoot: _root,
      model: 'voice.onnx',
    );
    installVoiceArchive(file.path, unchecked, voices.path);
    expect(
      voiceFilesPresent(p.join(voices.path, 'xx-unchecked'), unchecked),
      isTrue,
    );

    final damaged = File(p.join(temp.path, 'damaged.tar.bz2'))
      ..writeAsBytesSync(archive.sublist(0, archive.length - 20));
    expect(
      () => installVoiceArchive(damaged.path, unchecked, voices.path),
      throwsA(isA<VoiceArchiveInvalid>()),
    );
  });

  test('cancelling stops the download and cleans up', () async {
    final archive = voiceArchive(
      files: under({
        'voice.onnx': 'model',
        'tokens.txt': 'a 1',
        'espeak-ng-data/phontab': 'phonemes',
      }),
    );
    final voice = testVoice(archive);
    final body = StreamController<List<int>>();
    final client = MockClient.streaming(
      (request, _) async => http.StreamedResponse(
        body.stream,
        200,
        contentLength: archive.length,
      ),
    );
    final downloads = store(voice, client);
    final started = Completer<void>();
    downloads.observer(
      onState: (state) {
        final install = state['xx-test'];
        if (install is VoiceDownloading && install.received > 0) {
          if (!started.isCompleted) started.complete();
        }
      },
    );

    final download = downloads.download(voice);
    body.add(archive.sublist(0, 10));
    await started.future;
    downloads.cancel('xx-test');
    body.add(archive.sublist(10));
    await download;
    await body.close();

    expect(downloads.installOf('xx-test'), isA<VoiceAbsent>());
    expect(leftovers(), isEmpty);
    expect(Directory(p.join(voices.path, 'xx-test')).existsSync(), isFalse);
  });

  test('delete removes the voice from the device', () async {
    final archive = voiceArchive(
      files: under({
        'voice.onnx': 'model',
        'tokens.txt': 'a 1',
        'espeak-ng-data/phontab': 'phonemes',
      }),
    );
    final voice = testVoice(archive);
    final downloads = store(voice, serving(archive));
    await downloads.download(voice);
    expect(downloads.installOf('xx-test'), isA<VoiceReady>());

    await downloads.delete(voice);

    expect(downloads.installOf('xx-test'), isA<VoiceAbsent>());
    expect(Directory(p.join(voices.path, 'xx-test')).existsSync(), isFalse);
    expect(await downloads.installedFor('de'), isNull);
  });

  test('finds voices installed in an earlier session', () async {
    final archive = voiceArchive(
      files: under({
        'voice.onnx': 'model',
        'tokens.txt': 'a 1',
        'espeak-ng-data/phontab': 'phonemes',
      }),
    );
    final voice = testVoice(archive);
    await store(voice, serving(archive)).download(voice);

    final later = store(voice, serving([]));
    expect((await later.installedFor('de'))?.voice, voice);
    expect(later.installOf('xx-test'), isA<VoiceReady>());
  });

  test('a failed download can be tried again', () async {
    final archive = voiceArchive(
      files: under({
        'voice.onnx': 'model',
        'tokens.txt': 'a 1',
        'espeak-ng-data/phontab': 'phonemes',
      }),
    );
    final voice = testVoice(archive);
    var attempts = 0;
    final client = MockClient.streaming((request, _) async {
      attempts++;
      if (attempts == 1) {
        return http.StreamedResponse(const Stream.empty(), 503);
      }
      return http.StreamedResponse(
        Stream.value(archive),
        200,
        contentLength: archive.length,
      );
    });
    final downloads = store(voice, client);

    await downloads.download(voice);
    expect(
      downloads.installOf('xx-test'),
      isA<VoiceFailed>().having(
        (f) => f.reason,
        'reason',
        VoiceFailure.network,
      ),
    );

    await downloads.download(voice);
    expect(downloads.installOf('xx-test'), isA<VoiceReady>());
  });
}
