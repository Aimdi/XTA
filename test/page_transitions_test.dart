import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/ui/page_transitions.dart';
import 'package:xta/ui/x_look_theme.dart';

void main() {
  test('pushed screens fade in quickly on Android and the desktop', () {
    final builders = xLookThemeData(XLookTokens.lightsOut, null).pageTransitionsTheme.builders;
    for (final platform in [TargetPlatform.android, TargetPlatform.linux, TargetPlatform.windows]) {
      expect(builders[platform], isA<XtaQuickFadePageTransitionsBuilder>(), reason: '$platform');
    }
    const builder = XtaQuickFadePageTransitionsBuilder();
    expect(builder.transitionDuration, const Duration(milliseconds: 200));
    expect(builder.reverseTransitionDuration, const Duration(milliseconds: 160));
  });

  test('a transition theme handed in, as reduce motion does, still wins', () {
    const handed = PageTransitionsTheme(builders: {TargetPlatform.android: ZoomPageTransitionsBuilder()});
    final builders = xLookThemeData(XLookTokens.lightsOut, handed).pageTransitionsTheme.builders;
    expect(builders[TargetPlatform.android], isA<ZoomPageTransitionsBuilder>());
  });

  testWidgets('the route fades without moving', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: xLookThemeData(XLookTokens.lightsOut, null),
        home: Builder(
          builder: (context) => TextButton(
            onPressed: () => Navigator.push(context, MaterialPageRoute<void>(builder: (_) => const Text('opened'))),
            child: const Text('open'),
          ),
        ),
      ),
    );
    await tester.tap(find.text('open'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    final fade = tester.widget<FadeTransition>(
      find.ancestor(of: find.text('opened'), matching: find.byType(FadeTransition)).first,
    );
    expect(fade.opacity.value, inExclusiveRange(0, 1));
    expect(find.ancestor(of: find.text('opened'), matching: find.byType(SlideTransition)), findsNothing);

    await tester.pumpAndSettle();
    expect(find.text('opened'), findsOneWidget);
  });
}
