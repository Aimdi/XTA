/// Sherpa ONNX TTS Engine — the on-device Android speech engine XTA can
/// select without changing the system-wide default.
///
/// Package id matches
/// https://k2-fsa.github.io/sherpa/onnx/tts/apk-engine.html
/// Display name on the device: "TTS Engine: Next-gen Kaldi".
const sherpaOnnxTtsEngine = 'com.k2fsa.sherpa.onnx.tts.engine';

/// Where to get the Sherpa TTS Engine APK (per-language / per-arch builds).
const sherpaTtsInstallUrl =
    'https://k2-fsa.github.io/sherpa/onnx/tts/apk-engine.html';

/// What to offer when Listen fails: pick Sherpa, or install it.
enum TtsFailureAction { useSherpa, installSherpa }

bool isSherpaEngine(String? engine) {
  if (engine == null || engine.isEmpty) return false;
  final lower = engine.toLowerCase();
  return lower == sherpaOnnxTtsEngine ||
      lower.contains('sherpa') ||
      lower.contains('k2fsa') ||
      lower.contains('kaldi');
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

/// BCP 47 tag for a short locale like `de`, `zh`, `en_GB`.
String languageTagForShortLocale(String locale) {
  return switch (locale) {
    'zh' => 'zh-CN',
    'nb' => 'nb-NO',
    'pt' => 'pt-BR',
    _ => locale.contains('_')
        ? locale.replaceAll('_', '-')
        : locale.contains('-')
            ? locale
            : '$locale-${locale.toUpperCase()}',
  };
}

/// Languages to try, in order, so an English-only engine is not asked for de-DE.
List<String> speakLanguageCandidates({
  String? voiceLocale,
  String? appLocale,
}) {
  final tags = <String>[];
  void add(String? tag) {
    if (tag == null || tag.isEmpty) return;
    final bcp = tag.replaceAll('_', '-');
    if (!tags.contains(bcp)) tags.add(bcp);
    final dash = bcp.indexOf('-');
    if (dash > 0) {
      final lang = bcp.substring(0, dash);
      if (!tags.contains(lang)) tags.add(lang);
    }
  }

  add(voiceLocale);
  add(appLocale);
  add('en-US');
  add('en');
  return tags;
}

/// Android [isLanguageAvailable] returns 0/1/2 for success and negatives for
/// missing/unsupported. flutter_tts may also map that to 1/0 or a bool.
bool ttsLanguageAvailable(dynamic result) {
  if (result == true) return true;
  if (result is num) return result >= 0;
  return false;
}

/// Fold the system default engine into the discovered list so a preferred
/// module that [getEngines] hid (Android 11 package visibility) still counts.
List<String> mergeTtsEngines(Iterable<String> engines, String? defaultEngine) {
  final out = engines.where((e) => e.isNotEmpty).toList();
  if (defaultEngine != null &&
      defaultEngine.isNotEmpty &&
      !out.contains(defaultEngine)) {
    out.add(defaultEngine);
  }
  return out;
}

/// True when [installed] already offers [tag] (en-US vs en, en_US vs en-US).
bool languageListed(Iterable<String> installed, String tag) {
  final needle = tag.toLowerCase().replaceAll('_', '-');
  for (final item in installed) {
    final have = item.toLowerCase().replaceAll('_', '-');
    if (have == needle) return true;
    if (have.startsWith('$needle-') || needle.startsWith('$have-')) {
      return true;
    }
  }
  return false;
}

/// First candidate the engine can actually speak, else the first installed tag.
Future<String?> pickListedSpeakLanguage({
  required Iterable<String> candidates,
  required Iterable<String> installed,
  required Future<bool> Function(String tag) available,
}) async {
  for (final tag in candidates) {
    if (await available(tag) || languageListed(installed, tag)) return tag;
  }
  for (final tag in installed) {
    if (tag.isNotEmpty) return tag;
  }
  return null;
}
