import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/constants.dart';
import 'package:xta/home/home_chrome.dart';
import 'package:xta/home/home_selection_store.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/contrast.dart';
import 'package:xta/ui/x_look_theme.dart';

Widget _app(Widget child) => MaterialApp(
  theme: xLookLightTheme(null),
  home: Scaffold(appBar: AppBar(title: child)),
);

void main() {
  test('HomeSelectionStore only changes for a new selection', () {
    final store = HomeSelectionStore<int>(0);
    addTearDown(store.destroy);

    store.select(1);
    expect(store.state, 1);

    store.select(1);
    expect(store.state, 1);
  });

  testWidgets('feed switcher marks and returns the selected feed', (
    tester,
  ) async {
    int? selected;
    await tester.pumpWidget(
      _app(
        HomeFeedSwitcher<int>(
          selected: 0,
          options: const [
            HomeSwitcherOption(value: 0, label: 'Following'),
            HomeSwitcherOption(value: 1, label: 'For you'),
          ],
          onSelected: (value) => selected = value,
        ),
      ),
    );

    expect(
      tester.getSize(find.byType(HomeFeedSwitcher<int>)).height,
      greaterThanOrEqualTo(kTweetTouchTarget),
    );
    await tester.tap(find.text('Following'));
    await tester.pumpAndSettle();

    expect(find.byIcon(Icons.check), findsOneWidget);
    await tester.tap(find.text('For you'));
    await tester.pumpAndSettle();
    expect(selected, 1);
  });

  testWidgets(
    'bottom navigation uses distinct selected icons and a hairline boundary',
    (tester) async {
      int? selected;
      await tester.pumpWidget(
        MaterialApp(
          theme: xLookLightTheme(null),
          home: Scaffold(
            bottomNavigationBar: HomeNavigationBar(
              selectedIndex: 0,
              showLabels: true,
              disableAnimations: true,
              items: const [
                HomeNavigationItem(
                  label: 'Home',
                  icon: Icon(Icons.home_outlined),
                  selectedIcon: Icon(Icons.home),
                ),
                HomeNavigationItem(
                  label: 'Search',
                  icon: Icon(Icons.search_outlined),
                  selectedIcon: Icon(Icons.search),
                ),
              ],
              onSelected: (value) => selected = value,
            ),
          ),
        ),
      );

      final navigation = tester.widget<NavigationBar>(
        find.byType(NavigationBar),
      );
      expect(navigation.height, kHomeNavigationHeight);
      expect(find.byIcon(Icons.home), findsWidgets);
      expect(find.byIcon(Icons.home_outlined), findsNothing);

      final decorated = tester
          .widgetList<DecoratedBox>(find.byType(DecoratedBox))
          .firstWhere((widget) {
            final decoration = widget.decoration;
            if (decoration is! BoxDecoration || decoration.border is! Border) {
              return false;
            }
            return (decoration.border! as Border).top.width ==
                kTweetDividerThickness;
          });
      final border = (decorated.decoration as BoxDecoration).border! as Border;
      expect(border.top.width, kTweetDividerThickness);

      await tester.tap(find.text('Search'));
      await tester.pump();
      expect(selected, 1);
    },
  );

  testWidgets('selected navigation remains legible with a yellow accent', (
    tester,
  ) async {
    final accent = xLookAccents['yellow']!;
    final tokens = XLookTokens.light.copyWith(accent: accent);
    await tester.pumpWidget(
      MaterialApp(
        theme: xLookThemeData(tokens, null),
        home: Scaffold(
          bottomNavigationBar: HomeNavigationBar(
            selectedIndex: 0,
            showLabels: true,
            disableAnimations: true,
            items: const [
              HomeNavigationItem(
                label: 'Home',
                icon: Icon(Icons.home_outlined),
                selectedIcon: Icon(Icons.home),
              ),
              HomeNavigationItem(
                label: 'Search',
                icon: Icon(Icons.search_outlined),
                selectedIcon: Icon(Icons.search),
              ),
            ],
            onSelected: (_) {},
          ),
        ),
      ),
    );

    final context = tester.element(find.byType(NavigationBar));
    final navigationTheme = NavigationBarTheme.of(context);
    final selectedColor = navigationTheme.iconTheme!
        .resolve(<WidgetState>{WidgetState.selected})!
        .color!;
    expect(
      contrastRatio(selectedColor, tokens.background),
      greaterThanOrEqualTo(4.5),
    );
  });

  testWidgets('home title and actions remain bounded at large text scale', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: xLookLightTheme(null),
        home: MediaQuery(
          data: const MediaQueryData(
            size: Size(320, 640),
            textScaler: TextScaler.linear(2),
          ),
          child: Scaffold(
            appBar: AppBar(
              title: const HomeAppBarTitle(
                label: 'A deliberately long localized Home title',
              ),
              actions: [
                HomeAppBarActions(
                  children: [
                    IconButton(
                      tooltip: 'Filter',
                      icon: const Icon(Icons.tune),
                      onPressed: () {},
                    ),
                    IconButton(
                      tooltip: 'Accounts',
                      icon: const Icon(Icons.manage_accounts_outlined),
                      onPressed: () {},
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );

    expect(tester.takeException(), isNull);
    expect(
      tester.widget<Text>(
        find.text('A deliberately long localized Home title'),
      ),
      isA<Text>()
          .having((text) => text.maxLines, 'maxLines', 1)
          .having((text) => text.overflow, 'overflow', TextOverflow.ellipsis),
    );
    expect(
      tester.getSize(find.widgetWithIcon(IconButton, Icons.tune)),
      const Size.square(kTweetTouchTarget),
    );
    expect(
      tester.getSize(
        find.widgetWithIcon(IconButton, Icons.manage_accounts_outlined),
      ),
      const Size.square(kTweetTouchTarget),
    );
  });

  testWidgets('home feed strip keeps compact tabs and a fixed add action', (
    tester,
  ) async {
    var added = false;
    await tester.pumpWidget(
      MaterialApp(
        theme: xLookLightTheme(null),
        home: MediaQuery(
          data: const MediaQueryData(
            size: Size(320, 640),
            textScaler: TextScaler.linear(2),
          ),
          child: Scaffold(
            body: Align(
              alignment: Alignment.topCenter,
              child: SizedBox(
                width: 320,
                child: DefaultTabController(
                  length: 4,
                  child: HomeFeedStrip(
                    tabs: const [
                      Tab(text: 'Following'),
                      Tab(text: 'For you'),
                      Tab(text: 'Reddit'),
                      Tab(text: 'Blue'),
                    ],
                    addTooltip: 'Add timeline',
                    onAdd: () => added = true,
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );

    expect(tester.takeException(), isNull);
    expect(
      tester.getSize(find.byType(HomeFeedStrip)),
      const Size(320, kHomeFeedStripHeight),
    );
    expect(
      tester.getSize(find.byTooltip('Add timeline')),
      const Size.square(kTweetTouchTarget),
    );

    final tabBar = tester.widget<TabBar>(find.byType(TabBar));
    expect(tabBar.dividerHeight, 0);
    expect(tabBar.isScrollable, isTrue);
    expect(tabBar.tabAlignment, TabAlignment.start);
    expect(
      tabBar.labelPadding,
      const EdgeInsets.symmetric(horizontal: kHomeFeedTabHorizontalPadding),
    );
    expect(tabBar.indicatorWeight, kHomeFeedIndicatorThickness);

    final addBoundary = tester
        .widgetList<DecoratedBox>(find.byType(DecoratedBox))
        .map((widget) => widget.decoration)
        .whereType<BoxDecoration>()
        .map((decoration) => decoration.border)
        .whereType<BorderDirectional>()
        .singleWhere((border) => border.start.width == kTweetDividerThickness);
    expect(addBoundary.start.color, isNot(Colors.transparent));

    final tabRect = tester.getRect(find.byType(TabBar));
    final addRect = tester.getRect(find.byTooltip('Add timeline'));
    expect(tabRect.right, lessThanOrEqualTo(addRect.left));

    await tester.tap(find.byTooltip('Add timeline'));
    expect(added, isTrue);
  });
}
