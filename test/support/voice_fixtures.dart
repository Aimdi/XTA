import 'package:archive/archive.dart';
import 'package:crypto/crypto.dart';
import 'package:xta/speech/offline_voice_catalog.dart';

/// The folder a test voice's files sit under in its archive.
const testVoiceRoot = 'vits-test-voice';

/// A voice archive shaped like k2-fsa's: one folder holding the model, the
/// tokens and espeak-ng data.
List<int> voiceArchive({Map<String, String>? files}) {
  final archive = Archive()..add(ArchiveFile.directory('$testVoiceRoot/'));
  final contents = files ?? {'voice.onnx': 'model', 'tokens.txt': 'a 1', 'espeak-ng-data/phontab': 'phonemes'};
  for (final entry in contents.entries) {
    archive.add(ArchiveFile.string(entry.key, entry.value));
  }
  return BZip2Encoder().encodeBytes(TarEncoder().encodeBytes(archive));
}

Map<String, String> under(Map<String, String> files) => {
  for (final entry in files.entries) '$testVoiceRoot/${entry.key}': entry.value,
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
  archiveRoot: testVoiceRoot,
  model: 'voice.onnx',
);
