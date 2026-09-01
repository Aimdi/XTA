import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/home/home_chrome.dart';
import 'package:xta/home/home_selection_store.dart';
import 'package:xta/tweet/tweet_chrome.dart';
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
}
