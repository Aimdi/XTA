import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_detail_split.dart';
import 'package:xta/plugins/pixiv/pixiv_grid_columns.dart';
import 'package:xta/plugins/pixiv/pixiv_image_source.dart';
import 'package:xta/plugins/pixiv/pixiv_quality.dart';
import 'package:xta/plugins/pixiv/pixiv_settings_viewing.dart';
import 'package:xta/plugins/pixiv/pixiv_viewing_prefs.dart';

import 'support/pixiv_reader_harness.dart';

Future<PixivHarness> _pumpSection(WidgetTester tester, {Size size = const Size(390, 1600), double textScale = 1}) =>
    pumpPixiv(
      tester,
      const Scaffold(
        body: SingleChildScrollView(padding: EdgeInsets.all(16), child: PixivViewingSettings()),
      ),
      size: size,
      textScale: textScale,
    );

Finder _subtitleOf(String key, String text) =>
    find.descendant(of: find.byKey(ValueKey(key)), matching: find.text(text));

Future<void> _openHostDialog(WidgetTester tester) async {
  await tester.tap(find.byKey(const ValueKey('pixiv-image-host')));
  await settlePixiv(tester);
}

FilledButton _save(WidgetTester tester) =>
    tester.widget<FilledButton>(find.byKey(const ValueKey('pixiv-image-host-save')));

void main() {
  test('every viewing preference has a default that its reader agrees with', () {
    expect(pixivViewingDefaults.keys, {
      optionPluginPixivImageHost,
      optionPluginPixivQualityFeed,
      optionPluginPixivQualityDetail,
      optionPluginPixivQualityReader,
      optionPluginPixivGridColumnsPortrait,
      optionPluginPixivGridColumnsLandscape,
      optionPluginPixivSwipeBetweenWorks,
      optionPluginPixivDetailLayout,
      optionPluginPixivDetailSplit,
      optionPluginPixivAiBadge,
    });
    expect(pixivViewingDefaults[optionPluginPixivImageHost], pixivImageHostSetting(null));
    for (final slot in PixivQualitySlot.values) {
      expect(pixivViewingDefaults[slot.pref], pixivQuality(null, slot).name);
    }
    expect(pixivViewingDefaults[optionPluginPixivGridColumnsPortrait], pixivGridColumnsAuto);
    expect(pixivViewingDefaults[optionPluginPixivSwipeBetweenWorks], pixivSwipesBetweenWorks(null));
    expect(pixivViewingDefaults[optionPluginPixivDetailLayout], pixivDetailLayout(null).name);
    expect(pixivViewingDefaults[optionPluginPixivDetailSplit], pixivSplitFraction(null));
    expect(pixivViewingDefaults[optionPluginPixivAiBadge], pixivShowsAiBadge(null));
  });

  testWidgets('each choice shows its default and a pick from its dialog is kept', (tester) async {
    final harness = await _pumpSection(tester);
    expect(_subtitleOf('pixiv-quality-feed', 'Medium'), findsOneWidget);
    expect(_subtitleOf('pixiv-quality-detail', 'Large'), findsOneWidget);
    expect(_subtitleOf('pixiv-columns-landscape', 'Automatic'), findsOneWidget);
    expect(_subtitleOf('pixiv-detail-layout', 'Side by side on wide screens'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('pixiv-quality-reader')));
    await settlePixiv(tester);
    expect(find.widgetWithText(RadioListTile<String>, 'Medium'), findsNothing);
    await tester.tap(find.widgetWithText(RadioListTile<String>, 'Original'));
    await settlePixiv(tester);
    expect(harness.prefs.get<String>(optionPluginPixivQualityReader), 'original');
    expect(_subtitleOf('pixiv-quality-reader', 'Original'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('pixiv-columns-portrait')));
    await settlePixiv(tester);
    await tester.tap(find.widgetWithText(RadioListTile<int>, '3'));
    await settlePixiv(tester);
    expect(harness.prefs.get<int>(optionPluginPixivGridColumnsPortrait), 3);
    await disposePixiv(tester);
  });

  testWidgets('swiping between works starts off and the AI badge starts on', (tester) async {
    final harness = await _pumpSection(tester);
    SwitchListTile switchOf(String key) => tester.widget<SwitchListTile>(
      find.descendant(of: find.byKey(ValueKey(key)), matching: find.byType(SwitchListTile)),
    );
    expect(switchOf('pixiv-swipe-works').value, isFalse);
    expect(switchOf('pixiv-ai-badge').value, isTrue);

    await tester.tap(find.byKey(const ValueKey('pixiv-ai-badge')));
    await settlePixiv(tester);
    expect(harness.prefs.get<bool>(optionPluginPixivAiBadge), isFalse);
    expect(switchOf('pixiv-ai-badge').value, isFalse);
    await disposePixiv(tester);
  });

  group('image server', () {
    testWidgets('picks the mirror', (tester) async {
      final harness = await _pumpSection(tester);
      expect(_subtitleOf('pixiv-image-host', pixivImageHost), findsOneWidget);
      await _openHostDialog(tester);
      await tester.tap(find.text('Public mirror'));
      await settlePixiv(tester);
      await tester.tap(find.byKey(const ValueKey('pixiv-image-host-save')));
      await settlePixiv(tester);
      expect(harness.prefs.get<String>(optionPluginPixivImageHost), pixivMirrorHost);
      expect(_subtitleOf('pixiv-image-host', pixivMirrorHost), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('refuses a custom address with spaces and keeps a usable one', (tester) async {
      final harness = await _pumpSection(tester);
      await _openHostDialog(tester);
      await tester.tap(find.text('Your own server'));
      await settlePixiv(tester);
      expect(_save(tester).onPressed, isNull);

      await tester.enterText(find.byKey(const ValueKey('pixiv-image-host-field')), 'img example.com');
      await tester.pump();
      expect(find.text('Enter a valid address without spaces, such as img.example.com'), findsOneWidget);
      expect(_save(tester).onPressed, isNull);

      await tester.enterText(
        find.byKey(const ValueKey('pixiv-image-host-field')),
        'https://img.example.com:8443/pixiv',
      );
      await tester.pump();
      expect(_save(tester).onPressed, isNotNull);
      await tester.tap(find.byKey(const ValueKey('pixiv-image-host-save')));
      await settlePixiv(tester);
      expect(harness.prefs.get<String>(optionPluginPixivImageHost), 'https://img.example.com:8443/pixiv');
      await disposePixiv(tester);
    });

    testWidgets('reset goes back to Pixiv\'s own server, and cancel changes nothing', (tester) async {
      final harness = await _pumpSection(tester);
      await harness.prefs.set(optionPluginPixivImageHost, 'img.example.com');
      await _openHostDialog(tester);
      final field = find.byKey(const ValueKey('pixiv-image-host-field'));
      expect(find.descendant(of: field, matching: find.text('img.example.com')), findsOneWidget);
      await tester.tap(find.text('Cancel'));
      await settlePixiv(tester);
      expect(harness.prefs.get<String>(optionPluginPixivImageHost), 'img.example.com');

      await _openHostDialog(tester);
      await tester.tap(find.byKey(const ValueKey('pixiv-image-host-reset')));
      await settlePixiv(tester);
      expect(harness.prefs.get<String>(optionPluginPixivImageHost), pixivImageHost);
      await disposePixiv(tester);
    });

    testWidgets('the dialog fits a narrow phone at twice the text size', (tester) async {
      await _pumpSection(tester, size: const Size(320, 640), textScale: 2);
      await _openHostDialog(tester);
      await tester.tap(find.text('Your own server'));
      await settlePixiv(tester);
      expect(tester.takeException(), isNull);
      await disposePixiv(tester);
    });
  });
}
