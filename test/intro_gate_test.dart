import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/intro/intro_gate.dart';
import 'package:xta/intro/intro_store.dart';

/// Same order as `main`: look for any key, then write the defaults.
Future<void> _launch(BasePrefService prefs) async {
  final firstLaunch = prefs.getKeys().isEmpty;
  await prefs.setDefaultValues({...introLaunchDefaults(firstLaunch: firstLaunch), optionConfirmClose: true});
}

void main() {
  group('launch default', () {
    test('a fresh install has not seen the intro', () async {
      final prefs = PrefServiceCache();
      await _launch(prefs);

      expect(prefs.get<bool>(optionIntroSeen), isFalse);
    });

    test('an install that already has any pref never sees it', () async {
      final prefs = PrefServiceCache(cache: {optionConfirmClose: false});
      await _launch(prefs);

      expect(prefs.get<bool>(optionIntroSeen), isTrue);
    });

    test('a second launch keeps the flag a fresh install was given', () async {
      final prefs = PrefServiceCache();
      await _launch(prefs);
      await _launch(prefs);

      expect(prefs.get<bool>(optionIntroSeen), isFalse);
    });
  });

  group('IntroGate', () {
    Future<IntroStore> pump(WidgetTester tester, BasePrefService prefs) async {
      final store = IntroStore(prefs);
      addTearDown(store.destroy);
      await tester.pumpWidget(
        MaterialApp(
          home: IntroGate(store: store, home: const Text('home'), intro: const Text('intro')),
        ),
      );
      return store;
    }

    testWidgets('shows the cards until they are seen, then Home, and back on replay', (tester) async {
      final prefs = PrefServiceCache(cache: {optionIntroSeen: false});
      final store = await pump(tester, prefs);
      expect(find.text('intro'), findsOneWidget);

      await store.markSeen();
      await tester.pump();
      expect(find.text('home'), findsOneWidget);
      expect(prefs.get<bool>(optionIntroSeen), isTrue);

      await store.showAgain();
      await tester.pump();
      expect(find.text('intro'), findsOneWidget);
      expect(prefs.get<bool>(optionIntroSeen), isFalse);
    });

    testWidgets('starts on Home when the pref is already true', (tester) async {
      await pump(tester, PrefServiceCache(cache: {optionIntroSeen: true}));

      expect(find.text('home'), findsOneWidget);
      expect(find.text('intro'), findsNothing);
    });
  });
}
