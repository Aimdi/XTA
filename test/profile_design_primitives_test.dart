import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/profile/profile_chrome.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/reader_chrome.dart';
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

  testWidgets('profile counts stay side by side and remain tappable', (
    tester,
  ) async {
    var followingTapped = false;
    var followersTapped = false;
    await tester.pumpWidget(
      MaterialApp(
        theme: xLookLightTheme(null),
        home: Scaffold(
          body: ProfileIdentityHeader(
            banner: const SizedBox(height: 120),
            avatar: const ProfileAvatar(uri: null),
            actions: const SizedBox.shrink(),
            name: 'Name',
            handle: '@handle',
            verified: false,
            protected: false,
            protectedLabel: 'Private profile',
            counts: [
              ProfileCountButton(
                count: '23',
                label: 'Following',
                onTap: () => followingTapped = true,
              ),
              ProfileCountButton(
                count: '13.9K',
                label: 'Followers',
                onTap: () => followersTapped = true,
              ),
            ],
          ),
        ),
      ),
    );

    final following = find.textContaining('23');
    final followers = find.textContaining('13.9K');
    expect(tester.getTopLeft(following).dy, tester.getTopLeft(followers).dy);
    expect(
      tester.getTopLeft(following).dx,
      lessThan(tester.getTopLeft(followers).dx),
    );

    await tester.tap(following);
    await tester.tap(followers);
    expect(followingTapped, isTrue);
    expect(followersTapped, isTrue);
  });

  testWidgets(
    'avatar bottom, actions and wrapped counts have separate targets at 320px',
    (tester) async {
      tester.view.physicalSize = const Size(320, 800);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final tapped = <String>[];
      await tester.pumpWidget(
        MaterialApp(
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: const TextScaler.linear(2)),
            child: child!,
          ),
          home: Scaffold(
            body: SingleChildScrollView(
              child: ProfileIdentityHeader(
                banner: const SizedBox(height: 120, width: double.infinity),
                avatar: GestureDetector(
                  key: const ValueKey('avatar'),
                  onTap: () => tapped.add('avatar'),
                  child: const SizedBox.square(
                    dimension: kProfileAvatarSize,
                    child: ColoredBox(color: Colors.blue),
                  ),
                ),
                actions: ProfileActionCluster(
                  children: [
                    IconButton(
                      onPressed: () => tapped.add('settings'),
                      icon: const Icon(Icons.tune),
                    ),
                    IconButton(
                      onPressed: () => tapped.add('follow'),
                      icon: const Icon(Icons.person_add),
                    ),
                  ],
                ),
                name: 'Reader',
                handle: '@reader',
                verified: false,
                protected: false,
                protectedLabel: 'Protected',
                counts: [
                  ProfileCountButton(
                    count: '234',
                    label: 'Following',
                    onTap: () => tapped.add('following'),
                  ),
                  ProfileCountButton(
                    count: '13.9K',
                    label: 'Followers',
                    onTap: () => tapped.add('followers'),
                  ),
                ],
              ),
            ),
          ),
        ),
      );
      final avatar = tester.getRect(find.byKey(const ValueKey('avatar')));
      await tester.tapAt(Offset(avatar.center.dx, avatar.bottom - 5));
      await tester.tap(find.byIcon(Icons.tune));
      await tester.tap(find.byIcon(Icons.person_add));
      await tester.tap(find.textContaining('234'));
      await tester.tap(find.textContaining('13.9K'));
      expect(tapped, [
        'avatar',
        'settings',
        'follow',
        'following',
        'followers',
      ]);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('profile actions stay flat in one row and remain tappable', (
    tester,
  ) async {
    var settingsTapped = false;
    var followTapped = false;
    await tester.pumpWidget(
      MaterialApp(
        theme: xLookLightTheme(null),
        home: Scaffold(
          body: Align(
            alignment: Alignment.topRight,
            child: ProfileActionCluster(
              children: [
                IconButton(
                  onPressed: () => settingsTapped = true,
                  icon: const Icon(Icons.tune_outlined),
                ),
                IconButton(
                  onPressed: () => followTapped = true,
                  icon: const Icon(Icons.person_add),
                ),
              ],
            ),
          ),
        ),
      ),
    );

    final settings = find.byIcon(Icons.tune_outlined);
    final follow = find.byIcon(Icons.person_add);
    expect(tester.getTopLeft(settings).dy, tester.getTopLeft(follow).dy);
    expect(
      tester.getTopLeft(settings).dx,
      lessThan(tester.getTopLeft(follow).dx),
    );
    for (final button in find.byType(IconButton).evaluate()) {
      expect(
        tester.getSize(find.byWidget(button.widget)),
        const Size.square(kTweetTouchTarget),
      );
    }
    expect(
      find.descendant(
        of: find.byType(ProfileActionCluster),
        matching: find.byWidgetPredicate(
          (widget) =>
              widget is DecoratedBox &&
              (widget.decoration as BoxDecoration?)?.border != null,
        ),
      ),
      findsNothing,
      reason: 'no ring or pill around the actions',
    );

    await tester.tap(settings);
    await tester.tap(follow);
    expect(settingsTapped, isTrue);
    expect(followTapped, isTrue);
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
          data: const MediaQueryData(
            size: Size(320, 800),
            textScaler: TextScaler.linear(2),
          ),
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

  testWidgets('profile filter shows every choice and marks the selected one without color alone', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    String? picked;
    await tester.pumpWidget(
      MaterialApp(
        theme: xLookLightTheme(null),
        home: Scaffold(
          body: ProfileFilterBar<String>(
            selected: 'photos',
            options: const [
              ProfileFilterOption(value: 'all', label: 'All'),
              ProfileFilterOption(value: 'photos', label: 'Photos'),
              ProfileFilterOption(value: 'videos', label: 'Videos'),
            ],
            onSelected: (value) => picked = value,
          ),
        ),
      ),
    );

    FontWeight? weight(String label) =>
        tester.renderObject<RenderParagraph>(find.text(label)).text.style?.fontWeight;
    expect(weight('Photos'), FontWeight.w700);
    expect(weight('All'), FontWeight.w500);
    expect(weight('Videos'), FontWeight.w500);
    expect(find.byType(Icon), findsNothing, reason: 'words only: no icon, check or pill');
    expect(find.byType(ChoiceChip), findsNothing);
    expect(
      tester.getSemantics(find.text('Photos')),
      isSemantics(isSelected: true, isButton: true, hasSelectedState: true),
    );
    expect(
      tester.getSemantics(find.text('All')),
      isSemantics(isSelected: false, isButton: true, hasSelectedState: true),
    );
    for (final label in ['All', 'Photos', 'Videos']) {
      final target = find.ancestor(of: find.text(label), matching: find.byType(TextButton));
      expect(tester.getSize(target).height, greaterThanOrEqualTo(kTweetTouchTarget));
    }

    await tester.tap(find.text('All'));
    expect(picked, 'all');
    semantics.dispose();
  });

  testWidgets('flat profile tabs share the width and mark the selected name', (
    tester,
  ) async {
    late TabController controller;
    Future<void> pump({required bool even}) => tester.pumpWidget(
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
                  height: kProfileFlatTabHeight,
                  flat: true,
                  even: even,
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

    await pump(even: true);
    expect(
      tester.getSize(find.byType(ProfileTabsBar)).height,
      kProfileFlatTabHeight,
    );
    var tabBar = tester.widget<TabBar>(find.byType(TabBar));
    expect(tabBar.isScrollable, isFalse);
    expect(tabBar.indicatorSize, TabBarIndicatorSize.label);
    final marker = tabBar.indicator! as UnderlineTabIndicator;
    expect(marker.borderSide.width, kReaderTabMarkerHeight);
    expect(marker.borderRadius, isNotNull);
    expect(
      tester.getCenter(find.text('Media')).dx - tester.getCenter(find.text('Posts')).dx,
      moreOrLessEquals(tester.getSize(find.byType(TabBar)).width / 2, epsilon: 1),
      reason: 'each name centred in half the bar',
    );

    await pump(even: false);
    tabBar = tester.widget<TabBar>(find.byType(TabBar));
    expect(tabBar.isScrollable, isTrue);
    expect(tabBar.tabAlignment, TabAlignment.start);
  });
}
