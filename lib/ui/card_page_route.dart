import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'package:flutter/services.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/ui/motion.dart';
import 'package:xta/ui/swipe_back_card.dart';
import 'package:xta/ui/swipe_back_drag.dart';

/// The backdrop the page behind shrinks over: near-black, or black behind a
/// dark page that is not black itself, so the page always stands out from it.
const _stageNearBlack = Color(0xFF16181D);
const _stageBlack = Color(0xFF000000);

Color _stage(ThemeData theme) =>
    theme.brightness == Brightness.dark && theme.scaffoldBackgroundColor.computeLuminance() > 0.005
    ? _stageBlack
    : _stageNearBlack;

/// The faint edge of a card lifted off a dark page: a black card over a
/// black page would not show at all.
const _darkEdge = Color(0x24FFFFFF);

const _cardShadow = [
  BoxShadow(color: Color(0x5C000000), offset: Offset(0, 30), blurRadius: 70),
  BoxShadow(color: Color(0x2E000000), offset: Offset(0, 6), blurRadius: 18),
];

/// Short of complete while a hand holds the card, so the page behind is drawn.
const double _heldValue = 0.999;

/// The route an opened post comes in (`docs/specs/post-swipe-back-card.md`).
/// It slides in over the page it was opened from, which shrinks and darkens
/// behind it, and goes back as a card the reader drags away, throws, or lets
/// drop back. Android's back gesture moves the card too.
///
/// It fills the screen, so a pointer's position on screen is its position on
/// the card at rest.
class XtaCardPageRoute<T> extends PageRoute<T> {
  XtaCardPageRoute({required this.builder, super.settings});

  final WidgetBuilder builder;

  _CardHand? _hand;
  CurvedAnimation? _slide;
  Listenable? _frames;
  AnimationStatus? _slideDirection;
  bool _gesture = false;

  @override
  Color? get barrierColor => null;

  @override
  String? get barrierLabel => null;

  @override
  bool get maintainState => true;

  @override
  Duration get transitionDuration => _reduceMotion ? Duration.zero : kSwipeBackOpen;

  @override
  Duration get reverseTransitionDuration => _reduceMotion ? Duration.zero : kSwipeBackClose;

  bool get _reduceMotion {
    final context = navigator?.context;
    return context != null && xtaReduceMotion(context);
  }

  @override
  void install() {
    super.install();
    final hand = _hand = _CardHand(this, navigator!);
    // Going out, the slide eases in: the reverse curve runs backwards in time.
    _slide = CurvedAnimation(parent: animation!, curve: kSwipeBackEaseOutCubic, reverseCurve: kSwipeBackEaseOutQuad);
    _frames = Listenable.merge([animation, hand.clock]);
    animation!.addStatusListener(_latchSlide);
  }

  /// Which way the slide set off, which is how its curve picks a curve.
  void _latchSlide(AnimationStatus status) => _slideDirection = status.isAnimating ? (_slideDirection ?? status) : null;

  // The durations follow reduced motion, which can change after the
  // controller was made.
  @override
  TickerFuture didPush() {
    controller?.duration = transitionDuration;
    return super.didPush();
  }

  @override
  bool didPop(T? result) {
    controller?.reverseDuration = reverseTransitionDuration;
    final popped = super.didPop(result);
    final hand = _hand;
    if (!popped || hand == null || !hand.busy) return popped;
    final flight = hand.leaving();
    if (flight != null) controller?.animateBack(0, duration: flight);
    // After the navigator has told its observers of the pop, so no hero flies
    // with the card.
    scheduleMicrotask(_stopGesture);
    return popped;
  }

  @override
  void dispose() {
    animation?.removeStatusListener(_latchSlide);
    _slide?.dispose();
    _hand?.dispose();
    final navigator = this.navigator;
    if (_gesture && navigator != null) {
      _gesture = false;
      SchedulerBinding.instance.addPostFrameCallback((_) {
        if (navigator.mounted) navigator.didStopUserGesture();
      });
    }
    super.dispose();
  }

  @override
  Widget buildPage(BuildContext context, Animation<double> animation, Animation<double> secondaryAnimation) =>
      Semantics(scopesRoute: true, explicitChildNodes: true, child: builder(context));

  @override
  Widget buildTransitions(
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
    Widget child,
  ) => _SwipeBackCard(route: this, child: child);

  @override
  DelegatedTransitionBuilder? get delegatedTransition => _behind;

  Widget? _behind(
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
    bool allowSnapshotting,
    Widget? child,
  ) => child == null || xtaReduceMotion(context) ? child : _SwipeBackBehind(route: this, child: child);

  /// Where the card is drawn: where a hand holds it, else on the slide.
  CardPose _pose(Size size, {required bool rtl}) => _hand?.pose ?? _slidePose(size, rtl: rtl);

  CardPose _slidePose(Size size, {required bool rtl}) {
    final dx = slideOffset(_slide!.value, size.width, closing: _slideDirection == AnimationStatus.reverse);
    final offset = Offset(dx, 0);
    return CardPose(
      offset: offset,
      origin: size.center(Offset.zero),
      lift: slideLift(dx, size.width),
      mirrored: rtl,
      uncovered: behindUncovered(offset, size),
    );
  }

  /// Whether a hand may take the card now: it is on top and free to leave,
  /// and at rest, or dropping back when [catching].
  bool _mayTake({required bool catching}) {
    final hand = _hand;
    if (hand == null || !isCurrent || willHandlePopInternally || popDisposition != RoutePopDisposition.pop) {
      return false;
    }
    return (catching && hand.catchable) || (animation!.isCompleted && !hand.busy);
  }

  /// A hand took the card: the page behind is drawn again, and the post takes
  /// no taps until the card is let go.
  void _took() {
    if (!_gesture) {
      _gesture = true;
      navigator!.didStartUserGesture();
    }
    controller!.value = _heldValue;
  }

  /// The card dropped back into place.
  void _settled() {
    controller!.value = 1;
    _stopGesture();
  }

  /// The card was thrown, or let go past the line with reduced motion: the
  /// route pops, and [didPop] asks the hand how long the card takes to go.
  void _thrown() {
    if (isCurrent) {
      navigator!.pop();
    } else {
      navigator!.removeRoute(this);
    }
    _stopGesture();
  }

  void _stopGesture() {
    if (!_gesture) return;
    _gesture = false;
    navigator?.didStopUserGesture();
  }
}

/// What a hold keeps from the grab: the card's size, whether it leaves to the
/// left, the point it turns around, how it tilts, and whether it moves flat.
typedef _Grip = ({Size size, bool mirrored, Offset origin, double tilt, bool flat});

sealed class _Hold {
  const _Hold(this.grip);

  final _Grip grip;
}

/// A finger that went down at [start], or Android's back gesture, holds the
/// card, which was at [base] and had been lifting for [liftedFor].
final class _Held extends _Hold {
  _Held(super.grip, {required this.base, required this.start, required this.liftedFor, required this.system});

  final Offset base;
  final Offset start;
  final Duration liftedFor;
  final bool system;

  /// How far the finger has gone since the grab, leaving towards +x.
  Offset travel = Offset.zero;

  Offset get offset {
    final at = base + travel;
    return Offset(rubberBand(at.dx), at.dy);
  }
}

/// Let go short of the line: the card drops back on [spring].
final class _Settling extends _Hold {
  const _Settling(super.grip, {required this.spring, required this.lift, required this.distance, required this.sway});

  final CardSpring spring;
  final double lift;
  final double distance;
  final double sway;
}

/// Thrown: the card flies off on [flight].
final class _Thrown extends _Hold {
  const _Thrown(super.grip, {required this.flight, required this.lift});

  final CardThrow flight;
  final double lift;
}

/// Holds the card while the route's own animation does not: a finger or
/// Android's back gesture, the spring that drops it back, or a throw. Its
/// [clock] ticks every frame while it holds the card, counting the seconds
/// since the current phase began.
class _CardHand {
  _CardHand(this._route, TickerProvider vsync) : clock = AnimationController.unbounded(vsync: vsync) {
    clock.addListener(_tick);
  }

  final XtaCardPageRoute<dynamic> _route;
  final AnimationController clock;

  _Hold? _hold;
  CardPose? _pose;
  double _sway = 0;
  Duration _lastTick = Duration.zero;
  VelocityTracker? _tracker;

  CardPose? get pose => _pose;

  bool get busy => _hold != null;

  bool get catchable => _hold is _Settling;

  void dispose() => clock.dispose();

  /// A finger that went down at [start], given as [down], or Android's back
  /// gesture touching there, takes the card. A card caught on its way back
  /// keeps how it turns and lifts on from where it is.
  void grab({
    required Offset start,
    required Size size,
    required bool mirrored,
    required bool tilt,
    required bool flat,
    PointerDownEvent? down,
  }) {
    final hold = _hold;
    final caught = hold is _Settling ? _pose : null;
    final fresh = hold == null || caught == null || caught.offset.distance < 0.5;
    _hold = _Held(
      fresh ? _grip(start, size, mirrored: mirrored, tilt: tilt, flat: flat) : hold.grip,
      base: caught?.offset ?? Offset.zero,
      start: start,
      liftedFor: caught == null ? Duration.zero : grabbedFor(caught.lift),
      system: down == null,
    );
    _tracker = down == null ? null : (VelocityTracker.withKind(down.kind)..addPosition(down.timeStamp, down.position));
    _route._took();
    _run();
  }

  static _Grip _grip(Offset start, Size size, {required bool mirrored, required bool tilt, required bool flat}) {
    final origin = mirrored ? Offset(size.width - start.dx, start.dy) : start;
    return (
      size: size,
      mirrored: mirrored,
      origin: origin,
      tilt: tilt && !flat ? tiltFactor(origin.dy, size.height) : 0.0,
      flat: flat,
    );
  }

  void move(PointerEvent event) {
    final held = _hold;
    if (held is! _Held || held.system) return;
    final travel = event.position - held.start;
    held.travel = held.grip.mirrored ? Offset(-travel.dx, travel.dy) : travel;
    _tracker?.addPosition(event.timeStamp, event.position);
  }

  /// Android's back gesture is [progress] of the way across.
  void follow(double progress) {
    final held = _hold;
    if (held is! _Held || !held.system) return;
    held.travel = Offset(progress * held.grip.size.width, 0);
  }

  /// Let go: the card leaves or drops back by where it is and how fast it
  /// was going.
  void release() => _letGo(_velocity());

  /// The pointer was cancelled: as letting go, standing still.
  void cancel() => _letGo(Offset.zero);

  /// Android cancelled its back gesture: the card drops back.
  void drop() {
    final held = _hold;
    if (held is _Held) _settle(held, Offset.zero);
  }

  /// Android committed its back gesture: the card is thrown.
  void leave() {
    final held = _hold;
    if (held is _Held) _throwAway(held.grip, held.offset, Offset.zero);
  }

  /// The route is popping: how long the card takes to fly off when the hand
  /// holds it. A card held when something else pops the route is thrown.
  Duration? leaving() {
    final hold = _hold;
    if (hold == null) return null;
    if (hold is _Thrown) return hold.flight.duration;
    if (hold.grip.flat) {
      _clear();
      return null;
    }
    return _throw(hold.grip, _pose?.offset ?? Offset.zero, Offset.zero).duration;
  }

  void _letGo(Offset velocity) {
    final held = _hold;
    if (held is! _Held) return;
    if (leavesOnRelease(held.offset, velocity, held.grip.size)) {
      _throwAway(held.grip, held.offset, velocity);
    } else {
      _settle(held, velocity);
    }
  }

  /// The finger's velocity in px/ms, leaving towards +x.
  Offset _velocity() {
    final velocity = (_tracker?.getVelocity().pixelsPerSecond ?? Offset.zero) / 1000;
    final mirrored = _hold?.grip.mirrored ?? false;
    return mirrored ? Offset(-velocity.dx, velocity.dy) : velocity;
  }

  void _settle(_Held held, Offset velocity) {
    if (held.grip.flat) return _rest();
    final offset = held.offset;
    _hold = _Settling(
      held.grip,
      spring: CardSpring(offset, velocity * 1000),
      lift: _pose?.lift ?? 0,
      distance: math.max(offset.distance, 1),
      sway: _sway,
    );
    _run();
  }

  void _rest() {
    _clear();
    _route._settled();
  }

  void _throwAway(_Grip grip, Offset offset, Offset velocity) {
    grip.flat ? _clear() : _throw(grip, offset, velocity);
    _route._thrown();
  }

  CardThrow _throw(_Grip grip, Offset offset, Offset velocity) {
    final flight = cardThrow(offset: offset, velocity: velocity, size: grip.size, tilt: grip.tilt);
    _hold = _Thrown(grip, flight: flight, lift: _pose?.lift ?? 0);
    _run();
    return flight;
  }

  void _run() {
    _lastTick = Duration.zero;
    clock.animateWith(_Running());
  }

  void _clear() {
    _hold = null;
    _pose = null;
    _sway = 0;
    _tracker = null;
    clock.stop();
  }

  void _tick() {
    final now = Duration(microseconds: (clock.value * Duration.microsecondsPerSecond).round());
    final step = now - _lastTick;
    _lastTick = now;
    switch (_hold) {
      case final _Held held:
        _holdTick(held, now, step);
      case final _Settling settling:
        _settleTick(settling, now);
      case final _Thrown thrown:
        _throwTick(thrown, now);
      case null:
        break;
    }
  }

  void _holdTick(_Held held, Duration now, Duration step) {
    final grip = held.grip;
    _sway = grip.tilt == 0 ? 0 : swayToward(_sway, swayTarget(grip.tilt, _velocity().dx), step);
    _pose = _posed(grip, held.offset, lift: grip.flat ? 0 : liftAfterGrab(now + held.liftedFor));
  }

  void _settleTick(_Settling settling, Duration now) {
    final seconds = now.inMicroseconds / Duration.microsecondsPerSecond;
    if (settling.spring.landedAt(seconds)) return _rest();
    final offset = settling.spring.offsetAt(seconds);
    _sway = swayDecay(settling.sway, now);
    _pose = _posed(
      settling.grip,
      offset,
      lift: settlingLift(
        from: settling.lift,
        startDistance: settling.distance,
        distance: offset.distance,
        elapsed: now,
      ),
    );
  }

  void _throwTick(_Thrown thrown, Duration now) {
    final t = now.inMicroseconds / thrown.flight.duration.inMicroseconds;
    _pose = _posed(
      thrown.grip,
      thrown.flight.offsetAt(t),
      lift: thrown.lift,
      spin: thrown.flight.spinAt(t),
      uncovered: thrown.flight.progress(t),
    );
    if (t >= 1) clock.stop();
  }

  CardPose _posed(_Grip grip, Offset offset, {required double lift, double spin = 0, double uncovered = 0}) => CardPose(
    offset: offset,
    origin: grip.origin,
    rotation: cardRotation(tilt: grip.tilt, dx: offset.dx, width: grip.size.width, sway: _sway, spin: spin),
    lift: lift,
    mirrored: grip.mirrored,
    uncovered: math.max(behindUncovered(offset, grip.size), uncovered),
  );
}

/// Time itself, for a clock that runs until it is stopped.
class _Running extends Simulation {
  @override
  double x(double time) => time;

  @override
  double dx(double time) => 1;

  @override
  bool isDone(double time) => false;
}

/// The card: drawn where the route puts it, it takes the drags that head back
/// and Android's back gesture.
class _SwipeBackCard extends StatefulWidget {
  const _SwipeBackCard({required this.route, required this.child});

  final XtaCardPageRoute<dynamic> route;
  final Widget child;

  @override
  State<_SwipeBackCard> createState() => _SwipeBackCardState();
}

class _SwipeBackCardState extends State<_SwipeBackCard> with WidgetsBindingObserver {
  XtaCardPageRoute<dynamic> get _route => widget.route;

  _CardHand get _hand => _route._hand!;

  bool get _rtl => Directionality.of(context) == TextDirection.rtl;

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

  bool _pref(String key) => PrefService.of(context, listen: false).get<bool>(key) ?? true;

  bool _canStart(PointerDownEvent event) {
    final size = context.size;
    if (size == null || !_route._mayTake(catching: true) || _inSystemEdge(event.position, size)) return false;
    if (_hand.catchable || _pref(optionSwipeBackAnywhere)) return true;
    return (_rtl ? size.width - event.position.dx : event.position.dx) <= kSwipeBackEdge;
  }

  /// Android keeps swipes that start in its gesture edges for itself.
  bool _inSystemEdge(Offset at, Size size) {
    final insets = MediaQuery.systemGestureInsetsOf(context);
    return at.dx < insets.left ||
        at.dx > size.width - insets.right ||
        at.dy < insets.top ||
        at.dy > size.height - insets.bottom;
  }

  void _grab(PointerDownEvent down) => _hand.grab(
    start: down.position,
    size: context.size!,
    mirrored: _rtl,
    tilt: _pref(optionSwipeBackTilt),
    flat: xtaReduceMotion(context),
    down: down,
  );

  @override
  bool handleStartBackGesture(PredictiveBackEvent backEvent) {
    final touch = backEvent.touchOffset;
    final size = context.size;
    if (backEvent.isButtonEvent || touch == null || size == null || xtaReduceMotion(context)) return false;
    if (!_route._mayTake(catching: false)) return false;
    _hand.grab(
      start: touch / View.of(context).devicePixelRatio,
      size: size,
      mirrored: backEvent.swipeEdge == SwipeEdge.right,
      tilt: _pref(optionSwipeBackTilt),
      flat: false,
    );
    return true;
  }

  @override
  void handleUpdateBackGestureProgress(PredictiveBackEvent backEvent) => _hand.follow(backEvent.progress);

  @override
  void handleCancelBackGesture() => _hand.drop();

  @override
  void handleCommitBackGesture() => _hand.leave();

  @override
  Widget build(BuildContext context) {
    final rtl = _rtl;
    return LayoutBuilder(
      builder: (context, constraints) => AnimatedBuilder(
        animation: _route._frames!,
        builder: (context, child) => _CardFrame(
          pose: _route._pose(constraints.biggest, rtl: rtl),
          size: constraints.biggest,
          child: child!,
        ),
        child: RawGestureDetector(
          behavior: HitTestBehavior.opaque,
          excludeFromSemantics: true,
          gestures: {
            SwipeBackDragRecognizer: GestureRecognizerFactoryWithHandlers<SwipeBackDragRecognizer>(
              () => SwipeBackDragRecognizer(debugOwner: this),
              (recognizer) => recognizer
                ..direction = rtl ? -1 : 1
                ..canStart = _canStart
                ..catching = (() => _hand.catchable)
                ..onGrab = _grab
                ..onMove = _hand.move
                ..onRelease = _hand.release
                ..onCancel = _hand.cancel,
            ),
          },
          child: widget.child,
        ),
      ),
    );
  }
}

/// The card at [pose]: moved, turned and shrunk, its corners rounded and its
/// shadow under it by how far it is lifted.
class _CardFrame extends StatelessWidget {
  const _CardFrame({required this.pose, required this.size, required this.child});

  final CardPose pose;
  final Size size;
  final Widget child;

  Border? _edge(BuildContext context) => pose.lift > 0 && Theme.of(context).brightness == Brightness.dark
      ? Border.all(color: _darkEdge.withValues(alpha: _darkEdge.a * pose.lift))
      : null;

  @override
  Widget build(BuildContext context) {
    final transform = cardTransform(pose, size);
    final radius = BorderRadius.circular(cardRadius(pose.lift));
    return Stack(
      fit: StackFit.expand,
      children: [
        Transform(
          transform: transform,
          child: IgnorePointer(
            child: Opacity(
              opacity: pose.lift.clamp(0.0, 1.0),
              child: DecoratedBox(
                decoration: BoxDecoration(borderRadius: radius, boxShadow: _cardShadow),
              ),
            ),
          ),
        ),
        Transform(
          key: const ValueKey('swipe-back-card'),
          transform: transform,
          child: DecoratedBox(
            position: DecorationPosition.foreground,
            decoration: BoxDecoration(borderRadius: radius, border: _edge(context)),
            child: ClipRRect(
              borderRadius: radius,
              clipBehavior: pose.lift > 0 ? Clip.antiAlias : Clip.none,
              child: child,
            ),
          ),
        ),
      ],
    );
  }
}

/// The page behind the card: shrunk, rounded and darkened over a dark
/// backdrop while the card covers it, whole again as the card leaves.
class _SwipeBackBehind extends StatelessWidget {
  const _SwipeBackBehind({required this.route, required this.child});

  final XtaCardPageRoute<dynamic> route;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final rtl = Directionality.of(context) == TextDirection.rtl;
    final stage = _stage(Theme.of(context));
    return LayoutBuilder(
      builder: (context, constraints) => AnimatedBuilder(
        animation: route._frames!,
        builder: (context, child) {
          final look = behindLook(route._pose(constraints.biggest, rtl: rtl).uncovered);
          return ColoredBox(
            color: stage,
            child: Transform.scale(
              key: const ValueKey('swipe-back-behind'),
              scale: look.scale,
              child: ClipRRect(
                borderRadius: BorderRadius.circular(look.radius),
                clipBehavior: look.radius > 0 ? Clip.antiAlias : Clip.none,
                child: Stack(
                  fit: StackFit.passthrough,
                  children: [
                    child!,
                    Positioned.fill(
                      child: IgnorePointer(
                        child: ColoredBox(color: Colors.black.withValues(alpha: look.dim)),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          );
        },
        child: child,
      ),
    );
  }
}
