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
