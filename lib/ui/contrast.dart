import 'dart:math' as math;

import 'package:flutter/material.dart';

/// WCAG 2.1 relative luminance.
double relativeLuminance(Color c) {
  double channel(double v) => v <= 0.03928
      ? v / 12.92
      : math.pow((v + 0.055) / 1.055, 2.4).toDouble();
  return 0.2126 * channel(c.r) +
      0.7152 * channel(c.g) +
      0.0722 * channel(c.b);
}

double contrastRatio(Color a, Color b) {
  final la = relativeLuminance(a);
  final lb = relativeLuminance(b);
  return (math.max(la, lb) + 0.05) / (math.min(la, lb) + 0.05);
}

/// Returns [foreground], or the nearest lighter/darker variant of it that
/// reaches [minRatio] against [background].
///
/// Used instead of hand-tuning a colour per theme: a token is corrected against
/// the background it is actually painted on, so every theme (and every
/// user-chosen group colour washed over it) stays legible by construction.
Color ensureContrast(
  Color foreground,
  Color background, {
  double minRatio = 4.5,
}) {
  if (contrastRatio(foreground, background) >= minRatio) {
    return foreground;
  }

  final target = relativeLuminance(background) > 0.18
      ? Colors.black
      : Colors.white;
  var candidate = foreground;
  for (var step = 1; step <= 20; step++) {
    candidate = Color.lerp(foreground, target, step / 20)!;
    if (contrastRatio(candidate, background) >= minRatio) {
      return candidate;
    }
  }
  return target;
}

/// Chooses the higher-contrast neutral foreground for a solid fill.
///
/// XTA lets readers choose vivid accents. A fixed white foreground is not
/// legible on yellow, orange, green, pink, or the default blue, so controls
/// that paint the raw accent as a fill must derive their foreground from the
/// actual selected colour.
Color contrastingForeground(Color background) {
  final blackRatio = contrastRatio(Colors.black, background);
  final whiteRatio = contrastRatio(Colors.white, background);
  return blackRatio >= whiteRatio ? Colors.black : Colors.white;
}
