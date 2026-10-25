import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/main.dart' show xtaLocalizationsDelegates;
import 'package:xta/settings/_accessibility.dart';
import 'package:xta/ui/action_feedback.dart';

void main() {
  final taps = <Object?>[];

  setUp(() {
    taps.clear();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (call) async {
        if (call.method == 'HapticFeedback.vibrate') taps.add(call.arguments);
        return null;
      },
    );
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      null,
    );
  });

  /// Plays every kind of tap XTA gives, from under preferences holding
  /// [cache], or from under none.
  Future<void> playAll(WidgetTester tester, Map<String, dynamic>? cache) async {
    late BuildContext context;
    final probe = Builder(
      builder: (probeContext) {
        context = probeContext;
        return const SizedBox();
      },
    );
    await tester.pumpWidget(
      cache == null
          ? probe
          : PrefService(
              service: PrefServiceCache(cache: cache),
              child: probe,
            ),
    );
    lightHaptic(context);
    selectionHaptic(context);
    actionTap(context);
    await tester.pump();
  }

  const all = ['HapticFeedbackType.lightImpact', 'HapticFeedbackType.selectionClick', 'HapticFeedbackType.lightImpact'];

  testWidgets('XTA\'s taps play while haptic feedback is on, as it is unless turned off', (tester) async {
    await playAll(tester, null);
    expect(taps, all);

    taps.clear();
    await playAll(tester, {optionHapticFeedback: true});
    expect(taps, all);
  });

  testWidgets('turning haptic feedback off silences every one of them', (tester) async {
    await playAll(tester, {optionHapticFeedback: false});
    expect(taps, isEmpty);
  });

  testWidgets('the switch sits in Accessibility, on, and turns haptic feedback off', (tester) async {
    final prefs = PrefServiceCache(defaults: {optionHapticFeedback: true});
    await tester.pumpWidget(
      PrefService(
        service: prefs,
        child: MaterialApp(
          localizationsDelegates: xtaLocalizationsDelegates,
          supportedLocales: const [Locale('en')],
          home: const SettingsAccessibilityFragment(),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('A short vibration when you like, save or follow.'), findsOneWidget);

    await tester.tap(find.text('Haptic feedback'));
    await tester.pumpAndSettle();
    expect(prefs.get<bool>(optionHapticFeedback), isFalse);
  });
}
