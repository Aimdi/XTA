import 'dart:async';
import 'dart:io';
import 'dart:isolate';

import 'package:path/path.dart' as p;
import 'package:sherpa_onnx/sherpa_onnx.dart' as sherpa;
import 'package:xta/speech/offline_speech_engine.dart';
import 'package:xta/speech/offline_voice_catalog.dart';
import 'package:xta/speech/voice_download_store.dart';

/// sherpa-onnx configuration for an installed voice.
sherpa.OfflineTtsConfig sherpaConfigFor(InstalledVoice installed, {int threads = 2}) {
  final voice = installed.voice;
  String file(String name) => p.join(installed.path, name);
  final model = switch (voice.kind) {
    VoiceModelKind.vits => sherpa.OfflineTtsModelConfig(
      vits: sherpa.OfflineTtsVitsModelConfig(
        model: file(voice.model),
        tokens: file(voice.tokens),
        dataDir: file(voice.dataDir),
      ),
      numThreads: threads,
      debug: false,
    ),
    VoiceModelKind.kokoro => sherpa.OfflineTtsModelConfig(
      kokoro: sherpa.OfflineTtsKokoroModelConfig(
        model: file(voice.model),
        voices: file(voice.voices!),
        tokens: file(voice.tokens),
        dataDir: file(voice.dataDir),
      ),
      numThreads: threads,
      debug: false,
    ),
  };
  return sherpa.OfflineTtsConfig(model: model);
}

/// A downloaded voice loaded into sherpa-onnx in a background isolate.
///
/// sherpa-onnx synthesises with a blocking FFI call, so the model lives in
/// its own isolate for as long as the voice is in use; requests are answered
/// in order with the path of a WAV file.
class SherpaVoiceSynthesizer implements VoiceSynthesizer {
  final SendPort _requests;
  final StreamIterator<Object?> _replies;
  final String _clips;
  final _pending = <int, Completer<String>>{};
  var _next = 0;
  var _alive = true;

  SherpaVoiceSynthesizer._(this._requests, this._replies, this._clips) {
    unawaited(_pump());
  }

  /// Starts the isolate and loads [installed]; throws when it cannot.
  static Future<VoiceSynthesizer> load(InstalledVoice installed, {required Directory clips}) async {
    await clips.create(recursive: true);
    final port = ReceivePort();
    final replies = StreamIterator<Object?>(port);
    try {
      final isolate = await Isolate.spawn(
        _worker,
        (port.sendPort, installed),
        onExit: port.sendPort,
        debugName: 'offline-voice',
      );
      final ready = await replies.moveNext() ? replies.current : null;
      if (ready is SendPort) {
        return SherpaVoiceSynthesizer._(ready, replies, clips.path);
      }
      isolate.kill();
      throw StateError('Voice failed to load: $ready');
    } catch (_) {
      await replies.cancel();
      rethrow;
    }
  }

  Future<void> _pump() async {
    while (await _replies.moveNext()) {
      final message = _replies.current;
      if (message is (int, String?)) _answer(message.$1, message.$2);
      if (message == null) break;
    }
    _fail('Voice stopped');
  }

  void _answer(int id, String? path) {
    final pending = _pending.remove(id);
    if (pending == null) return;
    path == null ? pending.completeError(StateError('Synthesis failed')) : pending.complete(path);
  }

  void _fail(String reason) {
    _alive = false;
    for (final pending in _pending.values) {
      pending.completeError(StateError(reason));
    }
    _pending.clear();
  }

  @override
  Future<String> synthesize(String text, {required double speed}) {
    if (!_alive) return Future.error(StateError('Voice stopped'));
    final id = _next++;
    final completer = _pending[id] = Completer<String>();
    final out = p.join(_clips, 'clip-${identityHashCode(this)}-$id.wav');
    _requests.send((id, text, speed, out));
    return completer.future;
  }

  /// Asks the isolate to free the model and exit once it has finished what
  /// it is doing; killing it mid-synthesis would leak the native model.
  @override
  Future<void> dispose() async {
    if (_alive) _requests.send(null);
    _fail('Voice unloaded');
    await _replies.cancel();
  }
}

/// The isolate: loads the model, then answers synthesis requests until told
/// to stop with a null message.
Future<void> _worker((SendPort, InstalledVoice) setup) async {
  final (replies, installed) = setup;
  final sherpa.OfflineTts tts;
  try {
    sherpa.initBindings();
    tts = sherpa.OfflineTts(sherpaConfigFor(installed));
  } catch (error) {
    replies.send('$error');
    return;
  }
  final requests = ReceivePort();
  replies.send(requests.sendPort);
  await for (final message in requests) {
    if (message is! (int, String, double, String)) break;
    replies.send((message.$1, _speak(tts, installed.voice, message)));
  }
  tts.free();
  requests.close();
}

String? _speak(sherpa.OfflineTts tts, OfflineVoice voice, (int, String, double, String) request) {
  final (_, text, speed, out) = request;
  try {
    final audio = tts.generate(text: text, sid: voice.speakerId, speed: speed);
    if (audio.samples.isEmpty) return null;
    final written = sherpa.writeWave(filename: out, samples: audio.samples, sampleRate: audio.sampleRate);
    return written ? out : null;
  } catch (_) {
    return null;
  }
}
