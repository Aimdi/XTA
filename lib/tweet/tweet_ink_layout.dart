/// A post in a timeline as canvas concept A ("Ink") draws it, which is how X
/// lays one out: the avatar in a column of its own, and the author line, the
/// text, media and actions in a second column beside it.
/// See docs/specs/feed-ink.md.
library;

import 'package:flutter/material.dart';
import 'package:xta/tweet/tweet_chrome.dart';

/// Where the avatar's 48 dp target starts, so the 40 dp avatar centred in it
/// sits 16 dp from the edge.
const double kInkAvatarTargetStart = 12;

/// The target's top, so the avatar sits 12 dp down, level with the author line.
const double kInkAvatarTargetTop = 8;

/// Where the second column starts: the author line, text, media, footer.
const double kInkColumnStart = 68;

/// Where the author line starts, from the top of the post.
const double kInkColumnTop = 12;

/// The indent that puts a post's pieces at [kInkColumnStart]: each already
/// pads itself by [kTweetHorizontalPadding].
const double kInkBodyIndent = kInkColumnStart - kTweetHorizontalPadding;

/// The space between the author line and the text under it.
const double kInkTextGap = 2;

/// The avatar in its 48 dp target, which opens the author.
class TweetAvatarTarget extends StatelessWidget {
  final Widget avatar;
  final double size;
  final String? label;
  final VoidCallback onTap;

  /// The ripple's shape: round for a person, the picture's own for a
  /// community.
  final ShapeBorder shape;

  const TweetAvatarTarget({
    super.key,
    required this.avatar,
    required this.size,
    required this.onTap,
    this.label,
    this.shape = const CircleBorder(),
  });

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      label: label,
      excludeSemantics: true,
      onTap: onTap,
      child: InkResponse(
        onTap: onTap,
        radius: kTweetTouchTarget / 2,
        containedInkWell: true,
        customBorder: shape,
        child: SizedBox.square(
          dimension: kTweetTouchTarget,
          child: Center(
            child: SizedBox.square(dimension: size, child: avatar),
          ),
        ),
      ),
    );
  }
}

/// The two columns. [body]'s pieces keep their own 16 dp padding, as they
/// have in every other layout; [trailing] (translate, the ⋯ menu) sits at
/// the end of the author line, each a 48 dp target with its glyph at the top,
/// reaching down over the text as X's ⋯ does. [rail] is drawn behind, for a
/// thread.
class TweetInkLayout extends StatelessWidget {
  final Widget avatar;
  final Widget authorLine;
  final List<Widget> trailing;
  final List<Widget> body;
  final Widget? rail;

  const TweetInkLayout({
    super.key,
    required this.avatar,
    required this.authorLine,
    required this.body,
    this.trailing = const [],
    this.rail,
  });

  @override
  Widget build(BuildContext context) {
    final rail = this.rail;
    return Stack(
      children: [
        // Positioned, so the rail takes the post's height rather than
        // deciding it: in a list an unpositioned one makes the post endless.
        if (rail != null) Positioned.fill(child: rail),
        PositionedDirectional(start: kInkAvatarTargetStart, top: kInkAvatarTargetTop, child: avatar),
        Padding(
          padding: const EdgeInsetsDirectional.only(start: kInkBodyIndent),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Padding(
                padding: EdgeInsetsDirectional.fromSTEB(
                  kTweetHorizontalPadding,
                  kInkColumnTop,
                  trailing.isEmpty ? kTweetHorizontalPadding : trailing.length * kTweetTouchTarget,
                  0,
                ),
                child: authorLine,
              ),
              ...body,
            ],
          ),
        ),
        if (trailing.isNotEmpty)
          PositionedDirectional(
            top: kInkColumnTop,
            end: 0,
            child: Row(mainAxisSize: MainAxisSize.min, children: trailing),
          ),
      ],
    );
  }
}
