import 'dart:math' as math;

import 'package:flutter/material.dart';

/// How long X's like takes from the tap to the settled heart.
const Duration kHeartBurstDuration = Duration(milliseconds: 450);

/// X's like, driven by [progress] from 0 to 1: the outline heart shrinks to a
/// dot, a circle blooms out of it and thins into a ring while small dots
/// scatter, and the filled heart pops back with a little overshoot.
///
/// The burst is painted around the heart's box without taking space, so the
/// row the heart sits in never moves. At rest (0, or once finished) it is
/// just [outline] or [filled], whichever [filledAtRest] asks for.
class HeartBurst extends StatelessWidget {
  final Animation<double> progress;
  final Color color;
  final double size;
  final Widget outline;
  final Widget filled;
  final bool filledAtRest;

  const HeartBurst({
    super.key,
    required this.progress,
    required this.color,
    required this.size,
    required this.outline,
    required this.filled,
    required this.filledAtRest,
  });

  @override
  Widget build(BuildContext context) {
    return SizedBox.square(
      dimension: size,
      child: AnimatedBuilder(
        animation: progress,
        builder: (context, _) {
          final t = progress.value;
          if (t <= 0 || t >= 1) {
            return filledAtRest ? filled : outline;
          }
          return Stack(
            clipBehavior: Clip.none,
            alignment: Alignment.center,
            children: [
              Positioned.fill(
                child: CustomPaint(
                  painter: HeartBurstPainter(t: t, color: color),
                ),
              ),
              if (t < _shrinkEnd) Transform.scale(scale: 1 - _phase(t, 0, _shrinkEnd), child: outline),
              if (t >= _popStart) Transform.scale(scale: _popScale(_phase(t, _popStart, 1)), child: filled),
            ],
          );
        },
      ),
    );
  }

  static const _shrinkEnd = 0.15;
  static const _popStart = 0.45;

  /// 0 → 1.2 → 1, the overshoot X's heart lands with.
  static double _popScale(double p) => p < 0.6
      ? 1.2 * Curves.easeOutCubic.transform(p / 0.6)
      : 1.2 - 0.2 * Curves.easeInOutCubic.transform((p - 0.6) / 0.4);
}

/// Where [t] is inside the stretch from [start] to [end], as 0 to 1.
double _phase(double t, double start, double end) => ((t - start) / (end - start)).clamp(0.0, 1.0);

/// The bloom, the ring and the scattering dots of [HeartBurst] at [t].
class HeartBurstPainter extends CustomPainter {
  final double t;
  final Color color;

  const HeartBurstPainter({required this.t, required this.color});

  /// The bloom's widest, as a share of the heart's box.
  static const _reach = 0.7;
  static const _dotPairs = 7;

  @override
  void paint(Canvas canvas, Size size) {
    final center = size.center(Offset.zero);
    final radius = size.shortestSide * _reach;
    _paintBloom(canvas, center, radius);
    _paintDots(canvas, center, radius);
  }

  /// A filled circle growing out of the dot, then hollowing into a ring that
  /// thins away.
  void _paintBloom(Canvas canvas, Offset center, double radius) {
    final grow = Curves.easeOutCubic.transform(_phase(t, 0.15, 0.45));
    final hollow = _phase(t, 0.40, 0.70);
    if (grow <= 0 || hollow >= 1) {
      return;
    }
    final outer = radius * grow;
    final width = outer * (1 - hollow);
    canvas.drawCircle(
      center,
      outer - width / 2,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = width
        ..color = Color.lerp(color, _shifted(color, 25), grow)!,
    );
  }

  /// Seven pairs of dots flying out of the ring and fading as they go.
  void _paintDots(Canvas canvas, Offset center, double radius) {
    final fly = Curves.easeOutCubic.transform(_phase(t, 0.35, 0.85));
    if (fly <= 0 || fly >= 1) {
      return;
    }
    final distance = radius * (0.9 + 0.6 * fly);
    final dot = radius * 0.13 * (1 - fly);
    for (var i = 0; i < _dotPairs; i++) {
      final angle = -math.pi / 2 + i * 2 * math.pi / _dotPairs;
      final paint = Paint()..color = _shifted(color, i * 360 / _dotPairs).withValues(alpha: 1 - fly * 0.6);
      for (final (offset, scale) in [(-0.18, 1.0), (0.18, 0.7)]) {
        final at = center + Offset.fromDirection(angle + offset, distance * (scale == 1.0 ? 1 : 0.85));
        canvas.drawCircle(at, dot * scale, paint);
      }
    }
  }

  /// [color] turned [degrees] around the colour wheel, for the dots' variety
  /// and the bloom's warming as it opens.
  static Color _shifted(Color color, double degrees) {
    final hsl = HSLColor.fromColor(color);
    return hsl.withHue((hsl.hue + degrees) % 360).toColor();
  }

  @override
  bool shouldRepaint(HeartBurstPainter old) => old.t != t || old.color != color;
}
