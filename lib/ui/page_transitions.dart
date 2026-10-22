import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// A pushed screen fades in while settling from a little larger, as X opens a
/// post or a profile: readable almost at once, and nothing slides across the
/// screen. Android's back gesture drives the page instead: it shrinks and
/// follows the finger with the page behind showing around it, as X's
/// predictive back does.
class XtaPageTransitionsBuilder extends PageTransitionsBuilder {
  const XtaPageTransitionsBuilder();

  @override
  Duration get transitionDuration => const Duration(milliseconds: 200);

  @override
  Duration get reverseTransitionDuration => const Duration(milliseconds: 160);

  @override
  Widget buildTransitions<T>(
    PageRoute<T> route,
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
    Widget child,
  ) {
    if (MediaQuery.disableAnimationsOf(context)) {
      return child;
    }
    return _BackGestureDetector(
      route: route,
      builder: (context, gesture) => route.popGestureInProgress && gesture != null
          ? XtaBackGestureTransition(animation: animation, gesture: gesture, child: child)
          : XtaPushTransition(animation: animation, child: child),
    );
  }
}

/// How large a pushed page starts before settling to its size.
const double kXtaPushStartScale = 1.04;

/// The fade and settle of a page coming and going.
class XtaPushTransition extends StatelessWidget {
  final Animation<double> animation;
  final Widget child;

  const XtaPushTransition({super.key, required this.animation, required this.child});

  static final Animatable<double> _fade = CurveTween(curve: Curves.easeOutCubic);
  static final Animatable<double> _scale = Tween<double>(
    begin: kXtaPushStartScale,
    end: 1,
  ).chain(CurveTween(curve: Curves.easeOutCubic));

  @override
  Widget build(BuildContext context) {
    return FadeTransition(
      opacity: animation.drive(_fade),
      child: ScaleTransition(scale: animation.drive(_scale), child: child),
    );
  }
}

/// A back gesture under way: how far the finger has gone, from which edge,
/// and whether Android has decided the page goes.
typedef XtaBackGesture = ({double progress, bool fromLeft, bool committed});

/// How small the page gets at the end of a back gesture.
const double kXtaBackGestureMinScale = 0.9;

/// The page during a back gesture: shrinking and moving towards the far edge
/// with its corners rounding as the finger goes, back to whole when the
/// gesture is cancelled. Once it is committed, it fades from where the finger
/// left it.
///
/// While the finger moves, the route's animation is the gesture: it runs from
/// 1 to 0 as the gesture goes from nothing to all the way. A committed pop
/// restarts it from 1, which is then only the fade.
class XtaBackGestureTransition extends StatelessWidget {
  final Animation<double> animation;
  final XtaBackGesture gesture;
  final Widget child;

  const XtaBackGestureTransition({super.key, required this.animation, required this.gesture, required this.child});

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: animation,
      child: child,
      builder: (context, child) {
        final progress = gesture.committed ? gesture.progress : 1 - animation.value;
        final width = MediaQuery.sizeOf(context).width;
        final shift = (width / 20 - 8).clamp(0.0, double.infinity) * progress;
        return Opacity(
          opacity: gesture.committed ? animation.value : 1,
          child: Transform.translate(
            offset: Offset(gesture.fromLeft ? shift : -shift, 0),
            child: Transform.scale(
              scale: 1 - (1 - kXtaBackGestureMinScale) * progress,
              child: ClipRRect(borderRadius: BorderRadius.circular(32 * progress), child: child),
            ),
          ),
        );
      },
    );
  }
}

/// Hands Android's back gesture to [route] while it is the page on top, and
/// tells [builder] where the gesture is.
class _BackGestureDetector extends StatefulWidget {
  final PageRoute<dynamic> route;
  final Widget Function(BuildContext context, XtaBackGesture? gesture) builder;

  const _BackGestureDetector({required this.route, required this.builder});

  @override
  State<_BackGestureDetector> createState() => _BackGestureDetectorState();
}

class _BackGestureDetectorState extends State<_BackGestureDetector> with WidgetsBindingObserver {
  XtaBackGesture? _gesture;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  bool handleStartBackGesture(PredictiveBackEvent backEvent) {
    final route = widget.route;
    if (backEvent.isButtonEvent || !route.isCurrent || !route.popGestureEnabled) {
      return false;
    }
    route.handleStartBackGesture(progress: 1 - backEvent.progress);
    _show((progress: backEvent.progress, fromLeft: backEvent.swipeEdge == SwipeEdge.left, committed: false));
    return true;
  }

  @override
  void handleUpdateBackGestureProgress(PredictiveBackEvent backEvent) {
    widget.route.handleUpdateBackGestureProgress(progress: 1 - backEvent.progress);
    final gesture = _gesture;
    if (gesture != null) {
      _show((progress: backEvent.progress, fromLeft: gesture.fromLeft, committed: false));
    }
  }

  @override
  void handleCancelBackGesture() {
    widget.route.handleCancelBackGesture();
  }

  @override
  void handleCommitBackGesture() {
    final gesture = _gesture;
    if (gesture != null) {
      _show((progress: 1 - widget.route.animation!.value, fromLeft: gesture.fromLeft, committed: true));
    }
    widget.route.handleCommitBackGesture();
  }

  void _show(XtaBackGesture gesture) {
    if (mounted) {
      setState(() => _gesture = gesture);
    }
  }

  @override
  Widget build(BuildContext context) => widget.builder(context, _gesture);
}
