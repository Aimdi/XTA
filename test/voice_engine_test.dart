import 'dart:ffi' show Abi;
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:path/path.dart' as p;
import 'package:xta/speech/voice_archive.dart';
import 'package:xta/speech/voice_engine.dart';
import 'package:xta/speech/voice_engine_archive.dart';

import 'support/voice_engine_fixtures.dart';

/// docs/specs/downloadable-voice-engine.md.
void main() {
  group('the published engine', () {
    test('one per processor a release builds for, and none for 32-bit x86', () {
      expect(voiceEngineForAbi(Abi.androidArm64)?.abi, 'arm64-v8a');
      expect(voiceEngineForAbi(Abi.androidArm)?.abi, 'armeabi-v7a');
      expect(voiceEngineForAbi(Abi.androidX64)?.abi, 'x86_64');
      expect(voiceEngineForAbi(Abi.androidIA32), isNull);
      expect(voiceEngineForAbi(Abi.linuxX64), isNull);
    });

    test('each pins both libraries by size and SHA-256', () {
      for (final package in voiceEnginePackages) {
        expect(package.libraries.map((library) => library.name), ['libonnxruntime.so', 'libsherpa-onnx-c-api.so']);
        for (final library in package.libraries) {
          expect(library.sha256, matches(RegExp(r'^[0-9a-f]{64}$')), reason: '${package.abi} ${library.name}');
          expect(library.bytes, greaterThan(1 << 20));
        }
      }
    });

    test("a release build fetches its own release's archive, then the latest one", () {
      final arm64 = voiceEngineForAbi(Abi.androidArm64)!;

      expect(voiceEngineUrls(arm64, releaseTag: 'aimdi189'), [
        Uri.parse(
          'https://github.com/Aimdi/XTA/releases/download/aimdi189/xta-voice-engine-sherpa-onnx-1.13.8-arm64-v8a.tar.xz',
        ),
        Uri.parse(
          'https://github.com/Aimdi/XTA/releases/latest/download/xta-voice-engine-sherpa-onnx-1.13.8-arm64-v8a.tar.xz',
        ),
      ]);
      expect(voiceEngineUrls(arm64, releaseTag: ''), [
        Uri.parse(
          'https://github.com/Aimdi/XTA/releases/latest/download/xta-voice-engine-sherpa-onnx-1.13.8-arm64-v8a.tar.xz',
        ),
      ]);
    });
  });

  group('installing an archive', () {
    late Directory root;
    late String archivePath;

    setUp(() async {
      root = await Directory.systemTemp.createTemp('xta-engine-');
      archivePath = p.join(root.path, 'engine.tar.xz');
    });

    tearDown(() => root.delete(recursive: true));

    void write(List<int> archive) => File(archivePath).writeAsBytesSync(archive);
    String target(VoiceEnginePackage package) => voiceEngineDirectory(p.join(root.path, 'engine'), package);
    List<String> leftovers() => Directory(p.join(root.path, 'engine')).existsSync()
        ? Directory(p.join(root.path, 'engine')).listSync(recursive: true).map((entry) => entry.path).toList()
        : const [];

    test('puts both libraries in place when they match their pins', () {
      final package = fakeEnginePackage();
      write(engineArchive());

      installVoiceEngineArchive(archivePath, package, p.join(root.path, 'engine'));

      for (final MapEntry(key: name, value: bytes) in fakeEngineFiles.entries) {
        expect(File(p.join(target(package), name)).readAsBytesSync(), bytes);
      }
      expect(voiceEngineFilesPresent(p.join(root.path, 'engine'), package), isTrue);
      expect(leftovers().where((path) => path.endsWith('.partial')), isEmpty);
    });

    test('refuses a library that does not match its pin, and leaves nothing behind', () {
      final package = fakeEnginePackage();
      write(engineArchive({...fakeEngineFiles, 'libsherpa-onnx-c-api.so': List<int>.filled(2048, 1)}));

      expect(
        () => installVoiceEngineArchive(archivePath, package, p.join(root.path, 'engine')),
        throwsA(isA<VoiceChecksumMismatch>()),
      );
      expect(voiceEngineFilesPresent(p.join(root.path, 'engine'), package), isFalse);
      expect(leftovers().where((path) => path.endsWith('.so')), isEmpty);
    });

    test('refuses an archive without a library, or one that is damaged', () {
      final package = fakeEnginePackage();
      write(engineArchive({'libonnxruntime.so': fakeEngineFiles['libonnxruntime.so']!}));
      expect(
        () => installVoiceEngineArchive(archivePath, package, p.join(root.path, 'engine')),
        throwsA(isA<VoiceArchiveInvalid>()),
      );

      write(List<int>.generate(512, (i) => i % 256));
      expect(
        () => installVoiceEngineArchive(archivePath, package, p.join(root.path, 'engine')),
        throwsA(isA<VoiceArchiveInvalid>()),
      );
      expect(leftovers().where((path) => path.endsWith('.so')), isEmpty);
    });

    test('an installed library of the wrong size does not count as installed', () {
      final package = fakeEnginePackage();
      write(engineArchive());
      installVoiceEngineArchive(archivePath, package, p.join(root.path, 'engine'));

      File(p.join(target(package), 'libonnxruntime.so')).writeAsBytesSync([1, 2, 3]);

      expect(voiceEngineFilesPresent(p.join(root.path, 'engine'), package), isFalse);
    });
  });
}
