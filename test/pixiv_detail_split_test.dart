import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_detail_meta.dart';
import 'package:xta/plugins/pixiv/pixiv_detail_split.dart';
import 'package:xta/plugins/pixiv/pixiv_detail_viewer.dart';
import 'package:xta/plugins/pixiv/pixiv_illust_screen.dart';

import 'support/pixiv_reader_harness.dart';

Future<PixivHarness> _pumpDetail(WidgetTester tester, Size size, {PixivDetailLayout? layout}) => pumpPixiv(
  tester,
  PixivIllustScreen(illust: pixivWork(pages: 3)),
  size: size,
  client: (prefs) {
    if (layout != null) prefs.set(optionPluginPixivDetailLayout, layout.name);
    return FakePixivClient(prefs);
  },
);

double _infoWidth(WidgetTester tester) => tester.getSize(find.byType(CustomScrollView)).width;

void main() {
  group('when the detail splits', () {
    test('automatic splits from 840dp, stacked never does, side by side wherever both panes fit', () {
      expect(pixivDetailSplits(839, PixivDetailLayout.auto), isFalse);
      expect(pixivDetailSplits(840, PixivDetailLayout.auto), isTrue);
      expect(pixivDetailSplits(2000, PixivDetailLayout.vertical), isFalse);
      expect(pixivDetailSplits(575, PixivDetailLayout.split), isFalse);
      expect(pixivDetailSplits(576, PixivDetailLayout.split), isTrue);
    });

    test('the details keep 320dp and the pictures 240dp whatever the divider says', () {
      expect(pixivSplitImageWidth(1000, 0.5), 500);
      expect(pixivSplitImageWidth(840, 0.9), 840 - pixivSplitGutter - pixivSplitMinInfo);
      expect(pixivSplitImageWidth(840, 0.1), pixivSplitMinImages);
    });

    test('the layout and divider read from preferences, with defaults for anything unusable', () {
      expect(pixivDetailLayout(null), PixivDetailLayout.auto);
      expect(
        pixivDetailLayout(PrefServiceCache(cache: {optionPluginPixivDetailLayout: 'split'})),
        PixivDetailLayout.split,
      );
      expect(
        pixivDetailLayout(PrefServiceCache(cache: {optionPluginPixivDetailLayout: 'odd'})),
        PixivDetailLayout.auto,
      );
      expect(pixivSplitFraction(PrefServiceCache(cache: {optionPluginPixivDetailSplit: 0.5})), 0.5);
      expect(pixivSplitFraction(PrefServiceCache(cache: {optionPluginPixivDetailSplit: 3})), 0.9);
      expect(
        pixivSplitFraction(PrefServiceCache(cache: {optionPluginPixivDetailSplit: 'x'})),
        pixivSplitDefaultFraction,
      );
    });
  });

  group('split detail', () {
    testWidgets('a tablet puts the pictures left and the details right, at least 320dp wide', (tester) async {
      await _pumpDetail(tester, const Size(840, 700));
      expect(find.byType(PixivDetailSplit), findsOneWidget);
      final viewer = tester.getRect(find.byType(PixivDetailViewer));
      final meta = tester.getRect(find.byType(PixivDetailMeta));
      expect(viewer.right, lessThan(meta.left));
      expect(viewer.height, greaterThan(500));
      expect(_infoWidth(tester), greaterThanOrEqualTo(pixivSplitMinInfo));
      expect(find.byKey(const ValueKey('pixiv-illust-counter')), findsOneWidget);
      expect(tester.takeException(), isNull);
      await disposePixiv(tester);
    });

    testWidgets('a phone stacks them, and so does a tablet set to stacked', (tester) async {
      await _pumpDetail(tester, const Size(390, 844));
      expect(find.byType(PixivDetailSplit), findsNothing);
      await disposePixiv(tester);

      await _pumpDetail(tester, const Size(1000, 700), layout: PixivDetailLayout.vertical);
      expect(find.byType(PixivDetailSplit), findsNothing);
      await disposePixiv(tester);
    });

    testWidgets('side by side applies to a landscape phone too', (tester) async {
      await _pumpDetail(tester, const Size(740, 360), layout: PixivDetailLayout.split);
      expect(find.byType(PixivDetailSplit), findsOneWidget);
      expect(tester.takeException(), isNull);
      await disposePixiv(tester);
    });

    testWidgets('dragging the divider resizes the panes, keeps 320dp of details and is remembered', (tester) async {
      final harness = await _pumpDetail(tester, const Size(1000, 700));
      final handle = find.byKey(const ValueKey('pixiv-detail-split-handle'));
      final before = _infoWidth(tester);

      await tester.drag(handle, const Offset(-200, 0));
      await settlePixiv(tester);
      expect(_infoWidth(tester), closeTo(before + 200, 1));
      final saved = harness.prefs.get<double>(optionPluginPixivDetailSplit)!;
      expect(saved, closeTo(pixivSplitImageWidth(1000, pixivSplitDefaultFraction) / 1000 - 0.2, 0.001));

      await tester.drag(handle, const Offset(600, 0));
      await settlePixiv(tester);
      expect(_infoWidth(tester), closeTo(pixivSplitMinInfo, 1));
      await disposePixiv(tester);
    });
  });
}
