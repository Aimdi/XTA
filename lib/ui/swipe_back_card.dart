import 'dart:math' as math;

import 'package:flutter/animation.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/physics.dart';
import 'package:flutter/rendering.dart';

// The numbers of the swipe-back card, from the owner's concept
// (docs/specs/post-swipe-back-card.md).

/// How long the page takes to lift into a card once grabbed.
const Duration kSwipeBackLift = Duration(milliseconds: 140);

/// How much smaller a lifted card is, and how much more across a full width.
const double kSwipeBackLiftScale = 0.06;
const double kSwipeBackTravelScale = 0.03;

/// The corners of a lifted card.
const double kSwipeBackRadius = 24.0;

/// Degrees a card grabbed at the very top or bottom turns across a full width.
const double kSwipeBackTilt = 7.0;

/// Degrees a card swings per px/ms of sideways speed, and at most.
const double kSwipeBackSway = 3.0;
const double kSwipeBackSwayMax = 4.0;

/// Degrees a thrown card turns on its way out.
const double kSwipeBackThrowSpin = 10.0;

/// The page behind an open card: its size, corners and darkening.
const double kSwipeBackBehindScale = 0.96;
const double kSwipeBackBehindRadius = 16.0;
const double kSwipeBackBehindDim = 0.25;

/// Letting go past this much of the width, or of the height, leaves.
const double kSwipeBackLeaveX = 0.3;
const double kSwipeBackLeaveY = 0.25;

/// Letting go faster than this, in px/ms, away from rest leaves.
const double kSwipeBackFling = 0.35;

/// How far from the edge a drag may start when it may not start anywhere.
const double kSwipeBackEdge = 28.0;

/// How far a finger goes before the drag picks its direction.
const double kSwipeBackSlop = 6.0;

/// How many times more a drag must go across than up or down to be the card's.
const double kSwipeBackSideways = 1.15;

/// The spring that drops a card back: stiff and critically damped.
const double kSwipeBackStiffness = 900.0;

const Duration kSwipeBackOpen = Duration(milliseconds: 260);
const Duration kSwipeBackClose = Duration(milliseconds: 220);

/// The concept's eases exactly: Flutter's named curves are Bézier fits, and
/// a throw has to start at the speed it was thrown at.
const Curve kSwipeBackEaseOutQuad = _EaseOut(2);
const Curve kSwipeBackEaseOutCubic = _EaseOut(3);

class _EaseOut extends Curve {
  const _EaseOut(this.power);

  final int power;

  @override
  double transformInternal(double t) => 1 - math.pow(1 - t, power).toDouble();
}

const double _transitLift = 0.6;
const double _transitLiftDistance = 0.4;
const double _closeTravel = 1.1;
const double _behindClearAt = 0.85;
const double _rubberBand = 0.3;
const double _flickBack = 0.2;
const double _flingTravel = 16.0;
const double _throwAlongDrag = 0.3;
const double _throwMinSpeed = 1.6;
const Offset _throwMargin = Offset(140, 90);
const double _throwMinTravel = 40.0;
const Duration _throwShortest = Duration(milliseconds: 140);
const Duration _throwLongest = Duration(milliseconds: 260);
const double _settleLiftStretch = 1.6;
const double _swayFollow = 0.25;
const double _swayDecay = 0.8;
const double _restDistance = 0.4;
const double _restSpeed = 8.0;

/// Where the card is, in a frame where it leaves towards +x: [offset] from
/// rest, turned [rotation] degrees and shrunk around [origin], [lift] of the
/// way into a card. [mirrored] when it really leaves towards -x. [uncovered]
/// is how much of the page behind it shows, from 0 under the card to 1 clear.
@immutable
class CardPose {
  final Offset offset;
  final double rotation;
  final double lift;
  final Offset origin;
  final bool mirrored;
  final double uncovered;

  const CardPose({
    required this.offset,
    required this.origin,
    this.rotation = 0,
    this.lift = 0,
    this.mirrored = false,
    this.uncovered = 0,
  });
}

/// How much of a card at [lift] is left after [offset] of travel across [size].
double cardScale(Offset offset, double lift, Size size) {
  final travel = (offset.distance / size.width).clamp(0.0, 1.0);
  return 1 - kSwipeBackLiftScale * lift - kSwipeBackTravelScale * travel * lift;
}

/// A card's corners at [lift], in whole pixels.
double cardRadius(double lift) => (kSwipeBackRadius * lift).roundToDouble();

/// The card on screen: moved by its offset, turned and shrunk around its
/// origin, as the concept's CSS transform with its transform-origin.
Matrix4 cardTransform(CardPose pose, Size size) {
  final side = pose.mirrored ? -1.0 : 1.0;
  final origin = pose.mirrored ? Offset(size.width - pose.origin.dx, pose.origin.dy) : pose.origin;
  final scale = cardScale(pose.offset, pose.lift, size);
  return Matrix4.identity()
    ..translateByDouble(origin.dx + side * pose.offset.dx, origin.dy + pose.offset.dy, 0, 1)
    ..rotateZ(side * pose.rotation * math.pi / 180)
    ..scaleByDouble(scale, scale, 1, 1)
    ..translateByDouble(-origin.dx, -origin.dy, 0, 1);
}

/// How far a card sliding in or out is from rest, at [curved] of its route's
/// curved animation: it comes in from the far edge and goes out a little past
/// it, so its shadow leaves too.
double slideOffset(double curved, double width, {required bool closing}) =>
    (1 - curved) * width * (closing ? _closeTravel : 1);

/// The lift of a card sliding in or out: partly lifted while far, flat once
/// it is within 40 % of a width of rest.
double slideLift(double distance, double width) =>
    _transitLift * (distance.abs() / (_transitLiftDistance * width)).clamp(0.0, 1.0);

/// The lift [held] after a grab: up in [kSwipeBackLift], easing out.
double liftAfterGrab(Duration held) =>
    kSwipeBackEaseOutCubic.transform((held.inMicroseconds / kSwipeBackLift.inMicroseconds).clamp(0.0, 1.0));

/// How long after a grab a card has [lift]: a card caught on its way back
/// lifts on from where it is.
Duration grabbedFor(double lift) => kSwipeBackLift * (1 - math.pow(1 - lift.clamp(0.0, 0.999), 1 / 3).toDouble());

/// The lift of a card dropping back: it flattens as it nears rest, and is flat
/// within 1.6 times the lift time whatever happens.
double settlingLift({
  required double from,
  required double startDistance,
  required double distance,
  required Duration elapsed,
}) {
  final byDistance = (distance / startDistance).clamp(0.0, 1.0);
  final byTime = (1 - elapsed.inMicroseconds / (kSwipeBackLift.inMicroseconds * _settleLiftStretch)).clamp(0.0, 1.0);
  return from * math.min(byDistance, byTime);
}

/// How a card grabbed at [grabY] turns as it travels: clockwise above the
/// middle and the other way below it, more the further from the middle.
double tiltFactor(double grabY, double height) {
  final half = height / 2;
  final fromMiddle = (half - grabY) / half;
  return (fromMiddle < 0 ? -1 : 1) * (0.55 + 0.45 * math.min(1.0, fromMiddle.abs()));
}

/// A card's rotation in degrees: its tilt by how far across it has gone, its
/// swing, and a throw's spin.
double cardRotation({
  required double tilt,
  required double dx,
  required double width,
  double sway = 0,
  double spin = 0,
}) => tilt * (dx / width) * kSwipeBackTilt + sway + spin;

/// Where the swing heads at a sideways speed of [vx] px/ms.
double swayTarget(double tilt, double vx) =>
    tilt == 0 ? 0 : tilt.sign * (vx * kSwipeBackSway).clamp(-kSwipeBackSwayMax, kSwipeBackSwayMax);

/// [sway] after [elapsed] on its way to [target]: a quarter of the way in each
/// 60 Hz frame, so it swings back when the finger stops.
double swayToward(double sway, double target, Duration elapsed) =>
    target + (sway - target) * math.pow(1 - _swayFollow, _frames(elapsed));

/// What is left of a swing [elapsed] after a card was let go.
double swayDecay(double sway, Duration elapsed) => sway * math.pow(_swayDecay, _frames(elapsed));

double _frames(Duration elapsed) => elapsed.inMicroseconds / (Duration.microsecondsPerSecond / 60);

/// A drag past the starting edge gives only a third.
double rubberBand(double dx) => dx < 0 ? dx * _rubberBand : dx;

/// Whether letting go at [offset] with [velocity] (px/ms) leaves: past the
/// ellipse through 30 % of the width and 25 % of the height, or thrown away
/// from rest; never when flicked back towards it.
bool leavesOnRelease(Offset offset, Offset velocity, Size size) {
  final speed = velocity.distance;
  final away = velocity.dx * offset.dx + velocity.dy * offset.dy;
  if (away < 0 && speed > _flickBack) return false;
  final across = offset.dx / (kSwipeBackLeaveX * size.width);
  final down = offset.dy / (kSwipeBackLeaveY * size.height);
  final far = across * across + down * down > 1;
  return far || (speed > kSwipeBackFling && away > 0 && offset.distance > _flingTravel);
}

/// How much of the page behind shows with the card at [offset]: none while it
/// covers it, all once it is 85 % of a width away. Dragged past the starting
/// edge it uncovers nothing.
double behindUncovered(Offset offset, Size size) =>
    (Offset(math.max(offset.dx, 0), offset.dy).distance / (size.width * _behindClearAt)).clamp(0.0, 1.0);

/// The page behind a card that leaves [uncovered] of it.
({double scale, double radius, double dim}) behindLook(double uncovered) => (
  scale: kSwipeBackBehindScale + (1 - kSwipeBackBehindScale) * uncovered,
  radius: (kSwipeBackBehindRadius * (1 - uncovered)).roundToDouble(),
  dim: kSwipeBackBehindDim * (1 - uncovered),
);

/// A card flying off from [from] along [direction] until it is off screen.
@immutable
class CardThrow {
  final Offset from;
  final Offset direction;
  final double travel;
  final Duration duration;

  /// Degrees added to its turn by the end.
  final double spin;

  const CardThrow({
    required this.from,
    required this.direction,
    required this.travel,
    required this.duration,
    required this.spin,
  });

  /// It starts at the throw's speed and eases out.
  double progress(double t) => kSwipeBackEaseOutQuad.transform(t.clamp(0.0, 1.0));

  Offset offsetAt(double t) => from + direction * (travel * progress(t));

  double spinAt(double t) => spin * progress(t);
}

/// Throws a card let go at [offset] with [velocity] (px/ms) across [size]:
/// along the throw, or along the drag when let go slowly, as far as it takes
/// to be off screen, in 140–260 ms, spinning on with tilt on.
CardThrow cardThrow({required Offset offset, required Offset velocity, required Size size, required double tilt}) {
  final speed = velocity.distance;
  final direction = speed > _throwAlongDrag
      ? velocity / speed
      : offset.distance > 0
      ? offset / offset.distance
      : const Offset(1, 0);
  final travel = math.max(_throwMinTravel, _offScreen(offset, direction, size));
  final ms = (2 * travel / math.max(speed, _throwMinSpeed)).clamp(
    _throwShortest.inMilliseconds.toDouble(),
    _throwLongest.inMilliseconds.toDouble(),
  );
  final spin = tilt == 0 ? 0.0 : (direction.dx < 0 ? -1 : 1) * tilt.sign * kSwipeBackThrowSpin;
  return CardThrow(
    from: offset,
    direction: direction,
    travel: travel,
    duration: Duration(microseconds: (ms * 1000).round()),
    spin: spin,
  );
}

/// How far a card at [offset] goes along [direction] before it is off screen.
double _offScreen(Offset offset, Offset direction, Size size) => [
  if (direction.dx > 0.01) (size.width + _throwMargin.dx - offset.dx) / direction.dx,
  if (direction.dx < -0.01) (size.width + _throwMargin.dx + offset.dx) / -direction.dx,
  if (direction.dy > 0.01) (size.height + _throwMargin.dy - offset.dy) / direction.dy,
  if (direction.dy < -0.01) (size.height + _throwMargin.dy + offset.dy) / -direction.dy,
].reduce(math.min);

/// A card dropping back to rest from [from] at [velocity] (px/s).
class CardSpring {
  CardSpring(Offset from, Offset velocity)
    : _x = _towardsRest(from.dx, velocity.dx),
      _y = _towardsRest(from.dy, velocity.dy);

  final SpringSimulation _x;
  final SpringSimulation _y;

  static final _spring = SpringDescription.withDampingRatio(mass: 1, stiffness: kSwipeBackStiffness);

  static SpringSimulation _towardsRest(double from, double velocity) => SpringSimulation(_spring, from, 0, velocity);

  Offset offsetAt(double seconds) => Offset(_x.x(seconds), _y.x(seconds));

  Offset velocityAt(double seconds) => Offset(_x.dx(seconds), _y.dx(seconds));

  /// Within half a pixel of rest and all but still.
  bool landedAt(double seconds) {
    final offset = offsetAt(seconds);
    final velocity = velocityAt(seconds);
    return offset.dx.abs() < _restDistance &&
        offset.dy.abs() < _restDistance &&
        velocity.dx.abs() < _restSpeed &&
        velocity.dy.abs() < _restSpeed;
  }
}
