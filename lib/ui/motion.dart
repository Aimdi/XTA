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
      // Read out from the start: it is on screen, only not yet opaque.
      builder: (_, opacity, child) =>
          Opacity(opacity: opacity, alwaysIncludeSemantics: true, child: child),
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
    return FadeTransition(
      opacity: opacity,
      alwaysIncludeSemantics: true,
      child: widget.child,
    );
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

/// Fades content in where placeholders stood: [placeholders] notes that they
/// are on screen, and the [content] that next replaces them arrives with a
/// fade. Content replacing anything else, such as posts kept from last time,
/// is drawn at once, so nothing the reader is looking at blinks.
class XtaAfterPlaceholders {
  bool _shown = false;

  Widget placeholders(Widget placeholders) {
    _shown = true;
    return placeholders;
  }

  Widget content(Widget content) {
    final arriving = _shown;
    _shown = false;
    return XtaArrival(arriving: arriving, child: content);
  }

  /// Something other than the placeholders went on screen.
  void shownOther() => _shown = false;
}

/// [child] replaced in place whenever [swap] changes, as a post's text is by
/// its translation: the new content fades in while the space it takes eases
/// to its new height. It stays the same widget throughout, so whatever it
/// holds, such as a post expanded with "Show more", survives the swap.
class XtaSwap extends StatefulWidget {
  final Object? swap;
  final Widget child;

  const XtaSwap({super.key, required this.swap, required this.child});

  @override
  State<XtaSwap> createState() => _XtaSwapState();
}

class _XtaSwapState extends State<XtaSwap> with SingleTickerProviderStateMixin {
  /// Made on the first swap, so content that never changes runs no ticker.
  AnimationController? _fade;
  Animation<double> _opacity = kAlwaysCompleteAnimation;

  @override
  void didUpdateWidget(XtaSwap oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.swap == oldWidget.swap || xtaReduceMotion(context)) return;
    var fade = _fade;
    if (fade == null) {
      fade = _fade = AnimationController(
        vsync: this,
        duration: kXtaMotionStandard,
      );
      _opacity = fade.drive(CurveTween(curve: Curves.easeOutCubic));
    }
    fade.forward(from: 0);
  }

  @override
  void dispose() {
    _fade?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return XtaAnimatedSize(
      child: FadeTransition(
        opacity: _opacity,
        alwaysIncludeSemantics: true,
        child: widget.child,
      ),
    );
  }
}

/// An [AnimatedSize] for XTA's motion: [duration] long, or as short as an
/// [AnimatedSize] can be with reduced motion. Given no time at all it would
/// re-dirty itself in its own layout, so the new size lands a frame later
/// instead.
class XtaAnimatedSize extends StatelessWidget {
  final Duration duration;
  final AlignmentGeometry alignment;
  final Clip clipBehavior;
  final Widget child;

  const XtaAnimatedSize({
    super.key,
    this.duration = kXtaMotionStandard,
    this.alignment = AlignmentDirectional.topStart,
    this.clipBehavior = Clip.hardEdge,
    required this.child,
  });

  static const _instant = Duration(microseconds: 1);

  @override
  Widget build(BuildContext context) {
    return AnimatedSize(
      duration: xtaReduceMotion(context) ? _instant : duration,
      curve: Curves.easeOutCubic,
      alignment: alignment,
      clipBehavior: clipBehavior,
      child: child,
    );
  }
}

/// Cross-fades compact state changes. Optional size animation is reserved for
/// small chrome such as a filter strip, or a part of a post that opens and
/// closes, never whole feeds or paginated pages.
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

  /// Only the incoming child gives the size, so the size eases to it at once
  /// while the outgoing one fades out over it, clipped, instead of holding
  /// the old size until it has gone.
  static Widget _sizedByIncoming(Widget? current, List<Widget> previous) {
    return Stack(
      alignment: AlignmentDirectional.topStart,
      children: [
        for (final child in previous)
          PositionedDirectional(top: 0, start: 0, end: 0, child: child),
        ?current,
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final resolved = xtaMotionDuration(context, duration);
    final switcher = AnimatedSwitcher(
      duration: resolved,
      reverseDuration: resolved,
      switchInCurve: Curves.easeOutCubic,
      switchOutCurve: Curves.easeInCubic,
      layoutBuilder: animateSize
          ? _sizedByIncoming
          : AnimatedSwitcher.defaultLayoutBuilder,
      transitionBuilder: (child, animation) => FadeTransition(
        opacity: animation,
        child: child,
      ),
      child: child,
    );
    if (!animateSize) return switcher;
    return XtaAnimatedSize(
      duration: duration,
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
