import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/constants.dart';
import 'package:xta/ui/x_look_theme.dart';

/// X Look is now the app's only design language, chosen on two axes the way X
/// does it: how dark the background is, and which accent sits on it. These pin
/// the resolution between the two settings and an actual ThemeData, and the
/// migration that carries an existing install across.
void main() {
  group('xLookTokensFor', () {
    test('each background resolves to its own palette', () {
      expect(xLookTokensFor(xLookBackgroundLight, xLookAccentBlue).background, XLookTokens.light.background);
      expect(xLookTokensFor(xLookBackgroundDim, xLookAccentBlue).background, XLookTokens.dim.background);
      expect(xLookTokensFor(xLookBackgroundLightsOut, xLookAccentBlue).background, XLookTokens.lightsOut.background);
    });

    test('the accent is applied without disturbing the background', () {
      final green = xLookTokensFor(xLookBackgroundLightsOut, 'green');

      expect(green.accent, xLookAccents['green']);
      expect(green.background, XLookTokens.lightsOut.background);
      expect(green.onBackground, XLookTokens.lightsOut.onBackground);
    });

    test('every accent is offered on every background', () {
      for (final background in xLookBackgrounds) {
        for (final accent in xLookAccents.keys) {
          expect(xLookTokensFor(background, accent).accent, xLookAccents[accent], reason: '$background/$accent');
        }
      }
    });

    test('an accent we no longer recognise falls back to blue rather than crashing', () {
      expect(xLookAccentColor('chartreuse'), xLookAccents[xLookAccentBlue]);
      expect(xLookTokensFor(xLookBackgroundDim, 'chartreuse').accent, xLookAccents[xLookAccentBlue]);
    });
  });

  group('the dark half', () {
    test('Dim darkens to Dim, everything else to Lights Out', () {
      expect(xLookDarkTokensFor(xLookBackgroundDim, xLookAccentBlue).background, XLookTokens.dim.background);
      expect(xLookDarkTokensFor(xLookBackgroundSystem, xLookAccentBlue).background, XLookTokens.lightsOut.background);
      expect(
          xLookDarkTokensFor(xLookBackgroundLightsOut, xLookAccentBlue).background, XLookTokens.lightsOut.background);
    });

    test('a light-mode reader on System is never dragged into a black UI', () {
      expect(xLookThemeModeFor(xLookBackgroundSystem), ThemeMode.system);
      expect(xLookTokensFor(xLookBackgroundLight, xLookAccentBlue).background, XLookTokens.light.background);
    });
  });

  group('xLookThemeModeFor', () {
    test('only System defers to the phone', () {
      expect(xLookThemeModeFor(xLookBackgroundSystem), ThemeMode.system);
      expect(xLookThemeModeFor(xLookBackgroundLight), ThemeMode.light);
      expect(xLookThemeModeFor(xLookBackgroundDim), ThemeMode.dark);
      expect(xLookThemeModeFor(xLookBackgroundLightsOut), ThemeMode.dark);
    });
  });

  group('migrating an existing install', () {
    test('the three X Look presets keep the look they had', () {
      expect(xLookBackgroundForPreset(themePresetXLookLight), xLookBackgroundLight);
      expect(xLookBackgroundForPreset(themePresetXLookDim), xLookBackgroundDim);
      expect(xLookBackgroundForPreset(themePresetXLookLightsOut), xLookBackgroundLightsOut);
    });

    test('the retired presets have no equivalent, so they follow the system', () {
      for (final preset in [themePresetNone, themePresetFairyForest, themePresetPitchBlack, null, 'something_else']) {
        expect(xLookBackgroundForPreset(preset), xLookBackgroundSystem, reason: '$preset');
      }
    });
  });

  group('the snackbar', () {
    test('never inverts to a light slab in a dark theme', () {
      for (final background in [xLookBackgroundDim, xLookBackgroundLightsOut]) {
        final theme = xLookThemeData(xLookTokensFor(background, xLookAccentBlue), null);
        final snack = theme.snackBarTheme.backgroundColor!;

        expect(snack.computeLuminance(), lessThan(0.5), reason: '$background snackbar must stay dark');
        _expectReadable(snack, theme.snackBarTheme.contentTextStyle!.color!, reason: background);
      }
    });

    test('is visible against the screen behind it, even on pure black', () {
      final theme = xLookThemeData(xLookTokensFor(xLookBackgroundLightsOut, xLookAccentBlue), null);

      // Lights Out makes card and background both pure black, so an unlifted
      // snackbar would have no edge at all.
      expect(theme.snackBarTheme.backgroundColor, isNot(theme.scaffoldBackgroundColor));
    });

    test('the light theme keeps its dark-on-light the right way round', () {
      final theme = xLookThemeData(xLookTokensFor(xLookBackgroundLight, xLookAccentBlue), null);

      _expectReadable(theme.snackBarTheme.backgroundColor!, theme.snackBarTheme.contentTextStyle!.color!,
          reason: 'light');
    });

    test('the spinner and action text follow the chosen accent', () {
      for (final accent in xLookAccents.keys) {
        final theme = xLookThemeData(xLookTokensFor(xLookBackgroundLightsOut, accent), null);
        expect(theme.snackBarTheme.actionTextColor, xLookAccents[accent], reason: accent);
      }
    });
  });

  group('the resulting ThemeData', () {
    test('carries the tokens, so widgets reading XLookTokens still find them', () {
      final theme = xLookThemeData(xLookTokensFor(xLookBackgroundLightsOut, 'green'), null);

      expect(theme.extension<XLookTokens>()?.accent, xLookAccents['green']);
      expect(theme.colorScheme.primary, xLookAccents['green']);
      expect(theme.scaffoldBackgroundColor, XLookTokens.lightsOut.background);
    });

    test('brightness follows the background, not the accent', () {
      expect(xLookThemeData(xLookTokensFor(xLookBackgroundLight, 'purple'), null).brightness, Brightness.light);
      expect(xLookThemeData(xLookTokensFor(xLookBackgroundDim, 'yellow'), null).brightness, Brightness.dark);
      expect(xLookThemeData(xLookTokensFor(xLookBackgroundLightsOut, 'pink'), null).brightness, Brightness.dark);
    });
  });
}

/// The snackbar shown after saving an image came out as a light slab with dark
/// text in a dark app: Material 3 draws snackbars on `inverseSurface`, and the
/// X Look theme never overrode it.
void _expectReadable(Color background, Color text, {required String reason}) {
  final contrast = (background.computeLuminance() + 0.05) / (text.computeLuminance() + 0.05);
  final ratio = contrast < 1 ? 1 / contrast : contrast;

  // WCAG 2.2 SC 1.4.3 for normal text.
  expect(ratio, greaterThanOrEqualTo(4.5), reason: reason);
}

