import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/ui/x_look_theme.dart';

import 'support/bluesky_reading_images.dart';
import 'support/feed_ink_harness.dart';

/// Renders the Ink feed (canvas concept A) for review beside the canvas:
///   flutter test test/feed_ink_render_test.dart --dart-define=RENDER_INK=true
const _render = bool.fromEnvironment('RENDER_INK');

void main() {
  setUpAll(() async {
    autoUpdateGoldenFiles = true;
    await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-Regular.ttf'))).load();
    await (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
  });

  for (final (name, theme, size, scale, direction) in [
    ('black', xLookLightsOutTheme(null), const Size(390, 844), 1.0, null),
    ('dim', xLookDimTheme(null), const Size(390, 844), 1.0, null),
    ('light', xLookLightTheme(null), const Size(390, 844), 1.0, null),
    ('large-rtl', xLookLightsOutTheme(null), const Size(320, 740), 1.3, TextDirection.rtl),
  ]) {
    testWidgets('feed-ink-$name', skip: !_render, (tester) async {
      await installBlueskyReadingImages(tester);
      await pumpInk(tester, inkFeedScreen(), theme: theme, size: size, scale: scale, direction: direction);
      await settleBlueskyReadingImages(tester);
      expect(tester.takeException(), isNull);
      await expectLater(
        find.byKey(const ValueKey('ink-render')),
        matchesGoldenFile('../review-artifacts/renders/feed-ink-$name.png'),
      );
    });
  }
}
