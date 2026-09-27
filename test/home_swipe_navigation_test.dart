import 'package:dart_twitter_api/twitter_api.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/edge_swipe.dart';
import 'package:xta/home/home_swipe_navigation.dart';
import 'package:xta/tweet/_media.dart';

class _Harness {
  final selected = ValueNotifier(1);
  final sources = ValueNotifier(['Following', 'X', 'Substack']);
  final reduced = ValueNotifier(false);
  final navigator = GlobalKey<NavigatorState>();
  final changes = <int>[];
  bool accept = true;

  Widget app({Widget? child, bool rtl = false, EdgeInsets edges = EdgeInsets.zero}) => MaterialApp(
    navigatorKey: navigator,
    builder: (context, child) => ValueListenableBuilder<bool>(
      valueListenable: reduced,
      builder: (context, reduced, _) => MediaQuery(
        data: MediaQuery.of(context).copyWith(disableAnimations: reduced, systemGestureInsets: edges),
        child: Directionality(textDirection: rtl ? TextDirection.rtl : TextDirection.ltr, child: child!),
      ),
    ),
    home: Scaffold(
      body: ValueListenableBuilder<List<String>>(
        valueListenable: sources,
        builder: (context, names, _) => ValueListenableBuilder<int>(
          valueListenable: selected,
          builder: (context, index, _) => HomeSwipeNavigation(
            index: index,
            count: names.length,
            identity: names.join('|'),
            onChanged: (next) {
              if (!accept) return false;
              changes.add(next);
              selected.value = next;
              return true;
            },
            previewBuilder: (_, target) => Text(names[target]),
            child: SizedBox.expand(
              key: const ValueKey('reading-surface'),
              child: child ?? Center(child: Text('Body ${names[index]}')),
            ),
          ),
        ),
      ),
    ),
  );

  void dispose() {
    selected.dispose();
    sources.dispose();
    reduced.dispose();
  }
}

const _surface = ValueKey('reading-surface');
const _preview = ValueKey('home-swipe-preview');

Future<TestGesture> _drag(WidgetTester tester, {double distance = -110, int pointer = 1}) async {
  final gesture = await tester.startGesture(tester.getCenter(find.byKey(_surface)), pointer: pointer);
  await gesture.moveBy(Offset(distance / 2, 0));
  await gesture.moveBy(Offset(distance / 2, 0));
  await tester.pump();
  return gesture;
}

void main() {
  final haptics = <Object?>[];
  setUp(() {
    haptics.clear();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (call) async {
        if (call.method == 'HapticFeedback.vibrate') haptics.add(call.arguments);
        return null;
      },
    );
  });
  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      null,
    );
  });

  testWidgets('previews a destination but commits only on release, with one tick', (tester) async {
    final h = _Harness();
    addTearDown(h.dispose);
    await tester.pumpWidget(h.app());
    final gesture = await _drag(tester);
    expect(find.text('Substack'), findsOneWidget);
    expect(find.text('Body X'), findsOneWidget);
    expect(h.changes, isEmpty);
    expect(haptics, isEmpty);
    await gesture.up();
    await tester.pumpAndSettle();
    expect(find.text('Body Substack'), findsOneWidget);
    expect(h.changes, [2]);
    expect(haptics, ['HapticFeedbackType.selectionClick']);
    expect(find.byKey(_preview), findsNothing);
  });

  testWidgets('short, reversed, cancelled, and boundary drags stay quiet', (tester) async {
    final h = _Harness();
    addTearDown(h.dispose);
    await tester.pumpWidget(h.app());
    final short = await _drag(tester, distance: -35);
    await short.up();
    await tester.pumpAndSettle();
    final reversed = await _drag(tester);
    await reversed.moveBy(const Offset(180, 0));
    await reversed.up();
    await tester.pumpAndSettle();
    final cancelled = await _drag(tester);
    await cancelled.cancel();
    await tester.pumpAndSettle();
    h.selected.value = 0;
    await tester.pump();
    final boundary = await _drag(tester, distance: 110);
    await boundary.up();
    await tester.pumpAndSettle();
    expect(h.changes, isEmpty);
    expect(haptics, isEmpty);
    expect(find.byKey(_preview), findsNothing);
  });

  testWidgets('a second pointer cancels even if it lifts before the first', (tester) async {
    final h = _Harness();
    addTearDown(h.dispose);
    await tester.pumpWidget(h.app());
    final first = await _drag(tester, pointer: 11);
    final second = await tester.startGesture(const Offset(500, 250), pointer: 12);
    await second.up();
    await first.moveBy(const Offset(-70, 0));
    await first.up();
    await tester.pumpAndSettle();
    expect(h.changes, isEmpty);
    expect(haptics, isEmpty);
    final next = await _drag(tester, pointer: 13);
    await next.up();
    await tester.pumpAndSettle();
    expect(h.changes, [2]);
  });

  testWidgets('vertical and diagonal feed drags scroll without changing source', (tester) async {
    final h = _Harness();
    final scroll = ScrollController();
    addTearDown(h.dispose);
    addTearDown(scroll.dispose);
    await tester.pumpWidget(
      h.app(
        child: ListView.builder(
          controller: scroll,
          itemCount: 40,
          itemExtent: 70,
          itemBuilder: (_, index) => Text('Post $index'),
        ),
      ),
    );
    await tester.drag(find.byType(ListView), const Offset(0, -160));
    await tester.pumpAndSettle();
    final first = scroll.offset;
    await tester.drag(find.byType(ListView), const Offset(-35, -170));
    await tester.pumpAndSettle();
    expect(first, greaterThan(80));
    expect(scroll.offset, greaterThan(first));
    expect(h.changes, isEmpty);
    expect(haptics, isEmpty);
  });

  testWidgets('a nested carousel owns swipes at both ends', (tester) async {
    final h = _Harness();
    final scroll = ScrollController();
    addTearDown(h.dispose);
    addTearDown(scroll.dispose);
    await tester.pumpWidget(
      h.app(
        child: Center(
          child: SizedBox(
            height: 180,
            child: ListView.builder(
              controller: scroll,
              scrollDirection: Axis.horizontal,
              itemCount: 8,
              itemExtent: 180,
              itemBuilder: (_, index) => ColoredBox(color: Colors.blue, child: Text('Photo $index')),
            ),
          ),
        ),
      ),
    );
    await tester.drag(find.byType(ListView), const Offset(170, 0));
    await tester.pumpAndSettle();
    scroll.jumpTo(scroll.position.maxScrollExtent);
    await tester.pump();
    await tester.drag(find.byType(ListView), const Offset(-170, 0));
    await tester.pumpAndSettle();
    expect(h.changes, isEmpty);
    expect(haptics, isEmpty);
  });

  testWidgets('a slider and press controls retain their gestures and semantics', (tester) async {
    final h = _Harness();
    addTearDown(h.dispose);
    var slider = .5;
    var taps = 0;
    var holds = 0;
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(
      h.app(
        child: Column(
          children: [
            Slider(value: slider, onChanged: (value) => slider = value),
            TextButton(onPressed: () => taps++, onLongPress: () => holds++, child: const Text('Options')),
          ],
        ),
      ),
    );
    await tester.drag(find.byType(Slider), const Offset(150, 0));
    await tester.tap(find.text('Options'));
    await tester.longPress(find.text('Options'));
    await tester.pumpAndSettle();
    expect(slider, greaterThan(.5));
    expect(taps, 1);
    expect(holds, 1);
    expect(tester.getSemantics(find.byType(TextButton)).label, 'Options');
    expect(h.changes, isEmpty);
    semantics.dispose();
  });

  testWidgets('system back edges and the conservative fallback are excluded', (tester) async {
    final h = _Harness();
    addTearDown(h.dispose);
    for (final edges in [const EdgeInsets.fromLTRB(48, 40, 40, 40), EdgeInsets.zero]) {
      await tester.pumpWidget(h.app(edges: edges));
      final inset = edges == EdgeInsets.zero ? 12.0 : 32.0;
      for (final start in [
        Offset(inset, 280),
        Offset(800 - inset, 280),
        if (edges != EdgeInsets.zero) const Offset(400, 12),
        if (edges != EdgeInsets.zero) const Offset(400, 588),
      ]) {
        final gesture = await tester.startGesture(start);
        await gesture.moveBy(Offset(start.dx < 400 ? 180 : -180, 0));
        await gesture.up();
        await tester.pumpAndSettle();
      }
    }
    expect(h.changes, isEmpty);
    expect(haptics, isEmpty);
  });

  testWidgets('RTL reverses the physical direction of the source sequence', (tester) async {
    final h = _Harness();
    addTearDown(h.dispose);
    await tester.pumpWidget(h.app(rtl: true));
    final gesture = await _drag(tester, distance: 110);
    expect(find.text('Substack'), findsOneWidget);
    await gesture.up();
    await tester.pumpAndSettle();
    expect(find.text('Body Substack'), findsOneWidget);
    expect(h.changes, [2]);
  });

  testWidgets('external source and source-order changes invalidate an armed swipe', (tester) async {
    final h = _Harness();
    addTearDown(h.dispose);
    await tester.pumpWidget(h.app());
    final first = await _drag(tester);
    h.selected.value = 0;
    await tester.pump();
    await first.up();
    await tester.pumpAndSettle();
    final second = await _drag(tester);
    h.sources.value = ['Following', 'Substack', 'X'];
    await tester.pump();
    await second.up();
    await tester.pumpAndSettle();
    expect(find.text('Body Following'), findsOneWidget);
    expect(h.changes, isEmpty);
    expect(haptics, isEmpty);
  });

  testWidgets('a route covering the feed cancels the outstanding interaction', (tester) async {
    final h = _Harness();
    addTearDown(h.dispose);
    await tester.pumpWidget(h.app());
    final gesture = await _drag(tester);
    h.navigator.currentState!.push(MaterialPageRoute<void>(builder: (_) => const Scaffold(body: Text('Viewer'))));
    await tester.pumpAndSettle();
    await gesture.up();
    await tester.pumpAndSettle();
    expect(find.text('Viewer'), findsOneWidget);
    expect(h.changes, isEmpty);
    expect(haptics, isEmpty);
  });

  testWidgets('reduced motion during spring return removes it without a command', (tester) async {
    final h = _Harness();
    addTearDown(h.dispose);
    await tester.pumpWidget(h.app());
    final gesture = await _drag(tester);
    await gesture.cancel();
    await tester.pump(const Duration(milliseconds: 25));
    expect(find.byKey(_preview), findsOneWidget);
    h.reduced.value = true;
    await tester.pump();
    expect(find.byKey(_preview), findsNothing);
    expect(h.changes, isEmpty);
    expect(haptics, isEmpty);
    final next = await _drag(tester);
    await next.up();
    await tester.pumpAndSettle();
    expect(h.changes, [2]);
    expect(haptics, hasLength(1));
  });

  testWidgets('a fresh swipe during return previews and commits its own destination', (tester) async {
    final h = _Harness();
    addTearDown(h.dispose);
    await tester.pumpWidget(h.app());
    final cancelled = await _drag(tester);
    await cancelled.cancel();
    await tester.pump(const Duration(milliseconds: 16));
    final opposite = await _drag(tester, distance: 80, pointer: 31);
    expect(find.descendant(of: find.byKey(_preview), matching: find.text('Following')), findsOneWidget);
    await opposite.up();
    await tester.pumpAndSettle();
    expect(h.changes, [0]);
    expect(haptics, ['HapticFeedbackType.selectionClick']);
  });

  testWidgets('a rejected source change emits no success feedback', (tester) async {
    final h = _Harness()..accept = false;
    addTearDown(h.dispose);
    await tester.pumpWidget(h.app());
    final gesture = await _drag(tester);
    await gesture.up();
    await tester.pumpAndSettle();
    expect(find.text('Body X'), findsOneWidget);
    expect(h.changes, isEmpty);
    expect(haptics, isEmpty);
  });

  testWidgets('actual tweet media does not hand its edge drag to main navigation', (tester) async {
    final moves = <int>[];
    await tester.pumpWidget(
      PrefService(
        service: PrefServiceCache(),
        child: MaterialApp(
          localizationsDelegates: const [
            L10n.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
          ],
          supportedLocales: L10n.delegate.supportedLocales,
          home: HomePageSwiper(
            movePage: moves.add,
            child: Scaffold(
              body: Center(
                child: SizedBox(
                  width: 360,
                  child: TweetMedia(
                    sensitive: false,
                    username: 'fixture',
                    media: [for (var index = 0; index < 6; index++) Media()..type = 'photo'],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    final strip = find.byType(ListView);
    await tester.drag(strip, const Offset(150, 0));
    await tester.pumpAndSettle();
    expect(moves, isEmpty);
    expect(tester.takeException(), isNull);
  });
}
