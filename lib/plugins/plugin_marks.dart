import 'package:flutter/material.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/bluesky/bluesky_butterfly_icon.dart';
import 'package:xta/plugins/plugin.dart';

/// Service mark for a plugin — official glyphs where we have them, Material
/// fallback otherwise.
///
/// Path glyphs are Simple Icons (CC0). Bluesky reuses the butterfly already
/// painted on cards so the strip and a mixed feed do not disagree.
class PluginBrandMark extends StatelessWidget {
  final XtaPlugin plugin;
  final double size;
  final Color? color;

  const PluginBrandMark({
    super.key,
    required this.plugin,
    this.size = 24,
    this.color,
  });

  @override
  Widget build(BuildContext context) {
    final tint = color ?? plugin.brandColor;
    return switch (plugin.id) {
      pluginIdBluesky => BlueskyButterflyIcon(size: size, color: tint),
      pluginIdSubstack => _paint(_SubstackPainter(tint)),
      pluginIdPixiv => _paint(_PathPainter(_pixiv, tint)),
      pluginIdMastodon => _paint(_PathPainter(_mastodon, tint)),
      pluginIdTiktok => _paint(_PathPainter(_tiktok, tint)),
      pluginIdInstagram => _paint(_InstagramPainter(tint)),
      pluginIdEhViewer => _paint(_EhHPainter(tint)),
      pluginIdBooru => Icon(Icons.inventory_2, size: size, color: tint),
      _ => Icon(plugin.icon, size: size, color: tint),
    };
  }

  Widget _paint(CustomPainter painter) =>
      CustomPaint(size: Size.square(size), painter: painter);
}

/// The same mark the store, strip and plugin-timelines sheet should share.
Widget pluginMark(XtaPlugin plugin, {double size = 24, Color? color}) {
  return PluginBrandMark(plugin: plugin, size: size, color: color);
}

class _PathPainter extends CustomPainter {
  final Path path;
  final Color color;

  _PathPainter(this.path, this.color);

  @override
  void paint(Canvas canvas, Size size) {
    canvas
      ..save()
      ..scale(size.shortestSide / 24)
      ..drawPath(
        path,
        Paint()
          ..color = color
          ..style = PaintingStyle.fill
          ..isAntiAlias = true,
      )
      ..restore();
  }

  @override
  bool shouldRepaint(covariant _PathPainter old) =>
      old.color != color || old.path != path;
}

/// Substack's three-bar / folded-paper mark (Simple Icons geometry).
class _SubstackPainter extends CustomPainter {
  final Color color;

  _SubstackPainter(this.color);

  @override
  void paint(Canvas canvas, Size size) {
    final s = size.shortestSide / 24;
    final paint = Paint()
      ..color = color
      ..style = PaintingStyle.fill
      ..isAntiAlias = true;
    canvas
      ..save()
      ..scale(s)
      ..drawRect(const Rect.fromLTWH(1.46, 0, 21.08, 2.836), paint)
      ..drawRect(const Rect.fromLTWH(1.46, 5.406, 21.08, 2.836), paint)
      ..drawPath(_substackFold, paint)
      ..restore();
  }

  @override
  bool shouldRepaint(covariant _SubstackPainter old) => old.color != color;
}

final _substackFold = Path()
  ..moveTo(1.46, 10.812)
  ..lineTo(1.46, 24)
  ..lineTo(12, 18.11)
  ..lineTo(22.54, 24)
  ..lineTo(22.54, 10.812)
  ..close();

/// Instagram camera glyph: rounded square, lens, viewfinder.
class _InstagramPainter extends CustomPainter {
  final Color color;

  _InstagramPainter(this.color);

  @override
  void paint(Canvas canvas, Size size) {
    final s = size.shortestSide / 24;
    final paint = Paint()
      ..color = color
      ..style = PaintingStyle.fill
      ..isAntiAlias = true;
    final path = Path()
      ..fillType = PathFillType.evenOdd
      ..addRRect(RRect.fromLTRBR(2, 2, 22, 22, const Radius.circular(6)))
      ..addRRect(
        RRect.fromLTRBR(4.1, 4.1, 19.9, 19.9, const Radius.circular(4.2)),
      )
      ..addOval(Rect.fromCircle(center: const Offset(12, 12.2), radius: 5.1))
      ..addOval(Rect.fromCircle(center: const Offset(12, 12.2), radius: 3.15))
      ..addOval(
        Rect.fromCircle(center: const Offset(17.35, 6.65), radius: 1.25),
      );
    canvas
      ..save()
      ..scale(s)
      ..drawPath(path, paint)
      ..restore();
  }

  @override
  bool shouldRepaint(covariant _InstagramPainter old) => old.color != color;
}

/// A bold H — the usual shorthand for the E-Hentai / ExHentai plugin.
class _EhHPainter extends CustomPainter {
  final Color color;

  _EhHPainter(this.color);

  @override
  void paint(Canvas canvas, Size size) {
    final s = size.shortestSide / 24;
    final paint = Paint()
      ..color = color
      ..style = PaintingStyle.fill
      ..isAntiAlias = true;
    canvas
      ..save()
      ..scale(s)
      ..drawRRect(
        RRect.fromLTRBR(3.6, 2.4, 8.4, 21.6, const Radius.circular(1.2)),
        paint,
      )
      ..drawRRect(
        RRect.fromLTRBR(15.6, 2.4, 20.4, 21.6, const Radius.circular(1.2)),
        paint,
      )
      ..drawRRect(
        RRect.fromLTRBR(3.6, 10.1, 20.4, 13.9, const Radius.circular(1)),
        paint,
      )
      ..restore();
  }

  @override
  bool shouldRepaint(covariant _EhHPainter old) => old.color != color;
}

final _tiktok = Path()
  ..moveTo(12.525, 0.02)
  ..cubicTo(13.835, 0, 15.135, 0.01, 16.435, 0)
  ..cubicTo(16.515, 1.53, 17.065, 3.09, 18.185, 4.17)
  ..cubicTo(19.305, 5.28, 20.885, 5.79, 22.425, 5.96)
  ..lineTo(22.425, 9.99)
  ..cubicTo(20.985, 9.94, 19.535, 9.64, 18.225, 9.02)
  ..cubicTo(17.655, 8.76, 17.125, 8.43, 16.605, 8.09)
  ..cubicTo(16.595, 11.01, 16.615, 13.93, 16.585, 16.84)
  ..cubicTo(16.505, 18.24, 16.045, 19.63, 15.235, 20.78)
  ..cubicTo(13.925, 22.7, 11.655, 23.95, 9.325, 23.99)
  ..cubicTo(7.895, 24.07, 6.465, 23.68, 5.245, 22.96)
  ..cubicTo(3.225, 21.77, 1.805, 19.59, 1.595, 17.25)
  ..cubicTo(1.575, 16.75, 1.565, 16.25, 1.585, 15.76)
  ..cubicTo(1.765, 13.86, 2.705, 12.04, 4.165, 10.8)
  ..cubicTo(5.825, 9.36, 8.145, 8.67, 10.315, 9.08)
  ..cubicTo(10.335, 10.56, 10.275, 12.04, 10.275, 13.52)
  ..cubicTo(9.285, 13.2, 8.125, 13.29, 7.255, 13.89)
  ..cubicTo(6.625, 14.3, 6.145, 14.93, 5.895, 15.64)
  ..cubicTo(5.685, 16.15, 5.745, 16.71, 5.755, 17.25)
  ..cubicTo(5.995, 18.89, 7.575, 20.27, 9.255, 20.12)
  ..cubicTo(10.375, 20.11, 11.445, 19.46, 12.025, 18.51)
  ..cubicTo(12.215, 18.18, 12.425, 17.84, 12.435, 17.45)
  ..cubicTo(12.535, 15.66, 12.495, 13.88, 12.505, 12.09)
  ..cubicTo(12.515, 8.06, 12.495, 4.04, 12.525, 0.02)
  ..close();

final _mastodon = Path()
  ..fillType = PathFillType.evenOdd
  ..moveTo(23.268, 5.313)
  ..cubicTo(22.918, 2.735, 20.651, 0.703, 17.964, 0.309)
  ..cubicTo(17.51, 0.242, 15.792, 0, 11.813, 0)
  ..lineTo(11.783, 0)
  ..cubicTo(7.803, 0, 6.948, 0.242, 6.495, 0.309)
  ..cubicTo(3.882, 0.692, 1.496, 2.518, 0.917, 5.127)
  ..cubicTo(0.64, 6.412, 0.61, 7.837, 0.661, 9.143)
  ..cubicTo(0.735, 11.017, 0.749, 12.888, 0.921, 14.754)
  ..cubicTo(1.039, 15.994, 1.246, 17.224, 1.541, 18.434)
  ..cubicTo(2.091, 20.671, 4.318, 22.532, 6.501, 23.291)
  ..cubicTo(8.837, 24.083, 11.35, 24.214, 13.757, 23.671)
  ..cubicTo(14.022, 23.61, 14.284, 23.539, 14.543, 23.458)
  ..cubicTo(15.128, 23.274, 15.813, 23.068, 16.317, 22.705)
  ..cubicTo(16.331, 22.695, 16.339, 22.679, 16.34, 22.662)
  ..lineTo(16.34, 20.853)
  ..cubicTo(16.34, 20.837, 16.333, 20.822, 16.32, 20.812)
  ..cubicTo(16.307, 20.802, 16.29, 20.798, 16.274, 20.802)
  ..cubicTo(14.731, 21.167, 13.151, 21.35, 11.565, 21.347)
  ..cubicTo(8.835, 21.347, 8.102, 20.063, 7.891, 19.529)
  ..cubicTo(7.722, 19.067, 7.615, 18.586, 7.572, 18.096)
  ..cubicTo(7.571, 18.079, 7.578, 18.063, 7.591, 18.052)
  ..cubicTo(7.604, 18.042, 7.622, 18.038, 7.638, 18.042)
  ..cubicTo(9.155, 18.405, 10.71, 18.588, 12.27, 18.588)
  ..cubicTo(12.646, 18.588, 13.02, 18.588, 13.395, 18.578)
  ..cubicTo(14.965, 18.534, 16.619, 18.454, 18.163, 18.156)
  ..cubicTo(18.201, 18.148, 18.24, 18.141, 18.273, 18.132)
  ..cubicTo(20.708, 17.668, 23.026, 16.212, 23.262, 12.528)
  ..cubicTo(23.27, 12.383, 23.292, 11.008, 23.292, 10.858)
  ..cubicTo(23.294, 10.346, 23.459, 7.228, 23.268, 5.313)
  ..close()
  ..moveTo(19.52, 14.508)
  ..lineTo(16.959, 14.508)
  ..lineTo(16.959, 8.29)
  ..cubicTo(16.959, 6.981, 16.409, 6.314, 15.289, 6.314)
  ..cubicTo(14.059, 6.314, 13.443, 7.104, 13.443, 8.664)
  ..lineTo(13.443, 12.067)
  ..lineTo(10.897, 12.067)
  ..lineTo(10.897, 8.663)
  ..cubicTo(10.897, 7.103, 10.28, 6.313, 9.049, 6.313)
  ..cubicTo(7.937, 6.313, 7.381, 6.981, 7.379, 8.29)
  ..lineTo(7.379, 14.508)
  ..lineTo(4.822, 14.508)
  ..lineTo(4.822, 8.102)
  ..cubicTo(4.822, 6.792, 5.159, 5.752, 5.833, 4.982)
  ..cubicTo(6.529, 4.212, 7.441, 3.818, 8.573, 3.818)
  ..cubicTo(9.884, 3.818, 10.875, 4.318, 11.535, 5.316)
  ..lineTo(12.173, 6.376)
  ..lineTo(12.811, 5.316)
  ..cubicTo(13.471, 4.317, 14.461, 3.818, 15.771, 3.818)
  ..cubicTo(16.901, 3.818, 17.814, 4.213, 18.511, 4.982)
  ..cubicTo(19.186, 5.752, 19.523, 6.792, 19.523, 8.102)
  ..close();

final _pixiv = Path()
  ..fillType = PathFillType.evenOdd
  ..moveTo(4.935, 0)
  ..cubicTo(3.6253, -0.0029, 2.3683, 0.5161, 1.4422, 1.4422)
  ..cubicTo(0.5161, 2.3683, -0.0029, 3.6253, 0, 4.935)
  ..lineTo(0, 19.065)
  ..cubicTo(-0.0029, 20.3747, 0.5161, 21.6317, 1.4422, 22.5578)
  ..cubicTo(2.3683, 23.4839, 3.6253, 24.0029, 4.935, 24)
  ..lineTo(19.065, 24)
  ..cubicTo(20.3747, 24.0029, 21.6317, 23.4839, 22.5578, 22.5578)
  ..cubicTo(23.4839, 21.6317, 24.0029, 20.3747, 24, 19.065)
  ..lineTo(24, 4.935)
  ..cubicTo(24.0029, 3.6253, 23.4839, 2.3683, 22.5578, 1.4422)
  ..cubicTo(21.6317, 0.5161, 20.3747, -0.0029, 19.065, 0)
  ..close()
  ..moveTo(12.745, 4.547)
  ..cubicTo(14.926, 4.547, 16.803, 5.223, 18.144, 6.394)
  ..cubicTo(19.4964, 7.5635, 20.2695, 9.2661, 20.26, 11.054)
  ..cubicTo(20.265, 12.908, 19.38, 14.53, 18.003, 15.617)
  ..cubicTo(16.628, 16.709, 14.778, 17.314, 12.745, 17.314)
  ..cubicTo(10.431, 17.314, 8.285, 16.472, 8.285, 16.472)
  ..lineTo(8.285, 19.19)
  ..cubicTo(8.682, 19.306, 9.333, 19.555, 8.92, 19.969)
  ..lineTo(5.79, 19.969)
  ..cubicTo(5.38, 19.559, 5.98, 19.319, 6.434, 19.19)
  ..lineTo(6.434, 7.666)
  ..cubicTo(5.381, 8.476, 4.841, 9.176, 4.566, 9.697)
  ..cubicTo(4.886, 10.717, 4.282, 10.666, 4.282, 10.666)
  ..lineTo(3.192, 8.936)
  ..cubicTo(3.192, 8.936, 7.06, 4.546, 12.745, 4.546)
  ..close()
  ..moveTo(12.555, 5.518)
  ..cubicTo(11.132, 5.515, 9.371, 5.991, 8.285, 6.762)
  ..lineTo(8.285, 15.408)
  ..cubicTo(9.273, 15.895, 10.769, 16.24, 12.545, 16.24)
  ..lineTo(12.555, 16.24)
  ..cubicTo(14.151, 16.24, 15.535, 15.647, 16.485, 14.707)
  ..cubicTo(17.437, 13.759, 17.971, 12.524, 17.977, 11.024)
  ..cubicTo(17.972, 9.484, 17.473, 8.16, 16.557, 7.164)
  ..cubicTo(15.639, 6.172, 14.283, 5.519, 12.555, 5.518)
  ..close();
