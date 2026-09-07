import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/mastodon/mastodon_post_card.dart';
import 'package:xta/plugins/mastodon/mastodon_search_sheet.dart';
import 'support/mastodon_harness.dart';

void main() {
  setUpAll(() async {
    autoUpdateGoldenFiles = true;
    await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-Regular.ttf'))).load();
    await (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
  });
  for (final variant in ['light', 'black', 'compact', 'large-rtl', 'people', 'search']) {
    testWidgets('populated Mastodon $variant', (tester) async {
      final large = variant == 'large-rtl';
      tester.view.physicalSize = large ? const Size(320, 844) : const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final h = MastodonHarness();
      addTearDown(() => h.close(tester));
      await tester.pumpWidget(h.app(embedded: variant == 'compact', dark: variant == 'black',
        scale: large ? 2 : 1, rtl: large));
      await tester.pumpAndSettle();
      expect(find.byType(MastodonPostCard), findsWidgets);
      if (variant == 'people') {
        await tester.tap(find.text('Following').last);
        await tester.pumpAndSettle();
        if (!const bool.fromEnvironment('MASTODON_LAYOUT_BEFORE')) {
          await tester.tap(find.text('Accounts'));
          await tester.pumpAndSettle();
        }
      }
      if (variant == 'search') {
        final context = tester.element(find.byKey(const ValueKey('mastodon-render')));
        showMastodonSearchSheet(context, initialQuery: 'design');
        await tester.pumpAndSettle();
      }
      expect(tester.takeException(), isNull);
      await expectLater(find.byKey(const ValueKey('mastodon-window')),
        matchesGoldenFile('../review-artifacts/renders/mastodon-$variant.png'));
    });
  }
}
