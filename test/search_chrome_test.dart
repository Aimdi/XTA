import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/search/search_chrome.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/x_look_theme.dart';

void main() {
  testWidgets('Search result tabs are labeled, scrollable, and token styled', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: xLookLightTheme(null),
        home: DefaultTabController(
          length: 4,
          child: Builder(
            builder: (context) => Scaffold(
              body: SearchResultsTabBar(
                controller: DefaultTabController.of(context),
                tabs: const [
                  Tab(text: 'Popular'),
                  Tab(text: 'Recent'),
                  Tab(text: 'Media'),
                  Tab(text: 'Accounts'),
                ],
              ),
            ),
          ),
        ),
      ),
    );

    final tabBar = tester.widget<TabBar>(find.byType(TabBar));
    expect(tabBar.isScrollable, isTrue);
    expect(tabBar.indicatorColor, XLookTokens.accentBlue);
    expect(
      tester.getSize(find.byType(SearchResultsTabBar)).height,
      kSearchTabsHeight,
    );
  });

  testWidgets('active filter chips expose a full touch target', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: xLookLightTheme(null),
        home: Scaffold(
          body: SearchFilterStrip(
            chips: [
              SearchActiveFilterChip(
                label: 'Hashtags: flutter',
                onDeleted: () {},
              ),
            ],
          ),
        ),
      ),
    );

    expect(
      tester.getSize(find.byType(InputChip)).height,
      greaterThanOrEqualTo(kTweetTouchTarget),
    );
    expect(
      tester.getSize(find.byType(SearchFilterStrip)).height,
      kSearchFilterStripHeight,
    );
  });
}
