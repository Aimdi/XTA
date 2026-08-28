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
import 'package:xta/plugins/substack/substack_plugin.dart';
import 'package:xta/plugins/tiktok/tiktok_plugin.dart';

void main() {
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
}
