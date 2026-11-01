import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/saved/saved_screen.dart';

import 'support/reader_review_harness.dart';
import 'support/review_fonts.dart';

/// Renders the Archive's top bar, option A, beside the owner's board:
///   fvm flutter test test/saved_header_render_test.dart --dart-define=RENDER_ARCHIVE=true
const _render = bool.fromEnvironment('RENDER_ARCHIVE');

void main() {
  setUpAll(() async {
    autoUpdateGoldenFiles = true;
    await loadReviewFonts();
  });

  for (final (name, dark, locale, width, scale) in const [
    ('archive-top-de-390-lightsout', true, Locale('de'), 390.0, 1.0),
    ('archive-top-en-390-light', false, Locale('en'), 390.0, 1.0),
    ('archive-top-de-320-x2', true, Locale('de'), 320.0, 2.0),
  ]) {
    testWidgets(name, skip: !_render, (tester) async {
      tester.view.physicalSize = Size(width, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final h = ReaderReviewHarness();
      h.folders.update([
        for (final (index, folder) in const ['Inspiration', 'NSFW', 'Anime'].indexed)
          SavedTweetFolder(
            id: folder == 'Inspiration' ? 'inspiration' : folder.toLowerCase(),
            name: folder,
            position: index,
            createdAt: DateTime(2026, 9, 7),
          ),
      ]);
      await tester.pumpWidget(
        h.providers(
          h.app(
            dark: dark,
            locale: locale,
            scale: scale,
            // The Archive's scaffold is see-through; at home the shell paints the page behind it.
            child: Builder(
              builder: (context) => ColoredBox(
                color: Theme.of(context).scaffoldBackgroundColor,
                child: SavedScreen(scrollController: h.scroll),
              ),
            ),
          ),
        ),
      );
      for (var i = 0; i < 6; i++) {
        await tester.pump(const Duration(milliseconds: 100));
      }
      expect(tester.takeException(), isNull);
      await expectLater(
        find.byKey(const ValueKey('mastodon-window')),
        matchesGoldenFile('../review-artifacts/renders/$name.png'),
      );
      await h.close(tester);
    });
  }
}
