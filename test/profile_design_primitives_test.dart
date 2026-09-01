import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/profile/profile_chrome.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/theme_presets.dart';
import 'package:xta/ui/x_look_theme.dart';

void main() {
  final themes = <String, ThemeData>{
    'light': xLookLightTheme(null),
    'dark': xLookDimTheme(null),
    'true black': xLookLightsOutTheme(null),
    'fairy forest': fairyForestTheme(null),
    'pitch black': pitchBlackTheme(null),
  };

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
    expect(tabBar.indicatorColor, xLookLightTheme(null).colorScheme.primary);
  });

  testWidgets('profile identity wraps on a narrow large-text screen', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(
      MaterialApp(
        theme: xLookLightTheme(null),
        home: MediaQuery(
          data: const MediaQueryData(textScaler: TextScaler.linear(2)),
          child: Scaffold(
            body: SingleChildScrollView(
              child: ProfileIdentityHeader(
                banner: const SizedBox(height: 120),
                avatar: const ProfileAvatar(uri: null),
                actions: const ProfileActionCluster(
                  children: [
                    IconButton(onPressed: null, icon: Icon(Icons.tune)),
                  ],
                ),
                name: 'A deliberately long display name for a narrow phone',
                handle: '@long_handle',
                verified: true,
                protected: true,
                protectedLabel: 'Private profile',
                bio: const Text(
                  'A long biography remains readable instead of being clipped.',
                ),
                metadata: [
                  const ProfileMetadataItem(
                    icon: Icons.location_on_outlined,
                    child: Text('A very long location that needs to wrap'),
                  ),
                ],
                counts: [
                  ProfileCountButton(
                    count: '12K',
                    label: 'Followers',
                    onTap: () {},
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );

    expect(tester.takeException(), isNull);
    expect(find.textContaining('deliberately long'), findsOneWidget);
    expect(find.textContaining('very long location'), findsOneWidget);
  });

  testWidgets('profile filter exposes selected state without color alone', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: xLookLightTheme(null),
        home: Scaffold(
          body: ProfileFilterMenu<String>(
            selected: 'photos',
            defaultValue: 'all',
            options: const [
              ProfileFilterOption(
                value: 'all',
                label: 'All',
                icon: Icons.perm_media_outlined,
              ),
              ProfileFilterOption(
                value: 'photos',
                label: 'Photos',
                icon: Icons.photo_library_outlined,
              ),
            ],
            onSelected: (_) {},
          ),
        ),
      ),
    );

    expect(find.byIcon(Icons.filter_alt), findsOneWidget);
    expect(find.bySemanticsLabel('Photos'), findsWidgets);
    expect(
      tester.getSize(find.byType(PopupMenuButton<String>)).height,
      greaterThanOrEqualTo(kTweetTouchTarget),
    );
  });
}
