import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:intl/intl.dart';
import 'package:xta/speech/speech_store.dart';
import 'package:xta/speech/system_speech_engine.dart';
import 'package:xta/speech/text_language.dart';
import 'package:xta/speech/tts_engines.dart';

/// Plays the Android side of flutter_tts: answers method calls and, like the
/// real plugin, reports an utterance's start and end through callbacks.
class FakeTtsPlatform {
  static const channel = MethodChannel('flutter_tts');

  final calls = <MethodCall>[];
  List<String> engines = ['com.google.android.tts'];
  String? defaultEngine = 'com.google.android.tts';
  List<String> languages = ['en-US', 'de-DE'];

  /// What happens to a spoken utterance.
  SpeakBehaviour behaviour = SpeakBehaviour.finish;

  /// Holds utterances so a test can stop one mid-way.
  Completer<void>? holdUtterance;

  /// Android sends the text in a map, other hosts as the bare string.
  List<String> get spoken => [
    for (final call in calls)
      if (call.method == 'speak')
        '${call.arguments is Map ? call.arguments['text'] : call.arguments}',
  ];

  List<String> methods(String name) => [
    for (final call in calls)
      if (call.method == name) '${call.arguments}',
  ];

  void install() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, _answer);
  }

  void uninstall() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  }

  Future<Object?> _answer(MethodCall call) async {
    calls.add(call);
    return switch (call.method) {
      'getEngines' => engines,
      'getDefaultEngine' => defaultEngine,
      'getLanguages' => languages,
      'isLanguageAvailable' => languages.contains(call.arguments),
      'speak' => _speak(),
      _ => 1,
    };
  }

  Future<Object?> _speak() {
    switch (behaviour) {
      case SpeakBehaviour.finish:
        unawaited(_playUtterance());
        return Future.value(1);
      case SpeakBehaviour.error:
        unawaited(_report(['speak.onStart', 'speak.onError']));
        return Future.value(1);
      case SpeakBehaviour.refuse:
        return Future.value(0);
      case SpeakBehaviour.park:
        // What flutter_tts does when the engine turns the utterance away.
        return Completer<Object?>().future;
    }
  }

  Future<void> _playUtterance() async {
    await _report(['speak.onStart']);
    await holdUtterance?.future;
    if (holdUtterance != null) return;
    await _report(['speak.onComplete']);
  }

  Future<void> _report(List<String> events) async {
    for (final event in events) {
      await Future<void>.delayed(Duration.zero);
      await TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .handlePlatformMessage(
            channel.name,
            const StandardMethodCodec().encodeMethodCall(MethodCall(event)),
            (_) {},
          );
    }
  }
}

enum SpeakBehaviour { finish, error, refuse, park }

String sentences(String sentence, int count) =>
    List.filled(count, sentence).join(' ');

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late FakeTtsPlatform platform;

  setUp(() {
    Intl.defaultLocale = 'en_US';
    platform = FakeTtsPlatform()..install();
  });

  tearDown(() => platform.uninstall());

  SpeechStore store() => SpeechStore(
    tts: FlutterTts(),
    startTimeout: const Duration(milliseconds: 200),
    prepareTimeout: const Duration(seconds: 2),
  );

  const english = TtsChoice();
  final longEnglish = sentences(
    'This is the story of a sentence that is long enough to count.',
    200,
  );

  group('reading a long article', () {
    test('speaks every chunk in order and then clears the bar', () async {
      final speech = store();
      final read = await speech.speak(
        title: 'Post',
        text: longEnglish,
        choice: english,
      );

      expect(read, isTrue);
      expect(platform.spoken.length, greaterThan(2));
      expect(platform.spoken.join(' '), longEnglish);
      for (final chunk in platform.spoken) {
        expect(chunk.length, lessThan(4000), reason: 'Android max input');
      }
      expect(speech.state.speaking, isFalse);
    });

    test('shows the bar while it is reading', () async {
      final speech = store();
      platform.holdUtterance = Completer<void>();
      final reading = speech.speak(
        title: 'Post',
        text: 'A short post that is read aloud.',
        choice: english,
      );
      await pumpEventQueue();

      expect(speech.state, const SpeechPlayback(title: 'Post', speaking: true));
      await speech.stop();
      expect(await reading, isTrue);
      expect(speech.state.speaking, isFalse);
    });

    test('stopping half-way speaks no further chunk', () async {
      final speech = store();
      platform.holdUtterance = Completer<void>();
      final reading = speech.speak(
        title: 'Post',
        text: longEnglish,
        choice: english,
      );
      await pumpEventQueue();
      await speech.stop();

      expect(await reading, isTrue);
      expect(platform.spoken, hasLength(1));
      expect(platform.methods('stop'), isNotEmpty);
    });
  });

  group('an engine that goes quiet is reported, not waited on', () {
    test('a parked utterance fails after the start timeout', () async {
      platform.behaviour = SpeakBehaviour.park;
      final speech = store();

      final read = await speech
          .speak(title: 'Post', text: longEnglish, choice: english)
          .timeout(const Duration(seconds: 5));

      expect(read, isFalse);
      expect(speech.state.speaking, isFalse);
    });

    test('an utterance error ends the reading as failed', () async {
      platform.behaviour = SpeakBehaviour.error;
      final speech = store();

      final read = await speech
          .speak(title: 'Post', text: longEnglish, choice: english)
          .timeout(const Duration(seconds: 5));

      expect(read, isFalse);
      // Retried once, then given up on — the rest is not queued.
      expect(platform.spoken, hasLength(2));
    });

    test('a refused utterance is retried once, then reported', () async {
      platform.behaviour = SpeakBehaviour.refuse;
      final speech = store();

      final read = await speech.speak(
        title: 'Post',
        text: 'Short text to read.',
        choice: english,
      );

      expect(read, isFalse);
      expect(platform.spoken, hasLength(2));
    });

    test('a phone without any engine fails before speaking', () async {
      platform
        ..engines = []
        ..defaultEngine = null;
      final speech = store();

      final read = await speech.speak(
        title: 'Post',
        text: 'Short text to read.',
        choice: english,
      );

      expect(read, isFalse);
      expect(platform.spoken, isEmpty);
    });

    test('nothing to say is not a reading', () async {
      final read = await store().speak(
        title: 'Post',
        text: '   ',
        choice: english,
      );
      expect(read, isFalse);
      expect(platform.spoken, isEmpty);
    });
  });

  group('binding the engine', () {
    test('the system default is not rebound on every reading', () async {
      final speech = store();
      await speech.speak(title: 'A', text: 'One post.', choice: english);
      await speech.speak(title: 'B', text: 'Two posts.', choice: english);

      expect(platform.methods('setEngine'), isEmpty);
    });

    test('a chosen engine is bound once, not on every reading', () async {
      platform.engines = ['com.google.android.tts', sherpaOnnxTtsEngine];
      final speech = store();
      const sherpa = TtsChoice(engine: sherpaOnnxTtsEngine);

      await speech.speak(title: 'A', text: 'One post.', choice: sherpa);
      await speech.speak(title: 'B', text: 'Two posts.', choice: sherpa);

      expect(platform.methods('setEngine'), [sherpaOnnxTtsEngine]);
    });

    test(
      'still hears utterances end after the settings made a FlutterTts',
      () async {
        final speech = store();
        FlutterTts(); // what the settings screens used to do

        final read = await speech
            .speak(title: 'Post', text: longEnglish, choice: english)
            .timeout(const Duration(seconds: 5));

        expect(read, isTrue);
        expect(platform.spoken.length, greaterThan(2));
      },
    );
  });

  group('the language of the article picks the voice', () {
    final german = sentences(
      'Das ist ein Satz, der nicht auf Englisch ist und den wir vorlesen.',
      5,
    );

    test('an English article is read in English in a German app', () async {
      Intl.defaultLocale = 'de';
      const germanVoice = TtsChoice(voiceName: 'de-x-1', voiceLocale: 'de-DE');

      await store().speak(
        title: 'Post',
        text: longEnglish,
        choice: germanVoice,
      );

      expect(platform.methods('setLanguage'), ['en-US']);
      expect(platform.methods('setVoice'), isEmpty);
    });

    test('a German article keeps the chosen German voice', () async {
      const germanVoice = TtsChoice(voiceName: 'de-x-1', voiceLocale: 'de-DE');

      await store().speak(title: 'Post', text: german, choice: germanVoice);

      expect(platform.methods('setLanguage'), ['de-DE']);
      final order = platform.calls.map((call) => call.method).toList();
      expect(
        order.lastIndexOf('setVoice'),
        greaterThan(order.lastIndexOf('setLanguage')),
        reason: 'setLanguage resets the voice on Android',
      );
    });

    test('an engine without the article language falls back', () async {
      platform.languages = ['en-US'];

      await store().speak(title: 'Post', text: german, choice: english);

      expect(platform.methods('setLanguage').single, startsWith('en'));
    });
  });

  group('detectTextLanguage', () {
    test('tells German from English', () {
      expect(
        detectTextLanguage(
          'Die Regierung hat am Montag angekündigt, dass sie die Steuer nicht '
          'erhöhen wird. Das ist auch für uns eine gute Nachricht.',
        ),
        'de',
      );
      expect(
        detectTextLanguage(
          'The government said on Monday that it will not raise the tax. '
          'This is good news for all of us, and it was expected.',
        ),
        'en',
      );
    });

    test('reads French and Spanish too', () {
      expect(
        detectTextLanguage(
          'Le gouvernement a annoncé que les impôts ne vont pas augmenter et '
          'que la situation est stable pour les familles.',
        ),
        'fr',
      );
      expect(
        detectTextLanguage(
          'El gobierno dijo que los impuestos no van a subir y que la '
          'situación es estable para las familias del país.',
        ),
        'es',
      );
    });

    test('says nothing about too little text', () {
      expect(detectTextLanguage('Hello there'), isNull);
      expect(detectTextLanguage(''), isNull);
    });
  });

  group('language candidates', () {
    test('the text language goes first, with the voice region', () {
      expect(
        speakLanguageCandidates(
          textLanguage: 'en',
          voiceLocale: 'en-GB',
          appLocale: 'de-DE',
        ).first,
        'en-GB',
      );
      expect(
        speakLanguageCandidates(textLanguage: 'en', appLocale: 'de-DE').first,
        'en-US',
      );
    });

    test('a voice suits text in its own language only', () {
      expect(voiceSuitsLanguage('de-DE', 'de'), isTrue);
      expect(voiceSuitsLanguage('de_DE', 'de-AT'), isTrue);
      expect(voiceSuitsLanguage('de-DE', 'en-US'), isFalse);
      expect(voiceSuitsLanguage('de-DE', null), isTrue);
    });
  });
}
