import 'dart:io';

import 'package:intl/intl.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/speech/clip_player.dart';
import 'package:xta/speech/offline_speech_engine.dart';
import 'package:xta/speech/sherpa_voice_synthesizer.dart';
import 'package:xta/speech/voice_download_store.dart';

/// The on-device voice engine as the app runs it: downloaded voices from
/// [voices], sherpa-onnx in a background isolate, clips played by media_kit.
OfflineSpeechEngine createOfflineSpeechEngine(VoiceDownloadStore voices, BasePrefService prefs) => OfflineSpeechEngine(
  voiceFor: voices.installedFor,
  enabled: () => prefs.get<bool>(optionTtsOfflineVoice) ?? true,
  load: (installed) async => SherpaVoiceSynthesizer.load(installed, clips: await _freshClipFolder()),
  player: ContinuityClipPlayer(),
  discard: _deleteQuietly,
  appLanguage: () => Intl.shortLocale(Intl.getCurrentLocale()),
);

/// Where synthesised sentences wait to be played; emptied whenever a voice
/// loads, so clips a killed process left behind do not pile up.
Future<Directory> _freshClipFolder() async {
  final cache = await getTemporaryDirectory();
  final folder = Directory(p.join(cache.path, 'xta-voice-clips'));
  if (await folder.exists()) await folder.delete(recursive: true);
  return folder.create(recursive: true);
}

Future<void> _deleteQuietly(String path) async {
  try {
    await File(path).delete();
  } catch (_) {}
}
