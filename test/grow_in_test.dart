import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/ui/motion.dart';

Widget _screen({required bool visible, VoidCallback? onPressed, bool reduceMotion = false}) => MaterialApp(
  home: MediaQuery(
    data: MediaQueryData(disableAnimations: reduceMotion),
    child: Scaffold(
      body: const SizedBox.expand(),
      floatingActionButton: XtaGrowIn(
        visible: visible,
        child: FloatingActionButton(
          tooltip: 'Scroll to top',
          onPressed: onPressed ?? () {},
          child: const Icon(Icons.arrow_upward),
        ),
      ),
    ),
  ),
);

final _button = find.byType(FloatingActionButton);

// The nearest, not the scaffold's own.
double _scale(WidgetTester tester) => tester
    .widget<ScaleTransition>(find.ancestor(of: _button, matching: find.byType(ScaleTransition)).first)
    .scale
    .value;

/// The button as a screen reader finds it, by its tooltip.
final _announced = find.semantics.byPredicate((node) => node.tooltip == 'Scroll to top');

double _opacity(WidgetTester tester) => tester
    .widget<FadeTransition>(find.ancestor(of: _button, matching: find.byType(FadeTransition)).first)
    .opacity
    .value;

void main() {
  testWidgets('a floating button grows in as it fades in', (tester) async {
    await tester.pumpWidget(_screen(visible: false));
    expect(_button, findsNothing);

    await tester.pumpWidget(_screen(visible: true));
    expect(_scale(tester), kXtaGrowFrom);
    expect(_opacity(tester), 0);

    await tester.pump(const Duration(milliseconds: 90));
    expect(_scale(tester), inExclusiveRange(kXtaGrowFrom, 1));
    expect(_opacity(tester), inExclusiveRange(0, 1));

    await tester.pumpAndSettle();
    expect(_scale(tester), 1);
    expect(_opacity(tester), 1);
  });

  testWidgets('it shrinks away as it fades out, and cannot be tapped or read meanwhile', (tester) async {
    var taps = 0;
    await tester.pumpWidget(_screen(visible: true, onPressed: () => taps++));
    await tester.pumpAndSettle();
    final semantics = tester.ensureSemantics();
    expect(_announced, findsOne);

    await tester.pumpWidget(_screen(visible: false, onPressed: () => taps++));
    await tester.pump(const Duration(milliseconds: 60));
    expect(_scale(tester), inExclusiveRange(kXtaGrowFrom, 1));
    expect(_opacity(tester), inExclusiveRange(0, 1));
    expect(_announced, findsNothing);
    await tester.tap(_button, warnIfMissed: false);
    expect(taps, 0);

    await tester.pumpAndSettle();
    expect(_button, findsNothing);
    semantics.dispose();
  });

  testWidgets('hidden, it does not lift a floating snack bar', (tester) async {
    Future<double> snackBarBottom({required bool visible}) async {
      await tester.pumpWidget(_screen(visible: visible));
      await tester.pumpAndSettle();
      ScaffoldMessenger.of(
        tester.element(find.byType(Scaffold)),
      ).showSnackBar(const SnackBar(content: Text('Saved'), behavior: SnackBarBehavior.floating));
      await tester.pumpAndSettle();
      final bottom = tester.getRect(find.byType(SnackBar)).bottom;
      ScaffoldMessenger.of(tester.element(find.byType(Scaffold))).removeCurrentSnackBar();
      await tester.pumpAndSettle();
      return bottom;
    }

    final hidden = await snackBarBottom(visible: false);
    final shown = await snackBarBottom(visible: true);
    expect(shown, lessThan(hidden), reason: 'a shown button keeps the snack bar above it');
    expect(hidden, greaterThan(tester.getSize(find.byType(Scaffold)).height - 24));
  });

  testWidgets('with reduced motion it comes and goes at once', (tester) async {
    await tester.pumpWidget(_screen(visible: true, reduceMotion: true));
    expect(_scale(tester), 1);
    expect(_opacity(tester), 1);

    await tester.pumpWidget(_screen(visible: false, reduceMotion: true));
    await tester.pump();
    expect(_button, findsNothing);
  });
}
