import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/home/home_chrome.dart';
import 'package:xta/ui/floating_notice.dart';

Widget _page({Widget? notice, EdgeInsets padding = EdgeInsets.zero, bool reduceMotion = false}) => MaterialApp(
  home: MediaQuery(
    data: MediaQueryData(size: const Size(390, 800), padding: padding, disableAnimations: reduceMotion),
    child: Scaffold(
      body: FloatingNotice(
        notice: notice,
        child: ListView(children: const [SizedBox(height: 80, child: Text('first post'))]),
      ),
    ),
  ),
);

Widget _retry({String label = 'Retry', VoidCallback? onPressed}) => NoticePill(
  child: TextButton.icon(onPressed: onPressed ?? () {}, icon: const Icon(Icons.refresh), label: Text(label)),
);

/// How opaque the pill is drawn, through every fade around it.
double _opacity(WidgetTester tester) => tester
    .widgetList<Opacity>(find.ancestor(of: find.byType(FrostedPill), matching: find.byType(Opacity)))
    .fold(1.0, (opacity, fade) => opacity * fade.opacity);

double _top(WidgetTester tester) => tester.getRect(find.byType(FrostedPill)).top;

/// [read] after one more frame.
Future<double> _frame(WidgetTester tester, double Function() read) async {
  await tester.pump(const Duration(milliseconds: 16));
  return read();
}

void main() {
  testWidgets('the content does not move when a notice comes or goes', (tester) async {
    await tester.pumpWidget(_page());
    final idle = tester.getTopLeft(find.text('first post'));

    await tester.pumpWidget(_page(notice: _retry()));
    expect(tester.getTopLeft(find.text('first post')), idle);
    expect(find.text('Retry'), findsOneWidget);

    await tester.pumpWidget(_page());
    expect(tester.getTopLeft(find.text('first post')), idle);
    await tester.pumpAndSettle();
    expect(tester.getTopLeft(find.text('first post')), idle);
    expect(find.byType(FrostedPill), findsNothing);
  });

  testWidgets('a notice drops in from above as it fades in', (tester) async {
    await tester.pumpWidget(_page(notice: _retry()));
    expect(_top(tester), 8 - kNoticeDrop);
    expect(_opacity(tester), 0);

    await tester.pump(const Duration(milliseconds: 60));
    expect(_top(tester), inExclusiveRange(8 - kNoticeDrop, 8));
    expect(_opacity(tester), inExclusiveRange(0, 1));

    await tester.pumpAndSettle();
    expect(_top(tester), 8);
    expect(_opacity(tester), 1);
  });

  testWidgets('one that stays mounted drops in when its pill comes', (tester) async {
    await tester.pumpWidget(_page(notice: const SizedBox.shrink()));
    await tester.pumpWidget(_page(notice: _retry()));
    expect(_opacity(tester), 0);
    await tester.pumpAndSettle();
    expect(_opacity(tester), 1);
  });

  testWidgets('it lifts away as it fades out, and cannot be tapped meanwhile', (tester) async {
    var taps = 0;
    await tester.pumpWidget(_page(notice: _retry(onPressed: () => taps++)));
    await tester.pumpAndSettle();

    final semantics = tester.ensureSemantics();
    expect(find.semantics.byLabel('Retry'), findsOne);

    await tester.pumpWidget(_page());
    await tester.pump(const Duration(milliseconds: 60));
    expect(_top(tester), inExclusiveRange(8 - kNoticeDrop, 8));
    expect(_opacity(tester), inExclusiveRange(0, 1));
    expect(find.semantics.byLabel('Retry'), findsNothing);
    await tester.tap(find.text('Retry'), warnIfMissed: false);
    expect(taps, 0);
    semantics.dispose();

    await tester.pumpAndSettle();
    expect(find.byType(FrostedPill), findsNothing);
  });

  testWidgets('a notice whose content changes updates in place', (tester) async {
    await tester.pumpWidget(_page(notice: _retry()));
    await tester.pumpAndSettle();

    await tester.pumpWidget(_page(notice: _retry(label: 'Retry in 5 s')));
    expect(find.text('Retry in 5 s'), findsOneWidget);
    expect(find.text('Retry'), findsNothing);
    expect(_top(tester), 8);
    expect(_opacity(tester), 1);
  });

  testWidgets('with reduced motion a notice comes and goes at once', (tester) async {
    await tester.pumpWidget(_page(notice: _retry(), reduceMotion: true));
    expect(_top(tester), 8);
    expect(_opacity(tester), 1);

    await tester.pumpWidget(_page(reduceMotion: true));
    await tester.pump();
    expect(find.byType(FrostedPill), findsNothing);
  });

  testWidgets('the notice floats centred near the top, as wide as it needs', (tester) async {
    await tester.pumpWidget(_page(notice: _retry()));
    await tester.pumpAndSettle();
    final pill = tester.getRect(find.byType(FrostedPill));
    expect(pill.top, 8);
    expect(pill.center.dx, closeTo(tester.getSize(find.byType(Scaffold)).width / 2, 0.5));
    expect(pill.width, lessThan(200));
    expect(pill.height, greaterThanOrEqualTo(48));
  });

  testWidgets('it stays below a status bar the content runs under', (tester) async {
    await tester.pumpWidget(_page(notice: _retry(), padding: const EdgeInsets.only(top: 24)));
    await tester.pumpAndSettle();
    expect(_top(tester), 32);
  });

  testWidgets('a notice with nothing to say draws nothing and blocks nothing', (tester) async {
    var tapped = false;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: FloatingNotice(
            notice: const SizedBox.shrink(),
            child: Align(
              alignment: Alignment.topCenter,
              child: TextButton(onPressed: () => tapped = true, child: const Text('under')),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('under'));
    expect(tapped, isTrue);
    expect(find.byType(FrostedPill), findsNothing);
  });

  testWidgets('a notice inside another floats below its pill, and rises when it goes', (tester) async {
    Widget nested({required bool outer}) => MaterialApp(
      home: Scaffold(
        body: FloatingNotice(
          notice: outer
              ? NoticePill(key: const ValueKey('outer'), child: const SizedBox(width: 100, height: 48))
              : null,
          child: FloatingNotice(
            notice: NoticePill(key: const ValueKey('inner'), child: const SizedBox(width: 120, height: 48)),
            child: const SizedBox.expand(),
          ),
        ),
      ),
    );
    double innerTop() => tester.getRect(find.byKey(const ValueKey('inner'))).top;

    await tester.pumpWidget(nested(outer: true));
    await tester.pumpAndSettle();
    final outer = tester.getRect(find.byKey(const ValueKey('outer')));
    expect(outer.top, 8);
    expect(innerTop(), outer.bottom + 8);

    await tester.pumpWidget(nested(outer: false));
    // It waits for the outer pill to have gone, then glides up.
    await tester.pump(const Duration(milliseconds: 60));
    expect(innerTop(), outer.bottom + 8);
    final path = [for (var frame = 0; frame < 30; frame++) await _frame(tester, innerTop)];
    expect(path.where((top) => top > 8 && top < outer.bottom + 8), isNotEmpty, reason: 'it glides, not jumps');
    expect(path.last, 8);
  });

  testWidgets('a floating notice is announced when it appears', (tester) async {
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(_page(notice: _retry()));
    // From the moment it arrives, not once it has faded in.
    final pill = find.descendant(of: find.byType(NoticePill), matching: find.byType(FrostedPill));
    expect(tester.getSemantics(pill), matchesSemantics(isLiveRegion: true, children: [anything]));
    semantics.dispose();
  });
}
