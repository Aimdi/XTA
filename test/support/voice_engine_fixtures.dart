import 'dart:io';

import 'package:archive/archive.dart';
import 'package:crypto/crypto.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/downloads/download_transfer.dart';
import 'package:xta/speech/voice_engine.dart';
import 'package:xta/speech/voice_engine_archive.dart';
import 'package:xta/speech/voice_engine_store.dart';

/// Stand-ins for the engine's two libraries, small enough to pin in a test.
final fakeEngineFiles = {
  'libonnxruntime.so': List<int>.generate(4096, (i) => i % 251),
  'libsherpa-onnx-c-api.so': List<int>.generate(2048, (i) => (i * 7) % 253),
};

/// A package pinning [files], as voiceEnginePackages pins the real ones.
VoiceEnginePackage fakeEnginePackage([Map<String, List<int>>? files]) {
  final pinned = files ?? fakeEngineFiles;
  return VoiceEnginePackage(
    abi: 'arm64-v8a',
    archiveBytes: 3000,
    libraries: [
      for (final MapEntry(key: name, value: bytes) in pinned.entries)
        (name: name, bytes: bytes.length, sha256: sha256.convert(bytes).toString()),
    ],
  );
}

/// A tar.xz holding [files], shaped like the archives a release publishes.
List<int> engineArchive([Map<String, List<int>>? files]) {
  final archive = Archive();
  for (final MapEntry(key: name, value: bytes) in (files ?? fakeEngineFiles).entries) {
    archive.add(ArchiveFile.bytes(name, bytes));
  }
  return XZEncoder().encodeBytes(TarEncoder().encodeBytes(archive));
}

/// Serves [answers] by URL (a status code, or archive bytes for 200), and notes each request.
http.Client servingEngine(Map<Uri, Object> answers, {List<Uri>? requested}) => MockClient.streaming((request, _) async {
  requested?.add(request.url);
  final answer = answers[request.url];
  if (answer is! List<int>) return http.StreamedResponse(const Stream.empty(), answer as int? ?? 404);
  return http.StreamedResponse(Stream.value(answer), 200, contentLength: answer.length);
});

/// The engine as Android has it, downloading through [client] into [root].
VoiceEngineStore androidEngine(
  Directory root,
  Directory staging,
  http.Client client, {
  VoiceEnginePackage? package,
  List<Uri> urls = const [],
}) => VoiceEngineStore(
  package: package ?? fakeEnginePackage(),
  root: () async => root,
  transfer: (save) =>
      DownloadTransfer(clientFactory: () => client, temporaryDirectory: () async => staging, save: save).call,
  install: (archive, package, root) async => installVoiceEngineArchive(archive, package, root),
  urls: (_) => urls,
);
