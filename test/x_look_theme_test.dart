import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/constants.dart';
import 'package:xta/ui/contrast.dart';
import 'package:xta/ui/x_look_theme.dart';

void main() {
  test('XLookTokens light/dim/lightsOut keep accent blue', () {
    expect(XLookTokens.light.accent, XLookTokens.accentBlue);
    expect(XLookTokens.dim.accent, XLookTokens.accentBlue);
    expect(XLookTokens.lightsOut.accent, XLookTokens.accentBlue);
  });

  test('xLookThemeData exposes tokens extension', () {
    final theme = xLookLightTheme(null);
    final tokens = theme.extension<XLookTokens>()!;
    expect(tokens.background, const Color(0xFFFFFFFF));
    expect(
      contrastRatio(theme.colorScheme.primary, tokens.background),
      greaterThanOrEqualTo(4.5),
    );
    expect(theme.textTheme.bodyLarge?.fontFamily, 'Inter');
  });

  test('Lights Out preserves black reading surfaces with restrained depth', () {
    final theme = xLookLightsOutTheme(null);
    final tokens = theme.extension<XLookTokens>()!;

    expect(xLookIsLightsOut(tokens), isTrue);
    expect(theme.scaffoldBackgroundColor, Colors.black);
    expect(theme.cardColor, Colors.black);
    expect(theme.colorScheme.surface, Colors.black);
    expect(theme.colorScheme.surfaceContainerLowest, Colors.black);
    expect(xLookInsetSurface(tokens), isNot(Colors.black));
    expect(xLookFloatingSurface(tokens), isNot(Colors.black));
    expect(
      xLookInsetSurface(tokens).computeLuminance(),
      lessThan(xLookFloatingSurface(tokens).computeLuminance()),
    );
    expect(
      xLookFloatingSurface(tokens).computeLuminance(),
      lessThan(tokens.border.computeLuminance()),
    );
  });

  test('Lights Out inputs are quieter than dialogs and sheets', () {
    final theme = xLookLightsOutTheme(null);
    final input = theme.inputDecorationTheme.fillColor!;
    final dialog = theme.dialogTheme.backgroundColor!;

    expect(input, theme.colorScheme.surfaceContainer);
    expect(dialog, theme.colorScheme.surfaceContainerHighest);
    expect(input.computeLuminance(), lessThan(dialog.computeLuminance()));
    expect(theme.bottomSheetTheme.backgroundColor, dialog);
    expect(theme.popupMenuTheme.color, dialog);
  });

  test('Lights Out skeletons do not use the divider as a solid fill', () {
    const tokens = XLookTokens.lightsOut;

    expect(xLookSkeletonSurface(tokens), isNot(tokens.divider));
    expect(xLookSkeletonHighlight(tokens), isNot(tokens.divider));
    expect(
      xLookSkeletonSurface(tokens).computeLuminance(),
      lessThan(tokens.divider.computeLuminance()),
    );
  });

  test(
    'buttons share the stadium pill, including leftover ElevatedButtons',
    () {
      final theme = xLookLightTheme(null);
      final filled = theme.filledButtonTheme.style?.shape?.resolve({});
      final elevated = theme.elevatedButtonTheme.style?.shape?.resolve({});
      expect(filled, isA<StadiumBorder>());
      expect(elevated, isA<StadiumBorder>());
      expect(theme.elevatedButtonTheme.style?.elevation?.resolve({}), 0);
    },
  );

  test('isXLookPreset recognizes the three presets', () {
    expect(isXLookPreset('x_look_light'), isTrue);
    expect(isXLookPreset('x_look_dim'), isTrue);
    expect(isXLookPreset('x_look_lights_out'), isTrue);
    expect(isXLookPreset('fairy_forest'), isFalse);
  });

  test('runtime themes keep every accent state contrast-safe', () {
    const backgrounds = <XLookTokens>[
      XLookTokens.light,
      XLookTokens.dim,
      XLookTokens.lightsOut,
    ];
    for (final background in backgrounds) {
      for (final accent in xLookAccents.entries) {
        final tokens = background.copyWith(accent: accent.value);
        final theme = xLookThemeData(tokens, null);
        final primary = theme.colorScheme.primary;
        final buttonForeground = theme
            .filledButtonTheme
            .style!
            .foregroundColor!
            .resolve({})!;
        final segmentedForeground = theme
            .segmentedButtonTheme
            .style!
            .foregroundColor!
            .resolve({WidgetState.selected})!;
        final error = theme.colorScheme.error;
        final errorContainer = theme.colorScheme.errorContainer;
        final onErrorContainer = theme.colorScheme.onErrorContainer;
        final selectedSwitchTrack = theme.switchTheme.trackColor!.resolve({
          WidgetState.selected,
        })!;

        expect(
          contrastRatio(primary, tokens.background),
          greaterThanOrEqualTo(4.5),
          reason: '${tokens.background}/${accent.key} surface accent',
        );
        expect(
          contrastRatio(buttonForeground, accent.value),
          greaterThanOrEqualTo(4.5),
          reason: '${tokens.background}/${accent.key} filled button',
        );
        expect(
          contrastRatio(segmentedForeground, accent.value),
          greaterThanOrEqualTo(4.5),
          reason: '${tokens.background}/${accent.key} segmented control',
        );
        expect(
          contrastRatio(error, tokens.background),
          greaterThanOrEqualTo(4.5),
          reason: '${tokens.background}/${accent.key} destructive text',
        );
        expect(
          contrastRatio(selectedSwitchTrack, tokens.background),
          greaterThanOrEqualTo(4.5),
          reason: '${tokens.background}/${accent.key} selected switch',
        );
        expect(
          contrastRatio(onErrorContainer, errorContainer),
          greaterThanOrEqualTo(4.5),
          reason: '${tokens.background}/${accent.key} destructive container',
        );
      }
    }
  });

  testWidgets('XLookTokens.of reads from Theme', (tester) async {
    late XLookTokens read;
    await tester.pumpWidget(
      MaterialApp(
        theme: xLookDimTheme(null),
        home: Builder(
          builder: (context) {
            read = XLookTokens.of(context);
            return const SizedBox.shrink();
          },
        ),
      ),
    );
    expect(read.background, const Color(0xFF15202B));
    expect(read.card, const Color(0xFF192734));
  });
}
