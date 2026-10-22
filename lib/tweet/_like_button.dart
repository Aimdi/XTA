import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/tweet/tweet_action_style.dart';
import 'package:xta/tweet/tweet_footer.dart';
import 'package:xta/ui/heart_burst.dart';
import 'package:xta/ui/motion.dart';

/// The local-like heart. Liking plays X's burst ([HeartBurst]) in the colour
/// of a device like; the tooltip and label still say the like stays here.
class LikeButton extends StatefulWidget {
  final bool isLiked;
  final String label;
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
    if (widget.isLiked || _quietMotion) {
      _controller.value = 0;
    } else {
      _controller.forward(from: 0);
    }
    widget.onPressed?.call();
  }

  bool get _quietMotion {
    if (xtaReduceMotion(context)) return true;
    try {
      final prefs = PrefService.of(context, listen: false);
      return prefs.get(optionCalmMode) == true || prefs.get(optionZenMode) == true;
    } catch (_) {
      return false;
    }
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
