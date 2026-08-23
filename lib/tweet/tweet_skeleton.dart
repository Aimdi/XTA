import 'package:flutter/material.dart';
import 'package:xta/ui/x_look_theme.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';

/// Placeholder tiles shown while the first feed page loads.
///
/// One [AnimationController] drives every bone of every tile. Each bone owning
/// its own controller meant thirty tickers pulsing in lockstep to draw one
/// synchronized shimmer.
///
/// [primary] must stay false when this sits inside another vertical list
/// (PagedListView's first-page slot). A second primary ListView attaches
/// NestedScrollView's inner controller and freezes, then crashes, Following
/// and For you — the same trap empty Reddit hit.
class TweetFeedSkeleton extends StatefulWidget {
  final int count;
  final bool primary;

  const TweetFeedSkeleton({super.key, this.count = 6, this.primary = true});

  @override
  State<TweetFeedSkeleton> createState() => _TweetFeedSkeletonState();
}

class _TweetFeedSkeletonState extends State<TweetFeedSkeleton>
    with SingleTickerProviderStateMixin {
  late final AnimationController _pulse = _skeletonPulse(this);

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    applySkeletonPulse(context, _pulse);
  }

  @override
  void dispose() {
    _pulse.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ListView.builder(
      primary: widget.primary ? null : false,
      shrinkWrap: !widget.primary,
      physics: widget.primary
          ? const AlwaysScrollableScrollPhysics()
          : const NeverScrollableScrollPhysics(),
      padding: const EdgeInsets.only(top: 4),
      itemCount: widget.count,
      itemBuilder: (context, index) => TweetSkeletonTile(pulse: _pulse),
    );
  }
}

AnimationController _skeletonPulse(TickerProvider vsync) =>
    AnimationController(
      vsync: vsync,
      duration: const Duration(milliseconds: 1100),
    );

/// The accessibility preference and the platform's own "remove animations"
/// setting both leave the bones at a flat colour rather than pulsing.
bool skeletonWantsPulse(BuildContext context) =>
    !MediaQuery.disableAnimationsOf(context) &&
    PrefService.of(context, listen: false).get<bool>(optionDisableAnimations) !=
        true;

/// Starts or pins [controller] according to [skeletonWantsPulse].
void applySkeletonPulse(BuildContext context, AnimationController controller) {
  if (skeletonWantsPulse(context)) {
    if (!controller.isAnimating) {
      controller.repeat(reverse: true);
    }
  } else {
    controller.stop();
    controller.value = controller.upperBound;
  }
}

/// One placeholder post.
///
/// Also used as the footer while the next page loads: the list grows into
/// something post-shaped instead of a centred spinner that appears, animates
/// and is then swapped out, which is what made the timeline stall visibly.
/// Standalone it runs its own pulse; in a [TweetFeedSkeleton] it shares the
/// list's.
class TweetSkeletonTile extends StatefulWidget {
  final Animation<double>? pulse;

  const TweetSkeletonTile({super.key, this.pulse});

  @override
  State<TweetSkeletonTile> createState() => _TweetSkeletonTileState();
}

class _TweetSkeletonTileState extends State<TweetSkeletonTile>
    with SingleTickerProviderStateMixin {
  late final AnimationController? _own = widget.pulse == null
      ? _skeletonPulse(this)
      : null;

  Animation<double> get _pulse => widget.pulse ?? _own!;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();

    final own = _own;
    if (own != null) {
      applySkeletonPulse(context, own);
    }
  }

  @override
  void dispose() {
    _own?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final tokens = XLookTokens.maybeOf(context);
    final base =
        tokens?.border ?? Theme.of(context).colorScheme.surfaceContainerHighest;
    final highlight =
        tokens?.divider ?? Theme.of(context).colorScheme.surfaceContainerHigh;
    final avatarSize = tokens?.avatarSize ?? 40;
    final mediaRadius = tokens?.mediaRadius ?? 16;

    return RepaintBoundary(
      child: AnimatedBuilder(
        animation: _pulse,
        builder: (context, child) {
          final color = Color.lerp(
            base,
            highlight,
            Curves.easeInOut.transform(_pulse.value),
          )!;
          return Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _Bone(
                  width: avatarSize,
                  height: avatarSize,
                  radius: avatarSize / 2,
                  color: color,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _Bone(width: 140, height: 12, color: color),
                      const SizedBox(height: 8),
                      _Bone(width: double.infinity, height: 12, color: color),
                      const SizedBox(height: 6),
                      _Bone(width: 220, height: 12, color: color),
                      const SizedBox(height: 12),
                      _Bone(
                        width: double.infinity,
                        height: 120,
                        radius: mediaRadius,
                        color: color,
                      ),
                    ],
                  ),
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}

class _Bone extends StatelessWidget {
  final double width;
  final double height;
  final double radius;
  final Color color;

  const _Bone({
    required this.width,
    required this.height,
    required this.color,
    this.radius = 4,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: width == double.infinity ? null : width,
      height: height,
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(radius),
      ),
    );
  }
}
