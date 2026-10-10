import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/pixiv/pixiv_illust_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_page_overview.dart';
import 'package:xta/plugins/pixiv/pixiv_reader_screen.dart';

import 'support/pixiv_reader_harness.dart';

Finder _thumb(int page) => find.byWidgetPredicate((widget) => widget is PixivPageThumb && widget.page == page);

Future<void> _openFromCounter(WidgetTester tester, String key) async {
  await tester.tap(find.byKey(ValueKey(key)));
  await settlePixiv(tester);
  expect(find.byType(PixivPageOverview), findsOneWidget);
}

void main() {
  testWidgets('the reader counter opens every page as a numbered thumbnail with the current one ringed', (
    tester,
  ) async {
    await pumpPixiv(tester, PixivReaderScreen(illust: pixivWork(), initialPage: 2));
    expect(find.text('3 / 8'), findsOneWidget);

    await _openFromCounter(tester, 'pixiv-reader-counter');

    expect(find.text('Sommerfest'), findsWidgets);
    expect(find.text('8 pages'), findsOneWidget);
    expect(find.byType(PixivPageThumb), findsNWidgets(8));
    for (var page = 0; page < 8; page++) {
      expect(find.descendant(of: _thumb(page), matching: find.text('${page + 1}')), findsOneWidget);
    }
    final current = tester.widgetList<PixivPageThumb>(find.byType(PixivPageThumb)).where((thumb) => thumb.current);
    expect(current.map((thumb) => thumb.page), [2]);
    expect(
      tester.getSemantics(_thumb(2)),
      isSemantics(label: 'Page 3 of 8', isButton: true, isSelected: true, hasTapAction: true),
    );
    final ring = tester.widget<DecoratedBox>(find.byKey(const ValueKey('pixiv-overview-page-2')));
    final primary = Theme.of(tester.element(_thumb(2))).colorScheme.primary;
    expect(((ring.decoration as BoxDecoration).border as Border).top.color, primary);
    final plain = tester.widget<DecoratedBox>(find.byKey(const ValueKey('pixiv-overview-page-0')));
    expect(((plain.decoration as BoxDecoration).border as Border).top.color, Colors.transparent);
    await disposePixiv(tester);
  });

  testWidgets('tapping a thumbnail jumps the horizontal reader to that page', (tester) async {
    await pumpPixiv(tester, PixivReaderScreen(illust: pixivWork(), vertical: false));
    await _openFromCounter(tester, 'pixiv-reader-counter');

    await tester.tap(_thumb(5));
    await settlePixiv(tester);

    expect(find.byType(PixivPageOverview), findsNothing);
    expect(find.text('6 / 8'), findsOneWidget);
    expect(tester.widget<PageView>(find.byType(PageView)).controller!.page, 5);
    await disposePixiv(tester);
  });

  testWidgets('tapping a thumbnail jumps the vertical reader to that page', (tester) async {
    await pumpPixiv(tester, PixivReaderScreen(illust: pixivWork()));
    await _openFromCounter(tester, 'pixiv-reader-counter');

    await tester.tap(_thumb(4));
    await settlePixiv(tester);

    expect(find.text('5 / 8'), findsOneWidget);
    expect(find.byKey(const ValueKey('pixiv-reader-page-4')), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('the overview offers this page, all pages and the other reading direction', (tester) async {
    final harness = await pumpPixiv(tester, PixivReaderScreen(illust: pixivWork(), initialPage: 3));
    await _openFromCounter(tester, 'pixiv-reader-counter');

    expect(find.text('Download this page'), findsOneWidget);
    expect(find.text('Download all pages'), findsOneWidget);
    expect(find.text('Read horizontally'), findsOneWidget);
    for (final action in ['downloadPage', 'downloadAll', 'direction']) {
      final button = find.byKey(ValueKey('pixiv-overview-$action'));
      expect(tester.getSize(button).height, greaterThanOrEqualTo(48));
      expect(tester.getSemantics(button), isSemantics(isButton: true, hasTapAction: true));
    }

    await tester.tap(find.byKey(const ValueKey('pixiv-overview-downloadPage')));
    await settlePixiv(tester);
    expect(harness.downloader.pages, [3]);

    await _openFromCounter(tester, 'pixiv-reader-counter');
    await tester.tap(find.byKey(const ValueKey('pixiv-overview-downloadAll')));
    await settlePixiv(tester);
    expect(find.text('1 of 8 pages is already saved.'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('pixiv-resave-all')));
    await settlePixiv(tester);
    expect(harness.downloader.requests.map((request) => request.fileName), [
      for (var page = 0; page < 8; page++) '120_p$page.png',
    ]);

    await _openFromCounter(tester, 'pixiv-reader-counter');
    await tester.tap(find.byKey(const ValueKey('pixiv-overview-direction')));
    await settlePixiv(tester);
    expect(find.byType(PageView), findsOneWidget);
    expect(find.text('4 / 8'), findsOneWidget);
    await disposePixiv(tester);
  });

  for (final direction in TextDirection.values) {
    testWidgets('the overview fits 320dp at twice the text size (${direction.name})', (tester) async {
      await pumpPixiv(
        tester,
        PixivReaderScreen(illust: pixivWork(pages: 25), initialPage: 20),
        size: const Size(320, 640),
        textScale: 2,
        direction: direction,
      );
      await _openFromCounter(tester, 'pixiv-reader-counter');

      expect(tester.takeException(), isNull);
      expect(find.text('Download all pages'), findsOneWidget);
      expect(find.byKey(const ValueKey('pixiv-overview-page-20')), findsOneWidget);
      await tester.drag(find.byType(CustomScrollView), const Offset(0, -2000));
      await settlePixiv(tester);
      expect(find.byKey(const ValueKey('pixiv-overview-page-24')), findsOneWidget);
      expect(tester.takeException(), isNull);
      await disposePixiv(tester);
    });
  }

  testWidgets('the detail page counter opens the overview and a thumbnail turns the viewer', (tester) async {
    await pumpPixiv(tester, PixivIllustScreen(illust: pixivWork()));
    expect(find.text('1 / 8'), findsOneWidget);

    await _openFromCounter(tester, 'pixiv-illust-counter');
    expect(find.text('Read vertically'), findsWidgets);
    await tester.tap(_thumb(3));
    await settlePixiv(tester);

    expect(find.byType(PixivPageOverview), findsNothing);
    expect(find.text('4 / 8'), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('Read vertically opens the vertical reader where the viewer is', (tester) async {
    await pumpPixiv(tester, PixivIllustScreen(illust: pixivWork()));
    await _openFromCounter(tester, 'pixiv-illust-counter');
    await tester.tap(_thumb(2));
    await settlePixiv(tester);

    await tester.tap(find.byKey(const ValueKey('pixiv-illust-read-vertically')));
    await settlePixiv(tester);

    final reader = tester.widget<PixivReaderScreen>(find.byType(PixivReaderScreen));
    expect(reader.vertical, isTrue);
    expect(reader.initialPage, 2);
    await disposePixiv(tester);
  });

  testWidgets('the reader slider follows the drag and turns to the page where it is let go', (tester) async {
    await pumpPixiv(tester, PixivReaderScreen(illust: pixivWork(), vertical: false));
    final slider = find.byKey(const ValueKey('pixiv-reader-slider'));
    final gesture = await tester.startGesture(tester.getCenter(slider));
    await gesture.moveTo(tester.getTopRight(slider) + const Offset(-4, 24));
    await tester.pump();
    expect(find.text('8 / 8'), findsOneWidget);
    expect(tester.widget<PageView>(find.byType(PageView)).controller!.page, 0);

    await gesture.up();
    await settlePixiv(tester);
    expect(tester.widget<PageView>(find.byType(PageView)).controller!.page, 7);
    expect(find.text('8 / 8'), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('a single-page work has no page bar and no download-all', (tester) async {
    await pumpPixiv(tester, PixivReaderScreen(illust: pixivWork(pages: 1), vertical: false));
    expect(find.byKey(const ValueKey('pixiv-reader-slider')), findsNothing);
    expect(find.byKey(const ValueKey('pixiv-reader-counter')), findsNothing);

    await tester.tap(find.byKey(const ValueKey('pixiv-reader-more')));
    await settlePixiv(tester);
    expect(find.text('Download this page'), findsOneWidget);
    expect(find.text('Download all pages'), findsNothing);
    await disposePixiv(tester);
  });
}
