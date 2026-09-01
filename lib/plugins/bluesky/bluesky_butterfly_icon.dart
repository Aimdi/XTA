import 'package:flutter/material.dart';

/// Compact Bluesky butterfly mark for mixed feeds and group member rows.
///
/// Path taken from the public Bootstrap Icons Bluesky glyph (16×16 viewBox).
class BlueskyButterflyIcon extends StatelessWidget {
  final double size;
  final Color color;

  const BlueskyButterflyIcon({
    super.key,
    this.size = 14,
    this.color = const Color(0xFF0085FF),
  });

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: size,
      height: size,
      child: CustomPaint(
        painter: _BlueskyButterflyPainter(color),
      ),
    );
  }
}

class _BlueskyButterflyPainter extends CustomPainter {
  final Color color;

  _BlueskyButterflyPainter(this.color);

  /// Bootstrap Icons `bi-bluesky` path, viewBox 0 0 16 16.
  static final Path _butterfly = Path()
    ..moveTo(3.4680, 1.9480)
    ..cubicTo(5.3030, 3.3250, 7.2760, 6.1180, 8.0000, 7.6160)
    ..cubicTo(8.7250, 6.1180, 10.6980, 3.3260, 12.5320, 1.9480)
    ..cubicTo(13.8550, 0.9550, 16.0000, 0.1860, 16.0000, 2.6320)
    ..cubicTo(16.0000, 3.1210, 15.7200, 6.7370, 15.5560, 7.3240)
    ..cubicTo(14.9840, 9.3640, 12.9030, 9.8850, 11.0520, 9.5700)
    ..cubicTo(14.2880, 10.1210, 15.1120, 11.9450, 13.3330, 13.7700)
    ..cubicTo(9.9570, 17.2340, 8.4810, 12.9000, 8.1030, 11.7900)
    ..cubicTo(8.0330, 11.5860, 8.0000, 11.4900, 8.0000, 11.5720)
    ..cubicTo(8.0000, 11.4910, 7.9670, 11.5860, 7.8980, 11.7900)
    ..cubicTo(7.5190, 12.9000, 6.0430, 17.2340, 2.6670, 13.7700)
    ..cubicTo(0.8890, 11.9450, 1.7120, 10.1200, 4.9470, 9.5700)
    ..cubicTo(3.0970, 9.8850, 1.0150, 9.3650, 0.4440, 7.3240)
    ..cubicTo(0.2800, 6.7370, 0.0000, 3.1200, 0.0000, 2.6320)
    ..cubicTo(0.0000, 0.1860, 2.1450, 0.9550, 3.4680, 1.9480);

  @override
  void paint(Canvas canvas, Size size) {
    final scale = size.shortestSide / 16;
    canvas
      ..scale(scale)
      ..drawPath(
        _butterfly,
        Paint()
          ..color = color
          ..style = PaintingStyle.fill
          ..isAntiAlias = true,
      );
  }

  @override
  bool shouldRepaint(covariant _BlueskyButterflyPainter oldDelegate) => oldDelegate.color != color;
}
