import 'package:flutter/material.dart';

/// A pushed screen fades in quickly, the way X opens a post: the new page is
/// readable almost at once and nothing slides across the screen.
class XtaQuickFadePageTransitionsBuilder extends PageTransitionsBuilder {
  const XtaQuickFadePageTransitionsBuilder();

  static final Animatable<double> _fade = CurveTween(curve: Curves.easeOutCubic);

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
    return FadeTransition(opacity: animation.drive(_fade), child: child);
  }
}
