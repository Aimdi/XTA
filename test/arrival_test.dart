import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/ui/motion.dart';

Widget _app(Widget child, {bool reduceMotion = false}) => MaterialApp(
  home: MediaQuery(
    data: MediaQueryData(disableAnimations: reduceMotion),
    child: Scaffold(body: child),
  ),
);

/// The arrival's own fade, not the page route's.
final _fade = find.descendant(of: find.byType(XtaArrival), matching: find.byType(FadeTransition));

double? _opacity(WidgetTester tester) =>
    _fade.evaluate().isEmpty ? null : tester.widget<FadeTransition>(_fade.first).opacity.value;

/// A child with state of its own, [height] tall.
class _Counter extends StatefulWidget {
  final double height;

  const _Counter({super.key, this.height = 40});

  @override
  State<_Counter> createState() => _CounterState();
}

class _CounterState extends State<_Counter> {
  @override
  Widget build(BuildContext context) => SizedBox(height: widget.height, child: const Text('tile'));
}

void main() {
  group('an arriving child', () {
    testWidgets('fades in as it first appears', (tester) async {
      await tester.pumpWidget(_app(const XtaArrival(arriving: true, child: Text('tile'))));
      expect(_opacity(tester), 0);
      await tester.pump(const Duration(milliseconds: 90));
      expect(_opacity(tester), inExclusiveRange(0, 1));
      await tester.pumpAndSettle();
      expect(_opacity(tester), 1);
    });

    testWidgets('one that is not arriving is drawn as it is, with nothing animating', (tester) async {
      await tester.pumpWidget(_app(const XtaArrival(arriving: false, child: Text('tile'))));
      expect(_fade, findsNothing);
      expect(tester.hasRunningAnimations, isFalse);
    });

    testWidgets('is decided once: rebuilt not arriving, it keeps its place and its state', (tester) async {
      final key = GlobalKey<_CounterState>();
      await tester.pumpWidget(_app(XtaArrival(arriving: true, child: _Counter(key: key))));
      final state = key.currentState;
      await tester.pump(const Duration(milliseconds: 60));

      await tester.pumpWidget(_app(XtaArrival(arriving: false, child: _Counter(key: key))));
      expect(key.currentState, same(state));
      await tester.pumpAndSettle();
      expect(_opacity(tester), 1, reason: 'the fade it started runs to its end');
    });

    testWidgets('with reduced motion is there at once', (tester) async {
      await tester.pumpWidget(_app(const XtaArrival(arriving: true, child: Text('tile')), reduceMotion: true));
      expect(_fade, findsNothing);
    });
  });

  group('content after placeholders', () {
    /// What a surface shows over time: placeholders, posts kept from last
    /// time, or the content.
    Future<void> show(WidgetTester tester, XtaAfterPlaceholders after, String what) => tester.pumpWidget(
      _app(switch (what) {
        'placeholders' => after.placeholders(const Text('placeholders')),
        'kept' => () {
          after.shownOther();
          return const Text('kept');
        }(),
        _ => after.content(const Text('content')),
      }),
    );

    testWidgets('fades in where the placeholders were', (tester) async {
      final after = XtaAfterPlaceholders();
      await show(tester, after, 'placeholders');
      await show(tester, after, 'content');
      expect(_opacity(tester), 0);
      await tester.pumpAndSettle();
      expect(_opacity(tester), 1);
    });

    testWidgets('is drawn at once over posts kept from last time, and on a first build', (tester) async {
      final after = XtaAfterPlaceholders();
      await show(tester, after, 'content');
      expect(_fade, findsNothing);

      await show(tester, after, 'placeholders');
      await show(tester, after, 'kept');
      await show(tester, after, 'content');
      expect(_fade, findsNothing);
    });
  });

  group('a swap in place', () {
    final swapFade = find.descendant(of: find.byType(XtaSwap), matching: find.byType(FadeTransition));
    double swapOpacity(WidgetTester tester) => tester.widget<FadeTransition>(swapFade).opacity.value;
    double height(WidgetTester tester) => tester.getSize(find.byType(XtaSwap)).height;
    Widget swap(bool translated, {Key? key, bool reduceMotion = false}) => _app(
      Align(
        alignment: Alignment.topCenter,
        child: XtaSwap(
          swap: translated,
          child: _Counter(key: key, height: translated ? 120 : 40),
        ),
      ),
      reduceMotion: reduceMotion,
    );

    testWidgets('fades the new content in while it eases to its height', (tester) async {
      await tester.pumpWidget(swap(false));
      expect(height(tester), 40);
      expect(swapOpacity(tester), 1);

      await tester.pumpWidget(swap(true));
      await tester.pump(const Duration(milliseconds: 90));
      expect(height(tester), inExclusiveRange(40, 120));
      expect(swapOpacity(tester), inExclusiveRange(0, 1));

      await tester.pumpAndSettle();
      expect(height(tester), 120);
      expect(swapOpacity(tester), 1);
    });

    testWidgets('keeps what it holds through the swap', (tester) async {
      final key = GlobalKey<_CounterState>();
      await tester.pumpWidget(swap(false, key: key));
      final state = key.currentState;
      await tester.pumpWidget(swap(true, key: key));
      await tester.pumpAndSettle();
      expect(key.currentState, same(state));
    });

    testWidgets('runs nothing while nothing swaps', (tester) async {
      await tester.pumpWidget(swap(false));
      await tester.pumpWidget(swap(false));
      expect(tester.hasRunningAnimations, isFalse);
    });

    testWidgets('with reduced motion swaps at once', (tester) async {
      await tester.pumpWidget(swap(false, reduceMotion: true));
      await tester.pumpWidget(swap(true, reduceMotion: true));
      expect(swapOpacity(tester), 1);
      // The new size lands on the next frame.
      await tester.pump(const Duration(milliseconds: 16));
      expect(height(tester), 120);
      expect(tester.takeException(), isNull);
    });
  });

  testWidgets('a list\'s first tiles arrive; those built later, as it scrolls, do not', (tester) async {
    final window = XtaArrivalWindow();
    final arrived = <int, bool>{};
    await tester.pumpWidget(
      _app(
        ListView.builder(
          itemCount: 100,
          itemExtent: 100,
          itemBuilder: (context, index) =>
              Text('$index', key: ValueKey(arrived.putIfAbsent(index, () => window.arriving))),
        ),
      ),
    );
    final first = {...arrived.keys};
    expect(first, contains(0));
    expect(first.every((index) => arrived[index]!), isTrue);

    await tester.drag(find.byType(ListView), const Offset(0, -3000));
    await tester.pump();
    final later = arrived.keys.where((index) => !first.contains(index));
    expect(later, isNotEmpty);
    expect(later.every((index) => arrived[index] == false), isTrue);
  });
}
