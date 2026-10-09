/// The voices XTA can download for reading aloud on the device.
///
/// Each one is a sherpa-onnx model archive from k2-fsa's `tts-models` GitHub
/// release, pinned by URL, byte size and SHA-256: a release asset can be
/// replaced under the same name, and a voice that no longer matches is
/// refused rather than run. Nothing here is fetched until the reader taps
/// Download. See `docs/specs/downloadable-tts.md`.
library;

/// Which sherpa-onnx model family a voice is, which decides how it loads.
enum VoiceModelKind { vits, kokoro }

class OfflineVoice {
  /// Stable id, also the voice's folder name on the device.
  final String id;

  /// Proper name of the voice, shown as is in every language.
  final String name;

  /// Locale the voice speaks, `de_DE` or `en_US`.
  final String locale;

  final Uri url;

  /// Size of the archive at [url], in bytes.
  final int archiveBytes;

  /// SHA-256 of the archive, lower-case hex. Null only for a voice whose
  /// checksum could not be confirmed: it is then accepted on a clean archive
  /// with every file in [requiredFiles] present.
  final String? sha256;

  /// SPDX id of the licence the voice is published under.
  final String licence;

  final VoiceModelKind kind;

  /// Folder every file in the archive sits under.
  final String archiveRoot;

  /// The model, relative to the voice's folder.
  final String model;
  final String tokens;

  /// The espeak-ng phoneme data the model reads text through.
  final String dataDir;

  /// Kokoro's speaker embeddings; null for VITS.
  final String? voices;

  /// Which speaker of a multi-speaker model reads.
  final int speakerId;

  /// Higher is preferred when two installed voices speak the same language.
  final int preference;

  const OfflineVoice({
    required this.id,
    required this.name,
    required this.locale,
    required this.url,
    required this.archiveBytes,
    required this.sha256,
    required this.licence,
    required this.kind,
    required this.archiveRoot,
    required this.model,
    this.tokens = 'tokens.txt',
    this.dataDir = 'espeak-ng-data',
    this.voices,
    this.speakerId = 0,
    this.preference = 0,
  });

  /// Short language code, `de` for `de_DE`.
  String get language => locale.split('_').first;

  /// Files that must exist for the voice to load.
  List<String> get requiredFiles => [
    model,
    tokens,
    '$dataDir/phontab',
    ?voices,
  ];
}

Uri _ttsModel(String name) => Uri.https(
  'github.com',
  '/k2-fsa/sherpa-onnx/releases/download/tts-models/$name.tar.bz2',
);

/// Hashes and sizes were taken from the archives downloaded from these URLs
/// on 2026-10-09.
///
/// Kokoro (`kokoro-int8-en-v0_19`) loads through [VoiceModelKind.kokoro] but
/// is not offered yet: it synthesised at about real time on a 4-core x64
/// desktop, which on a phone means pauses between sentences. Its pin is in
/// `docs/specs/downloadable-tts.md`.
final offlineVoiceCatalog = List<OfflineVoice>.unmodifiable([
  OfflineVoice(
    id: 'de-thorsten-emotional',
    name: 'Thorsten',
    locale: 'de_DE',
    url: _ttsModel('vits-piper-de_DE-thorsten_emotional-medium-int8'),
    archiveBytes: 23479221,
    sha256: 'e522bea5bb42d8f572b85a39ccb2d7ec114d6c2838c198827aadaeea3d31822a',
    // Thorsten-Voice dataset, per the archive's MODEL_CARD.
    licence: 'CC0-1.0',
    kind: VoiceModelKind.vits,
    archiveRoot: 'vits-piper-de_DE-thorsten_emotional-medium-int8',
    model: 'de_DE-thorsten_emotional-medium.onnx',
    // The model's eight speakers are emotions; 4 is "neutral".
    speakerId: 4,
  ),
  OfflineVoice(
    id: 'en-libritts-r',
    name: 'LibriTTS-R',
    locale: 'en_US',
    url: _ttsModel('vits-piper-en_US-libritts_r-medium-int8'),
    archiveBytes: 23398348,
    sha256: '7e4552e239988f4896872822b56e99e0e9e00958164e3f6bdf5ee14391fbe829',
    licence: 'CC-BY-4.0',
    kind: VoiceModelKind.vits,
    archiveRoot: 'vits-piper-en_US-libritts_r-medium-int8',
    model: 'en_US-libritts_r-medium.onnx',
  ),
]);

OfflineVoice? offlineVoiceById(String id) {
  for (final voice in offlineVoiceCatalog) {
    if (voice.id == id) return voice;
  }
  return null;
}

/// The voice that should read [language] out of [installed], or null.
OfflineVoice? pickOfflineVoice(
  Iterable<OfflineVoice> installed,
  String? language,
) {
  if (language == null || language.isEmpty) return null;
  final base = language.replaceAll('-', '_').split('_').first.toLowerCase();
  final matching = installed.where((voice) => voice.language == base).toList()
    ..sort((a, b) => b.preference.compareTo(a.preference));
  return matching.isEmpty ? null : matching.first;
}
