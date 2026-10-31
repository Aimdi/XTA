import 'package:flutter/material.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/tweet_ink_layout.dart';

const double kThreadRailLineWidth = 2;
const double kThreadLevelWidth = 40;

const int kThreadMaxVisualDepth = 2;

/// Where a rail meets the avatars it joins: their start, top and size.
@immutable
class ThreadRailGeometry {
  final double avatarStart;
  final double avatarTop;
  final double avatarSize;

  const ThreadRailGeometry({
    required this.avatarStart,
    required this.avatarTop,
    required this.avatarSize,
  });

  /// The line's leading edge, so the line runs through the avatars' centres.
  double get lineX => avatarStart + avatarSize / 2 - kThreadRailLineWidth / 2;

  double get avatarCenterY => avatarTop + avatarSize / 2;
}

/// Posts in a timeline or a conversation: the 40 dp avatar 16 dp in and
/// 12 dp down (docs/specs/feed-ink.md).
const kPostRailGeometry = ThreadRailGeometry(
  avatarStart: kTweetHorizontalPadding,
  avatarTop: kInkColumnTop,
  avatarSize: kTweetAvatarSize,
);

/// Cards that draw a 48 dp avatar 16 dp in and 10 dp down, as Threads' do.
const kWideAvatarRailGeometry = ThreadRailGeometry(
  avatarStart: 16,
  avatarTop: 10,
  avatarSize: 48,
);

/// Extra left inset for a reply at [depth] in the status-thread tree.
///
/// Depth 0 is the opened tweet. Depth 1 is a direct reply — those sit flush
/// with the opened tweet, the way X lays them out. Only a reply-to-a-reply
/// (depth 2+) steps in, so a conversation does not open with a blank strip
/// down the left of every answer.
double threadNestedIndent(int depth) {
  if (depth <= 1) {
    return 0;
  }
  return (depth - 1) * kThreadLevelWidth;
}

/// Vertical connector segments aligned through the avatar column.
class ThreadRailLines extends StatelessWidget {
  final bool connectTop;
  final bool connectBottom;
  final Color? lineColor;
  final ThreadRailGeometry geometry;

  const ThreadRailLines({
    super.key,
    required this.connectTop,
    required this.connectBottom,
    this.lineColor,
    this.geometry = kPostRailGeometry,
  });

  @override
  Widget build(BuildContext context) {
    if (!connectTop && !connectBottom) {
      return const SizedBox.shrink();
    }
    final color = lineColor ?? Theme.of(context).colorScheme.outlineVariant;
    Widget lineSeg() => SizedBox(
      width: kThreadRailLineWidth,
      child: ColoredBox(color: color),
    );
    return Stack(
      clipBehavior: Clip.none,
      children: [
        if (connectTop)
          PositionedDirectional(
            start: geometry.lineX,
            top: 0,
            height: geometry.avatarCenterY,
            child: lineSeg(),
          ),
        if (connectBottom)
          PositionedDirectional(
            start: geometry.lineX,
            top: geometry.avatarCenterY,
            bottom: 0,
            child: lineSeg(),
          ),
      ],
    );
  }
}

/// The opened post of a thread: the rail from the post above into its
/// avatar, the author beside the avatar, the text across the width under it.
class ThreadRailBody extends StatelessWidget {
  final bool connectTop;
  final bool connectBottom;
  final Widget avatar;
  final String? avatarLabel;
  final Widget header;
  final List<Widget> bodyChildren;
  final VoidCallback onTapProfile;

  const ThreadRailBody({
    super.key,
    required this.connectTop,
    required this.connectBottom,
    required this.avatar,
    required this.header,
    required this.bodyChildren,
    required this.onTapProfile,
    this.avatarLabel,
  });

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        // Positioned, so the rail takes the height the content ends up with
        // rather than deciding it. A Stack whose children are all positioned
        // sizes itself to the biggest its constraints allow, and in a list
        // that is infinity: the whole tile became infinitely tall, and every
        // post after it in the timeline was laid out off the bottom of the
        // world. A thread in the feed emptied the feed under it.
        Positioned.fill(
          child: ThreadRailLines(
            connectTop: connectTop,
            connectBottom: connectBottom,
          ),
        ),
        Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsetsDirectional.only(
                start: kInkAvatarTargetStart,
                top: kInkAvatarTargetTop,
              ),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  TweetAvatarTarget(
                    avatar: avatar,
                    size: kTweetAvatarSize,
                    label: avatarLabel,
                    onTap: onTapProfile,
                  ),
                  Expanded(child: header),
                ],
              ),
            ),
            ...bodyChildren,
          ],
        ),
      ],
    );
  }
}
