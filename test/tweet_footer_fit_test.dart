import 'package:flutter_test/flutter_test.dart';
import 'package:xta/tweet/tweet_footer.dart';

/// Width one count action occupies with a label of [labelWidth].
double _item(double labelWidth) => kFooterCountItemBase + labelWidth;

void main() {
  group('resolveFooterFit', () {
    // Realistic label widths at 14px: "0" ~8, "3" ~8, "6" ~8, "1.2K" ~28.
    const narrowLabels = [8.0, 8.0, 8.0];
    const viewsLabel = 28.0;

    test('shows everything when the strip fits', () {
      final needed = _item(8) * 3 + _item(viewsLabel) + kFooterIconItem * 3 + kFooterGroupGap;

      final fit = resolveFooterFit(
        available: needed + 20,
        countLabelWidths: narrowLabels,
        viewsLabelWidth: viewsLabel,
        iconButtons: 3,
      );

      expect(fit.showCounts, isTrue);
      expect(fit.showViews, isTrue);
      expect(fit.mustScaleDown, isFalse);
    });

    test('drops the view count first, because it is only a number', () {
      final withViews = _item(8) * 3 + _item(viewsLabel) + kFooterIconItem * 3 + kFooterGroupGap;

      final fit = resolveFooterFit(
        available: withViews - 10,
        countLabelWidths: narrowLabels,
        viewsLabelWidth: viewsLabel,
        iconButtons: 3,
      );

      expect(fit.showViews, isFalse);
      expect(fit.showCounts, isTrue, reason: 'the engagement counts are worth more than the view count');
      expect(fit.mustScaleDown, isFalse);
    });

    test('drops the labels only once dropping views is not enough', () {
      final withCounts = _item(8) * 3 + kFooterIconItem * 3 + kFooterGroupGap;

      final fit = resolveFooterFit(
        available: withCounts - 5,
        countLabelWidths: narrowLabels,
        viewsLabelWidth: viewsLabel,
        iconButtons: 3,
      );

      expect(fit.showCounts, isFalse);
      expect(fit.showViews, isFalse);
      expect(fit.mustScaleDown, isFalse, reason: 'a bare row of glyphs still fits, so nothing needs scaling');
    });

    test('asks to scale down when even bare glyphs overflow', () {
      final bare = _item(0) * 3 + kFooterIconItem * 3 + kFooterGroupGap;

      final fit = resolveFooterFit(
        available: bare - 1,
        countLabelWidths: narrowLabels,
        viewsLabelWidth: viewsLabel,
        iconButtons: 3,
      );

      expect(fit.mustScaleDown, isTrue);
    });

    test('a tweet without a view count never reserves room for one', () {
      final withCounts = _item(8) * 3 + kFooterIconItem * 3 + kFooterGroupGap;

      final fit = resolveFooterFit(
        available: withCounts,
        countLabelWidths: narrowLabels,
        viewsLabelWidth: null,
        iconButtons: 3,
      );

      expect(fit.showCounts, isTrue);
      expect(fit.showViews, isFalse);
    });

    test('the reported layout fits on a 360dp phone', () {
      // The screenshot: 0 replies, 3 reposts, 6 likes and a view count, with
      // bookmark, share and translate — on a 360dp screen less the 8dp margins.
      const available = 360.0 - 16;

      final fit = resolveFooterFit(
        available: available,
        countLabelWidths: narrowLabels,
        viewsLabelWidth: viewsLabel,
        iconButtons: 3,
      );

      expect(fit.showCounts, isTrue);
      expect(fit.showViews, isTrue, reason: 'the view count must not be pushed off the end any more');
      expect(fit.mustScaleDown, isFalse);
    });

    test('Material default metrics could not fit that phone at all', () {
      // Regression guard on the metrics themselves: a 64dp minimum width plus
      // 16dp padding either side is what pushed the strip past the screen.
      const materialItem = 64.0 + 32;
      const materialIcon = 48.0;
      final material = materialItem * 4 + materialIcon * 3 + kFooterGroupGap;
      final tightened = _item(28) * 4 + kFooterIconItem * 3 + kFooterGroupGap;

      expect(material, greaterThan(360.0 - 16));
      expect(tightened, lessThan(material), reason: 'the tightened metrics must recover real width');
    });

    test('a viral post keeps its engagement counts and gives up the view count', () {
      // Four wide labels ("1.2K", "24K", "1.2M", "3.4M") still exceed a 360dp
      // phone even with the tightened metrics, so the ladder sheds the view
      // count rather than clipping a digit off the end.
      const wide = [28.0, 28.0, 28.0];

      final fit = resolveFooterFit(
        available: 360.0 - 16,
        countLabelWidths: wide,
        viewsLabelWidth: 28,
        iconButtons: 3,
      );

      expect(fit.showCounts, isTrue);
      expect(fit.showViews, isFalse);
      expect(fit.mustScaleDown, isFalse);
    });
  });
}
