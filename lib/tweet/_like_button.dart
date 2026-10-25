import 'package:flutter/material.dart';
import 'package:xta/tweet/tweet_action_style.dart';
import 'package:xta/tweet/tweet_footer.dart';
import 'package:xta/ui/action_feedback.dart';
import 'package:xta/ui/heart_burst.dart';
import 'package:xta/ui/motion.dart';

/// The local-like heart. Liking plays X's burst ([HeartBurst]) in the colour
/// of a device like; the tooltip and label still say the like stays here.
class LikeButton extends StatefulWidget {
  final bool isLiked;
  final String label;

  /// X's like count, which [label] shows; a like here never changes it.
  final int? count;

  /// What the count showed before this button was built, which it rolls from.
  final FooterCountFrom? from;
  final Color? color;

  /// The heart's colour once liked, which the burst plays in. The theme accent
  /// when not given.
  final Color? activeColor;
  final VoidCallback? onPressed;
  final String? tooltip;

  const LikeButton({
    super.key,
    required this.isLiked,
    required this.label,
    this.count,
    this.from,
    required this.color,
    required this.onPressed,
    this.activeColor,
    this.tooltip,
  });

  @override
  State<LikeButton> createState() => _LikeButtonState();
}

class _LikeButtonState extends State<LikeButton> with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(vsync: this, duration: kHeartBurstDuration);

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _handleTap() {
    if (widget.isLiked || xtaReduceMotion(context) || quietActions(context)) {
      _controller.value = 0;
    } else {
      _controller.forward(from: 0);
    }
    if (!widget.isLiked) actionTap(context);
    widget.onPressed?.call();
  }

  @override
  Widget build(BuildContext context) {
    final active = widget.activeColor ?? Theme.of(context).colorScheme.primary;
    final button = TextButton(
      onPressed: widget.onPressed == null ? null : _handleTap,
      style: footerButtonStyle,
      child: footerCountContent(
        HeartBurst(
          progress: _controller,
          color: active,
          size: kTweetActionGlyphSize,
          filledAtRest: widget.isLiked,
          outline: Icon(Icons.favorite_border, size: kTweetActionGlyphSize, color: widget.color),
          filled: Icon(Icons.favorite, size: kTweetActionGlyphSize, color: widget.isLiked ? widget.color : active),
        ),
        widget.label,
        widget.color,
        count: widget.count,
        from: widget.from,
      ),
    );

    final tooltip = widget.tooltip;
    if (tooltip == null || tooltip.isEmpty) {
      return button;
    }
    final count = widget.label.trim();
    return Semantics(
      button: true,
      enabled: widget.onPressed != null,
      label: count.isEmpty ? tooltip : '$tooltip, $count',
      child: ExcludeSemantics(child: button),
    );
  }
}
