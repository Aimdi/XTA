import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';

const Duration kXtaMotionFast = Duration(milliseconds: 120);
const Duration kXtaMotionStandard = Duration(milliseconds: 180);
const Duration kXtaMotionNavigation = Duration(milliseconds: 250);

/// Resolves both Android's reduced-motion signal and XTA's own accessibility
/// preference. The preference fallback keeps isolated routes and widget tests
/// correct even when they are mounted without the root app MediaQuery.
bool xtaReduceMotion(BuildContext context) {
  if (MediaQuery.disableAnimationsOf(context)) return true;
  final prefs = context.findAncestorWidgetOfExactType<PrefService>()?.service;
  return prefs?.get<bool>(optionDisableAnimations) == true;
}

Duration xtaMotionDuration(BuildContext context, Duration duration) =>
    xtaReduceMotion(context) ? Duration.zero : duration;

/// A quiet entrance for a complete loading/error/content state. It deliberately
/// fades only; translating large reader surfaces makes them feel unstable.
class XtaFadeIn extends StatelessWidget {
  final Widget child;
  final Duration duration;

  const XtaFadeIn({
    super.key,
    required this.child,
    this.duration = kXtaMotionStandard,
  });

  @override
  Widget build(BuildContext context) {
    return TweenAnimationBuilder<double>(
      tween: Tween<double>(begin: 0, end: 1),
      duration: xtaMotionDuration(context, duration),
      curve: Curves.easeOutCubic,
      builder: (_, opacity, child) => Opacity(opacity: opacity, child: child),
      child: child,
    );
  }
}

/// [child] fading in as it first appears when [arriving], as content does
/// where its placeholders stood; otherwise drawn as it is. [arriving] is read
/// once, as it first appears, and only an arriving child runs an animation: a
/// list can wrap every tile and fade only its first ones.
class XtaArrival extends StatefulWidget {
  final bool arriving;
  final Widget child;

  const XtaArrival({super.key, required this.arriving, required this.child});

  @override
  State<XtaArrival> createState() => _XtaArrivalState();
}

class _XtaArrivalState extends State<XtaArrival>
    with SingleTickerProviderStateMixin {
  AnimationController? _fade;
  Animation<double>? _opacity;
  bool _decided = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_decided) return;
    _decided = true;
    if (!widget.arriving || xtaReduceMotion(context)) return;
    final fade = _fade = AnimationController(
      vsync: this,
      duration: kXtaMotionStandard,
    )..forward();
    _opacity = fade.drive(CurveTween(curve: Curves.easeOutCubic));
  }

  @override
  void dispose() {
    _fade?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    // Decided before the first build and never again, so the child keeps its
    // place in the tree, and its state, for good.
    final opacity = _opacity;
    if (opacity == null) return widget.child;
    return FadeTransition(opacity: opacity, child: widget.child);
  }
}

/// The first frame of something that builds its children as they are needed,
/// such as a list's first tiles: [arriving] is true for the children built in
/// it, and false for every one built after, as the reader scrolls.
class XtaArrivalWindow {
  bool _open = true;
  bool _closing = false;

  bool get arriving {
    if (_open && !_closing) {
      _closing = true;
      WidgetsBinding.instance.addPostFrameCallback((_) => _open = false);
    }
    return _open;
  }
}

/// Cross-fades compact state changes. Optional size animation is reserved for
/// small chrome such as a filter strip, never whole feeds or paginated pages.
class XtaAnimatedSwitcher extends StatelessWidget {
  final Widget child;
  final Duration duration;
  final bool animateSize;
  final AlignmentGeometry alignment;

  const XtaAnimatedSwitcher({
    super.key,
    required this.child,
    this.duration = kXtaMotionStandard,
    this.animateSize = false,
    this.alignment = AlignmentDirectional.topStart,
  });

  @override
  Widget build(BuildContext context) {
    final resolved = xtaMotionDuration(context, duration);
    final switcher = AnimatedSwitcher(
      duration: resolved,
      reverseDuration: resolved,
      switchInCurve: Curves.easeOutCubic,
      switchOutCurve: Curves.easeInCubic,
      transitionBuilder: (child, animation) => FadeTransition(
        opacity: animation,
        child: child,
      ),
      child: child,
    );
    if (!animateSize) return switcher;
    return AnimatedSize(
      duration: resolved,
      curve: Curves.easeOutCubic,
      alignment: alignment,
      child: switcher,
    );
  }
}

/// [child] in an [AnimatedSwitcher] transition, which can no longer be
/// tapped, focused or read out once [animation] runs it out.
class XtaLeaving extends StatelessWidget {
  final Animation<double> animation;
  final Widget child;

  const XtaLeaving({super.key, required this.animation, required this.child});

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: animation,
      builder: (context, child) {
        final leaving = animation.status == AnimationStatus.reverse;
        return IgnorePointer(
          ignoring: leaving,
          child: ExcludeFocus(
            excluding: leaving,
            child: ExcludeSemantics(excluding: leaving, child: child!),
          ),
        );
      },
      child: child,
    );
  }
}

/// How small a floating button starts as it grows in, and ends as it shrinks
/// away.
const double kXtaGrowFrom = 0.6;

/// Shows [child], a floating button, while [visible]: it grows in from
/// [kXtaGrowFrom] as it fades in, and shrinks away as it fades out, instead
/// of popping. Hidden, it is not there at all, so nothing can tap or focus it
/// and a snack bar does not rise above it.
class XtaGrowIn extends StatelessWidget {
  final bool visible;
  final Widget child;

  const XtaGrowIn({super.key, required this.visible, required this.child});

  static final _grow = Tween<double>(begin: kXtaGrowFrom, end: 1);

  static Widget _transition(Widget child, Animation<double> animation) {
    return XtaLeaving(
      animation: animation,
      child: ScaleTransition(
        scale: animation.drive(_grow),
        child: FadeTransition(opacity: animation, child: child),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedSwitcher(
      duration: xtaMotionDuration(context, kXtaMotionStandard),
      reverseDuration: xtaMotionDuration(context, kXtaMotionFast),
      switchInCurve: Curves.easeOutCubic,
      switchOutCurve: Curves.easeInCubic,
      transitionBuilder: _transition,
      child: visible
          ? KeyedSubtree(key: const ValueKey('shown'), child: child)
          : const SizedBox.shrink(key: ValueKey('hidden')),
    );
  }
}
