import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/tweet/_like_button.dart';
import 'package:xta/ui/heart_burst.dart';

/// X's like: the burst plays when a heart is liked, and only then.
void main() {
  Finder burst() => find.byWidgetPredicate((w) => w is CustomPaint && w.painter is HeartBurstPainter);

  Future<void> pump(
    WidgetTester tester, {
    required bool isLiked,
    Map<String, Object> prefs = const {},
    bool disableAnimations = false,
  }) => tester.pumpWidget(
    PrefService(
      service: PrefServiceCache(cache: Map.of(prefs)),
      child: MaterialApp(
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(disableAnimations: disableAnimations),
          child: child!,
        ),
        home: Material(
          child: Scaffold(
            body: Center(
              child: LikeButton(
                isLiked: isLiked,
                label: '12',
                color: Colors.grey,
                activeColor: Colors.pink,
                tooltip: 'Like on this device',
                onPressed: () {},
              ),
            ),
          ),
        ),
      ),
    ),
  );

  testWidgets('liking plays the burst around the heart without moving the footer', (tester) async {
    await pump(tester, isLiked: false);
    final before = tester.getRect(find.byType(LikeButton));

    await tester.tap(find.byType(LikeButton));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 150));

    expect(burst(), findsOneWidget);
    expect(tester.getRect(find.byType(LikeButton)), before, reason: 'the burst takes no space');

    await tester.pumpAndSettle();
    expect(burst(), findsNothing, reason: 'it ends on the plain heart');
  });

  testWidgets('unliking just empties the heart', (tester) async {
    await pump(tester, isLiked: true);

    await tester.tap(find.byType(LikeButton));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 150));

    expect(burst(), findsNothing);
  });

  for (final (name, prefs, disable) in [
    ('the reduced-motion setting', {optionDisableAnimations: true}, false),
    ("Android's remove animations", <String, Object>{}, true),
    ('calm mode', {optionCalmMode: true}, false),
    ('zen mode', {optionZenMode: true}, false),
  ]) {
    testWidgets('$name keeps the heart still', (tester) async {
      await pump(tester, isLiked: false, prefs: prefs, disableAnimations: disable);

      await tester.tap(find.byType(LikeButton));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 150));

      expect(burst(), findsNothing);
    });
  }

  test('every stage of the burst paints, and only a new moment repaints', () {
    for (var t = 0.0; t <= 1.0; t += 0.05) {
      final recorder = ui.PictureRecorder();
      HeartBurstPainter(t: t, color: Colors.pink).paint(Canvas(recorder), const Size.square(18.75));
      recorder.endRecording().dispose();
    }

    const painter = HeartBurstPainter(t: 0.3, color: Colors.pink);
    expect(painter.shouldRepaint(const HeartBurstPainter(t: 0.3, color: Colors.pink)), isFalse);
    expect(painter.shouldRepaint(const HeartBurstPainter(t: 0.4, color: Colors.pink)), isTrue);
  });
}
