import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/ui/stepped_scroll.dart';

void main() {
  group('nextScrollStep', () {
    test('a near target is reached at once', () {
      expect(nextScrollStep(100, 900, maxStep: 1000), 900);
      expect(nextScrollStep(900, 100, maxStep: 1000), 100);
    });

    test('a far target is approached one step at a time, in either direction', () {
      expect(nextScrollStep(0, 50000, maxStep: 12000), 12000);
      expect(nextScrollStep(50000, 0, maxStep: 12000), 38000);
    });
  });

  Future<ScrollController> pumpLongList(WidgetTester tester) async {
    final controller = ScrollController();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: ListView.builder(
          controller: controller,
          itemCount: 2000,
          itemBuilder: (context, index) => SizedBox(height: 100, child: Text('post $index')),
        ),
      ),
    );
    return controller;
  }

  testWidgets('a far restore moves a bounded distance per frame until it arrives', (tester) async {
    final controller = await pumpLongList(tester);
    var arrived = false;

    steppedJumpTo(controller.position, 150000, cancelled: () => false, onArrived: () => arrived = true, maxStep: 12000);
    expect(controller.offset, 12000);
    expect(arrived, isFalse);

    var frames = 0;
    while (!arrived && frames < 50) {
      await tester.pump();
      frames++;
      expect(controller.offset, lessThanOrEqualTo(12000.0 * (frames + 1)));
    }
    expect(arrived, isTrue);
    expect(controller.offset, 150000);
  });

  testWidgets('the reader scrolling cancels the rest of the restore', (tester) async {
    final controller = await pumpLongList(tester);
    var scrolled = false;

    steppedJumpTo(controller.position, 150000, cancelled: () => scrolled, maxStep: 12000);
    await tester.pump();
    scrolled = true;
    final stoppedAt = controller.offset;
    for (var i = 0; i < 5; i++) {
      await tester.pump();
    }
    expect(controller.offset, stoppedAt);
    expect(stoppedAt, lessThan(150000));
  });

  testWidgets('a target past the end lands on the end', (tester) async {
    final controller = await pumpLongList(tester);
    var arrived = false;
    steppedJumpTo(controller.position, 1e9, cancelled: () => false, onArrived: () => arrived = true);
    for (var i = 0; i < 60 && !arrived; i++) {
      await tester.pump();
    }
    expect(arrived, isTrue);
    expect(controller.offset, controller.position.maxScrollExtent);
  });
}
