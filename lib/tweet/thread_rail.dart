import 'package:flutter/material.dart';

/// Shared thread-rail geometry (feed thread bodies and status reply nesting).
const double kThreadRailLeft = 16;
const double kThreadRailTopGap = 10;
const double kThreadRailAvatarSize = 48;
const double kThreadRailLineWidth = 2;
const double kThreadLevelWidth = 40;

const int kThreadMaxVisualDepth = 2;

double get threadRailLineX =>
    kThreadRailLeft +
    kThreadRailAvatarSize / 2 -
    kThreadRailLineWidth / 2;

double get threadRailAvatarCenterY =>
    kThreadRailTopGap + kThreadRailAvatarSize / 2;

double get threadRailBodyIndent => kThreadRailLeft + kThreadRailAvatarSize;

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

  const ThreadRailLines({
    super.key,
    required this.connectTop,
    required this.connectBottom,
    this.lineColor,
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
            start: threadRailLineX,
            top: 0,
            height: threadRailAvatarCenterY,
            child: lineSeg(),
          ),
        if (connectBottom)
          PositionedDirectional(
            start: threadRailLineX,
            top: threadRailAvatarCenterY,
            bottom: 0,
            child: lineSeg(),
          ),
      ],
    );
  }
}

/// Feed-style thread body: avatar column, optional rail, header and body.
class ThreadRailBody extends StatelessWidget {
  final bool connectTop;
  final bool connectBottom;
  final bool indentBody;
  final Widget avatar;
  final Widget header;
  final List<Widget> bodyChildren;
  final VoidCallback onTapProfile;

  const ThreadRailBody({
    super.key,
    required this.connectTop,
    required this.connectBottom,
    required this.indentBody,
    required this.avatar,
    required this.header,
    required this.bodyChildren,
    required this.onTapProfile,
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
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const SizedBox(width: kThreadRailLeft),
                Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const SizedBox(height: kThreadRailTopGap),
                    SizedBox(
                      width: kThreadRailAvatarSize,
                      height: kThreadRailAvatarSize,
                      child: Semantics(
                        button: true,
                        child: InkResponse(
                          onTap: onTapProfile,
                          radius: kThreadRailAvatarSize / 2,
                          containedInkWell: true,
                          customBorder: const CircleBorder(),
                          child: avatar,
                        ),
                      ),
                    ),
                  ],
                ),
                Expanded(child: header),
              ],
            ),
            if (indentBody)
              Padding(
                padding: EdgeInsetsDirectional.only(
                  start: threadRailBodyIndent,
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: bodyChildren,
                ),
              ),
            if (!indentBody) ...bodyChildren,
          ],
        ),
      ],
    );
  }
}
