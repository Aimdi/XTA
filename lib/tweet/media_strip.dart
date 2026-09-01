/// How a post's media is laid out along one row.
///
/// A post used to show one image at a time in a full-width pager: whatever else
/// was attached was invisible until you swiped, and nothing said how much there
/// was beyond a counter in the corner. Threads instead lays the media out along
/// a row at one height, so several are in view at once and the next one shows
/// an edge — which is also why the number you can see changes with the post.
/// Tall photos are narrow and three fit; wide ones are broad and barely two do.
/// That is not a rule about the count, it falls out of the shapes.
library;

import 'dart:math' as math;

/// The range media is shown between: 4:5 at the tallest, 1.91:1 at the widest.
///
/// Beyond either end a card would be a sliver, so the picture is cropped to the
/// nearest instead. The same bounds the source networks impose on what can be
/// posted, which is why so little is actually outside them.
const double kMediaMinAspect = 0.8;
const double kMediaMaxAspect = 1.91;

/// How tall a row of several is, against the width it has to fill.
const double kMediaStripHeightFactor = 0.62;

/// The most of the row one card may take, so there is always an edge of the
/// next one showing rather than a full-width card that looks like the only one.
const double kMediaCardMaxWidthFactor = 0.86;

/// The shape one picture on its own is shown at: its own.
///
/// Not clamped, unlike a card in a row. A row has to agree on a height, so a
/// shape too far outside the range would make every other card a sliver — but a
/// picture with nothing beside it has nothing to agree with, and forcing it into
/// that range puts a tall photo inside bars that are not part of it.
double singleMediaAspect(double aspect) =>
    (!aspect.isFinite || aspect <= 0) ? 1 : aspect;

/// Shape of one attached photo or video.
///
/// Videos must use `videoInfo.aspectRatio` when X sent it. Falling back to the
/// thumbnail's `sizes` (or to 1.0 when those were missing) put 16:9 broadcasts
/// inside a square and left a fat white bar under the player.
double mediaItemAspect({
  required String? type,
  List<int>? videoAspect,
  int? thumbW,
  int? thumbH,
}) {
  if (type == 'video' || type == 'animated_gif') {
    if (videoAspect != null && videoAspect.length >= 2 && videoAspect[1] != 0) {
      final ratio = videoAspect[0] / videoAspect[1];
      if (ratio.isFinite && ratio > 0) {
        return ratio;
      }
    }
  }
  if (thumbW != null && thumbH != null && thumbH != 0) {
    final ratio = thumbW / thumbH;
    if (ratio.isFinite && ratio > 0) {
      return ratio;
    }
  }
  return 16 / 9;
}

double clampMediaAspect(double aspect) {
  if (!aspect.isFinite || aspect <= 0) {
    return 1;
  }
  return aspect.clamp(kMediaMinAspect, kMediaMaxAspect);
}

/// The height of the row, and the width of every card in it.
typedef MediaStripLayout = ({double height, List<double> widths});

/// Lays [aspects] out along a row [width] wide.
///
/// One item keeps the full width and its own shape — a single photo is not a
/// carousel, and cropping it to a row height would lose the top and bottom of
/// something nothing is competing with. Several share a height, and each is as
/// wide as its shape at that height.
MediaStripLayout mediaStripLayout({
  required double width,
  required List<double> aspects,
}) {
  if (aspects.isEmpty) {
    return (height: 0, widths: const []);
  }

  if (aspects.length == 1) {
    return (height: width / singleMediaAspect(aspects.single), widths: [width]);
  }

  final height = width * kMediaStripHeightFactor;
  final widest = width * kMediaCardMaxWidthFactor;

  return (
    height: height,
    widths: [
      for (final aspect in aspects)
        math.min(height * clampMediaAspect(aspect), widest),
    ],
  );
}

/// The gap between cards.
const double kMediaCardGap = 8;

/// How far the row must be scrolled for the card at [index] to sit at its left
/// edge. Used when a post is opened at a particular picture — from the media
/// grid, say — so the one that was tapped is the one in view.
double mediaStripOffsetOf(
  int index,
  List<double> widths, {
  double gap = kMediaCardGap,
}) {
  var offset = 0.0;
  for (var i = 0; i < index && i < widths.length; i++) {
    offset += widths[i] + gap;
  }
  return offset;
}
