import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_gallery_layout.dart';
import 'package:xta/ui/x_look_theme.dart';

void main() {
  for (final tokens in [XLookTokens.light, XLookTokens.dim, XLookTokens.lightsOut]) {
    for (final direction in [TextDirection.ltr, TextDirection.rtl]) {
      for (final embedded in [true, false]) {
        for (final size in [const Size(320, 280), const Size(840, 360)]) {
          testWidgets('reader controls fit $tokens $direction embedded=$embedded at $size and 200% text', (
            tester,
          ) async {
            tester.view.physicalSize = size;
            tester.view.devicePixelRatio = 1;
            addTearDown(tester.view.resetPhysicalSize);
            addTearDown(tester.view.resetDevicePixelRatio);
            var settings = 0;
            var selected = 0;
            final chrome = PluginHomeChrome(
              title: 'A long localized plugin name',
              mark: const Icon(Icons.rss_feed),
              tabs: [
                PluginHomeTab(label: 'Home', icon: Icons.home_outlined, selected: true, onTap: () => selected++),
                PluginHomeTab(
                  label: 'Subscriptions and publications',
                  icon: Icons.article_outlined,
                  selected: false,
                  onTap: () {},
                ),
              ],
              actions: [
                IconButton(tooltip: 'Settings', icon: const Icon(Icons.settings_outlined), onPressed: () => settings++),
              ],
            );
            await tester.pumpWidget(
              MaterialApp(
                theme: xLookThemeData(tokens, null),
                home: MediaQuery(
                  data: MediaQueryData(
                    size: size,
                    textScaler: const TextScaler.linear(2),
                    padding: const EdgeInsets.only(top: 24, bottom: 16),
                    disableAnimations: true,
                  ),
                  child: Directionality(
                    textDirection: direction,
                    child: Scaffold(body: embedded ? PluginEmbedded(child: chrome) : chrome),
                  ),
                ),
              ),
            );
            expect(tester.takeException(), isNull);
            final settingsButton = find.byTooltip('Settings');
            expect(tester.getSize(settingsButton).height, greaterThanOrEqualTo(48));
            expect(tester.getSize(settingsButton).width, greaterThanOrEqualTo(48));
            await tester.tap(settingsButton);
            await tester.tap(find.byIcon(Icons.home_outlined));
            expect(settings, 1, reason: 'The section rail must not intercept settings.');
            expect(selected, 1);
            expect(find.text('A long localized plugin name'), embedded ? findsNothing : findsOneWidget);
          });
        }
      }
    }
  }

  test('gallery columns preserve room for artwork and large captions', () {
    expect(pluginGalleryColumns(320, TextScaler.noScaling), 2);
    expect(pluginGalleryColumns(320, TextScaler.linear(2)), 1);
    expect(pluginGalleryColumns(1000, TextScaler.noScaling), 4);
  });
}
