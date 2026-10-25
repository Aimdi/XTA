import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/group/group_chrome.dart';
import 'package:xta/home/home_timeline_controls.dart';
import 'package:xta/home/home_timeline_picker.dart';
import 'package:xta/main.dart' show xtaLocalizationsDelegates;
import 'package:xta/ui/x_look_theme.dart';

const _posts = ValueKey('home-posts-tab');
const _media = ValueKey('home-media-toggle');
const _order = ValueKey('home-order-menu');

SubscriptionGroupGet _group() => SubscriptionGroupGet(
  id: 'following',
  name: 'Following',
  icon: '',
  subscriptions: const [],
  includeReplies: true,
  includeRetweets: true,
  popular: false,
  custom: true,
  minLikes: 10,
);

Widget _app(
  Widget child, {
  double width = 390,
  TextDirection direction = TextDirection.ltr,
  Locale locale = const Locale('en'),
  double scale = 1,
}) => MaterialApp(
  theme: xLookLightsOutTheme(null),
  localizationsDelegates: xtaLocalizationsDelegates,
  supportedLocales: [locale],
  locale: locale,
  builder: (context, app) => MediaQuery(
    data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(scale)),
    child: app!,
  ),
  home: Directionality(
    textDirection: direction,
    child: Scaffold(
      body: Align(
        alignment: Alignment.topCenter,
        child: SizedBox(width: width, child: child),
      ),
    ),
  ),
);

HomeFollowingBar _bar({
  bool loaded = true,
  HomeFollowingBarLayout layout = HomeFollowingBarLayout.oneRow,
  VoidCallback? onMediaToggle,
  ValueChanged<int>? onOrderSelected,
  VoidCallback? onFilters,
}) => HomeFollowingBar(
  group: loaded ? _group() : null,
  layout: layout,
  mediaOnly: false,
  onOrderSelected: onOrderSelected ?? (_) {},
  onMediaToggle: onMediaToggle ?? () {},
  onFilters: onFilters ?? () {},
  actions: [
    for (final (tooltip, icon) in [
      ('Feeds', Icons.filter_none),
      ('Search loaded posts', Icons.manage_search),
      ('Home feed accounts', Icons.manage_accounts_outlined),
    ])
      IconButton(tooltip: tooltip, icon: Icon(icon), onPressed: () {}),
  ],
);

List<double> _centres(WidgetTester tester) => [
  for (final control in [
    find.byKey(_posts),
    find.byKey(_media),
    find.byKey(_order),
    find.byTooltip('Feeds'),
    find.byTooltip('Search loaded posts'),
    find.byTooltip('Home feed accounts'),
  ])
    tester.getCenter(control).dx,
];

void main() {
  setUpAll(() async {
    // The fit is measured from real glyphs: the test font draws every one 1 em wide.
    for (final weight in ['Regular', 'Medium', 'SemiBold', 'Bold']) {
      await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-$weight.ttf'))).load();
    }
  });

  testWidgets('Following\'s header is one bar: Posts | Media, then the chip, Feeds, search and accounts', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(_app(_bar()));
    expect(tester.getSize(find.byType(HomeFollowingBar)).height, kHomeFollowingBarHeight);
    final centres = _centres(tester);
    expect(centres, orderedEquals([...centres]..sort()));
    expect(find.byIcon(Icons.photo_library_outlined), findsNothing, reason: 'Media is text only');
    await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
    await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));

    await tester.pumpWidget(_app(_bar(), direction: TextDirection.rtl));
    final mirrored = _centres(tester);
    expect(mirrored, orderedEquals([...mirrored]..sort((a, b) => b.compareTo(a))));
    expect(tester.takeException(), isNull);
    semantics.dispose();
  });

  testWidgets('The chip counts filters and says the order, without drawing its name', (tester) async {
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(_app(_bar()));
    final chip = find.byKey(_order);
    expect(find.descendant(of: chip, matching: find.text('Custom')), findsNothing);
    expect(find.descendant(of: chip, matching: find.text('${groupActiveFilterCount(_group())}')), findsOneWidget);
    expect(find.descendant(of: chip, matching: find.byIcon(Icons.expand_more)), findsOneWidget);
    expect(tester.getSemantics(chip), isSemantics(tooltip: 'Custom · Filters', isButton: true));
    semantics.dispose();
  });

  for (final (locale, width, scale, fit) in [
    (const Locale('en'), 390.0, 1.0, HomeFollowingBarLayout.oneRow),
    (const Locale('de'), 411.0, 1.0, HomeFollowingBarLayout.oneRow),
    (const Locale('de'), 390.0, 1.0, HomeFollowingBarLayout.oneRow),
    (const Locale('de'), 360.0, 1.0, HomeFollowingBarLayout.compact),
    (const Locale('de'), 320.0, 1.3, HomeFollowingBarLayout.twoRows),
  ]) {
    testWidgets('Tweets | Media at $width dp, text ×$scale, ${locale.languageCode}: $fit', (tester) async {
      HomeFollowingBarLayout? measured;
      await tester.pumpWidget(
        _app(
          Builder(
            builder: (context) {
              measured = homeFollowingBarLayout(context, width: width, actions: 3);
              return const SizedBox();
            },
          ),
          locale: locale,
          scale: scale,
        ),
      );
      expect(measured, fit);
    });
  }

  testWidgets('The closer fit keeps the chip and drops its chevron', (tester) async {
    await tester.pumpWidget(_app(_bar(layout: HomeFollowingBarLayout.compact), width: 360));
    final chip = find.byKey(_order);
    expect(chip.hitTestable(), findsOneWidget);
    expect(find.descendant(of: chip, matching: find.byIcon(Icons.expand_more)), findsNothing);
    expect(tester.getSize(find.byType(HomeFollowingBar)).height, kHomeFollowingBarHeight);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Without room for one row the tabs sit under the controls', (tester) async {
    await tester.pumpWidget(_app(_bar(layout: HomeFollowingBarLayout.twoRows), width: 320, scale: 1.3));
    expect(tester.getSize(find.byType(HomeFollowingBar)).height, 2 * kHomeFollowingBarHeight);
    final chip = tester.getCenter(find.byKey(_order));
    for (final tab in [_posts, _media]) {
      expect(find.byKey(tab).hitTestable(), findsOneWidget);
      expect(tester.getCenter(find.byKey(tab)).dy, greaterThan(chip.dy + kHomeFollowingBarHeight / 2));
    }
    expect(find.descendant(of: find.byKey(_order), matching: find.byIcon(Icons.expand_more)), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('The chip waits for the group; the rest is there at once', (tester) async {
    await tester.pumpWidget(_app(_bar(loaded: false)));
    expect(find.byKey(_order), findsNothing);
    for (final tooltip in ['Feeds', 'Search loaded posts', 'Home feed accounts']) {
      expect(find.byTooltip(tooltip).hitTestable(), findsOneWidget, reason: tooltip);
    }
    expect(find.byKey(_posts).hitTestable(), findsOneWidget);
  });

  testWidgets('The selected tab carries the marker, and Media asks for media', (tester) async {
    var toggled = 0;
    await tester.pumpWidget(_app(_bar(onMediaToggle: () => toggled++)));
    Color? marker(Key tab) {
      final container = tester.widget<AnimatedContainer>(
        find.descendant(of: find.byKey(tab), matching: find.byType(AnimatedContainer)),
      );
      return (container.decoration as BoxDecoration?)?.color;
    }

    expect(marker(_posts), isNot(Colors.transparent));
    expect(marker(_media), Colors.transparent);
    await tester.tap(find.byKey(_posts));
    expect(toggled, 0, reason: 'the selected tab stays as it is');
    await tester.tap(find.byKey(_media));
    expect(toggled, 1);
  });

  testWidgets('The chip opens Recent, Popular, Custom and Filters', (tester) async {
    int? picked;
    var filters = 0;
    await tester.pumpWidget(_app(_bar(onOrderSelected: (order) => picked = order, onFilters: () => filters++)));
    await tester.tap(find.byKey(_order));
    await tester.pumpAndSettle();
    for (final label in ['Recent', 'Popular', 'Custom', 'Filters']) {
      expect(find.text(label), findsOneWidget, reason: label);
    }
    await tester.tap(find.text('Popular'));
    await tester.pumpAndSettle();
    expect(picked, 1);

    await tester.tap(find.byKey(_order));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Filters'));
    await tester.pumpAndSettle();
    expect(filters, 1);
  });

  testWidgets('The Feeds sheet offers the side menu, and the avatar on a long press', (tester) async {
    HomeTimelineSelection? result;
    await tester.pumpWidget(
      _app(
        Builder(
          builder: (context) => TextButton(
            onPressed: () async => result = await showModalBottomSheet<HomeTimelineSelection>(
              context: context,
              isScrollControlled: true,
              builder: (_) => const HomeTimelinePicker(
                selected: 'following',
                showMenu: true,
                options: [
                  HomeTimelineOption(
                    id: 'following',
                    label: 'Following',
                    mark: Icon(Icons.home),
                    plugin: false,
                    unread: false,
                  ),
                ],
              ),
            ),
            child: const Text('Feeds'),
          ),
        ),
      ),
    );
    final menu = find.byKey(const ValueKey('home-open-menu'));
    await tester.tap(find.text('Feeds'));
    await tester.pumpAndSettle();
    expect(find.descendant(of: menu, matching: find.text('Open navigation menu')), findsOneWidget);
    await tester.tap(menu);
    await tester.pumpAndSettle();
    expect(result?.menu, HomeTimelineMenu.drawer);

    await tester.tap(find.text('Feeds'));
    await tester.pumpAndSettle();
    await tester.longPress(menu);
    await tester.pumpAndSettle();
    expect(result?.menu, HomeTimelineMenu.avatar);
    expect(tester.takeException(), isNull);
  });
}
