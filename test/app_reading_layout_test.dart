import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/mastodon/mastodon_profile_screen.dart';
import 'package:xta/plugins/mastodon/mastodon_thread_screen.dart';
import 'package:xta/saved/saved_screen.dart';
import 'package:xta/search/search_scope.dart';
import 'package:xta/search/recent_searches_store.dart';
import 'package:xta/settings/settings.dart';
import 'package:xta/trends/trends_screen.dart';
import 'settings_direct_access_test.dart' show readerMediaPreferences;
import 'support/reader_review_harness.dart';

Future<void> settleReviewImages(WidgetTester tester) async {
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
  for (final variant in [
    'profile',
    'media',
    'thread',
    'thread-black',
    'thread-large-rtl',
    'saved',
    'discover',
    'settings',
    'setting-target',
  ]) {
    testWidgets('app reading layout $variant', (tester) async {
      final large = variant == 'thread-large-rtl';
      tester.view.physicalSize = Size(large ? 320 : 390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final oldOverrides = HttpOverrides.current;
      final bytes = await tester.runAsync(reviewImageBytes);
      HttpOverrides.global = ReviewImageOverrides(bytes!);
      addTearDown(() => HttpOverrides.global = oldOverrides);
      final h = ReaderReviewHarness();
      addTearDown(() => h.close(tester));
      for (final entry in readerMediaPreferences().entries) {
        await h.prefs.set(entry.key, entry.value);
      }
      await h.prefs.set(optionPluginMastodonEnabled, true);
      await h.prefs.set(
        recentSearchesPreference,
        jsonEncode({
          'mastodon': ['photography', 'design', 'opensource'],
        }),
      );
      final scope = SearchScopeStore()..select(pluginIdMastodon);
      final query = DiscoverQueryStore();
      final focus = FocusNode();
      addTearDown(scope.destroy);
      addTearDown(query.destroy);
      addTearDown(focus.dispose);
      final Widget screen = switch (variant) {
        'profile' || 'media' => const MastodonProfileScreen(acct: 'maya@studio.example'),
        'saved' => SavedScreen(scrollController: h.scroll),
        'discover' => MultiProvider(
          providers: [
            Provider<SearchScopeStore>.value(value: scope),
            Provider<DiscoverQueryStore>.value(value: query),
          ],
          child: TrendsScreen(scrollController: h.scroll, focusNode: focus),
        ),
        'settings' || 'setting-target' => const SettingsScreen(),
        _ => MastodonThreadScreen(post: reviewPost('root')),
      };
      await tester.pumpWidget(
        h.providers(h.app(child: screen, dark: variant == 'thread-black', scale: large ? 2 : 1, rtl: large)),
      );
      await settleReviewImages(tester);
      if (variant == 'media') {
        await tester.tap(find.text('Media'));
        await settleReviewImages(tester);
      }
      if (variant == 'settings' || variant == 'setting-target') {
        await tester.enterText(find.byType(TextField), variant == 'settings' ? 'video' : 'autoplay');
        await tester.pumpAndSettle();
        if (variant == 'setting-target') {
          await tester.tap(find.text('Autoplay videos'));
          await tester.pumpAndSettle();
        }
      }
      // Image decoding finishes outside the fake test clock.
      await tester.runAsync(() async {
        await Future<void>.delayed(const Duration(milliseconds: 80));
      });
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await expectLater(
        find.byKey(const ValueKey('mastodon-window')),
        matchesGoldenFile('../review-artifacts/renders/app-$variant.png'),
      );
    });
  }
}
