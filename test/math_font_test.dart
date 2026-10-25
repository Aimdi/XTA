import 'dart:math';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/ui/x_look_theme.dart';

/// 𝗲, MATHEMATICAL SANS-SERIF BOLD SMALL E: a letter Inter does not have.
const _boldE = '\u{1D5F2}';
const _acute = '́';

/// The box around the pixels [text] inks in [style].
Future<Rect> _ink(String text, TextStyle style) async {
  final painter = TextPainter(
    text: TextSpan(text: text, style: style),
    textDirection: TextDirection.ltr,
  )..layout();
  final recorder = ui.PictureRecorder();
  painter.paint(Canvas(recorder), Offset.zero);
  final width = painter.width.ceil();
  final image = await recorder.endRecording().toImage(width, painter.height.ceil());
  final pixels = (await image.toByteData())!;
  painter.dispose();
  image.dispose();

  final inked = [
    for (var i = 0; i < pixels.lengthInBytes ~/ 4; i++)
      if (pixels.getUint8(i * 4 + 3) > 0) (x: (i % width).toDouble(), y: (i ~/ width).toDouble()),
  ];
  return Rect.fromLTRB(
    inked.map((p) => p.x).reduce(min),
    inked.map((p) => p.y).reduce(min),
    inked.map((p) => p.x).reduce(max),
    inked.map((p) => p.y).reduce(max),
  );
}

/// Every text style the theme names, and what it is for.
List<(String, TextStyle?)> _themeStyles(ThemeData theme) {
  final text = theme.textTheme;
  final selected = {WidgetState.selected};
  return [
    ('displayLarge', text.displayLarge),
    ('displayMedium', text.displayMedium),
    ('displaySmall', text.displaySmall),
    ('headlineLarge', text.headlineLarge),
    ('headlineMedium', text.headlineMedium),
    ('headlineSmall', text.headlineSmall),
    ('titleLarge', text.titleLarge),
    ('titleMedium', text.titleMedium),
    ('titleSmall', text.titleSmall),
    ('bodyLarge', text.bodyLarge),
    ('bodyMedium', text.bodyMedium),
    ('bodySmall', text.bodySmall),
    ('labelLarge', text.labelLarge),
    ('labelMedium', text.labelMedium),
    ('labelSmall', text.labelSmall),
    ('app bar title', theme.appBarTheme.titleTextStyle),
    ('navigation label', theme.navigationBarTheme.labelTextStyle?.resolve({})),
    ('selected navigation label', theme.navigationBarTheme.labelTextStyle?.resolve(selected)),
    ('snack bar', theme.snackBarTheme.contentTextStyle),
    ('popup menu', theme.popupMenuTheme.textStyle),
    ('dropdown menu', theme.dropdownMenuTheme.textStyle),
    ('dialog title', theme.dialogTheme.titleTextStyle),
    ('dialog content', theme.dialogTheme.contentTextStyle),
    ('tab label', theme.tabBarTheme.labelStyle),
    ('unselected tab label', theme.tabBarTheme.unselectedLabelStyle),
    ('list tile subtitle', theme.listTileTheme.subtitleTextStyle),
    ('chip label', theme.chipTheme.labelStyle),
    ('selected chip label', theme.chipTheme.secondaryLabelStyle),
    ('tooltip', theme.tooltipTheme.textStyle),
    ('input hint', theme.inputDecorationTheme.hintStyle),
    ('text button', theme.textButtonTheme.style?.textStyle?.resolve({})),
    ('filled button', theme.filledButtonTheme.style?.textStyle?.resolve({})),
    ('elevated button', theme.elevatedButtonTheme.style?.textStyle?.resolve({})),
  ];
}

void main() {
  setUpAll(() async {
    await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-Regular.ttf'))).load();
    await (FontLoader(xLookMathFontFamily)..addFont(rootBundle.load('assets/fonts/NotoSansMath-Accents.ttf'))).load();
  });

  TextStyle body() => xLookLightTheme(null).textTheme.bodyMedium!.copyWith(fontSize: 100, color: Colors.black);

  testWidgets('the theme draws the accent of a bold Unicode letter on the letter', (tester) async {
    late Rect letter;
    late Rect accented;
    await tester.runAsync(() async {
      letter = await _ink(_boldE, body());
      accented = await _ink('$_boldE$_acute', body());
    });

    expect(accented.top, lessThan(letter.top), reason: 'the accent sits above the letter, not as a box nor left out');
    expect(accented.left, letter.left, reason: 'it does not stick out on the left of the letter');
    expect(accented.right, letter.right, reason: 'nor on its right');
  });

  testWidgets('plain letters keep Inter', (tester) async {
    late Rect themed;
    late Rect inter;
    await tester.runAsync(() async {
      themed = await _ink('Accents é', body());
      inter = await _ink('Accents é', body().copyWith(fontFamilyFallback: const []));
    });

    expect(themed, inter, reason: 'only what Inter lacks comes from the math font');
  });

  test('the math font travels with its licence', () async {
    final license = await rootBundle.loadString('assets/fonts/NotoSansMath-OFL.txt');

    expect(license, contains('The Noto Project Authors'));
    expect(license, contains('SIL OPEN FONT LICENSE Version 1.1'));
  });

  test('every Inter style of the theme falls back on the math font', () {
    for (final theme in [xLookLightTheme(null), xLookDimTheme(null), xLookLightsOutTheme(null)]) {
      for (final (name, style) in _themeStyles(theme)) {
        expect(style?.fontFamily, 'Inter', reason: name);
        expect(style?.fontFamilyFallback, contains(xLookMathFontFamily), reason: name);
      }
    }
  });
}
