import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/ui/reader_tab_view.dart';

Widget _app({bool rtl = false, bool reduced = false, Widget? first}) => MaterialApp(
  builder: (context, child) => MediaQuery(
    data: MediaQuery.of(context).copyWith(disableAnimations: reduced),
    child: Directionality(textDirection: rtl ? TextDirection.rtl : TextDirection.ltr, child: child!),
  ),
  home: DefaultTabController(
    length: 3,
    child: Scaffold(
      appBar: AppBar(
        title: const Text('Reader'),
        bottom: const TabBar(
          tabs: [
            Tab(text: 'Posts'),
            Tab(text: 'Media'),
            Tab(text: 'Saved'),
          ],
        ),
      ),
      body: ReaderTabView(
        children: [
          first ??
              ListView.builder(
                key: const PageStorageKey('posts'),
                itemCount: 40,
                itemExtent: 70,
                itemBuilder: (_, i) => Text('Post $i'),
              ),
          const Center(child: Text('Media content')),
          const Center(child: Text('Saved content')),
        ],
      ),
    ),
  ),
);

Future<TestGesture> _drag(WidgetTester tester, {double dx = -120, int pointer = 1}) async {
  final touch = await tester.startGesture(tester.getCenter(find.byType(ReaderTabView)), pointer: pointer);
  await touch.moveBy(Offset(dx / 2, 0));
  await touch.moveBy(Offset(dx / 2, 0));
  return touch;
}

TabController _tabs(WidgetTester tester) => DefaultTabController.of(tester.element(find.byType(ReaderTabView)));

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
  tearDown(
    () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      null,
    ),
  );

  testWidgets('reader tabs switch only on release and preserve reading position', (tester) async {
    await tester.pumpWidget(_app());
    await tester.drag(find.byType(ListView), const Offset(0, -240));
    await tester.pumpAndSettle();
    final position = tester
        .state<ScrollableState>(find.descendant(of: find.byType(ListView), matching: find.byType(Scrollable)))
        .position
        .pixels;
    final gesture = await _drag(tester);
    await tester.pump();
    expect(_tabs(tester).index, 0);
    await gesture.up();
    await tester.pumpAndSettle();
    expect(find.text('Media content'), findsOneWidget);
    final back = await _drag(tester, dx: 120);
    await back.up();
    await tester.pumpAndSettle();
    final restored = tester
        .state<ScrollableState>(find.descendant(of: find.byType(ListView), matching: find.byType(Scrollable)))
        .position
        .pixels;
    expect(restored, closeTo(position, 1));
    expect(haptics, hasLength(2));
  });

  testWidgets('cancellation and a second pointer in the header do not change tabs', (tester) async {
    await tester.pumpWidget(_app());
    final cancelled = await _drag(tester);
    await cancelled.cancel();
    final first = await _drag(tester, pointer: 11);
    final second = await tester.startGesture(tester.getCenter(find.text('Reader')), pointer: 12);
    await second.up();
    await first.up();
    await tester.pumpAndSettle();
    expect(_tabs(tester).index, 0);
    expect(haptics, isEmpty);
  });

  testWidgets('rapid swipes do not duplicate an unfinished tab change', (tester) async {
    await tester.pumpWidget(_app());
    final first = await _drag(tester);
    await first.up();
    await tester.pump(const Duration(milliseconds: 16));
    final repeated = await _drag(tester, pointer: 21);
    await repeated.up();
    await tester.pumpAndSettle();
    expect(_tabs(tester).index, 1);
    expect(haptics, hasLength(1));
    await tester.tap(find.text('Saved'));
    await tester.pumpAndSettle();
    expect(find.text('Saved content'), findsOneWidget);
  });

  testWidgets('RTL and reduced motion retain navigation and visible alternatives', (tester) async {
    await tester.pumpWidget(_app(rtl: true, reduced: true));
    final gesture = await _drag(tester, dx: 120);
    await gesture.up();
    await tester.pump();
    expect(_tabs(tester).index, 1);
    expect(_tabs(tester).indexIsChanging, isFalse);
    expect(find.text('Media content'), findsOneWidget);
    await tester.tap(find.text('Posts'));
    await tester.pumpAndSettle();
    expect(_tabs(tester).index, 0);
  });

  testWidgets('a nested carousel keeps its boundary gestures', (tester) async {
    await tester.pumpWidget(
      _app(
        first: ListView(
          scrollDirection: Axis.horizontal,
          children: [for (var i = 0; i < 8; i++) SizedBox(width: 200, child: Text('Photo $i'))],
        ),
      ),
    );
    await tester.drag(find.byType(ListView), const Offset(150, 0));
    await tester.pumpAndSettle();
    final scroll = tester
        .state<ScrollableState>(find.descendant(of: find.byType(ListView), matching: find.byType(Scrollable)))
        .position;
    scroll.jumpTo(scroll.maxScrollExtent);
    await tester.pump();
    await tester.drag(find.byType(ListView), const Offset(-150, 0));
    await tester.pumpAndSettle();
    expect(_tabs(tester).index, 0);
    expect(haptics, isEmpty);
  });

  testWidgets('changing reduced motion during navigation preserves the selected page', (tester) async {
    await tester.pumpWidget(_app());
    final gesture = await _drag(tester);
    await gesture.up();
    await tester.pump(const Duration(milliseconds: 60));
    await tester.pumpWidget(_app(reduced: true));
    await tester.pump();
    expect(_tabs(tester).index, 1);
    expect(find.text('Media content'), findsOneWidget);
    await tester.pumpAndSettle();
    await tester.pumpWidget(_app());
    final next = await _drag(tester);
    await next.up();
    await tester.pumpAndSettle();
    expect(find.text('Saved content'), findsOneWidget);
    expect(tester.takeException(), isNull);
    expect(haptics, hasLength(2));
  });

  testWidgets('pinch zoom belongs to the image viewer', (tester) async {
    final transform = TransformationController();
    addTearDown(transform.dispose);
    await tester.pumpWidget(
      _app(
        first: InteractiveViewer(
          transformationController: transform,
          child: const SizedBox.expand(child: ColoredBox(color: Colors.blue)),
        ),
      ),
    );
    final center = tester.getCenter(find.byType(InteractiveViewer));
    final one = await tester.startGesture(center - const Offset(40, 0), pointer: 31);
    final two = await tester.startGesture(center + const Offset(40, 0), pointer: 32);
    await one.moveBy(const Offset(-45, 0));
    await two.moveBy(const Offset(45, 0));
    await one.up();
    await two.up();
    await tester.pumpAndSettle();
    expect(transform.value.getMaxScaleOnAxis(), greaterThan(1));
    expect(_tabs(tester).index, 0);
    expect(haptics, isEmpty);
  });
}
