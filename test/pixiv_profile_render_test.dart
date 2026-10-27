import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/pixiv/pixiv_user_screen.dart';
import 'package:xta/ui/x_look_theme.dart';

import 'support/pixiv_reader_harness.dart';
import 'support/pixiv_social_fakes.dart';

/// Renders the Pixiv profile header in Inter for review:
///   fvm flutter test test/pixiv_profile_render_test.dart --dart-define=RENDER_PIXIV_PROFILE=true
const _render = bool.fromEnvironment('RENDER_PIXIV_PROFILE');

void main() {
  setUpAll(() async {
    autoUpdateGoldenFiles = true;
    for (final weight in ['Regular', 'Medium', 'SemiBold', 'Bold']) {
      await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-$weight.ttf'))).load();
    }
    await (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
  });

  for (final (locale, width, scale) in const [
    (Locale('de'), 390.0, 1.0),
    (Locale('en'), 390.0, 1.0),
    (Locale('de'), 320.0, 2.0),
  ]) {
    final name = 'pixiv-profile-kanten-${locale.languageCode}-${width.round()}-x$scale';
    testWidgets(name, skip: !_render, (tester) async {
      final api = FakePixivSocialApi(profile: pixivProfileOf(illusts: 100, manga: 39, premium: true));
      await pumpPixiv(
        tester,
        const PixivUserScreen(userId: 9),
        extraProviders: [api.provider],
        size: Size(width, 844),
        textScale: scale,
        locale: locale,
        theme: xLookThemeData(XLookTokens.lightsOut.copyWith(accent: const Color(0xFF8B7CF6)), null),
      );
      expect(tester.takeException(), isNull);
      await expectLater(find.byType(PixivUserScreen), matchesGoldenFile('../review-artifacts/renders/$name.png'));
      await disposePixiv(tester);
    });
  }
}
