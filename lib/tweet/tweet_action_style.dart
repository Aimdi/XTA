import 'package:flutter/material.dart';
import 'package:xta/tweet/tweet_chrome.dart';

/// X draws a post's actions — the footer glyphs, translate and the ⋯ menu —
/// at 18.75dp rather than Material's 24dp.
const double kTweetActionGlyphSize = 18.75;

/// Gap between a glyph and the edge of its target that faces a partner action.
///
/// Two 48dp targets with centred glyphs leave a 29dp hole between the glyphs.
/// Pushing the first glyph towards its partner closes that to the ~20dp X uses,
/// while each target stays a full 48 x 48 and the two never overlap.
const double kTweetPairedGlyphInset = 6;

const double _slack = kTweetTouchTarget - kTweetActionGlyphSize;

/// Places an action glyph inside its 48dp target: centred, or pushed to the
/// end of the target when [towardEnd], and level with the display name's line
/// when [alignTop].
EdgeInsetsDirectional tweetActionGlyphPadding({
  bool towardEnd = false,
  bool alignTop = false,
}) {
  final start = towardEnd ? _slack - kTweetPairedGlyphInset : _slack / 2;
  return EdgeInsetsDirectional.only(
    start: start,
    end: _slack - start,
    top: alignTop ? 0 : _slack / 2,
    bottom: alignTop ? _slack : _slack / 2,
  );
}

/// The flat, ripple-free style every post action shares, with [padding]
/// deciding where the glyph sits in its 48dp target.
ButtonStyle tweetActionButtonStyle(EdgeInsetsGeometry padding) => ButtonStyle(
  overlayColor: const WidgetStatePropertyAll(Colors.transparent),
  splashFactory: NoSplash.splashFactory,
  padding: WidgetStatePropertyAll(padding),
  minimumSize: const WidgetStatePropertyAll(Size.square(kTweetTouchTarget)),
  tapTargetSize: MaterialTapTargetSize.shrinkWrap,
  visualDensity: VisualDensity.standard,
);
