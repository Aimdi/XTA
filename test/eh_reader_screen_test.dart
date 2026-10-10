import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/ehviewer/eh_reader_store.dart';

import 'support/eh_reader_harness.dart';
import 'support/fixture_images.dart';

final _previews = ehFixturePreviews(20);

Finder _page(int page) => find.byKey(ValueKey('eh-reader-page-$page'));

Future<void> _tapAt(WidgetTester tester, double x) async {
  await tester.tapAt(Offset(x, 250));
  await settleFixtureImages(tester);
}

void main() {
  testWidgets('opens at the initial page and remembers it', (tester) async {
    final harness = await pumpEhReader(tester, initialPage: 3, previews: _previews);

    expect(ehReaderCounter(3), findsOneWidget);
    expect(_page(3), findsOneWidget);
    expect(harness.history.pages, [3]);
    expect(harness.site.imageRequests(3), isNotEmpty);
    expect(harness.site.imageRequests(6), isNotEmpty, reason: 'the next three pages are preloaded');
    expect(harness.site.imageRequests(2), isNotEmpty, reason: 'and the one before');
    await disposeFixtureScreen(tester);
  });

  testWidgets('left to right: swipe left and tap the right third for the next page', (tester) async {
    final harness = await pumpEhReader(tester, previews: _previews);

    await tester.fling(_page(1), const Offset(-300, 0), 1500);
    await settleFixtureImages(tester);
    expect(ehReaderCounter(2), findsOneWidget);

    await _tapAt(tester, 370);
    expect(ehReaderCounter(3), findsOneWidget);
    await _tapAt(tester, 20);
    expect(ehReaderCounter(2), findsOneWidget);
    expect(harness.history.pages, [1, 2, 3, 2]);
    await disposeFixtureScreen(tester);
  });

  testWidgets('right to left: swipe right and tap the left third for the next page', (tester) async {
    await pumpEhReader(tester, mode: EhReadingMode.rightToLeft, initialPage: 2, previews: _previews);

    await tester.fling(_page(2), const Offset(300, 0), 1500);
    await settleFixtureImages(tester);
    expect(ehReaderCounter(3), findsOneWidget);

    await _tapAt(tester, 20);
    expect(ehReaderCounter(4), findsOneWidget);
    await _tapAt(tester, 370);
    expect(ehReaderCounter(3), findsOneWidget);

    final slider = find.byKey(const ValueKey('eh-reader-slider'));
    final direction = tester.widget<Directionality>(
      find.ancestor(of: slider, matching: find.byType(Directionality)).first,
    );
    expect(direction.textDirection, TextDirection.rtl, reason: 'the slider runs the way the pages do');
    await disposeFixtureScreen(tester);
  });

  testWidgets('tapping the middle hides the bars, and tapping again brings them back', (tester) async {
    await pumpEhReader(tester, previews: _previews);
    final title = find.text('Sommerfest').hitTestable();
    final slider = find.byKey(const ValueKey('eh-reader-slider')).hitTestable();
    expect(title, findsOneWidget);

    await _tapAt(tester, 195);
    expect(title, findsNothing);
    expect(slider, findsNothing);
    expect(ehReaderCounter(1), findsOneWidget, reason: 'tapping the middle never turns the page');

    await _tapAt(tester, 195);
    expect(title, findsOneWidget);
    expect(slider, findsOneWidget);
    await disposeFixtureScreen(tester);
  });

  testWidgets('the slider previews pages while dragged and jumps once on release', (tester) async {
    final harness = await pumpEhReader(tester, previews: _previews);
    final slider = find.byKey(const ValueKey('eh-reader-slider'));

    final gesture = await tester.startGesture(tester.getTopLeft(slider) + const Offset(30, 24));
    for (var i = 0; i < 6; i++) {
      await gesture.moveBy(const Offset(20, 0));
      await tester.pump();
    }
    final previewed = harness.history.pages.length;
    await gesture.up();
    await settleFixtureImages(tester);

    expect(previewed, 1, reason: 'dragging only previews the page number');
    expect(harness.history.pages, hasLength(2));
    expect(ehReaderCounter(harness.history.pages.last), findsOneWidget);
    expect(harness.history.pages.last, greaterThan(1));
    await disposeFixtureScreen(tester);
  });

  testWidgets('switching to vertical shows the pages in one list from the current page', (tester) async {
    final harness = await pumpEhReader(tester, initialPage: 3, previews: _previews);

    await tester.tap(find.byKey(const ValueKey('eh-reader-mode')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('eh-reader-mode-vertical')));
    await settleFixtureImages(tester);

    expect(find.byType(ExtendedImageGesturePageView), findsNothing);
    expect(find.byType(CustomScrollView), findsOneWidget);
    expect(_page(3), findsOneWidget);
    expect(_page(4), findsOneWidget);
    expect(tester.getTopLeft(_page(3)).dy, 0);
    expect(tester.getSize(_page(3)).height, closeTo(390 * 1.4142, 0.5), reason: 'placeholder height');
    expect(ehReaderCounter(3), findsOneWidget);
    expect(harness.prefs.get<String>(optionPluginEhReadingMode), 'vertical');
    await disposeFixtureScreen(tester);
  });

  testWidgets('vertical reading opens at the page and follows the scroll', (tester) async {
    final harness = await pumpEhReader(tester, mode: EhReadingMode.vertical, initialPage: 5, previews: _previews);

    expect(tester.getTopLeft(_page(5)).dy, 0);
    await tester.drag(find.byType(CustomScrollView), const Offset(0, -700));
    await settleFixtureImages(tester);

    expect(ehReaderCounter(6), findsOneWidget);
    expect(harness.history.pages, [5, 6]);
    await disposeFixtureScreen(tester);
  });

  testWidgets('long-press opens the page sheet; reload asks another server', (tester) async {
    final harness = await pumpEhReader(tester, previews: _previews);

    await tester.longPress(_page(1));
    await tester.pumpAndSettle();

    expect(find.text('Reload image'), findsOneWidget);
    expect(find.byKey(const ValueKey('eh-page-action-save')), findsOneWidget);
    expect(find.byKey(const ValueKey('eh-page-action-copyLink')), findsOneWidget);
    expect(find.byKey(const ValueKey('eh-page-action-openOriginal')), findsNothing, reason: 'guests get no original');

    await tester.tap(find.text('Reload image'));
    await settleFixtureImages(tester);
    expect(harness.site.imageRequests(1).last.queryParameters['nl'], 'nl-1');
    await disposeFixtureScreen(tester);
  });

  testWidgets('a page that will not load offers retry and another server', (tester) async {
    final site = EhFakeSite(failingPages: {1});
    await pumpEhReader(tester, site: site, previews: _previews);

    expect(find.text('The site returned something unexpected'), findsOneWidget);
    site.failingPages.clear();
    await tester.tap(find.text('Retry'));
    await settleFixtureImages(tester);

    expect(find.text("This page's image did not load"), findsOneWidget, reason: 'the test images never load');
    await tester.tap(find.text('Try another server'));
    await settleFixtureImages(tester);
    expect(site.imageRequests(1).last.queryParameters['nl'], 'nl-1');
    await disposeFixtureScreen(tester);
  });

  testWidgets('the page bar fits a narrow screen with large text', (tester) async {
    await pumpEhReader(tester, previews: _previews, size: const Size(320, 640), textScale: 2);

    expect(tester.takeException(), isNull);
    expect(find.byKey(const ValueKey('eh-reader-slider')).hitTestable(), findsOneWidget);
    expect(tester.getSize(find.byKey(const ValueKey('eh-reader-slider'))).width, greaterThan(120));
    await disposeFixtureScreen(tester);
  });
}
