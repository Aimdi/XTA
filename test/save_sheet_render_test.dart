import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/ui/x_look_theme.dart';

import 'support/save_sheet_harness.dart';

/// Renders the save-to-folder sheet in Inter for review:
///   fvm flutter test test/save_sheet_render_test.dart --dart-define=RENDER_SAVE_SHEET=true
const _render = bool.fromEnvironment('RENDER_SAVE_SHEET');

const _names = [
  'Ai',
  'Ai Art',
  'Alternativeto',
  'Anime',
  'Art (1)',
  'Art (2)',
  'Art NSFW',
  'Asia',
  'Black',
  'Blue',
  'China',
];

SaveSheetHarness _harness() => SaveSheetHarness(
  saved: [
    savedPost('t1', 'f3'),
    savedPost('a', 'f5'),
    savedPost('b', 'f1'),
    savedPost('c', 'f7'),
    savedPost('d', 'f9'),
  ],
  folders: [for (final (index, name) in _names.indexed) savedFolder('f$index', name, position: index)],
);

void main() {
  setUpAll(() async {
    autoUpdateGoldenFiles = true;
    for (final weight in ['Regular', 'Medium', 'SemiBold', 'Bold']) {
      await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-$weight.ttf'))).load();
    }
    await (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
  });

  const shot = ValueKey('save-sheet-shot');

  Future<void> pump(WidgetTester tester, {required Locale locale, required double width, double scale = 1}) async {
    tester.view.physicalSize = Size(width, 844);
    tester.view.devicePixelRatio = 1;
    tester.view.padding = const FakeViewPadding(top: 24, bottom: 16);
    tester.platformDispatcher.textScaleFactorTestValue = scale;
    addTearDown(tester.view.reset);
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
    await _harness().open(
      tester,
      locale: locale,
      shotKey: shot,
      theme: xLookThemeData(XLookTokens.lightsOut.copyWith(accent: const Color(0xFF8B7CF6)), null),
    );
  }

  for (final (locale, width, scale) in const [
    (Locale('de'), 390.0, 1.0),
    (Locale('en'), 390.0, 1.0),
    (Locale('de'), 320.0, 2.0),
  ]) {
    final tag = '${locale.languageCode}-${width.round()}-x$scale';

    testWidgets('save-sheet-$tag', skip: !_render, (tester) async {
      await pump(tester, locale: locale, width: width, scale: scale);
      expect(tester.takeException(), isNull);
      await expectLater(find.byKey(shot), matchesGoldenFile('../review-artifacts/renders/save-sheet-$tag.png'));
    });

    testWidgets('save-sheet-create-$tag', skip: !_render, (tester) async {
      await pump(tester, locale: locale, width: width, scale: scale);
      await tester.enterText(find.byType(TextField), 'Japan');
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await expectLater(find.byKey(shot), matchesGoldenFile('../review-artifacts/renders/save-sheet-create-$tag.png'));
    });
  }
}
