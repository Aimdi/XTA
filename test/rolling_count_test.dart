import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/ui/rolling_count.dart';

Future<void> _pump(WidgetTester tester, Widget count, {bool reduceMotion = false}) => tester.pumpWidget(
  MediaQuery(
    data: MediaQueryData(disableAnimations: reduceMotion),
    child: Directionality(
      textDirection: TextDirection.ltr,
      child: Center(child: count),
    ),
  ),
);

/// How far [text] has moved down from where it rests.
double _drop(WidgetTester tester, String text) => tester
    .widget<Transform>(find.ancestor(of: find.text(text), matching: find.byType(Transform)).first)
    .transform
    .getTranslation()
    .y;

void main() {
  test('a rise rolls up, a fall down, an unknown change up', () {
    expect(rollingCountRises(35, 36), isTrue);
    expect(rollingCountRises(36, 35), isFalse);
    expect(rollingCountRises(null, 35), isTrue);
  });

  testWidgets('a new number rolls in as the old one leaves upwards', (tester) async {
    await _pump(tester, const RollingCount('35', value: 35));
    await _pump(tester, const RollingCount('36', value: 36));
    await tester.pump(const Duration(milliseconds: 100));

    expect(find.text('35'), findsOneWidget);
    expect(find.text('36'), findsOneWidget);
    expect(_drop(tester, '35'), lessThan(0), reason: 'the old count slides up and out');
    expect(_drop(tester, '36'), greaterThan(0), reason: 'the new one comes up from below');

    await tester.pump(kRollingCountDuration);
    expect(find.text('35'), findsNothing);
    expect(find.byType(Transform), findsNothing, reason: 'at rest it is plain text');
  });

  testWidgets('a count that fell rolls downwards', (tester) async {
    await _pump(tester, const RollingCount('36', value: 36));
    await _pump(tester, const RollingCount('35', value: 35));
    await tester.pump(const Duration(milliseconds: 100));

    expect(_drop(tester, '36'), greaterThan(0));
    expect(_drop(tester, '35'), lessThan(0));
  });

  testWidgets('rolls from what it showed before on its first build', (tester) async {
    await _pump(tester, const RollingCount('692', value: 692, from: '624', fromValue: 624));
    await tester.pump(const Duration(milliseconds: 100));

    expect(find.text('624'), findsOneWidget);
    expect(find.text('692'), findsOneWidget);
  });

  testWidgets('a count appearing, going away or unchanged does not roll', (tester) async {
    await _pump(tester, const RollingCount(''));
    await _pump(tester, const RollingCount('35'));
    expect(tester.binding.hasScheduledFrame, isFalse);

    await _pump(tester, const RollingCount('35', from: '35'));
    expect(tester.binding.hasScheduledFrame, isFalse);
    expect(find.text('35'), findsOneWidget);
  });

  testWidgets('with reduced motion the count changes at once', (tester) async {
    await _pump(tester, const RollingCount('35'), reduceMotion: true);
    await _pump(tester, const RollingCount('36'), reduceMotion: true);

    expect(tester.binding.hasScheduledFrame, isFalse);
    expect(find.text('35'), findsNothing);
    expect(find.text('36'), findsOneWidget);
  });

  testWidgets('a screen reader hears only the new count while it rolls', (tester) async {
    final semantics = tester.ensureSemantics();
    await _pump(tester, const RollingCount('35'));
    await _pump(tester, const RollingCount('36'));
    await tester.pump(const Duration(milliseconds: 100));

    expect(find.bySemanticsLabel('35'), findsNothing);
    expect(find.bySemanticsLabel('36'), findsOneWidget);
    semantics.dispose();
  });

  testWidgets('its width moves from the old number\'s to the new one\'s', (tester) async {
    await _pump(tester, const RollingCount('99', value: 99));
    final narrow = tester.getSize(find.byType(RollingCount)).width;
    await _pump(tester, const RollingCount('100', value: 100));
    final wide = tester.getSize(find.text('100')).width;
    await tester.pump(const Duration(milliseconds: 150));

    final between = tester.getSize(find.byType(RollingCount)).width;
    expect(between, greaterThan(narrow));
    expect(between, lessThan(wide));
  });
}
