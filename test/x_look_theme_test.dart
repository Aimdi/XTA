import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/ui/x_look_theme.dart';

void main() {
  test('XLookTokens light/dim/lightsOut keep accent blue', () {
    expect(XLookTokens.light.accent, XLookTokens.accentBlue);
    expect(XLookTokens.dim.accent, XLookTokens.accentBlue);
    expect(XLookTokens.lightsOut.accent, XLookTokens.accentBlue);
  });

  test('xLookThemeData exposes tokens extension', () {
    final theme = xLookLightTheme(null);
    final tokens = theme.extension<XLookTokens>();
    expect(tokens, isNotNull);
    expect(tokens!.background, const Color(0xFFFFFFFF));
    expect(theme.colorScheme.primary, XLookTokens.accentBlue);
    expect(theme.textTheme.bodyLarge?.fontFamily, 'Inter');
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
