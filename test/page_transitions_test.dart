import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/ui/page_transitions.dart';
import 'package:xta/ui/x_look_theme.dart';

void main() {
  test("pushed screens use X's fade and settle on Android and the desktop", () {
    final builders = xLookThemeData(XLookTokens.lightsOut, null).pageTransitionsTheme.builders;
    for (final platform in [TargetPlatform.android, TargetPlatform.linux, TargetPlatform.windows]) {
      expect(builders[platform], isA<XtaPageTransitionsBuilder>(), reason: '$platform');
    }
    const builder = XtaPageTransitionsBuilder();
    expect(builder.transitionDuration, const Duration(milliseconds: 200));
    expect(builder.reverseTransitionDuration, const Duration(milliseconds: 160));
  });

  test('a transition theme handed in, as reduce motion does, still wins', () {
    const handed = PageTransitionsTheme(builders: {TargetPlatform.android: ZoomPageTransitionsBuilder()});
    final builders = xLookThemeData(XLookTokens.lightsOut, handed).pageTransitionsTheme.builders;
    expect(builders[TargetPlatform.android], isA<ZoomPageTransitionsBuilder>());
  });

  const page = Key('page');

  Future<void> open(WidgetTester tester, {bool disableAnimations = false}) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: xLookThemeData(XLookTokens.lightsOut, null),
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(disableAnimations: disableAnimations),
          child: child!,
        ),
        home: Builder(
          builder: (context) => TextButton(
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute<void>(
                builder: (_) => const Material(
                  key: page,
                  child: Column(children: [Text('opened'), TextField()]),
                ),
              ),
            ),
            child: const Text('open'),
          ),
        ),
      ),
    );
    await tester.tap(find.text('open'));
    await tester.pump();
  }

  testWidgets('the route fades in while settling from a little larger, without sliding', (tester) async {
    await open(tester);
    await tester.pump(const Duration(milliseconds: 100));

    final push = find.ancestor(of: find.byKey(page), matching: find.byType(XtaPushTransition));
    final fade = tester.widget<FadeTransition>(find.descendant(of: push, matching: find.byType(FadeTransition)).first);
    final scale = tester.widget<ScaleTransition>(
      find.descendant(of: push, matching: find.byType(ScaleTransition)).first,
    );
    expect(fade.opacity.value, inExclusiveRange(0, 1));
    expect(scale.scale.value, inExclusiveRange(1, kXtaPushStartScale));
    expect(find.ancestor(of: find.byKey(page), matching: find.byType(SlideTransition)), findsNothing);

    await tester.pumpAndSettle();
    expect(tester.getRect(find.byKey(page)), Offset.zero & tester.view.physicalSize / tester.view.devicePixelRatio);
  });

  testWidgets('with animations removed the page is simply there', (tester) async {
    await open(tester, disableAnimations: true);

    expect(find.byType(XtaPushTransition), findsNothing);
    expect(find.byType(XtaBackGestureTransition), findsNothing);
  });

  group("Android's back gesture", () {
    Future<void> send(WidgetTester tester, String method, [Map<String, Object?>? arguments]) =>
        tester.binding.defaultBinaryMessenger.handlePlatformMessage(
          'flutter/backgesture',
          const StandardMethodCodec().encodeMethodCall(MethodCall(method, arguments)),
          (_) {},
        );

    Map<String, Object?> event(double progress) => {
      'touchOffset': [5.0, 300.0],
      'progress': progress,
      'swipeEdge': 0,
    };

    testWidgets('shrinks the page with the finger, and a cancel makes it whole', (tester) async {
      await open(tester);
      await tester.pumpAndSettle();
      final whole = tester.getRect(find.byKey(page));

      await send(tester, 'startBackGesture', event(0));
      await send(tester, 'updateBackGestureProgress', event(0.5));
      await tester.pump();

      final shrunk = tester.getRect(find.byKey(page));
      expect(find.byType(XtaBackGestureTransition), findsOneWidget);
      expect(shrunk.width, closeTo(whole.width * (1 - (1 - kXtaBackGestureMinScale) * 0.5), 1));
      expect(shrunk.center.dx, greaterThan(whole.center.dx), reason: 'it moves away from the edge swiped from');
      expect(find.text('open'), findsOneWidget, reason: 'the page behind shows around it');

      await send(tester, 'cancelBackGesture');
      await tester.pumpAndSettle();
      expect(tester.getRect(find.byKey(page)), whole);
    });

    testWidgets('the page keeps what it holds through a gesture and its cancel', (tester) async {
      await open(tester);
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), 'kept');

      await send(tester, 'startBackGesture', event(0));
      await send(tester, 'updateBackGestureProgress', event(0.4));
      await tester.pump();
      expect(find.text('kept'), findsOneWidget, reason: 'the page moved into the gesture, it was not rebuilt');

      await send(tester, 'cancelBackGesture');
      await tester.pumpAndSettle();
      expect(find.text('kept'), findsOneWidget);
    });

    testWidgets('committed, it leaves the page behind', (tester) async {
      await open(tester);
      await tester.pumpAndSettle();

      await send(tester, 'startBackGesture', event(0));
      await send(tester, 'updateBackGestureProgress', event(0.6));
      await tester.pump();
      await send(tester, 'commitBackGesture');
      await tester.pumpAndSettle();

      expect(find.byKey(page), findsNothing);
      expect(find.text('open'), findsOneWidget);
    });
  });
}
