import 'package:flutter/widgets.dart';

/// The width [labels] take side by side in [style] at the text scale of
/// [context], each with [padding] beside it: what a row of tabs needs before
/// it is laid out.
double labelsWidth(BuildContext context, List<String> labels, TextStyle style, {double padding = 0}) =>
    labels.fold(0.0, (width, label) {
      final painter = TextPainter(
        text: TextSpan(text: label, style: style),
        textDirection: Directionality.of(context),
        textScaler: MediaQuery.textScalerOf(context),
        maxLines: 1,
      )..layout();
      final measured = painter.width + padding;
      painter.dispose();
      return width + measured;
    });
