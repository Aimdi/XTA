import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:intl/intl.dart';
import 'package:xta/media/continuity_player.dart';
import 'package:xta/speech/clip_player.dart';
import 'package:xta/speech/offline_speech_engine.dart';
import 'package:xta/speech/offline_voice_catalog.dart';
import 'package:xta/speech/speech_store.dart';
import 'package:xta/speech/tts_engines.dart';
import 'package:xta/speech/voice_download_store.dart';

import 'speech_engine_test.dart' show FakeTtsPlatform;
import 'support/continuity_player_fake.dart';

/// Everything that happened, in order, across synthesiser and player.
typedef Log = List<String>;

class FakeSynthesizer implements VoiceSynthesizer {
  final Log log;
  final speeds = <double>[];
  var disposed = false;

  FakeSynthesizer(this.log);

  @override
  Future<String> synthesize(String text, {required double speed}) async {
    log.add('synth:$text');
    speeds.add(speed);
    await Future<void>.delayed(Duration.zero);
    return 'clip:$text';
  }

  @override
  Future<void> dispose() async => disposed = true;
}

class FakeClipPlayer implements ClipPlayer {
  final Log log;
  Completer<UtteranceOutcome>? _playing;

  /// Holds playback so a test can look at what happens meanwhile.
  Completer<void>? hold;

  FakeClipPlayer(this.log);

  @override
  Future<UtteranceOutcome> play(String path) async {
    log.add('play:$path');
    final playing = _playing = Completer<UtteranceOutcome>();
    unawaited(
      Future<void>.delayed(Duration.zero).then((_) => hold?.future).then((_) {
        if (!playing.isCompleted) {
          log.add('done:$path');
          playing.complete(UtteranceOutcome.completed);
        }
      }),
    );
    return playing.future;
  }

  @override
  Future<void> stop() async {
    final playing = _playing;
    if (playing != null && !playing.isCompleted) {
      log.add('stop');
      playing.complete(UtteranceOutcome.cancelled);
    }
  }
}

final german = offlineVoiceById('de-thorsten-emotional')!;

const germanText =
    'Das ist der erste Satz, und er ist nicht sehr lang. '
    'Der zweite Satz folgt sofort und sagt auch nicht viel. '
    'Der dritte Satz ist das Ende, und wir sind froh darüber.';

const englishText =
    'This is the first sentence and it is not very long. '
    'The second one follows and it does not say much either. '
    'The third sentence is the end and we are glad of that.';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late FakeTtsPlatform platform;
  late Log log;
  late FakeClipPlayer player;
  late List<InstalledVoice> loaded;
  late List<FakeSynthesizer> synthesizers;
  late List<String> discarded;
  var enabled = true;
  List<OfflineVoice> installed = [german];
  Object? loadError;

  setUp(() {
    Intl.defaultLocale = 'en_US';
    platform = FakeTtsPlatform()..install();
    log = [];
    player = FakeClipPlayer(log);
    loaded = [];
    synthesizers = [];
    discarded = [];
    enabled = true;
    installed = [german];
    loadError = null;
  });

  tearDown(() => platform.uninstall());

  OfflineSpeechEngine engine() => OfflineSpeechEngine(
    voiceFor: (language) async {
      final voice = pickOfflineVoice(installed, language);
      return voice == null ? null : (voice: voice, path: '/voices/${voice.id}');
    },
    enabled: () => enabled,
    load: (voice) async {
      loaded.add(voice);
      if (loadError != null) throw loadError!;
      final synthesizer = FakeSynthesizer(log);
      synthesizers.add(synthesizer);
      return synthesizer;
    },
    player: player,
    discard: (path) async => discarded.add(path),
    appLanguage: () => 'en',
  );

  SpeechStore store(OfflineSpeechEngine offline) => SpeechStore(
    tts: FlutterTts(),
    preferred: [offline],
    startTimeout: const Duration(milliseconds: 200),
    prepareTimeout: const Duration(seconds: 2),
  );

  Future<bool> read(SpeechStore speech, String text) =>
      speech.speak(title: 'Post', text: text, choice: const TtsChoice());

  group('choosing who reads', () {
    test('a German article is read by the downloaded German voice', () async {
      final read0 = await read(store(engine()), germanText);

      expect(read0, isTrue);
      expect(loaded.single.voice, german);
      expect(log.where((e) => e.startsWith('play:')), hasLength(1));
      expect(platform.spoken, isEmpty, reason: 'system engine stays quiet');
    });

    test('an English article falls back to the system engine', () async {
      await read(store(engine()), englishText);

      expect(loaded, isEmpty);
      expect(log, isEmpty);
      expect(platform.spoken, isNotEmpty);
    });

    test('switched off, the system engine reads', () async {
      enabled = false;
      await read(store(engine()), germanText);

      expect(loaded, isEmpty);
      expect(platform.spoken, isNotEmpty);
    });

    test('a voice that fails to load hands over to the system', () async {
      loadError = StateError('model missing');
      await read(store(engine()), germanText);

      expect(loaded, hasLength(1));
      expect(log, isEmpty);
      expect(platform.spoken, isNotEmpty);
    });

    test('text of unknown language uses the app language', () async {
      installed = [german, offlineVoiceById('en-libritts-r')!];
      final offline = engine();
      expect(await offline.prepare(const TtsChoice()), isTrue);
      expect(loaded.single.voice.id, 'en-libritts-r');
    });

    test('keeps a loaded voice and swaps it for another language', () async {
      installed = [german, offlineVoiceById('en-libritts-r')!];
      final offline = engine();
      await offline.prepare(const TtsChoice(), textLanguage: 'de');
      await offline.prepare(const TtsChoice(), textLanguage: 'de');
      expect(loaded, hasLength(1));

      await offline.prepare(const TtsChoice(), textLanguage: 'en');
      expect(loaded, hasLength(2));
      expect(synthesizers.first.disposed, isTrue);
    });
  });

  group('reading ahead', () {
    test('a short article is a single clip', () async {
      await read(store(engine()), germanText);

      final sentences = chunkForSpeech(germanText, maxChars: 300);
      expect(sentences, hasLength(1), reason: 'short text is one chunk');
      expect(log, ['synth:$germanText', 'play:clip:$germanText', 'done:clip:$germanText']);
    });

    test('chunks play in order with the next one prepared early', () async {
      final text = List.filled(4, germanText).join(' ');
      final chunks = chunkForSpeech(text, maxChars: 300);
      expect(chunks.length, greaterThan(2));

      await read(store(engine()), text);

      final plays = [
        for (final e in log)
          if (e.startsWith('play:')) e,
      ];
      expect(plays, [for (final c in chunks) 'play:clip:$c']);
      final synths = [
        for (final e in log)
          if (e.startsWith('synth:')) e,
      ];
      expect(synths, [for (final c in chunks) 'synth:$c']);
      for (var i = 0; i + 1 < chunks.length; i++) {
        expect(
          log.indexOf('synth:${chunks[i + 1]}'),
          lessThan(log.indexOf('done:clip:${chunks[i]}')),
          reason: 'chunk ${i + 1} is ready before chunk $i ends',
        );
      }
      expect(discarded, [for (final c in chunks) 'clip:$c']);
    });

    test('stopping drops what was prepared and plays nothing more', () async {
      final text = List.filled(4, germanText).join(' ');
      player.hold = Completer<void>();
      final speech = store(engine());
      final reading = read(speech, text);
      while (!log.any((e) => e.startsWith('play:'))) {
        await Future<void>.delayed(Duration.zero);
      }

      await speech.stop();
      player.hold!.complete();
      await reading;
      await Future<void>.delayed(Duration.zero);

      expect(log.where((e) => e.startsWith('play:')), hasLength(1));
      expect(log, contains('stop'));
      final synthesised = log.where((e) => e.startsWith('synth:')).length;
      expect(discarded, hasLength(synthesised), reason: 'no clip left behind');
    });

    test('the speech rate becomes sherpa speed', () async {
      await store(engine()).speak(title: 'Post', text: germanText, choice: const TtsChoice(rate: 0.6));
      expect(synthesizers.single.speeds.single, closeTo(1.2, 1e-9));
    });
  });

  group('clip player', () {
    test('completes when the clip has played to the end', () async {
      final fake = FakeContinuityPlayer();
      final clips = ContinuityClipPlayer(create: () => fake);
      final playing = clips.play('/tmp/a.wav');
      await Future<void>.delayed(Duration.zero);
      fake.emit(completed: true, playing: false);

      expect(await playing, UtteranceOutcome.completed);
      expect(fake.commands, ['open:/tmp/a.wav', 'play']);
    });

    test('a stale "completed" before playback starts is ignored', () async {
      final fake = FakeContinuityPlayer(frame: const PlaybackFrame(completed: true));
      fake.beforeOpen = (_) async => fake.emit(completed: true);
      final clips = ContinuityClipPlayer(create: () => fake);
      var finished = false;
      final playing = clips.play('/tmp/b.wav')..then((_) => finished = true);
      await Future<void>.delayed(Duration.zero);
      expect(finished, isFalse);

      await clips.stop();
      expect(await playing, UtteranceOutcome.cancelled);
    });

    test('a player error fails the clip', () async {
      final fake = FakeContinuityPlayer();
      final clips = ContinuityClipPlayer(create: () => fake);
      final playing = clips.play('/tmp/c.wav');
      await Future<void>.delayed(Duration.zero);
      fake.emit(failed: true);
      expect(await playing, UtteranceOutcome.failed);
    });

    test('a clip that never starts fails instead of hanging', () async {
      final fake = FakeContinuityPlayer();
      fake.beforeOpen = (_) => Completer<void>().future;
      final clips = ContinuityClipPlayer(create: () => fake, startTimeout: const Duration(milliseconds: 20));
      expect(await clips.play('/tmp/d.wav'), UtteranceOutcome.failed);
    });
  });
}
