import 'package:flutter_test/flutter_test.dart';
import 'package:xta/tweet/media_strip.dart';

void main() {
  group('what shape a card is allowed to be', () {
    test('an ordinary photo keeps its own', () {
      expect(clampMediaAspect(1.0), 1.0);
      expect(clampMediaAspect(1.5), 1.5);
    });

    test('a sliver is cropped to the nearest end rather than shown as one', () {
      expect(clampMediaAspect(0.2), kMediaMinAspect);
      expect(clampMediaAspect(6.0), kMediaMaxAspect);
    });

    test('a size the response did not carry is treated as square', () {
      // Not a shape at all — no width, a negative one, or a number that is not
      // one. A square is the guess that is wrong by the least.
      expect(clampMediaAspect(0), 1);
      expect(clampMediaAspect(-3), 1);
      expect(clampMediaAspect(double.nan), 1);
      expect(clampMediaAspect(double.infinity), 1);
    });
  });

  group('laying media out along a row', () {
    // A picture with nothing beside it is not a card in a row. Clamping it into
    // the row's range put tall photos inside black bars, with the rounded
    // corners landing on the bars instead of the picture.
    test('one photo takes the full width and keeps its own shape, unclamped', () {
      final layout = mediaStripLayout(width: 400, aspects: [0.5]);

      expect(layout.widths, [400]);
      expect(layout.height, closeTo(800, 0.001), reason: 'its own 1:2, not the row\'s tallest allowed');
    });

    test('a single photo with no usable size falls back to a square', () {
      expect(singleMediaAspect(0), 1);
      expect(singleMediaAspect(double.nan), 1);
      expect(singleMediaAspect(1.7), 1.7);
    });

    test('several share one height', () {
      final layout = mediaStripLayout(width: 400, aspects: [0.8, 1.0, 1.5]);

      expect(layout.height, closeTo(400 * kMediaStripHeightFactor, 0.001));
      expect(layout.widths, hasLength(3));
    });

    // This is the whole point: how many fit is not a rule about the count, it
    // falls out of the shapes. Tall photos are narrow, so more are in view.
    test('tall photos are narrower than wide ones, so more of them fit', () {
      final tall = mediaStripLayout(width: 400, aspects: [0.8, 0.8, 0.8]);
      final wide = mediaStripLayout(width: 400, aspects: [1.91, 1.91, 1.91]);

      expect(tall.widths.first, lessThan(wide.widths.first));

      // How much of the row one card takes is the whole of it: the number in
      // view is that, and nothing is counting images.
      double across(MediaStripLayout layout) => 400 / (layout.widths.first + kMediaCardGap);

      expect(across(tall), greaterThan(across(wide)));
      expect(across(tall), greaterThan(1.5), reason: 'a second tall card is well into view');
    });

    test('no card fills the row, so the next one always shows an edge', () {
      final layout = mediaStripLayout(width: 400, aspects: [1.91, 1.91]);

      for (final width in layout.widths) {
        expect(width, lessThan(400));
      }
    });

    test('nothing attached lays out as nothing', () {
      final layout = mediaStripLayout(width: 400, aspects: const []);

      expect(layout.widths, isEmpty);
      expect(layout.height, 0);
    });
  });

  group('opening a post at one of its pictures', () {
    test('the first one needs no scrolling', () {
      expect(mediaStripOffsetOf(0, const [100, 100, 100]), 0);
    });

    test('a later one counts the cards and the gaps before it', () {
      expect(mediaStripOffsetOf(2, const [100, 120, 100], gap: 8), 100 + 8 + 120 + 8);
    });

    test('an index past the end stops at the last card rather than throwing', () {
      expect(mediaStripOffsetOf(9, const [100, 100], gap: 8), 100 + 8 + 100 + 8);
    });
  });

  group('mediaItemAspect', () {
    test('a video uses videoInfo, not the thumbnail size', () {
      expect(
        mediaItemAspect(type: 'video', videoAspect: [16, 9], thumbW: 1, thumbH: 1),
        closeTo(16 / 9, 0.001),
      );
    });

    test('a broadcast with no sizes is 16:9, not a square', () {
      expect(
        mediaItemAspect(type: 'video', videoAspect: null, thumbW: null, thumbH: null),
        closeTo(16 / 9, 0.001),
      );
    });

    test('a photo still uses its pixel size', () {
      expect(mediaItemAspect(type: 'photo', thumbW: 4, thumbH: 5), closeTo(4 / 5, 0.001));
    });
  });
}
