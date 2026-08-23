/// Sherpa ONNX TTS Engine — the on-device Android speech engine XTA can
/// select without changing the system-wide default.
///
/// Package id matches
/// https://k2-fsa.github.io/sherpa/onnx/tts/apk-engine.html
const sherpaOnnxTtsEngine = 'com.k2fsa.sherpa.onnx.tts.engine';

/// Where to get the Sherpa TTS Engine APK (per-language / per-arch builds).
const sherpaTtsInstallUrl =
    'https://k2-fsa.github.io/sherpa/onnx/tts/apk-engine.html';

/// What to offer when Listen fails: pick Sherpa, or install it.
enum TtsFailureAction { useSherpa, installSherpa }

bool isSherpaEngine(String? engine) {
  if (engine == null || engine.isEmpty) return false;
  final lower = engine.toLowerCase();
  return lower == sherpaOnnxTtsEngine || lower.contains('sherpa');
}

/// The Sherpa package actually installed, if the platform listed one.
String? pickInstalledSherpaEngine(Iterable<String> engines) {
  for (final engine in engines) {
    if (engine == sherpaOnnxTtsEngine) return engine;
  }
  for (final engine in engines) {
    if (isSherpaEngine(engine)) return engine;
  }
  return null;
}

bool sherpaIsInstalled(Iterable<String> engines) =>
    pickInstalledSherpaEngine(engines) != null;

/// Engines that are not Sherpa, so the picker does not list it twice.
List<String> nonSherpaEngines(Iterable<String> engines) =>
    engines.where((engine) => !isSherpaEngine(engine)).toList(growable: false);

/// The package to hand `setEngine` when the reader asked for Sherpa.
String resolveSherpaEngineId(Iterable<String> engines) =>
    pickInstalledSherpaEngine(engines) ?? sherpaOnnxTtsEngine;

TtsFailureAction ttsFailureActionFor(String? engine) => isSherpaEngine(engine)
    ? TtsFailureAction.installSherpa
    : TtsFailureAction.useSherpa;
