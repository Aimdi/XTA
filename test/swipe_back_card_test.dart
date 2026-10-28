import 'package:flutter/painting.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/ui/swipe_back_card.dart';

const _size = Size(390, 844);

void main() {
  group('the card', () {
    test('shrinks by 6 % lifted, and 3 % more across a full width', () {
      expect(cardScale(Offset.zero, 0, _size), 1);
      expect(cardScale(Offset.zero, 1, _size), closeTo(0.94, 1e-9));
      expect(cardScale(const Offset(390, 0), 1, _size), closeTo(0.91, 1e-9));
      expect(cardScale(const Offset(195, 0), 0.5, _size), closeTo(1 - 0.03 - 0.0075, 1e-9));
    });

    test('rounds its corners to 24 at full lift, in whole pixels', () {
      expect(cardRadius(0), 0);
      expect(cardRadius(1), 24);
      expect(cardRadius(0.51), 12);
    });

    test('at rest it is drawn exactly where it is', () {
      const pose = CardPose(offset: Offset.zero, origin: Offset(100, 300));
      expect(MatrixUtils.getAsTranslation(cardTransform(pose, _size)), Offset.zero);
    });

    test('the point it turns around follows the offset, whatever the turn and lift', () {
      const pose = CardPose(offset: Offset(120, -40), origin: Offset(80, 200), rotation: 5, lift: 1);
      final moved = MatrixUtils.transformPoint(cardTransform(pose, _size), const Offset(80, 200));
      expect(moved.dx, closeTo(200, 1e-6));
      expect(moved.dy, closeTo(160, 1e-6));
    });

    test('turning clockwise brings a point right of the origin down', () {
      const pose = CardPose(offset: Offset.zero, origin: Offset(100, 300), rotation: 5);
      final turned = MatrixUtils.transformPoint(cardTransform(pose, _size), const Offset(200, 300));
      expect(turned.dy, greaterThan(300));
    });

    test('mirrored, it leaves to the left around the mirrored point', () {
      const pose = CardPose(offset: Offset(120, 0), origin: Offset(80, 200), rotation: 5, mirrored: true);
      final transform = cardTransform(pose, _size);
      final moved = MatrixUtils.transformPoint(transform, const Offset(310, 200));
      expect(moved.dx, closeTo(190, 1e-6));
      expect(moved.dy, closeTo(200, 1e-6));
      final turned = MatrixUtils.transformPoint(transform, const Offset(210, 200));
      expect(turned.dy, greaterThan(200), reason: 'mirrored, the turn mirrors too');
    });
  });

  group('sliding in and out', () {
    test('comes in from the far edge and goes out a tenth past it', () {
      expect(slideOffset(0, 390, closing: false), 390);
      expect(slideOffset(0, 390, closing: true), closeTo(429, 1e-9));
      expect(slideOffset(1, 390, closing: true), 0);
    });

    test('is lifted while far and flat within 40 % of a width', () {
      expect(slideLift(390, 390), closeTo(0.6, 1e-9));
      expect(slideLift(78, 390), closeTo(0.3, 1e-9));
      expect(slideLift(0, 390), 0);
    });
  });

  group('the lift', () {
    test('rises in 140 ms, easing out', () {
      expect(liftAfterGrab(Duration.zero), 0);
      expect(liftAfterGrab(const Duration(milliseconds: 70)), closeTo(0.875, 1e-9));
      expect(liftAfterGrab(kSwipeBackLift), 1);
      expect(liftAfterGrab(const Duration(seconds: 1)), 1);
    });

    test('a caught card lifts on from where it is', () {
      for (final lift in [0.0, 0.2, 0.5, 0.9]) {
        expect(liftAfterGrab(grabbedFor(lift)), closeTo(lift, 1e-3));
      }
    });

    test('dropping back, it flattens by distance and is flat within 224 ms', () {
      expect(settlingLift(from: 1, startDistance: 100, distance: 50, elapsed: Duration.zero), 0.5);
      expect(settlingLift(from: 1, startDistance: 100, distance: 100, elapsed: const Duration(milliseconds: 112)), 0.5);
      expect(settlingLift(from: 1, startDistance: 100, distance: 100, elapsed: const Duration(milliseconds: 224)), 0);
    });
  });

  group('the turn', () {
    test('is clockwise grabbed above the middle and the other way below', () {
      expect(tiltFactor(0, 844), 1);
      expect(tiltFactor(844, 844), -1);
      expect(tiltFactor(422, 844), 0.55);
      expect(tiltFactor(211, 844), closeTo(0.775, 1e-9));
    });

    test('reaches 7° across a full width, plus swing and spin', () {
      expect(cardRotation(tilt: 1, dx: 390, width: 390), 7);
      expect(cardRotation(tilt: -0.55, dx: 195, width: 390), closeTo(-1.925, 1e-9));
      expect(cardRotation(tilt: 1, dx: 0, width: 390, sway: 2, spin: 10), 12);
    });

    test('the swing follows the speed, up to 4°, and only with tilt', () {
      expect(swayTarget(0, 1), 0);
      expect(swayTarget(1, 1), 3);
      expect(swayTarget(0.6, 2), 4);
      expect(swayTarget(-1, 1), -3);
    });

    test('the swing goes a quarter of the way each 60 Hz frame', () {
      const frame = Duration(microseconds: 16667);
      expect(swayToward(0, 4, frame), closeTo(1, 1e-3));
      expect(swayToward(0, 4, const Duration(seconds: 1)), closeTo(4, 1e-6));
      expect(swayDecay(4, frame), closeTo(3.2, 1e-3));
    });
  });

  test('past the starting edge a drag gives only a third', () {
    expect(rubberBand(-30), closeTo(-9, 1e-9));
    expect(rubberBand(30), 30);
  });

  group('letting go', () {
    test('leaves past 30 % of the width, standing still', () {
      expect(leavesOnRelease(const Offset(118, 0), Offset.zero, _size), isTrue);
      expect(leavesOnRelease(const Offset(116, 0), Offset.zero, _size), isFalse);
    });

    test('leaves past the ellipse through 30 % across and 25 % down', () {
      expect(leavesOnRelease(const Offset(0, 212), Offset.zero, _size), isTrue);
      expect(leavesOnRelease(const Offset(78, 169), Offset.zero, _size), isTrue);
      expect(leavesOnRelease(const Offset(58, 127), Offset.zero, _size), isFalse);
    });

    test('a throw away from rest leaves after 16 px', () {
      expect(leavesOnRelease(const Offset(20, 0), const Offset(0.4, 0), _size), isTrue);
      expect(leavesOnRelease(const Offset(10, 0), const Offset(0.4, 0), _size), isFalse);
      expect(leavesOnRelease(const Offset(20, 0), const Offset(0.3, 0), _size), isFalse);
    });

    test('a flick back towards rest never leaves, a slow drift back still does', () {
      expect(leavesOnRelease(const Offset(200, 0), const Offset(-0.3, 0), _size), isFalse);
      expect(leavesOnRelease(const Offset(200, 0), const Offset(-0.1, 0), _size), isTrue);
    });
  });

  group('the page behind', () {
    test('shows as the card leaves, and not when it is dragged past its edge', () {
      expect(behindUncovered(Offset.zero, _size), 0);
      expect(behindUncovered(const Offset(-50, 0), _size), 0);
      expect(behindUncovered(const Offset(165.75, 0), _size), closeTo(0.5, 1e-9));
      expect(behindUncovered(const Offset(331.5, 0), _size), 1);
      expect(behindUncovered(const Offset(0, -400), _size), 1);
    });

    test('is 96 %, rounded and dimmed under the card, whole when clear', () {
      expect(behindLook(0), (scale: 0.96, radius: 16.0, dim: 0.25));
      expect(behindLook(1), (scale: 1.0, radius: 0.0, dim: 0.0));
      final half = behindLook(0.5);
      expect(half.scale, closeTo(0.98, 1e-9));
      expect(half.radius, 8);
      expect(half.dim, 0.125);
    });
  });

  group('a throw', () {
    test('goes along the throw until the card is off screen, in 140–260 ms', () {
      final flight = cardThrow(offset: const Offset(150, 0), velocity: const Offset(2, 0), size: _size, tilt: 0);
      expect(flight.direction, const Offset(1, 0));
      expect(flight.travel, 380);
      expect(flight.duration, const Duration(milliseconds: 260));
      expect(flight.spin, 0);
      expect(flight.offsetAt(1), const Offset(530, 0));
      expect(flight.offsetAt(0.5).dx, closeTo(150 + 380 * 0.75, 1e-9));
    });

    test('let go slowly, it leaves along the drag', () {
      final flight = cardThrow(offset: const Offset(150, 80), velocity: const Offset(0.1, 0), size: _size, tilt: 1);
      expect(flight.direction.dx, closeTo(150 / 170, 1e-9));
      expect(flight.direction.dy, closeTo(80 / 170, 1e-9));
    });

    test('a fast short throw still takes 140 ms', () {
      final flight = cardThrow(offset: const Offset(500, 0), velocity: const Offset(3, 0), size: _size, tilt: 0);
      expect(flight.travel, 40);
      expect(flight.duration, const Duration(milliseconds: 140));
    });

    test('spins on the way it turns', () {
      expect(cardThrow(offset: const Offset(150, 0), velocity: const Offset(2, 0), size: _size, tilt: 0.7).spin, 10);
      expect(cardThrow(offset: const Offset(150, 0), velocity: const Offset(2, 0), size: _size, tilt: -0.7).spin, -10);
      expect(cardThrow(offset: const Offset(-40, 0), velocity: const Offset(-1, 0), size: _size, tilt: 0.7).spin, -10);
    });
  });

  group('the spring', () {
    test('drops the card back without overshooting when let go still', () {
      final spring = CardSpring(const Offset(100, -30), Offset.zero);
      var last = 100.0;
      for (var ms = 0; ms <= 600; ms += 10) {
        final at = spring.offsetAt(ms / 1000);
        expect(at.dx, inInclusiveRange(0, last + 1e-9), reason: '$ms ms');
        expect(at.dy, lessThanOrEqualTo(1e-9), reason: '$ms ms');
        last = at.dx;
      }
      expect(spring.landedAt(0.6), isTrue);
      expect(spring.landedAt(0.05), isFalse);
    });

    test('carries the speed it was let go at', () {
      final spring = CardSpring(const Offset(40, 0), const Offset(600, 0));
      expect(spring.offsetAt(0.02).dx, greaterThan(40));
      expect(spring.velocityAt(0), const Offset(600, 0));
    });
  });
}
