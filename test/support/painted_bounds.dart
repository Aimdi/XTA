import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

/// Test runs draw Material icons as boxes unless the real font is loaded.
Future<void> loadMaterialIcons() =>
    (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();

/// What is actually painted inside each of [boxes], in logical pixels, read back from the [shot] repaint boundary.
///
/// A pixel counts as painted when it differs from the colour at its box's top left corner, so each box must start on
/// background.
Future<Map<K, Rect>> paintedBounds<K>(
  WidgetTester tester,
  Finder shot,
  Map<K, Rect> boxes, {
  double pixelRatio = 4,
}) async {
  final boundary = tester.renderObject<RenderRepaintBoundary>(shot);
  final image = await tester.runAsync(() => boundary.toImage(pixelRatio: pixelRatio));
  final pixels = await tester.runAsync(() => image!.toByteData(format: ui.ImageByteFormat.rawRgba));
  final origin = boundary.localToGlobal(Offset.zero);
  return {
    for (final MapEntry(:key, :value) in boxes.entries)
      key: _bounds(pixels!, image!.width, value.shift(-origin), pixelRatio).shift(origin),
  };
}

Rect _bounds(ByteData pixels, int width, Rect box, double ratio) {
  int at(int x, int y, int channel) => pixels.getUint8((y * width + x) * 4 + channel);
  final left = (box.left * ratio).round();
  final top = (box.top * ratio).round();
  bool painted(int x, int y) => [0, 1, 2].any((c) => (at(x, y, c) - at(left, top, c)).abs() > 64);
  final hits = [
    for (var y = top; y < (box.bottom * ratio).round(); y++)
      for (var x = left; x < (box.right * ratio).round(); x++)
        if (painted(x, y)) Offset(x.toDouble(), y.toDouble()),
  ];
  if (hits.isEmpty) return Rect.zero;
  final xs = hits.map((hit) => hit.dx);
  final ys = hits.map((hit) => hit.dy);
  return Rect.fromLTRB(
    xs.reduce(math.min) / ratio,
    ys.reduce(math.min) / ratio,
    (xs.reduce(math.max) + 1) / ratio,
    (ys.reduce(math.max) + 1) / ratio,
  );
}
