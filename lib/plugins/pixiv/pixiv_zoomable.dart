import 'package:flutter/material.dart';
import 'package:xta/ui/motion.dart';

const pixivDoubleTapScale = 2.5;

/// The transform that magnifies by [scale] while keeping [focal] in place.
Matrix4 pixivZoomAt(Offset focal, double scale) => Matrix4.identity()
  ..translateByDouble(-focal.dx * (scale - 1), -focal.dy * (scale - 1), 0, 1)
  ..scaleByDouble(scale, scale, 1, 1);

/// Pinch to zoom, and double-tap to zoom in where tapped and back out.
class PixivZoomable extends StatefulWidget {
  final Widget child;
  final VoidCallback? onTap;
  final VoidCallback? onLongPress;
  final TransformationController? controller;

  /// Off where the child has its own buttons: a double-tap detector delays every tap below it.
  final bool doubleTapZoom;

  const PixivZoomable({
    super.key,
    required this.child,
    this.onTap,
    this.onLongPress,
    this.controller,
    this.doubleTapZoom = true,
  });

  @override
  State<PixivZoomable> createState() => _PixivZoomableState();
}

class _PixivZoomableState extends State<PixivZoomable> with SingleTickerProviderStateMixin {
  late final _transform = widget.controller ?? TransformationController();
  AnimationController? _animationController;
  Matrix4Tween? _tween;
  var _focal = Offset.zero;

  /// Only built on the first double-tap, so pages never zoomed carry no ticker.
  AnimationController get _animation =>
      _animationController ??= AnimationController(vsync: this, duration: kXtaMotionStandard)..addListener(_step);

  @override
  void dispose() {
    _animationController?.dispose();
    if (widget.controller == null) _transform.dispose();
    super.dispose();
  }

  void _step() {
    final tween = _tween;
    if (tween != null) _transform.value = tween.transform(Curves.easeOutCubic.transform(_animation.value));
  }

  void _toggleZoom() {
    final zoomed = _transform.value.getMaxScaleOnAxis() > 1.01;
    final end = zoomed ? Matrix4.identity() : pixivZoomAt(_focal, pixivDoubleTapScale);
    if (xtaReduceMotion(context)) {
      _transform.value = end;
      return;
    }
    _tween = Matrix4Tween(begin: _transform.value.clone(), end: end);
    _animation.forward(from: 0);
  }

  @override
  Widget build(BuildContext context) => GestureDetector(
    onTap: widget.onTap,
    onLongPress: widget.onLongPress,
    onDoubleTapDown: widget.doubleTapZoom ? (details) => _focal = details.localPosition : null,
    onDoubleTap: widget.doubleTapZoom ? _toggleZoom : null,
    child: InteractiveViewer(transformationController: _transform, minScale: 1, maxScale: 4, child: widget.child),
  );
}
