import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'support/profile_harness.dart';

/// Renders the X profile in Inter for review:
///   fvm flutter test test/profile_render_test.dart --dart-define=RENDER_PROFILE=true
const _render = bool.fromEnvironment('RENDER_PROFILE');

void main() {
  setUpAll(() async {
    autoUpdateGoldenFiles = true;
    await loadProfileFonts();
  });

  for (final (locale, width, scale, subscribed) in const [
    (Locale('de'), 390.0, 1.0, true),
    (Locale('en'), 390.0, 1.0, false),
    (Locale('de'), 320.0, 2.0, true),
  ]) {
    final name = 'profile-flat-${locale.languageCode}-${width.round()}-x$scale';
    testWidgets(name, skip: !_render, (tester) async {
      await pumpXProfile(tester, locale: locale, width: width, scale: scale, subscribed: subscribed);
      expect(tester.takeException(), isNull);
      await expectLater(
        find.byKey(const ValueKey('profile-shot')),
        matchesGoldenFile('../review-artifacts/renders/$name.png'),
      );
      await settleProfile(tester);
    });
  }
}
