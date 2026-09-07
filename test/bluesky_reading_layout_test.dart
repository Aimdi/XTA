import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/bluesky/bluesky_profile_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_thread_screen.dart';
import 'support/bluesky_reading_harness.dart';
import 'support/reader_review_harness.dart' show reviewImageBytes, ReviewImageOverrides;

Future<void> _settleImages(WidgetTester tester) async {
  for (var i = 0; i < 2; i++) {
    await tester.pump();
    await tester.runAsync(() async {
      await Future<void>.delayed(const Duration(milliseconds: 80));
    });
  }
  await tester.pumpAndSettle();
}

void main() {
  setUpAll(() async {
    autoUpdateGoldenFiles = true;
    await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-Regular.ttf'))).load();
    await (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
  });
  for (final variant in ['profile', 'media', 'thread', 'thread-black', 'thread-large-rtl']) {
    testWidgets('Bluesky reading layout $variant', (tester) async {
      final large = variant == 'thread-large-rtl';
      tester.view.physicalSize = Size(large ? 320 : 390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final previous = HttpOverrides.current;
      final bytes = await tester.runAsync(reviewImageBytes);
      HttpOverrides.global = ReviewImageOverrides(bytes!);
      addTearDown(() => HttpOverrides.global = previous);
      final h = BlueReadingHarness();
      addTearDown(() => h.close(tester));
      final screen = variant == 'profile' || variant == 'media'
          ? const BlueskyProfileScreen(actor: 'maya.bsky.social')
          : BlueskyThreadScreen(post: bluePost('root'));
      await tester.pumpWidget(h.app(screen, dark: variant == 'thread-black', scale: large ? 2 : 1, rtl: large));
      await _settleImages(tester);
      if (variant == 'media') {
        await tester.tap(find.byIcon(Icons.smart_display_outlined).first);
        await _settleImages(tester);
      }
      expect(tester.takeException(), isNull);
      await expectLater(find.byKey(const ValueKey('bluesky-window')),
        matchesGoldenFile('../review-artifacts/renders/bluesky-$variant.png'));
    });
  }
}
