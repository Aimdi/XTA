import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/ui/motion.dart';

void main() {
  testWidgets('XTA motion uses the standard duration by default', (
    tester,
  ) async {
    late Duration duration;
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) {
            duration = xtaMotionDuration(context, kXtaMotionStandard);
            return const SizedBox();
          },
        ),
      ),
    );

    expect(duration, kXtaMotionStandard);
  });

  testWidgets('Android reduced motion resolves every XTA duration to zero', (
    tester,
  ) async {
    late Duration duration;
    await tester.pumpWidget(
      MaterialApp(
        home: MediaQuery(
          data: const MediaQueryData(disableAnimations: true),
          child: Builder(
            builder: (context) {
              duration = xtaMotionDuration(context, kXtaMotionNavigation);
              return const SizedBox();
            },
          ),
        ),
      ),
    );

    expect(duration, Duration.zero);
  });

  testWidgets('XTA accessibility preference also disables local motion', (
    tester,
  ) async {
    late Duration duration;
    await tester.pumpWidget(
      PrefService(
        service: PrefServiceCache(
          cache: {optionDisableAnimations: true},
        ),
        child: MaterialApp(
          home: Builder(
            builder: (context) {
              duration = xtaMotionDuration(context, kXtaMotionFast);
              return const SizedBox();
            },
          ),
        ),
      ),
    );

    expect(duration, Duration.zero);
  });

  testWidgets('compact switcher removes both fade and size timing', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: MediaQuery(
          data: MediaQueryData(disableAnimations: true),
          child: XtaAnimatedSwitcher(
            animateSize: true,
            child: SizedBox(key: ValueKey('content'), height: 48),
          ),
        ),
      ),
    );

    expect(
      tester.widget<AnimatedSwitcher>(find.byType(AnimatedSwitcher)).duration,
      Duration.zero,
    );
    expect(
      tester.widget<AnimatedSize>(find.byType(AnimatedSize)).duration,
      Duration.zero,
    );
  });
}
