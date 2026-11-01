/// The engine that runs downloaded voices: onnxruntime and sherpa-onnx's C API.
///
/// On Android it is not in the APK, where it was a third of it and only a downloaded voice loaded it. It downloads
/// with the first voice from the app's own GitHub release, which publishes it beside the APKs, one archive per
/// processor. Every library is pinned here by size and SHA-256, so a file that does not match is refused whatever
/// served it. The Linux build keeps the engine in its bundle. See docs/specs/downloadable-voice-engine.md.
///
/// scripts/voice_engine_archives.py reads these pins to check the archives a release publishes.
library;

import 'dart:ffi' show Abi;

import 'package:path/path.dart' as p;
import 'package:xta/constants.dart';
import 'package:xta/utils/updates.dart';

/// The sherpa-onnx release the engine is, the same as the `sherpa_onnx` package in pubspec.yaml.
const voiceEngineVersion = '1.13.8';

/// The licences of what the engine holds: sherpa-onnx, onnxruntime and the espeak-ng sherpa-onnx links in.
const voiceEngineLicences = 'Apache-2.0, MIT, GPL-3.0';

/// One library of the engine, as pub.dev's package ships it.
typedef VoiceEngineLibrary = ({String name, int bytes, String sha256});

/// The engine for one processor.
class VoiceEnginePackage {
  /// Android's name for the processor, as the `lib/` folder of an APK names it.
  final String abi;

  /// About how large the download is, shown before it starts.
  final int archiveBytes;

  final List<VoiceEngineLibrary> libraries;

  const VoiceEnginePackage({required this.abi, required this.archiveBytes, required this.libraries});

  String get archiveName => 'xta-voice-engine-sherpa-onnx-$voiceEngineVersion-$abi.tar.xz';

  /// What it takes on the device once unpacked.
  int get installedBytes => libraries.fold(0, (sum, library) => sum + library.bytes);
}

/// Hashed on 2026-11-01 from pub.dev's `sherpa_onnx_android_*` 1.13.8 packages, which pub.dev never changes. The
/// archive sizes are what scripts/voice_engine_archives.py wrote that day.
const voiceEnginePackages = [
  VoiceEnginePackage(
    abi: 'arm64-v8a',
    archiveBytes: 6023748,
    libraries: [
      (
        name: 'libonnxruntime.so',
        bytes: 22249560,
        sha256: '33847ad43bffe204699fd4a27f7f3603452a8cdaf2f9a44983a0bc31ffcf2da1',
      ),
      (
        name: 'libsherpa-onnx-c-api.so',
        bytes: 4499112,
        sha256: '42308b0dd41ac79639330c58ac69dec9c3a6bc94c8b6ae96b0311963bc13f0b0',
      ),
    ],
  ),
  VoiceEnginePackage(
    abi: 'armeabi-v7a',
    archiveBytes: 6175724,
    libraries: [
      (
        name: 'libonnxruntime.so',
        bytes: 15359592,
        sha256: '7a8f17a707e52e0ac3a026be319272876d9eae61f4bb19585292772d785f7744',
      ),
      (
        name: 'libsherpa-onnx-c-api.so',
        bytes: 3202776,
        sha256: '66b2e4caa8abc40cd8b33d4856a5e494072b97f4dca4b69d81fdcc860feab03e',
      ),
    ],
  ),
  VoiceEnginePackage(
    abi: 'x86_64',
    archiveBytes: 7315652,
    libraries: [
      (
        name: 'libonnxruntime.so',
        bytes: 25581120,
        sha256: '087e84297cad64acb823c3d901d6e0cc934e91add7cdb20da802cd1c0bea5c3e',
      ),
      (
        name: 'libsherpa-onnx-c-api.so',
        bytes: 5002888,
        sha256: 'f3204723864da2d2ed43898e08baff0975472cc1d7412dcde77c5dc0b051d336',
      ),
    ],
  ),
];

final _androidAbiNames = {Abi.androidArm64: 'arm64-v8a', Abi.androidArm: 'armeabi-v7a', Abi.androidX64: 'x86_64'};

/// The engine a process on [abi] loads, or null when none is published for it.
VoiceEnginePackage? voiceEngineForAbi(Abi abi) =>
    voiceEnginePackages.where((package) => package.abi == _androidAbiNames[abi]).firstOrNull;

/// Where [package] is downloaded from: this build's own release, then the latest one, for a local build (which has
/// no release) or a release that does not answer. The libraries are pinned, whichever serves them.
List<Uri> voiceEngineUrls(VoiceEnginePackage package, {String releaseTag = buildReleaseTag}) => [
  if (releaseTag.isNotEmpty) _releaseAsset('download/$releaseTag', package),
  _releaseAsset('latest/download', package),
];

Uri _releaseAsset(String release, VoiceEnginePackage package) =>
    Uri.https('github.com', '/$githubRepo/releases/$release/${package.archiveName}');

/// The folder the engine for [package] is installed in under [root].
String voiceEngineDirectory(String root, VoiceEnginePackage package) =>
    p.join(root, 'sherpa-onnx-$voiceEngineVersion', package.abi);
