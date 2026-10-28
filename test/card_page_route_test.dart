import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/main.dart' show xtaLocalizationsDelegates;
import 'package:xta/settings/_accessibility.dart';
import 'package:xta/ui/card_page_route.dart';

const _size = Size(390, 844);
const _card = ValueKey('swipe-back-card');
const _behind = ValueKey('swipe-back-behind');

class _Feed extends StatelessWidget {
  const _Feed();

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Feed')),
    body: ListView(
      children: [
        TextButton(onPressed: () => Navigator.pushNamed(context, '/post'), child: const Text('open post')),
        for (var i = 0; i < 20; i++) ListTile(title: Text('feed $i')),
      ],
    ),
  );
}

class _Post extends StatelessWidget {
  final Widget? top;
  final VoidCallback? onButton;

  const _Post({this.top, this.onButton});

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Post')),
    body: ListView(
      children: [
        ?top,
        TextButton(onPressed: onButton, child: const Text('a button')),
        for (var i = 0; i < 30; i++) ListTile(title: Text('reply $i')),
      ],
    ),
  );
}

Future<void> _pump(
  WidgetTester tester, {
  Widget post = const _Post(),
  Map<String, Object> prefs = const {},
  TextDirection direction = TextDirection.ltr,
}) async {
  tester.view.physicalSize = _size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final service = PrefServiceCache(
    defaults: {optionDisableAnimations: false, optionSwipeBackAnywhere: true, optionSwipeBackTilt: true, ...prefs},
  );
  await tester.pumpWidget(
    PrefService(
      service: service,
      child: MaterialApp(
        builder: (context, child) => Directionality(textDirection: direction, child: child!),
        onGenerateRoute: (settings) => switch (settings.name) {
          '/post' => XtaCardPageRoute<dynamic>(settings: settings, builder: (_) => post),
          _ => MaterialPageRoute<dynamic>(settings: settings, builder: (_) => const _Feed()),
        },
      ),
    ),
  );
}

Future<void> _open(WidgetTester tester) async {
  await tester.tap(find.text('open post'));
  await tester.pumpAndSettle();
}

Matrix4 _matrix(WidgetTester tester) => tester.widget<Transform>(find.byKey(_card)).transform;

/// Where a point of the card at rest is drawn now.
Offset _where(WidgetTester tester, Offset point) => MatrixUtils.transformPoint(_matrix(tester), point);

/// The card's turn in degrees, clockwise.
double _turn(WidgetTester tester) {
  final matrix = _matrix(tester);
  return math.atan2(matrix.entry(1, 0), matrix.entry(0, 0)) * 180 / math.pi;
}

double _behindScale(WidgetTester tester) => tester.widget<Transform>(find.byKey(_behind)).transform.entry(0, 0);

/// Drags from [from] by [by] in [steps] moves a frame apart, then holds still
/// long enough that letting go is not a throw.
Future<TestGesture> _drag(WidgetTester tester, Offset from, Offset by, {int steps = 10}) async {
  final gesture = await tester.startGesture(from);
  var time = Duration.zero;
  for (var i = 0; i < steps; i++) {
    time += const Duration(milliseconds: 16);
    await gesture.moveBy(by / steps.toDouble(), timeStamp: time);
    await tester.pump(const Duration(milliseconds: 16));
  }
  await tester.pump(const Duration(milliseconds: 100));
  return gesture;
}

Future<void> _back(WidgetTester tester, String method, [Map<String, Object?>? arguments]) =>
    tester.binding.defaultBinaryMessenger.handlePlatformMessage(
      'flutter/backgesture',
      const StandardMethodCodec().encodeMethodCall(MethodCall(method, arguments)),
      (_) {},
    );

Map<String, Object?> _backEvent(double progress, {int edge = 0, double x = 5}) => {
  'touchOffset': [x, 200.0],
  'progress': progress,
  'swipeEdge': edge,
};

void main() {
  const grab = Offset(150, 300);

  group('opening and leaving', () {
    testWidgets('a post slides in from the right over the page it was opened from', (tester) async {
      await _pump(tester);
      await tester.tap(find.text('open post'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 60));

      final center = _size.center(Offset.zero);
      expect(_where(tester, center).dx - center.dx, inExclusiveRange(0, _size.width));
      expect(_turn(tester), 0);
      expect(find.text('feed 0'), findsOneWidget, reason: 'the page behind shows');
      expect(_behindScale(tester), inExclusiveRange(0.96, 1));

      await tester.pumpAndSettle();
      expect(_where(tester, center), center);
      expect(find.text('feed 0'), findsNothing, reason: 'covered, it is no longer drawn');
    });

    testWidgets('the back button slides it out past the right edge', (tester) async {
      await _pump(tester);
      await _open(tester);
      await tester.tap(find.byType(BackButton));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 110));

      final center = _size.center(Offset.zero);
      expect(_where(tester, center).dx, greaterThan(center.dx));
      await tester.pumpAndSettle();
      expect(find.text('reply 0'), findsNothing);
      expect(find.text('feed 0'), findsOneWidget);
    });
  });

  group('dragging', () {
    testWidgets('the card follows the finger, the page behind showing and scaled under it', (tester) async {
      await _pump(tester);
      await _open(tester);
      final gesture = await _drag(tester, grab, const Offset(100, 20));

      final drawn = _where(tester, grab);
      expect(drawn.dx, closeTo(grab.dx + 100, 0.5), reason: 'the grabbed point stays under the finger');
      expect(drawn.dy, closeTo(grab.dy + 20, 0.5));
      expect(find.text('feed 0'), findsOneWidget);
      expect(_behindScale(tester), inExclusiveRange(0.96, 1));
      await gesture.up();
      await tester.pumpAndSettle();
    });

    testWidgets('let go past 30 % of the width, it flies off and the post is gone', (tester) async {
      await _pump(tester);
      await _open(tester);
      final gesture = await _drag(tester, grab, const Offset(140, 0));
      await gesture.up();
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      expect(_where(tester, grab).dx, greaterThan(grab.dx + 140), reason: 'it flies on');

      await tester.pumpAndSettle();
      expect(find.text('reply 0'), findsNothing);
      expect(find.text('feed 0'), findsOneWidget);
    });

    testWidgets('let go short of the line, it drops back and the post stays', (tester) async {
      await _pump(tester);
      await _open(tester);
      final gesture = await _drag(tester, grab, const Offset(80, 30));
      await gesture.up();
      await tester.pumpAndSettle();

      expect(find.text('reply 0'), findsOneWidget);
      expect(_where(tester, grab), grab);
      expect(_turn(tester), 0);
      expect(find.text('feed 0'), findsNothing, reason: 'back in place, the page behind is covered again');
      expect(tester.state<NavigatorState>(find.byType(Navigator)).userGestureInProgress, isFalse);
    });

    testWidgets('a quick throw leaves from a short drag', (tester) async {
      await _pump(tester);
      await _open(tester);
      await tester.flingFrom(grab, const Offset(70, 0), 1200);
      await tester.pumpAndSettle();
      expect(find.text('reply 0'), findsNothing);
    });

    testWidgets('a flick back towards rest keeps the post, even past the line', (tester) async {
      await _pump(tester);
      await _open(tester);
      final gesture = await tester.startGesture(grab);
      var time = Duration.zero;
      for (var i = 0; i < 12; i++) {
        time += const Duration(milliseconds: 16);
        await gesture.moveBy(const Offset(20, 0), timeStamp: time);
        await tester.pump(const Duration(milliseconds: 16));
      }
      for (var i = 0; i < 4; i++) {
        time += const Duration(milliseconds: 8);
        await gesture.moveBy(const Offset(-20, 0), timeStamp: time);
      }
      await tester.pump(const Duration(milliseconds: 8));
      await gesture.up(timeStamp: time);
      await tester.pumpAndSettle();
      expect(find.text('reply 0'), findsOneWidget);
    });

    testWidgets('grabbed above the middle it turns clockwise, below the other way', (tester) async {
      await _pump(tester);
      await _open(tester);
      var gesture = await _drag(tester, const Offset(150, 150), const Offset(100, 0));
      expect(_turn(tester), greaterThan(0));
      await gesture.up();
      await tester.pumpAndSettle();

      gesture = await _drag(tester, const Offset(150, 700), const Offset(100, 0));
      expect(_turn(tester), lessThan(0));
      await gesture.up();
      await tester.pumpAndSettle();
    });

    testWidgets('with tilt off it does not turn', (tester) async {
      await _pump(tester, prefs: {optionSwipeBackTilt: false});
      await _open(tester);
      final gesture = await _drag(tester, const Offset(150, 150), const Offset(100, 0));
      expect(_turn(tester), 0);
      expect(_where(tester, const Offset(150, 150)).dx, closeTo(250, 0.5));
      await gesture.up();
      await tester.pumpAndSettle();
    });

    testWidgets('dragged left of rest it gives only a third', (tester) async {
      await _pump(tester);
      await _open(tester);
      final gesture = await tester.startGesture(grab);
      await gesture.moveBy(const Offset(30, 0));
      await tester.pump(const Duration(milliseconds: 16));
      await gesture.moveBy(const Offset(-90, 0));
      await tester.pump(const Duration(milliseconds: 16));
      expect(_where(tester, grab).dx, closeTo(grab.dx - 18, 0.5));
      await gesture.up();
      await tester.pumpAndSettle();
      expect(find.text('reply 0'), findsOneWidget);
    });

    testWidgets('caught while it drops back, it is held again', (tester) async {
      await _pump(tester);
      await _open(tester);
      var gesture = await _drag(tester, grab, const Offset(100, 0));
      await gesture.up();
      await tester.pump(const Duration(milliseconds: 16));
      final caughtAt = _where(tester, grab);
      expect(caughtAt.dx, greaterThan(grab.dx));

      gesture = await tester.startGesture(caughtAt);
      await tester.pump(const Duration(milliseconds: 50));
      expect(_where(tester, grab).dx, closeTo(caughtAt.dx, 0.5), reason: 'it stays where it was caught');
      await gesture.moveBy(const Offset(-20, 0));
      await tester.pump(const Duration(milliseconds: 16));
      expect(_where(tester, grab).dx, closeTo(caughtAt.dx - 20, 0.5));
      await gesture.up();
      await tester.pumpAndSettle();
      expect(find.text('reply 0'), findsOneWidget);
    });
  });

  group('popped from elsewhere', () {
    testWidgets('a back press while it drops back throws it from where it is', (tester) async {
      await _pump(tester);
      await _open(tester);
      final gesture = await _drag(tester, grab, const Offset(90, 0));
      await gesture.up();
      await tester.pump(const Duration(milliseconds: 16));
      await tester.binding.handlePopRoute();
      await tester.pump(const Duration(milliseconds: 60));
      expect(_where(tester, grab).dx, greaterThan(grab.dx + 60), reason: 'it flies on from where it was');

      await tester.pumpAndSettle();
      expect(find.text('reply 0'), findsNothing);
      expect(tester.state<NavigatorState>(find.byType(Navigator)).userGestureInProgress, isFalse);
      await _open(tester);
      expect(find.text('reply 0'), findsOneWidget, reason: 'the page behind takes taps again');
    });

    testWidgets('a post opened from a post slides over it, the first shrinking behind', (tester) async {
      await _pump(tester);
      await _open(tester);
      Navigator.of(tester.element(find.text('reply 0'))).pushNamed('/post');
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 120));
      expect(find.byKey(_behind), findsOneWidget);
      expect(_behindScale(tester), inExclusiveRange(0.96, 1));
      await tester.pumpAndSettle();
      expect(find.text('reply 0'), findsOneWidget);
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      expect(find.text('reply 0'), findsOneWidget);
    });
  });

  group('what the drag leaves alone', () {
    testWidgets('an up or down drag scrolls the post', (tester) async {
      await _pump(tester);
      await _open(tester);
      final before = tester.getTopLeft(find.text('reply 5')).dy;
      final gesture = await _drag(tester, const Offset(200, 600), const Offset(0, -150));
      expect(_where(tester, const Offset(200, 600)), const Offset(200, 600));
      expect(tester.getTopLeft(find.text('reply 5')).dy, lessThan(before));
      await gesture.up();
      await tester.pumpAndSettle();
    });

    testWidgets('a drag to the left is left to the page', (tester) async {
      await _pump(tester);
      await _open(tester);
      final gesture = await _drag(tester, grab, const Offset(-120, 0));
      expect(_where(tester, grab), grab);
      await gesture.up();
      await tester.pumpAndSettle();
      expect(find.text('reply 0'), findsOneWidget);
    });

    testWidgets('a row that scrolls sideways keeps its drags', (tester) async {
      final row = SizedBox(
        height: 120,
        child: ListView(
          key: const ValueKey('row'),
          scrollDirection: Axis.horizontal,
          children: [for (var i = 0; i < 8; i++) SizedBox(width: 150, child: Text('photo $i'))],
        ),
      );
      await _pump(tester, post: _Post(top: row));
      await _open(tester);
      await tester.drag(find.byKey(const ValueKey('row')), const Offset(-200, 0));
      await tester.pumpAndSettle();

      final inRow = tester.getCenter(find.byKey(const ValueKey('row')));
      final gesture = await _drag(tester, inRow, const Offset(120, 0));
      expect(_where(tester, inRow), inRow, reason: 'the row scrolls back instead');
      await gesture.up();
      await tester.pumpAndSettle();
      expect(find.text('reply 0'), findsOneWidget);
    });

    testWidgets('a tap still presses a button, and a touch lifts nothing', (tester) async {
      var pressed = 0;
      await _pump(tester, post: _Post(onButton: () => pressed++));
      await _open(tester);
      await tester.tap(find.text('a button'));
      await tester.pump();
      expect(pressed, 1);

      final gesture = await tester.startGesture(tester.getCenter(find.text('Post')));
      await tester.pump(const Duration(milliseconds: 200));
      expect(_where(tester, grab), grab);
      await gesture.up();
      await tester.pumpAndSettle();
    });

    testWidgets('a drag that started on a button does not press it', (tester) async {
      var pressed = 0;
      await _pump(tester, post: _Post(onButton: () => pressed++));
      await _open(tester);
      final gesture = await _drag(tester, tester.getCenter(find.text('a button')), const Offset(60, 0));
      await gesture.up();
      await tester.pumpAndSettle();
      expect(pressed, 0);
    });

    testWidgets('a post that may not be left cannot be dragged away', (tester) async {
      await _pump(tester, post: const PopScope(canPop: false, child: _Post()));
      await _open(tester);
      final gesture = await _drag(tester, grab, const Offset(200, 0));
      expect(_where(tester, grab), grab);
      await gesture.up();
      await tester.pumpAndSettle();
      expect(find.text('reply 0'), findsOneWidget);
    });
  });

  group('swipe back only from the edge', () {
    testWidgets('a drag away from the edge does nothing, one from the edge works', (tester) async {
      await _pump(tester, prefs: {optionSwipeBackAnywhere: false});
      await _open(tester);
      var gesture = await _drag(tester, grab, const Offset(150, 0));
      expect(_where(tester, grab), grab);
      await gesture.up();
      await tester.pumpAndSettle();

      gesture = await _drag(tester, const Offset(10, 400), const Offset(150, 0));
      expect(_where(tester, const Offset(10, 400)).dx, closeTo(160, 0.5));
      await gesture.up();
      await tester.pumpAndSettle();
      expect(find.text('reply 0'), findsNothing);
    });
  });

  group("Android's back gesture", () {
    testWidgets('moves the card with the finger and throws it on commit', (tester) async {
      await _pump(tester);
      await _open(tester);
      await _back(tester, 'startBackGesture', _backEvent(0));
      await _back(tester, 'updateBackGestureProgress', _backEvent(0.3));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 16));
      expect(_where(tester, const Offset(5, 200)).dx, closeTo(5 + 0.3 * _size.width, 0.5));
      expect(find.text('feed 0'), findsOneWidget);

      await _back(tester, 'commitBackGesture');
      await tester.pumpAndSettle();
      expect(find.text('reply 0'), findsNothing);
      expect(find.text('feed 0'), findsOneWidget);
    });

    testWidgets('drops the card back on cancel', (tester) async {
      await _pump(tester);
      await _open(tester);
      await _back(tester, 'startBackGesture', _backEvent(0));
      await _back(tester, 'updateBackGestureProgress', _backEvent(0.4));
      await tester.pump();
      await _back(tester, 'cancelBackGesture');
      await tester.pumpAndSettle();
      expect(find.text('reply 0'), findsOneWidget);
      expect(_where(tester, grab), grab);
    });

    testWidgets('from the right edge the card goes left', (tester) async {
      await _pump(tester);
      await _open(tester);
      await _back(tester, 'startBackGesture', _backEvent(0, edge: 1, x: 385));
      await _back(tester, 'updateBackGestureProgress', _backEvent(0.3, edge: 1, x: 385));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 16));
      expect(_where(tester, const Offset(385, 200)).dx, closeTo(385 - 0.3 * _size.width, 0.5));
      await _back(tester, 'commitBackGesture');
      await tester.pumpAndSettle();
      expect(find.text('reply 0'), findsNothing);
    });

    testWidgets('a post that may not be left keeps the gesture away', (tester) async {
      await _pump(tester, post: const PopScope(canPop: false, child: _Post()));
      await _open(tester);
      await _back(tester, 'startBackGesture', _backEvent(0));
      await _back(tester, 'updateBackGestureProgress', _backEvent(0.4));
      await tester.pump();
      expect(_where(tester, grab), grab);
      await _back(tester, 'commitBackGesture');
      await tester.pumpAndSettle();
      expect(find.text('reply 0'), findsOneWidget);
    });
  });

  group('with reduced motion', () {
    testWidgets('the post appears and leaves at once, and still follows a drag, flat', (tester) async {
      await _pump(tester, prefs: {optionDisableAnimations: true});
      await tester.tap(find.text('open post'));
      await tester.pump();
      await tester.pump();
      final center = _size.center(Offset.zero);
      expect(_where(tester, center), center);

      final gesture = await _drag(tester, const Offset(150, 150), const Offset(150, 0));
      expect(_where(tester, const Offset(150, 150)).dx, closeTo(300, 0.5));
      expect(_turn(tester), 0);
      expect(find.byKey(_behind), findsNothing, reason: 'the page behind is drawn as it is');
      expect(find.text('feed 0'), findsOneWidget);

      await gesture.up();
      await tester.pump();
      expect(find.text('reply 0'), findsNothing);
    });

    testWidgets('let go short of the line, it is back at once', (tester) async {
      await _pump(tester, prefs: {optionDisableAnimations: true});
      await _open(tester);
      final gesture = await _drag(tester, grab, const Offset(60, 0));
      await gesture.up();
      await tester.pump();
      expect(_where(tester, grab), grab);
      expect(find.text('reply 0'), findsOneWidget);
    });
  });

  testWidgets('both switches sit in Accessibility, on, and turn off', (tester) async {
    final prefs = PrefServiceCache(defaults: {optionSwipeBackAnywhere: true, optionSwipeBackTilt: true});
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
    for (final (title, option) in [
      ('Swipe back from anywhere', optionSwipeBackAnywhere),
      ('Tilt with your finger', optionSwipeBackTilt),
    ]) {
      await tester.ensureVisible(find.text(title));
      await tester.tap(find.text(title));
      await tester.pumpAndSettle();
      expect(prefs.get<bool>(option), isFalse, reason: title);
    }
    expect(
      find.text('Drag an opened post from anywhere on it to go back. Off: only from the edge of the screen.'),
      findsOneWidget,
    );
  });

  group('right to left', () {
    testWidgets('the post is dragged to the left, and a drag to the right is left alone', (tester) async {
      await _pump(tester, direction: TextDirection.rtl);
      await _open(tester);
      var gesture = await _drag(tester, const Offset(240, 300), const Offset(100, 0));
      expect(_where(tester, const Offset(240, 300)), const Offset(240, 300));
      await gesture.up();
      await tester.pumpAndSettle();

      gesture = await _drag(tester, const Offset(240, 300), const Offset(-140, 0));
      expect(_where(tester, const Offset(240, 300)).dx, closeTo(100, 0.5));
      await gesture.up();
      await tester.pumpAndSettle();
      expect(find.text('reply 0'), findsNothing);
    });
  });
}
