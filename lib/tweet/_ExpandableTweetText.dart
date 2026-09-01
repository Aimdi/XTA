import 'package:flutter/material.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/tweet/tweet_chrome.dart';

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

  const ExpandableTweetText({
    super.key,
    required this.textSpans,
    this.onTap,
    this.maxLines = kTweetTextMaxLines,
  });

  @override
  ExpandableTweetTextState createState() => ExpandableTweetTextState();
}

class ExpandableTweetTextState extends State<ExpandableTweetText> {
  bool _isExpanded = false;

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

    final painter = TextPainter(
      text: TextSpan(style: style, children: widget.textSpans),
      textDirection: Directionality.of(context),
      textScaler: MediaQuery.textScalerOf(context),
      // One line past the cap is all it takes to know the text overflows.
      maxLines: maxLines + 1,
    );
    painter.layout(maxWidth: maxWidth);
    final truncated = painter.computeLineMetrics().length > maxLines;
    painter.dispose();

    return truncated;
  }

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final style = DefaultTextStyle.of(context).style;
        final clipped =
            !_isExpanded && _isTruncated(context, constraints.maxWidth, style);

        final text = SelectableText.rich(
          TextSpan(children: widget.textSpans),
          scrollPhysics: const NeverScrollableScrollPhysics(),
          maxLines: clipped ? widget.maxLines : null,
          style: style,
          onTap: widget.onTap,
        );

        return Column(
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
                alignment: Alignment.centerLeft,
                child: Semantics(
                  button: true,
                  child: InkWell(
                    onTap: () {
                      setState(() {
                        _isExpanded = true;
                      });
                    },
                    borderRadius: BorderRadius.circular(kTweetMediaRadius),
                    child: ConstrainedBox(
                      constraints: const BoxConstraints(
                        minHeight: kTweetTouchTarget,
                      ),
                      child: Align(
                        alignment: Alignment.centerLeft,
                        child: Padding(
                          padding: const EdgeInsets.only(right: kTweetSpace3),
                          child: Text(
                            L10n.of(context).clickToShowMore,
                            style: tweetLabelStyle(
                              context,
                            ).copyWith(color: tweetAccentColor(context)),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
          ],
        );
      },
    );
  }
}
