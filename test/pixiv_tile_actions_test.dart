import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_button.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_store.dart';
import 'package:xta/plugins/pixiv/pixiv_illust_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_illust_tile.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_reader_screen.dart';

import 'support/pixiv_bookmark_fakes.dart';
import 'support/pixiv_reader_harness.dart';

Widget _tile(PixivIllust illust) => Scaffold(
  body: Align(
    alignment: Alignment.topLeft,
    child: SizedBox(width: 180, child: PixivIllustTile(illust: illust)),
  ),
);

Future<void> _longPressTile(WidgetTester tester) async {
  await tester.longPress(find.byType(PixivIllustTile));
  await settlePixiv(tester);
}

Future<void> _pick(WidgetTester tester, String id) async {
  await tester.tap(find.byKey(ValueKey('plugin-post-action-$id')));
  await settlePixiv(tester);
}

/// A one-page work wearing both bottom labels.
PixivIllust _labelledWork() {
  final work = pixivWork(pages: 1);
  return PixivIllust(
    id: work.id,
    title: work.title,
    caption: '',
    type: 'illust',
    thumbnailUrl: work.thumbnailUrl,
    pageUrls: work.pageUrls,
    originalUrls: work.originalUrls,
    pageThumbUrls: work.pageThumbUrls,
    pageCount: 1,
    width: work.width,
    height: work.height,
    userId: work.userId,
    userName: work.userName,
    userAccount: work.userAccount,
    isR18: true,
    isAi: true,
  );
}

T _read<T>(WidgetTester tester) => Provider.of<T>(tester.element(find.byType(PixivIllustTile)), listen: false);

void main() {
  testWidgets('a long-pressed tile lists Pixiv actions above the shared ones', (tester) async {
    await pumpPixiv(tester, _tile(pixivWork(pages: 3)));
    await _longPressTile(tester);

    final ids = ['pixiv-download', 'pixiv-bookmark', 'pixiv-copy-link', 'pixiv-mute-author', 'pixiv-mute-work'];
    final tops = [for (final id in ids) tester.getTopLeft(find.byKey(ValueKey('plugin-post-action-$id'))).dy];
    expect(tops, orderedEquals([...tops]..sort()));
    expect(find.text('Download all pages'), findsOneWidget);
    expect(find.text('Bookmark'), findsOneWidget);
    expect(tops.last, lessThan(tester.getTopLeft(find.text('Share link')).dy));
    expect(find.text('Open in browser'), findsOneWidget);
    for (final id in ids) {
      expect(tester.getSize(find.byKey(ValueKey('plugin-post-action-$id'))).height, greaterThanOrEqualTo(48));
    }
    await disposePixiv(tester);
  });

  testWidgets('Download saves every page of the work and marks the tile', (tester) async {
    final harness = await pumpPixiv(tester, _tile(pixivWork(pages: 3)));
    expect(find.byKey(const ValueKey('pixiv-downloaded-120')), findsNothing);

    await _longPressTile(tester);
    await _pick(tester, 'pixiv-download');

    expect(harness.downloader.requests.map((request) => request.fileName), ['120_p0.png', '120_p1.png', '120_p2.png']);
    expect(find.text('Files saved: 3 / 3'), findsOneWidget);
    expect(find.byKey(const ValueKey('pixiv-downloaded-120')), findsOneWidget);
    expect(find.bySemanticsLabel(RegExp('Saved on this device')), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('a single-page work offers a plain Download', (tester) async {
    final harness = await pumpPixiv(tester, _tile(pixivWork(pages: 1)));
    await _longPressTile(tester);

    expect(find.text('Download'), findsOneWidget);
    await _pick(tester, 'pixiv-download');
    expect(harness.downloader.pages, [0]);
    await disposePixiv(tester);
  });

  testWidgets('Bookmark goes through the shared bookmark store', (tester) async {
    final api = FakePixivBookmarkApi();
    await pumpPixiv(tester, _tile(pixivWork()), extraProviders: [api.provider]);
    await _longPressTile(tester);
    await _pick(tester, 'pixiv-bookmark');

    expect(api.writes, ['add:120:public:']);
    expect(_read<PixivBookmarkStore>(tester).isBookmarked(pixivWork()), isTrue);
    await disposePixiv(tester);
  });

  testWidgets('Copy link puts the work link on the clipboard', (tester) async {
    String? copied;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
      if (call.method == 'Clipboard.setData') copied = (call.arguments as Map)['text'] as String?;
      return null;
    });
    addTearDown(() => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, null));
    await pumpPixiv(tester, _tile(pixivWork()));

    await _longPressTile(tester);
    await _pick(tester, 'pixiv-copy-link');

    expect(copied, 'https://www.pixiv.net/artworks/120');
    expect(find.text('Link copied'), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('Mute author and Mute work ask first, then mute', (tester) async {
    await pumpPixiv(tester, _tile(pixivWork()));
    final mute = _read<PixivMuteStore>(tester);

    await _longPressTile(tester);
    await _pick(tester, 'pixiv-mute-author');
    await tester.tap(find.widgetWithText(TextButton, 'Cancel'));
    await settlePixiv(tester);
    expect(mute.state.authorIds, isEmpty);

    await _longPressTile(tester);
    await _pick(tester, 'pixiv-mute-author');
    await tester.tap(find.widgetWithText(FilledButton, 'Mute author'));
    await settlePixiv(tester);
    expect(mute.state.authorIds, {42});

    await _longPressTile(tester);
    await _pick(tester, 'pixiv-mute-work');
    await tester.tap(find.widgetWithText(FilledButton, 'Mute this work'));
    await settlePixiv(tester);
    expect(mute.state.illustIds, {120});
    await disposePixiv(tester);
  });

  group('the save button shows the page on screen is saved', () {
    testWidgets('on the work, it fills for a saved page and follows the page shown', (tester) async {
      final harness = await pumpPixiv(tester, PixivIllustScreen(illust: pixivWork()));
      final button = find.byKey(const ValueKey('pixiv-illust-download'));
      expect(find.descendant(of: button, matching: find.byIcon(Icons.download_outlined)), findsOneWidget);

      await harness.downloads.record(120, [0]);
      await tester.pump();
      expect(find.descendant(of: button, matching: find.byIcon(Icons.download_done)), findsOneWidget);
      expect(find.byTooltip('Saved – download again'), findsOneWidget);

      await tester.drag(find.byType(PageView), const Offset(-400, 0));
      await settlePixiv(tester);
      expect(find.text('2 / 8'), findsOneWidget);
      expect(find.descendant(of: button, matching: find.byIcon(Icons.download_outlined)), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('in the reader, too', (tester) async {
      final harness = await pumpPixiv(tester, PixivReaderScreen(illust: pixivWork(), initialPage: 2, vertical: false));
      await harness.downloads.record(120, [2]);
      await tester.pump();

      final button = find.byKey(const ValueKey('pixiv-reader-download'));
      expect(find.descendant(of: button, matching: find.byIcon(Icons.download_done)), findsOneWidget);
      await disposePixiv(tester);
    });
  });

  for (final textScale in [1.0, 1.3, 2.0]) {
    testWidgets('on a narrow tile the saved mark clears the R-18 and AI labels and the heart (x$textScale)', (
      tester,
    ) async {
      final harness = await pumpPixiv(
        tester,
        Scaffold(
          body: Align(
            alignment: Alignment.topLeft,
            child: SizedBox(width: 152, child: PixivIllustTile(illust: _labelledWork())),
          ),
        ),
        textScale: textScale,
      );
      await harness.downloads.record(120, const [0]);
      await tester.pump();

      final badge = tester.getRect(find.byKey(const ValueKey('pixiv-downloaded-120')));
      for (final label in ['R-18', 'AI']) {
        expect(badge.overlaps(tester.getRect(find.text(label))), isFalse, reason: label);
      }
      expect(badge.overlaps(tester.getRect(find.byType(PixivBookmarkButton))), isFalse);
      expect(tester.takeException(), isNull);
      await disposePixiv(tester);
    });
  }
}
