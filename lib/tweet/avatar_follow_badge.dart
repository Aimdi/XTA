import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/motion.dart';

/// Diameter of the follow badge on an avatar of [avatarSize], ring included.
double avatarFollowBadgeSize(double avatarSize) =>
    (avatarSize * .42).clamp(14.0, 20.0);

/// Threads' follow badge: a small disc sitting on the avatar's rim at the
/// lower-right, where the circle crosses the 45° diagonal. One tap follows;
/// the plus turns into a check and the badge then shrinks away.
class AvatarFollowBadge extends StatelessWidget {
  final Widget avatar;
  final double avatarSize;
  final bool followed;
  final VoidCallback onFollow;
  final VoidCallback onMore;

  /// Large enough to hit reliably without taking the whole avatar from the profile tap.
  static const double target = 36;

  const AvatarFollowBadge({
    super.key,
    required this.avatar,
    required this.avatarSize,
    required this.followed,
    required this.onFollow,
    required this.onMore,
  });

  @override
  Widget build(BuildContext context) {
    final badge = avatarFollowBadgeSize(avatarSize);
    final radius = avatarSize / 2;
    final center = radius + radius * math.cos(math.pi / 4);
    final duration = xtaMotionDuration(context, kXtaMotionNavigation * 2);
    return SizedBox.square(
      dimension: avatarSize,
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          avatar,
          Positioned(
            left: center - target / 2,
            top: center - target / 2,
            width: target,
            height: target,
            child: IgnorePointer(
              ignoring: followed,
              child: Semantics(
                button: true,
                label: L10n.of(context).subscribe,
                onLongPressHint: L10n.of(context).add_to_group,
                excludeSemantics: true,
                child: AnimatedScale(
                  scale: followed ? 0 : 1,
                  duration: duration,
                  // The first half holds the check so the follow reads as done before it leaves.
                  curve: const Interval(.5, 1, curve: Curves.easeInBack),
                  child: Material(
                    type: MaterialType.transparency,
                    child: InkResponse(
                      key: const ValueKey('avatar-follow-badge'),
                      radius: target / 2,
                      onTap: () {
                        HapticFeedback.lightImpact();
                        onFollow();
                      },
                      onLongPress: () {
                        HapticFeedback.selectionClick();
                        onMore();
                      },
                      child: Center(
                        child: _Disc(size: badge, followed: followed),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _Disc extends StatelessWidget {
  final double size;
  final bool followed;
  const _Disc({required this.size, required this.followed});

  @override
  Widget build(BuildContext context) {
    final ring = size >= 18 ? 2.0 : 1.5;
    final surface = tweetSurfaceColor(context);
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: tweetPrimaryColor(context),
        shape: BoxShape.circle,
        border: Border.all(color: surface, width: ring),
      ),
      child: AnimatedSwitcher(
        duration: xtaMotionDuration(context, kXtaMotionFast),
        transitionBuilder: (child, animation) =>
            ScaleTransition(scale: animation, child: child),
        child: Icon(
          followed ? Icons.check_rounded : Icons.add_rounded,
          key: ValueKey(followed),
          size: size - ring * 2 - 2,
          color: surface,
        ),
      ),
    );
  }
}
