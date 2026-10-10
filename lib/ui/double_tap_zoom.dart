import 'package:extended_image/extended_image.dart';
import 'package:flutter/widgets.dart';
import 'package:xta/ui/motion.dart';

/// Animated double-tap zoom for an [ExtendedImage] in gesture mode: toggles
/// between the two [doubleTapScales] around the tapped point.
mixin DoubleTapZoom<T extends StatefulWidget> on SingleTickerProviderStateMixin<T> {
  Animation<double>? _zoomAnimation;
  VoidCallback? _zoomListener;

  /// Only built on the first double-tap, so an image never zoomed carries no ticker.
  AnimationController? _zoomController;

  List<double> get doubleTapScales => const [1.0, 4.0];

  AnimationController get _zoom =>
      _zoomController ??= AnimationController(duration: xtaMotionDuration(context, kXtaMotionFast), vsync: this);

  void zoomOnDoubleTap(ExtendedImageGestureState state) {
    final position = state.pointerDownPosition;
    final begin = state.gestureDetails!.totalScale;
    final end = begin == doubleTapScales[0] ? doubleTapScales[1] : doubleTapScales[0];

    final previous = _zoomListener;
    if (previous != null) _zoomAnimation?.removeListener(previous);
    _zoom
      ..stop()
      ..reset();

    final animation = _zoomAnimation = _zoom.drive(Tween<double>(begin: begin, end: end));
    void listener() => state.handleDoubleTap(scale: animation.value, doubleTapPosition: position);
    _zoomListener = listener;
    animation.addListener(listener);
    _zoom.forward();
  }

  @override
  void dispose() {
    _zoomController?.dispose();
    super.dispose();
  }
}
