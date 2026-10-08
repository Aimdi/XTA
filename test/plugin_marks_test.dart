import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/bluesky/bluesky_butterfly_icon.dart';
import 'package:xta/plugins/bluesky/bluesky_plugin.dart';
import 'package:xta/plugins/booru/booru_plugin.dart';
import 'package:xta/plugins/ehviewer/eh_plugin.dart';
import 'package:xta/plugins/instagram/instagram_plugin.dart';
import 'package:xta/plugins/mastodon/mastodon_plugin.dart';
import 'package:xta/plugins/pixiv/pixiv_plugin.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_marks.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/plugins/substack/substack_plugin.dart';
import 'package:xta/plugins/tiktok/tiktok_plugin.dart';

import 'support/painted_bounds.dart';

final _everyPlugin = <XtaPlugin>[coreXPlugin, ...builtInPlugins];

bool _isGlyph(Widget widget) => widget is CustomPaint || widget is Icon;

void main() {
  setUpAll(loadMaterialIcons);

  testWidgets('plugin marks are glyphs, not the old generic icons', (
    tester,
  ) async {
    final plugins = <XtaPlugin>[
      BlueskyPlugin(),
      SubstackPlugin(),
      PixivPlugin(),
      MastodonPlugin(),
      TikTokPlugin(),
      InstagramPlugin(),
      BooruPlugin(),
      EhViewerPlugin(),
    ];

    await tester.pumpWidget(
      MaterialApp(
        home: Column(
          children: [
            for (final plugin in plugins)
              PluginBrandMark(plugin: plugin, size: 24),
          ],
        ),
      ),
    );

    expect(find.byType(PluginBrandMark), findsNWidgets(8));
    expect(find.byType(BlueskyButterflyIcon), findsOneWidget);
    expect(find.byIcon(Icons.inventory_2), findsOneWidget);
    expect(find.byIcon(Icons.cloud), findsNothing);
    expect(find.byIcon(Icons.newspaper), findsNothing);
    expect(find.byIcon(Icons.brush), findsNothing);
    expect(find.byIcon(Icons.public), findsNothing);
    expect(find.byIcon(Icons.music_video_outlined), findsNothing);
    expect(find.byIcon(Icons.camera_alt_outlined), findsNothing);
    expect(find.byIcon(Icons.photo_library_outlined), findsNothing);
    expect(find.byIcon(Icons.collections_bookmark_outlined), findsNothing);
  });

  testWidgets('every plugin mark keeps its size inside a bigger tight box', (
    tester,
  ) async {
    for (final plugin in _everyPlugin) {
      await tester.pumpWidget(
        MaterialApp(
          home: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              PluginBrandMark(plugin: plugin, size: 16),
              SizedBox.square(
                dimension: 48,
                child: PluginBrandMark(plugin: plugin, size: 16),
              ),
            ],
          ),
        ),
      );
      final marks = find.byType(PluginBrandMark);
      final glyph = find.descendant(
        of: marks.last,
        matching: find.byWidgetPredicate(_isGlyph),
      );
      final glyphSize = tester.widget(glyph.first) is Icon
          ? 16.0
          : 16 * pluginMarkLiveShare;

      expect(tester.getSize(marks.first), const Size.square(16));
      expect(tester.getSize(marks.last), const Size.square(48));
      expect(
        tester.getSize(glyph.first),
        Size.square(glyphSize),
        reason: plugin.id,
      );
      expect(
        tester.getCenter(glyph.first),
        tester.getCenter(marks.last),
        reason: plugin.id,
      );
    }
  });

  testWidgets('every plugin mark paints across the same centred live area', (
    tester,
  ) async {
    const size = 24.0;
    const shot = ValueKey('marks');
    await tester.pumpWidget(
      MaterialApp(
        home: Center(
          child: RepaintBoundary(
            key: shot,
            child: ColoredBox(
              color: Colors.white,
              child: Wrap(
                children: [
                  for (final plugin in _everyPlugin)
                    Padding(
                      padding: const EdgeInsets.all(4),
                      child: PluginBrandMark(
                        key: ValueKey(plugin.id),
                        plugin: plugin,
                        size: size,
                      ),
                    ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
    final boxes = {
      for (final plugin in _everyPlugin)
        plugin.id: tester.getRect(find.byKey(ValueKey(plugin.id))),
    };

    final painted = await paintedBounds(tester, find.byKey(shot), boxes);

    // Some Material glyphs sit a little off centre by design (bookmark_add).
    for (final MapEntry(key: id, value: glyph) in painted.entries) {
      expect(
        glyph.longestSide,
        closeTo(size * pluginMarkLiveShare, 1),
        reason: id,
      );
      expect(
        (glyph.center - boxes[id]!.center).distance,
        lessThan(1.5),
        reason: id,
      );
    }
  });
}
