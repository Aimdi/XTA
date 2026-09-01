import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/theme_presets.dart';
import 'package:xta/ui/x_look_theme.dart';

double _luminance(Color c) {
  double channel(double v) => v <= 0.03928 ? v / 12.92 : math.pow((v + 0.055) / 1.055, 2.4).toDouble();
  return 0.2126 * channel(c.r) + 0.7152 * channel(c.g) + 0.0722 * channel(c.b);
}

double _contrast(Color a, Color b) {
  final la = _luminance(a);
  final lb = _luminance(b);
  return (math.max(la, lb) + 0.05) / (math.min(la, lb) + 0.05);
}

/// The chrome the quote card used before: a translucent surface tint, which is
/// what made nested quotes invisible on the dark themes.
Color _previousBorder(ColorScheme scheme) => Color.alphaBlend(
      scheme.surfaceBright.withAlpha(180),
      scheme.surface,
    );

void main() {
  final themes = <String, ThemeData>{
    'x-look light': xLookLightTheme(null),
    'x-look dim': xLookDimTheme(null),
    'x-look lights out': xLookLightsOutTheme(null),
    'fairy forest': fairyForestTheme(null),
    'pitch black': pitchBlackTheme(null),
    'default light': ThemeData(useMaterial3: true, brightness: Brightness.light),
    'default dark': ThemeData(useMaterial3: true, brightness: Brightness.dark),
    'true black': ThemeData(
      useMaterial3: true,
      colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue, brightness: Brightness.dark)
          .copyWith(surface: Colors.black),
    ),
  };

  Future<({BoxDecoration decoration, ColorScheme scheme})> resolve(
      WidgetTester tester, ThemeData theme) async {
    late BoxDecoration decoration;
    late ColorScheme scheme;
    await tester.pumpWidget(MaterialApp(
      theme: theme,
      home: Builder(builder: (context) {
        decoration = quoteCardDecoration(context);
        scheme = Theme.of(context).colorScheme;
        return const SizedBox();
      }),
    ));
    return (decoration: decoration, scheme: scheme);
  }

  themes.forEach((name, theme) {
    testWidgets('$name draws a quote card that separates from the tweet behind it', (tester) async {
      final resolved = await resolve(tester, theme);
      final decoration = resolved.decoration;
      final fill = decoration.color!;
      final border = (decoration.border as Border).top.color;

      // A hairline, not text: it only has to be perceptible against the card it
      // outlines, and against the surface the tweet itself is painted on.
      expect(_contrast(border, fill), greaterThanOrEqualTo(1.5),
          reason: '$name: quote border must be visible on its fill');
      expect(_contrast(border, resolved.scheme.surface), greaterThan(1.3),
          reason: '$name: quote border must be visible on the tweet surface');

      // The fill is lifted off the parent card so the nesting reads even where
      // the border is subtle.
      expect(fill, isNot(equals(resolved.scheme.surface)), reason: '$name: quote fill must differ from the surface');
    });

    testWidgets('$name is more visible than the previous translucent border', (tester) async {
      final resolved = await resolve(tester, theme);
      final border = (resolved.decoration.border as Border).top.color;
      final surface = resolved.scheme.surface;

      expect(_contrast(border, surface), greaterThan(_contrast(_previousBorder(resolved.scheme), surface)),
          reason: '$name: the new border must beat the old surfaceBright tint');
    });

    testWidgets('$name rounds the quote card', (tester) async {
      final resolved = await resolve(tester, theme);
      expect(resolved.decoration.borderRadius, isNotNull);
    });
  });
}
