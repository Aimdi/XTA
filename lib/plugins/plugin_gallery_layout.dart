import 'dart:math' as math;
import 'package:flutter/material.dart';

/// Artwork stays readable at large text sizes and gains columns on wide views.
int pluginGalleryColumns(double width, TextScaler textScaler) {
  final minWidth = textScaler.scale(14) > 20 ? 240.0 : 152.0;
  return math.max(1, math.min(4, (width / minWidth).floor()));
}
