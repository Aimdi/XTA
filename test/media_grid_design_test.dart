import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/profile/media_grid/media_grid.dart';
import 'package:xta/tweet/media_strip.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/x_look_theme.dart';

Future<MediaGridConfig> _resolveConfig(
  WidgetTester tester, {
  required String layout,
  int columns = 3,
}) async {
  late MediaGridConfig config;
  final prefs = PrefServiceCache(
    cache: {
      optionMediaGridLayout: layout,
      optionMediaGridColumns: columns,
    },
  );
  await tester.pumpWidget(
    PrefService(
      service: prefs,
      child: MaterialApp(
        theme: xLookLightTheme(null),
        home: Builder(
          builder: (context) {
            config = mediaGridConfigOf(context);
            return const SizedBox();
          },
        ),
      ),
    ),
  );
  return config;
}

void main() {
  testWidgets('the three media layouts remain intentionally distinct', (
    tester,
  ) async {
    final masonry = await _resolveConfig(
      tester,
      layout: mediaGridLayoutMasonry,
      columns: 4,
    );
    final timeline = await _resolveConfig(
      tester,
      layout: mediaGridLayoutFeed,
      columns: 4,
    );
    final twoColumns = await _resolveConfig(
      tester,
      layout: mediaGridLayoutTwoColumns,
      columns: 4,
    );

    expect(masonry.columns, 4);
    expect(masonry.spacing, kTweetMediaGap);
    expect(timeline.columns, 1);
    expect(timeline.spacing, kTweetSpace3);
    expect(timeline.radius, kTweetMediaRadius);
    expect(twoColumns.columns, 2);
    expect(twoColumns.spacing, kTweetSpace2);
  });

  testWidgets('browse thumbnails bound invalid and extreme source ratios', (
    tester,
  ) async {
    final config = await _resolveConfig(
      tester,
      layout: mediaGridLayoutFeed,
    );

    expect(mediaGridAspectRatio(double.nan, config), 1);
    expect(mediaGridAspectRatio(0, config), 1);
    expect(
      mediaGridAspectRatio(0.1, config),
      kMediaMinAspect,
    );
    expect(
      mediaGridAspectRatio(9, config),
      kMediaMaxAspect,
    );
  });

  testWidgets('an icon-only media badge retains a semantic label', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: xLookLightsOutTheme(null),
        home: const Scaffold(
          body: TweetMediaBadge(
            icon: Icons.gif_box_outlined,
            semanticLabel: 'Animated media',
          ),
        ),
      ),
    );

    expect(find.byIcon(Icons.gif_box_outlined), findsOneWidget);
    expect(find.bySemanticsLabel('Animated media'), findsOneWidget);
  });
}
