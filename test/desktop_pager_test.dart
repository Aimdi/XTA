import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/ui/desktop_pager.dart';
import 'package:xta/utils/desktop.dart';

const _count = 3;

Future<ExtendedPageController> _viewer(WidgetTester tester) async {
  final controller = ExtendedPageController();
  addTearDown(controller.dispose);
  await tester.pumpWidget(
    MaterialApp(
      home: Scaffold(
        body: DesktopPager(
          controller: controller,
          count: _count,
          child: ExtendedImageGesturePageView.builder(
            controller: controller,
            itemCount: _count,
            itemBuilder: (context, i) => Center(child: Text('picture $i')),
          ),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
  return controller;
}

int _page(ExtendedPageController controller) => controller.page!.round();

Finder get _previous => find.byTooltip('Previous page');
Finder get _next => find.byTooltip('Next page');

void main() {
  setUp(() => debugDesktopOverride = true);
  tearDown(() => debugDesktopOverride = null);

  testWidgets('the buttons show only where there is a page to turn to', (tester) async {
    final controller = await _viewer(tester);
    expect(_previous, findsNothing);
    expect(_next, findsOneWidget);

    await tester.tap(_next);
    await tester.pumpAndSettle();
    expect(_page(controller), 1);
    expect(_previous, findsOneWidget);
    expect(_next, findsOneWidget);

    await tester.tap(_next);
    await tester.pumpAndSettle();
    expect(_page(controller), 2);
    expect(_next, findsNothing);

    await tester.tap(_previous);
    await tester.pumpAndSettle();
    expect(_page(controller), 1);
  });

  testWidgets('← and → turn the pages, and stop at either end', (tester) async {
    final controller = await _viewer(tester);

    Future<void> press(LogicalKeyboardKey key) async {
      await tester.sendKeyEvent(key);
      await tester.pumpAndSettle();
    }

    await press(LogicalKeyboardKey.arrowLeft);
    expect(_page(controller), 0);
    await press(LogicalKeyboardKey.arrowRight);
    await press(LogicalKeyboardKey.arrowRight);
    await press(LogicalKeyboardKey.arrowRight);
    expect(_page(controller), 2);
    await press(LogicalKeyboardKey.arrowLeft);
    expect(_page(controller), 1);
  });

  testWidgets('off the desktop the viewer is as it was', (tester) async {
    debugDesktopOverride = false;
    final controller = await _viewer(tester);

    await tester.sendKeyEvent(LogicalKeyboardKey.arrowRight);
    await tester.pumpAndSettle();

    expect(_next, findsNothing);
    expect(_page(controller), 0);
  });
}
