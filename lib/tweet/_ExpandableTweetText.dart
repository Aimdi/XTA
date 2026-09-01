import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/motion.dart';

/// The line cap a post has to break before it is worth collapsing.
///
/// A classic post is 280 characters, which already wraps past eight lines in
/// the width a post card leaves for text — so the old cap of eight collapsed
/// ordinary posts, and "show more" appeared on almost everything. Sixteen
/// reaches roughly twice that, which is long-form territory.
const int kTweetTextMaxLines = 16;

class ExpandableTweetText extends StatefulWidget {
  final List<InlineSpan> textSpans;
  final VoidCallback? onTap;
  final int? maxLines;

  /// Whether the text can be selected. Off in the feed: SelectableText builds
  /// the whole editable-text stack — focus node, selection overlay, caret
  /// ticker — per tile, which is an order of magnitude more work than Text
  /// and is only ever used on a post the reader has actually opened.
  final bool selectable;

  const ExpandableTweetText({
    super.key,
    required this.textSpans,
    this.onTap,
    this.maxLines = kTweetTextMaxLines,
    this.selectable = false,
  });

  @override
  ExpandableTweetTextState createState() => ExpandableTweetTextState();
}

class ExpandableTweetTextState extends State<ExpandableTweetText> {
  // The truncation answer, remembered per (spans, width, scale): laying the
  // paragraph out to count its lines costs as much as painting it, and
  // LayoutBuilder re-runs this on every relayout of the tile.
  List<InlineSpan>? _memoSpans;
  double? _memoWidth;
  TextScaler? _memoScaler;
  bool _memoTruncated = false;

  /// Whether the text needs more than [ExpandableTweetText.maxLines] at the
  /// width it is actually painted at, in the style it is actually painted in.
  ///
  /// Measuring against the screen width mis-counts lines: tweet text sits
  /// inside horizontal padding, and a thread body is indented further still.
  /// Measuring without the rendered style compounds it, because the spans
  /// inherit their size from [DefaultTextStyle] rather than carrying it.
  bool _isTruncated(BuildContext context, double maxWidth, TextStyle style) {
    final maxLines = widget.maxLines;
    if (maxLines == null || !maxWidth.isFinite || maxWidth <= 0) {
      return false;
    }

    final scaler = MediaQuery.textScalerOf(context);
    if (identical(_memoSpans, widget.textSpans) &&
        _memoWidth == maxWidth &&
        _memoScaler == scaler) {
      return _memoTruncated;
    }

    final painter = TextPainter(
      text: TextSpan(style: style, children: widget.textSpans),
      textDirection: Directionality.of(context),
      textScaler: scaler,
      // One line past the cap is all it takes to know the text overflows.
      maxLines: maxLines + 1,
    );

    // A WidgetSpan in the text — an alt-text badge, an inline chip — has no
    // intrinsic size a bare TextPainter could know; laying out without
    // declaring one is a null-check crash inside WidgetSpan.build, which took
    // the whole timeline down the first time a post carried one. For counting
    // lines, a glyph-sized box is the right stand-in: these spans sit inline
    // and scale with the text around them.
    final placeholders = _placeholderCount(widget.textSpans);
    if (placeholders > 0) {
      final side = scaler.scale(style.fontSize ?? 14);
      painter.setPlaceholderDimensions(
        List.filled(
          placeholders,
          PlaceholderDimensions(
            size: Size.square(side),
            alignment: PlaceholderAlignment.middle,
          ),
        ),
      );
    }

    painter.layout(maxWidth: maxWidth);
    final truncated = painter.computeLineMetrics().length > maxLines;
    painter.dispose();

    _memoSpans = widget.textSpans;
    _memoWidth = maxWidth;
    _memoScaler = scaler;
    _memoTruncated = truncated;
    return truncated;
  }

  /// How many spans in the tree want a placeholder box, however deep they sit.
  static int _placeholderCount(List<InlineSpan> spans) {
    var count = 0;
    for (final span in spans) {
      span.visitChildren((child) {
        if (child is PlaceholderSpan) {
          count++;
        }
        return true;
      });
    }
    return count;
  }

  bool _isExpanded = false;

  @override
  void didUpdateWidget(ExpandableTweetText oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!identical(oldWidget.textSpans, widget.textSpans)) {
      _memoSpans = null;
      _memoWidth = null;
      _memoScaler = null;
      _memoTruncated = false;
      _isExpanded = false;
    }
  }

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final style = DefaultTextStyle.of(context).style;
        final expandable = _isTruncated(
          context,
          constraints.maxWidth,
          style,
        );
        final clipped = !_isExpanded && expandable;

        // While the fade is up, text taps must not navigate: the faded bottom
        // looks like part of the "show more" affordance, and on a nested quote
        // that open-on-tap used to steal the expand gesture.
        final openOnTap = clipped ? null : widget.onTap;

        final Widget text = widget.selectable
            ? SelectableText.rich(
                TextSpan(children: widget.textSpans),
                scrollPhysics: const NeverScrollableScrollPhysics(),
                maxLines: clipped ? widget.maxLines : null,
                style: style,
                onTap: openOnTap,
              )
            : GestureDetector(
                behavior: HitTestBehavior.translucent,
                onTap: openOnTap,
                child: Text.rich(
                  TextSpan(children: widget.textSpans),
                  maxLines: clipped ? widget.maxLines : null,
                  overflow: clipped ? TextOverflow.clip : null,
                  style: style,
                ),
              );

        final content = Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (clipped)
              ShaderMask(
                shaderCallback: (bounds) {
                  return const LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [
                      Colors.black,
                      Colors.black,
                      Colors.black,
                      Colors.transparent,
                    ],
                    stops: [0.0, 0.6, 0.8, 1.0],
                  ).createShader(bounds);
                },
                blendMode: BlendMode.dstIn,
                child: text,
              )
            else
              text,
            if (clipped)
              Align(
                alignment: AlignmentDirectional.centerStart,
                child: Semantics(
                  button: true,
                  child: InkWell(
                    onTap: () {
                      setState(() {
                        _isExpanded = true;
                      });
                    },
                    child: ConstrainedBox(
                      constraints: const BoxConstraints(
                        minHeight: kTweetTouchTarget,
                      ),
                      child: Padding(
                        padding: const EdgeInsetsDirectional.only(
                          start: 2,
                          end: 12,
                          top: 8,
                          bottom: 8,
                        ),
                        child: Align(
                          alignment: AlignmentDirectional.centerStart,
                          child: Text(
                            L10n.of(context).clickToShowMore,
                            style: TextStyle(
                              color: tweetReadableAccentColor(context),
                              fontWeight: FontWeight.w700,
                              fontSize: 14,
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
          ],
        );

        // Most timeline posts are short. Avoid giving every tile an implicit
        // animation controller for a transition it can never perform.
        if (!expandable) return content;
        return AnimatedSize(
          duration: xtaMotionDuration(context, kXtaMotionFast),
          curve: Curves.easeOutCubic,
          alignment: AlignmentDirectional.topStart,
          clipBehavior: Clip.hardEdge,
          child: content,
        );
      },
    );
  }
}
