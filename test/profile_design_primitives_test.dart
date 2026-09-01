import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:quax/profile/media_grid/media_grid_items/media_grid_item.dart';
import 'package:quax/profile/profile_chrome.dart';
import 'package:quax/profile/profile_model.dart';
import 'package:quax/tweet/tweet_chrome.dart';
import 'package:quax/ui/theme_presets.dart';
import 'package:quax/ui/x_look_theme.dart';

void main() {
  final themes = <String, ThemeData>{
    'light': xLookLightTheme(null),
    'dark': xLookDimTheme(null),
    'true black': xLookLightsOutTheme(null),
    'fairy forest': fairyForestTheme(null),
    'pitch black': pitchBlackTheme(null),
  };

  test('ProfileViewStore owns tab, media, and scroll selections', () {
    final store = ProfileViewStore(0);
    addTearDown(store.destroy);

    store.selectTab(2);
    store.selectMediaFilter(MediaFilter.videos);
    store.setBackToTopVisible(true);

    expect(store.state.tabIndex, 2);
    expect(store.state.mediaFilter, MediaFilter.videos);
    expect(store.state.showBackToTop, isTrue);
  });

  for (final entry in themes.entries) {
    testWidgets('${entry.key} missing profile media uses tonal fallbacks', (
      tester,
    ) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: entry.value,
          home: const Scaffold(
            body: Column(
              children: [
                SizedBox(
                  height: kProfileBannerHeight,
                  child: ProfileBanner(uri: null),
                ),
                ProfileAvatar(uri: null),
              ],
            ),
          ),
        ),
      );

      expect(find.byIcon(Icons.person_outline), findsOneWidget);
      expect(
        tester.getSize(find.byType(ProfileAvatar)),
        const Size.square(kProfileAvatarSize),
      );
      expect(find.byType(ExtendedImage), findsNothing);
    });
  }

  testWidgets('profile count link meets the interaction minimum', (
    tester,
  ) async {
    var tapped = false;
    await tester.pumpWidget(
      MaterialApp(
        theme: xLookLightTheme(null),
        home: Scaffold(
          body: ProfileCountButton(
            count: '1.2K',
            label: 'Followers',
            onTap: () => tapped = true,
          ),
        ),
      ),
    );

    expect(
      tester.getSize(find.byType(InkWell)).height,
      greaterThanOrEqualTo(kTweetTouchTarget),
    );
    await tester.tap(find.textContaining('1.2K'));
    expect(tapped, isTrue);
  });

  testWidgets('profile tabs use one pinned-height selection surface', (
    tester,
  ) async {
    late TabController controller;
    await tester.pumpWidget(
      MaterialApp(
        theme: xLookLightTheme(null),
        home: DefaultTabController(
          length: 2,
          child: Builder(
            builder: (context) {
              controller = DefaultTabController.of(context);
              return Scaffold(
                body: ProfileTabsBar(
                  controller: controller,
                  tabs: const [
                    Tab(text: 'Posts'),
                    Tab(text: 'Media'),
                  ],
                ),
              );
            },
          ),
        ),
      ),
    );

    expect(
      tester.getSize(find.byType(ProfileTabsBar)).height,
      kProfileTabHeight,
    );
    final tabBar = tester.widget<TabBar>(find.byType(TabBar));
    expect(tabBar.isScrollable, isTrue);
    expect(tabBar.indicatorColor, XLookTokens.accentBlue);
  });
}
