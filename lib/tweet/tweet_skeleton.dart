import 'package:flutter/material.dart';
import 'package:quax/tweet/tweet_chrome.dart';
import 'package:quax/ui/x_look_theme.dart';

/// Placeholder tiles shown while the first feed page loads.
class TweetFeedSkeleton extends StatelessWidget {
  final int count;

  const TweetFeedSkeleton({super.key, this.count = 6});

  @override
  Widget build(BuildContext context) {
    return ListView.builder(
      physics: const NeverScrollableScrollPhysics(),
      padding: const EdgeInsets.only(top: kTweetSpace1),
      itemCount: count,
      itemBuilder: (context, index) => const TweetSkeletonTile(),
    );
  }
}

/// One placeholder post.
///
/// Also used as the footer while the next page loads: the list grows into
/// something post-shaped instead of a centred spinner that appears, animates
/// and is then swapped out, which is what made the timeline stall visibly.
class TweetSkeletonTile extends StatelessWidget {
  const TweetSkeletonTile({super.key});

  @override
  Widget build(BuildContext context) {
    final tokens = XLookTokens.maybeOf(context);
    final base =
        tokens?.border ?? Theme.of(context).colorScheme.surfaceContainerHighest;
    final highlight =
        tokens?.divider ?? Theme.of(context).colorScheme.surfaceContainerHigh;
    final avatarSize = tokens?.avatarSize ?? kTweetAvatarSize;

    return RepaintBoundary(
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: kTweetHorizontalPadding,
              vertical: kTweetVerticalPadding,
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _Bone(
                  width: avatarSize,
                  height: avatarSize,
                  radius: avatarSize / 2,
                  color: base,
                  highlight: highlight,
                ),
                const SizedBox(width: kTweetSpace3),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _Bone(
                        width: 140,
                        height: 12,
                        color: base,
                        highlight: highlight,
                      ),
                      const SizedBox(height: kTweetSpace2),
                      _Bone(
                        width: double.infinity,
                        height: 12,
                        color: base,
                        highlight: highlight,
                      ),
                      const SizedBox(height: 6),
                      _Bone(
                        width: 220,
                        height: 12,
                        color: base,
                        highlight: highlight,
                      ),
                      const SizedBox(height: kTweetSpace3),
                      _Bone(
                        width: double.infinity,
                        height: 120,
                        radius: tokens?.mediaRadius ?? kTweetMediaRadius,
                        color: base,
                        highlight: highlight,
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          Divider(
            height: 0,
            thickness: kTweetDividerThickness,
            color: tokens?.divider ?? Theme.of(context).dividerColor,
          ),
        ],
      ),
    );
  }
}

class _Bone extends StatefulWidget {
  final double width;
  final double height;
  final double radius;
  final Color color;
  final Color highlight;

  const _Bone({
    required this.width,
    required this.height,
    required this.color,
    required this.highlight,
    this.radius = 4,
  });

  @override
  State<_Bone> createState() => _BoneState();
}

class _BoneState extends State<_Bone> with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1100),
  )..repeat(reverse: true);

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (MediaQuery.disableAnimationsOf(context)) {
      return _buildBone(widget.color);
    }
    return AnimatedBuilder(
      animation: _controller,
      builder: (context, child) {
        final t = Curves.easeInOut.transform(_controller.value);
        return _buildBone(Color.lerp(widget.color, widget.highlight, t)!);
      },
    );
  }

  Widget _buildBone(Color color) => Container(
    width: widget.width == double.infinity ? null : widget.width,
    height: widget.height,
    decoration: BoxDecoration(
      color: color,
      borderRadius: BorderRadius.circular(widget.radius),
    ),
  );
}
