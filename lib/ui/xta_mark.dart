import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:xta/ui/x_look_theme.dart';

/// The launcher mark: two congruent ellipses crossed at ±45°, stroked with one
/// even width and no fill.
///
/// The ratios are measured from `assets/icon.png` as fractions of the mark's
/// own square extent, so a drawn mark matches the icon at any size.
class XtaMarkPainter extends CustomPainter {
  final Color color;

  const XtaMarkPainter(this.color);

  static const double semiMajor = 0.608;
  static const double semiMinor = 0.229;
  static const double strokeShare = 0.089;
  static const List<double> angles = [math.pi / 4, -math.pi / 4];

  @override
  void paint(Canvas canvas, Size size) {
    final s = size.shortestSide;
    final paint = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = strokeShare * s
      ..isAntiAlias = true;
    final oval = Rect.fromCenter(center: Offset.zero, width: 2 * semiMajor * s, height: 2 * semiMinor * s);
    canvas.translate(size.width / 2, size.height / 2);
    for (final angle in angles) {
      canvas
        ..save()
        ..rotate(angle)
        ..drawOval(oval, paint)
        ..restore();
    }
  }

  @override
  bool shouldRepaint(covariant XtaMarkPainter old) => old.color != color;
}

/// A point on the centreline of one of the mark's ellipses, relative to the
/// mark's centre: [angle] picks the ellipse, [t] in 0..1 walks around it.
Offset xtaMarkOrbitPoint(double markSize, double angle, double t) {
  final theta = 2 * math.pi * t;
  final x = XtaMarkPainter.semiMajor * markSize * math.cos(theta);
  final y = XtaMarkPainter.semiMinor * markSize * math.sin(theta);
  return Offset(x * math.cos(angle) - y * math.sin(angle), x * math.sin(angle) + y * math.cos(angle));
}

/// The mark as a widget, in the text colour unless told otherwise. Decorative,
/// so it is left out of the semantics tree.
class XtaMark extends StatelessWidget {
  final double size;
  final Color? color;

  const XtaMark({super.key, this.size = 56, this.color});

  @override
  Widget build(BuildContext context) {
    final tint = color ?? XLookTokens.maybeOf(context)?.onBackground ?? Theme.of(context).colorScheme.onSurface;
    return ExcludeSemantics(
      child: CustomPaint(size: Size.square(size), painter: XtaMarkPainter(tint)),
    );
  }
}
