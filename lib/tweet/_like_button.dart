import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/tweet/tweet_footer.dart';
import 'package:xta/ui/motion.dart';

/// The local-like heart uses one short confirmation pulse. It stays quiet in a
/// repeated timeline and never resembles a server-side X write celebration.
class LikeButton extends StatefulWidget {
  final bool isLiked;
  final String label;
  final Color? color;
  final VoidCallback onPressed;
  final String? tooltip;

  const LikeButton({
    super.key,
    required this.isLiked,
    required this.label,
    required this.color,
    required this.onPressed,
    this.tooltip,
  });

  @override
  State<LikeButton> createState() => _LikeButtonState();
}

class _LikeButtonState extends State<LikeButton>
    with SingleTickerProviderStateMixin {
  static const double _iconSize = 20;

  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: kXtaMotionStandard,
  );

  late final Animation<double> _scale = TweenSequence<double>([
    TweenSequenceItem(
      tween: Tween(
        begin: 1.0,
        end: 1.16,
      ).chain(CurveTween(curve: Curves.easeOutCubic)),
      weight: 45,
    ),
    TweenSequenceItem(
      tween: Tween(
        begin: 1.16,
        end: 1.0,
      ).chain(CurveTween(curve: Curves.easeInOutCubic)),
      weight: 55,
    ),
  ]).animate(_controller);

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _handleTap() {
    if (!widget.isLiked && !_quietMotion) {
      _controller.forward(from: 0);
    }
    widget.onPressed();
  }

  bool get _quietMotion {
    if (xtaReduceMotion(context)) return true;
    try {
      final prefs = PrefService.of(context, listen: false);
      return prefs.get(optionCalmMode) == true ||
          prefs.get(optionZenMode) == true;
    } catch (_) {
      return false;
    }
  }

  @override
  Widget build(BuildContext context) {
    final button = TextButton.icon(
      onPressed: _handleTap,
      style: footerButtonStyle,
      icon: SizedBox(
        width: _iconSize,
        height: _iconSize,
        child: ScaleTransition(
          scale: _scale,
          child: Icon(
            widget.isLiked ? Icons.favorite : Icons.favorite_border,
            size: _iconSize,
            color: widget.color,
          ),
        ),
      ),
      label: Text(
        widget.label,
        style: TextStyle(color: widget.color, fontSize: 14),
      ),
    );

    final tooltip = widget.tooltip;
    if (tooltip == null || tooltip.isEmpty) {
      return button;
    }
    final count = widget.label.trim();
    return Semantics(
      button: true,
      label: count.isEmpty ? tooltip : '$tooltip, $count',
      child: ExcludeSemantics(child: button),
    );
  }
}
