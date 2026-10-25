import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/tweet/ticker/ticker_chart.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/ui/reorder_lift.dart';
import 'package:xta/ui/x_look_theme.dart';

TickerQuote _quote(double last) => TickerQuote(
  symbol: 'TSLA',
  currency: 'USD',
  price: last,
  previousClose: 321.66,
  points: [
    for (var i = 0; i < 20; i++) TickerPoint(at: DateTime.utc(2026, 7, 17, 9, 30 + i), close: 320 + i * 3.0),
    TickerPoint(at: DateTime.utc(2026, 7, 17, 9, 50), close: last),
  ],
);

Widget _app(Widget child, {bool reduceMotion = false}) => MaterialApp(
  theme: xLookLightsOutTheme(null),
  home: MediaQuery(
    data: MediaQueryData(disableAnimations: reduceMotion),
    child: Scaffold(body: child),
  ),
);

/// How much of the price line is drawn, from the chart's painter.
double _drawn(WidgetTester tester) {
  final paint = tester.widget<CustomPaint>(
    find.descendant(of: find.byType(TickerChart), matching: find.byType(CustomPaint)).first,
  );
  return ((paint.painter as dynamic).drawn as Animation<double>).value;
}

const _row = Key('row');

/// The lift of a row at [t] of its pick-up.
Future<(Transform, Material)> _lifted(WidgetTester tester, double t, {bool reduceMotion = false}) async {
  await tester.pumpWidget(
    _app(
      Center(child: xtaReorderLift(const SizedBox(key: _row, width: 200, height: 56), 0, AlwaysStoppedAnimation(t))),
      reduceMotion: reduceMotion,
    ),
  );
  T nearest<T extends Widget>() =>
      tester.widget<T>(find.ancestor(of: find.byKey(_row), matching: find.byType(T)).first);
  return (nearest<Transform>(), nearest<Material>());
}

void main() {
  group('a dragged row', () {
    testWidgets('lifts: it grows, rises on a shadow and takes the raised surface', (tester) async {
      final (transform, material) = await _lifted(tester, 1);
      final surface = xLookFloatingSurface(XLookTokens.of(tester.element(find.byType(Scaffold))));

      expect(transform.transform.getMaxScaleOnAxis(), closeTo(kReorderLiftScale, 0.001));
      expect(material.elevation, kReorderLiftElevation);
      expect(material.color, surface);
      expect(material.borderRadius, BorderRadius.circular(kReorderLiftRadius));
    });

    testWidgets('settles back flat when dropped', (tester) async {
      final (transform, material) = await _lifted(tester, 0);

      expect(transform.transform.getMaxScaleOnAxis(), 1);
      expect(material.elevation, 0);
    });

    testWidgets('with reduced motion rises without growing', (tester) async {
      final (transform, material) = await _lifted(tester, 1, reduceMotion: true);

      expect(transform.transform.getMaxScaleOnAxis(), 1);
      expect(material.elevation, kReorderLiftElevation);
    });

    test('every reorderable list lifts its rows', () {
      final constructor = RegExp(r'\b(ReorderableListView(\.builder)?|SliverReorderableList)\(');
      final missing = <String>[];
      for (final file in Directory('lib').listSync(recursive: true).whereType<File>()) {
        if (!file.path.endsWith('.dart')) continue;
        final source = file.readAsStringSync();
        final lists = constructor.allMatches(source).length;
        final lifted = 'proxyDecorator: xtaReorderLift'.allMatches(source).length;
        if (lists != lifted) missing.add('${file.path}: $lists lists, $lifted lifted');
      }
      expect(missing, isEmpty);
    });
  });

  group('the price chart', () {
    testWidgets('draws its line in from the left when it appears', (tester) async {
      await tester.pumpWidget(_app(TickerChart(quote: _quote(380), drawKey: 'day')));
      expect(_drawn(tester), 0);

      await tester.pump(const Duration(milliseconds: 350));
      expect(_drawn(tester), inExclusiveRange(0, 1));

      await tester.pump(kTickerChartDrawIn);
      expect(_drawn(tester), 1);
    });

    testWidgets('draws it again for another range, not for a refreshed quote', (tester) async {
      await tester.pumpWidget(_app(TickerChart(quote: _quote(380), drawKey: 'day')));
      await tester.pumpAndSettle();

      await tester.pumpWidget(_app(TickerChart(quote: _quote(381), drawKey: 'day')));
      await tester.pump();
      expect(_drawn(tester), 1, reason: 'a refresh of the same range keeps the line drawn');

      await tester.pumpWidget(_app(TickerChart(quote: _quote(390), drawKey: 'year')));
      await tester.pump();
      expect(_drawn(tester), lessThan(0.2));
      await tester.pumpAndSettle();
      expect(_drawn(tester), 1);
    });

    testWidgets('with reduced motion the line is there at once', (tester) async {
      await tester.pumpWidget(_app(TickerChart(quote: _quote(380), drawKey: 'day'), reduceMotion: true));

      expect(_drawn(tester), 1);
      await tester.pump(const Duration(milliseconds: 100));
      expect(_drawn(tester), 1);
    });
  });
}
