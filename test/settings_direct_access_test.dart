import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/settings/settings.dart';
import 'package:xta/settings/settings_search_index.dart';
import 'package:xta/settings/settings_search_target.dart';
import 'package:xta/settings/_plugin_row.dart';
import 'package:xta/plugins/mastodon/mastodon_plugin.dart';
import 'package:xta/plugins/mastodon/mastodon_screen.dart';
import 'package:xta/plugins/mastodon/mastodon_settings.dart';
import 'support/mastodon_harness.dart';

Map<String, dynamic> readerMediaPreferences() => {
  optionMediaDisableAutoload: false, optionImageQuality: 'medium', optionMediaGridColumns: 2,
  optionMediaGridLayout: mediaGridLayoutMasonry, optionMediaVideoQuality: 'medium',
  optionMediaDefaultMute: true, optionMediaDefaultLoop: false, optionMediaDefaultAutoPlay: false,
  optionMediaVideoPrefetchSeconds: 5, optionMediaDirectHardwareDecoding: false,
  optionMediaBackgroundPlayback: false, optionMediaAllowBackgroundPlayOtherApps: false,
  optionDownloadType: optionDownloadTypeAsk, optionDownloadPath: '', optionDownloadTreeUri: '',
  optionTextScaleFactor: 1.0, optionTickerChart: false, optionGestureDoubleTapLike: false,
};

void main() {
  testWidgets('search opens and highlights autoplay below the fold, and the toggle works', (tester) async {
    tester.view.physicalSize = const Size(390, 844); tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize); addTearDown(tester.view.resetDevicePixelRatio);
    final h = MastodonHarness();
    for (final entry in readerMediaPreferences().entries) { await h.prefs.set(entry.key, entry.value); }
    await tester.pumpWidget(h.app(child: const SettingsScreen())); await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'autoplay'); await tester.pumpAndSettle();
    await tester.tap(find.text('Autoplay videos')); await tester.pumpAndSettle();
    final focus = find.byKey(ValueKey('settings-focus-$optionMediaDefaultAutoPlay'));
    expect(focus, findsOneWidget); expect(focus.hitTestable(), findsOneWidget);
    final toggle = find.descendant(of: focus, matching: find.byType(Switch));
    await tester.tap(toggle); await tester.pumpAndSettle();
    expect(h.prefs.get<bool>(optionMediaDefaultAutoPlay), isTrue);
    expect(tester.takeException(), isNull); await h.close(tester);
  });

  testWidgets('translated control index includes reading position and text size', (tester) async {
    final h = MastodonHarness(); await tester.pumpWidget(h.app(child: const SettingsScreen()));
    await tester.pumpAndSettle();
    final l10n = L10n.of(tester.element(find.byType(SettingsScreen)));
    expect(searchSettingsControls(l10n, 'reading position').any((result) => result.target == optionFeedReadingPosition), isTrue);
    expect(settingsControls(l10n).any((result) => result.target == optionTextScaleFactor), isTrue);
    expect(searchSettingsControls(l10n, 'not-a-setting'), isEmpty);
    await h.close(tester);
  });

  testWidgets('installed Mastodon has separate client and settings actions', (tester) async {
    final h = MastodonHarness();
    await tester.pumpWidget(h.app(child: Scaffold(body: InstalledPluginRow(
      plugin: MastodonPlugin(), onUninstall: () {}, onChanged: () {}))));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('plugin-open-mastodon'))); await tester.pumpAndSettle();
    expect(find.byType(MastodonScreen), findsOneWidget); expect(find.byType(MastodonSettingsScreen), findsNothing);
    await tester.pageBack(); await tester.pumpAndSettle();
    await tester.tap(find.byIcon(Icons.tune)); await tester.pumpAndSettle();
    expect(find.byType(MastodonSettingsScreen), findsOneWidget); expect(find.byType(SettingsControlTarget), findsNothing);
    expect(tester.takeException(), isNull); await h.close(tester);
  });
}
