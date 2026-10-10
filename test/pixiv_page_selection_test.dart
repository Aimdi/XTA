import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_page_overview.dart';
import 'package:xta/plugins/pixiv/pixiv_page_selection.dart';
import 'package:xta/plugins/pixiv/pixiv_reader_screen.dart';

import 'support/pixiv_reader_harness.dart';

Finder _thumb(int page) => find.byWidgetPredicate((widget) => widget is PixivPageThumb && widget.page == page);

Future<void> _startPicking(WidgetTester tester) async {
  await tester.tap(find.byKey(const ValueKey('pixiv-reader-counter')));
  await settlePixiv(tester);
  await tester.tap(find.byKey(const ValueKey('pixiv-overview-selectPages')));
  await settlePixiv(tester);
}

Future<void> _tick(WidgetTester tester, List<int> pages) async {
  for (final page in pages) {
    await tester.ensureVisible(_thumb(page));
    await tester.tap(_thumb(page));
    await tester.pump();
  }
}

void main() {
  group('PixivPageSelectionStore', () {
    test('starts empty, ticks and unticks pages, and keeps reading order', () {
      final store = PixivPageSelectionStore(5);
      addTearDown(store.destroy);
      store.toggle(1);
      expect(store.state.active, isFalse, reason: 'ticking needs picking to have started');

      store.start();
      store
        ..toggle(4)
        ..toggle(1)
        ..toggle(9)
        ..toggle(-1);
      expect(store.state.ordered, [1, 4]);
      store.toggle(4);
      expect(store.state.ordered, [1]);
    });

    test('All ticks every page, and once all are ticked clears them', () {
      final store = PixivPageSelectionStore(3)..start();
      addTearDown(store.destroy);
      store.toggleAll();
      expect(store.allSelected, isTrue);
      expect(store.state.ordered, [0, 1, 2]);
      store.toggleAll();
      expect(store.allSelected, isFalse);
      expect(store.state.pages, isEmpty);
      store.stop();
      expect(store.state.active, isFalse);
    });
  });

  testWidgets('picked pages, and only those, are saved in reading order', (tester) async {
    final harness = await pumpPixiv(tester, PixivReaderScreen(illust: pixivWork()));
    await _startPicking(tester);

    expect(find.text('No pages selected'), findsOneWidget);
    final save = find.byKey(const ValueKey('pixiv-overview-save-selected'));
    expect(tester.widget<ButtonStyleButton>(save).onPressed, isNull);

    await _tick(tester, [5, 1]);
    expect(find.text('2 pages selected'), findsOneWidget);
    expect(find.byKey(const ValueKey('pixiv-overview-check-5')), findsOneWidget);
    expect(tester.getSemantics(_thumb(5)), isSemantics(label: 'Page 6 of 8', isChecked: true, hasCheckedState: true));
    expect(tester.getSemantics(_thumb(2)), isSemantics(label: 'Page 3 of 8', isChecked: false, hasCheckedState: true));

    await tester.tap(find.text('Save 2 pages'));
    await settlePixiv(tester);

    expect(find.byType(PixivPageOverview), findsNothing);
    expect(harness.downloader.requests.map((request) => request.fileName), ['120_p1.png', '120_p5.png']);
    expect(find.text('Files saved: 2 / 2'), findsOneWidget);
    expect(harness.downloads.savedAmong(pixivWork(), [0, 1, 5]), [1, 5]);
    await disposePixiv(tester);
  });

  testWidgets('All and None tick and clear every page, Cancel goes back to the actions', (tester) async {
    await pumpPixiv(tester, PixivReaderScreen(illust: pixivWork()));
    await _startPicking(tester);

    await tester.tap(find.byKey(const ValueKey('pixiv-overview-select-all')));
    await tester.pump();
    expect(find.text('8 pages selected'), findsOneWidget);
    expect(find.text('Save 8 pages'), findsOneWidget);
    expect(find.text('None'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('pixiv-overview-select-all')));
    await tester.pump();
    expect(find.text('No pages selected'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('pixiv-overview-select-cancel')));
    await tester.pump();
    expect(find.text('8 pages'), findsOneWidget);
    expect(find.byKey(const ValueKey('pixiv-overview-downloadAll')), findsOneWidget);
    expect(find.byKey(const ValueKey('pixiv-overview-check-0')), findsNothing);
    await disposePixiv(tester);
  });

  testWidgets('Select pages in a page sheet opens the overview ready to pick', (tester) async {
    final harness = await pumpPixiv(tester, PixivReaderScreen(illust: pixivWork(), vertical: false));
    await tester.tap(find.byKey(const ValueKey('pixiv-reader-more')));
    await settlePixiv(tester);
    await tester.tap(find.byKey(const ValueKey('pixiv-page-action-selectPages')));
    await settlePixiv(tester);

    expect(find.byType(PixivPageOverview), findsOneWidget);
    expect(find.text('No pages selected'), findsOneWidget);
    await _tick(tester, [3]);
    await tester.tap(find.text('Save 1 page'));
    await settlePixiv(tester);
    expect(harness.downloader.pages, [3], reason: 'one page goes the single-page way');
    await disposePixiv(tester);
  });

  testWidgets('while picking, a long-press still opens the page', (tester) async {
    await pumpPixiv(tester, PixivReaderScreen(illust: pixivWork(), vertical: false));
    await _startPicking(tester);

    await tester.longPress(_thumb(4));
    await settlePixiv(tester);

    expect(find.byType(PixivPageOverview), findsNothing);
    expect(find.text('5 / 8'), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('picked pages already saved are asked about, and can be left out', (tester) async {
    final harness = await pumpPixiv(
      tester,
      PixivReaderScreen(illust: pixivWork()),
      client: (prefs) {
        prefs.set(optionPluginPixivDownloadIndex, '["120_p1"]');
        return FakePixivClient(prefs);
      },
    );
    await _startPicking(tester);
    await _tick(tester, [1, 2]);

    await tester.tap(find.text('Save 2 pages'));
    await settlePixiv(tester);
    expect(find.text('1 of 2 pages is already saved.'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('pixiv-resave-new')));
    await settlePixiv(tester);

    expect(harness.downloader.requests.map((request) => request.fileName), ['120_p2.png']);
    await disposePixiv(tester);
  });

  testWidgets('cancelling the already-saved question saves nothing', (tester) async {
    final harness = await pumpPixiv(
      tester,
      PixivReaderScreen(illust: pixivWork()),
      client: (prefs) {
        prefs.set(optionPluginPixivDownloadIndex, '["120_p0","120_p1"]');
        return FakePixivClient(prefs);
      },
    );
    await _startPicking(tester);
    await _tick(tester, [0, 1]);
    await tester.tap(find.text('Save 2 pages'));
    await settlePixiv(tester);

    expect(find.text('2 of 2 pages are already saved.'), findsOneWidget);
    expect(find.byKey(const ValueKey('pixiv-resave-new')), findsNothing, reason: 'nothing new to save');
    await tester.tap(find.text('Cancel'));
    await settlePixiv(tester);
    expect(harness.downloader.requests, isEmpty);
    await disposePixiv(tester);
  });

  for (final direction in TextDirection.values) {
    testWidgets('picking fits 320dp at twice the text size (${direction.name})', (tester) async {
      await pumpPixiv(
        tester,
        PixivReaderScreen(illust: pixivWork(pages: 12)),
        size: const Size(320, 640),
        textScale: 2,
        direction: direction,
      );
      await _startPicking(tester);
      await _tick(tester, [0]);

      expect(tester.takeException(), isNull);
      for (final key in ['pixiv-overview-select-cancel', 'pixiv-overview-save-selected']) {
        expect(tester.getSize(find.byKey(ValueKey(key))).height, greaterThanOrEqualTo(48));
      }
      expect(find.text('Save 1 page'), findsOneWidget);
      await disposePixiv(tester);
    });
  }
}
