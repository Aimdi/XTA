import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/home/home_screen.dart';
import 'package:xta/main.dart';
import 'package:xta/search/search_scope.dart';

final _prefs = PrefServiceCache(cache: {optionShowNavigationLabels: true, optionDisableAnimations: false});

/// The home tabs with [pages] selected, the way the home screen rebuilds them
/// when a tab is enabled or disabled in the settings. Returns the focus nodes
/// the pages were built with.
Future<Map<int, FocusNode>> _pumpTabs(
  WidgetTester tester,
  List<NavigationPage> pages, {
  SearchScopeStore? scope,
}) async {
  var focusNodes = <int, FocusNode>{};
  await tester.pumpWidget(
    PrefService(
      service: _prefs,
      child: MaterialApp(
        localizationsDelegates: xtaLocalizationsDelegates,
        supportedLocales: L10n.delegate.supportedLocales,
        home: MultiProvider(
          providers: [
            Provider<GroupsModel>(create: (_) => GroupsModel(_prefs)),
            if (scope != null) Provider<SearchScopeStore>.value(value: scope),
          ],
          child: ScaffoldWithBottomNavigation(
            pages: pages,
            prefs: _prefs,
            initialPage: 0,
            builder: (index, _, nodes) {
              focusNodes = Map.of(nodes);
              return Center(child: Text('page ${pages[index].id}'));
            },
          ),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
  return focusNodes;
}

int _highlighted(WidgetTester tester) => tester.widget<NavigationBar>(find.byType(NavigationBar)).selectedIndex;

void main() {
  final [feed, subscriptions, trending, _] = defaultHomePages;

  testWidgets('a tab enabled after the start gets a focus node', (tester) async {
    await _pumpTabs(tester, [feed, subscriptions]);
    final focusNodes = await _pumpTabs(tester, [feed, subscriptions, trending]);

    expect(focusNodes.keys, [0, 1, 2], reason: 'Discover reads its focus node by index');
  });

  testWidgets('tapping a tab fades its page in', (tester) async {
    await _pumpTabs(tester, [feed, subscriptions, trending]);
    double pageOpacity() => tester
        .widget<FadeTransition>(find.ancestor(of: find.byType(PageView), matching: find.byType(FadeTransition)).first)
        .opacity
        .value;
    expect(pageOpacity(), 1);

    await tester.tap(find.text(L10n.current.discover));
    await tester.pump();
    expect(find.text('page trending'), findsOneWidget, reason: 'the page is there at once');
    await tester.pump(const Duration(milliseconds: 30));
    expect(pageOpacity(), inExclusiveRange(0, 1));
    await tester.pumpAndSettle();
    expect(pageOpacity(), 1);
  });

  testWidgets('a tab enabled after the start is highlighted when tapped', (tester) async {
    await _pumpTabs(tester, [feed, subscriptions]);
    await _pumpTabs(tester, [feed, subscriptions, trending]);

    await tester.tap(find.text(L10n.current.discover));
    await tester.pumpAndSettle();

    expect(find.text('page trending'), findsOneWidget);
    expect(_highlighted(tester), 2, reason: 'the tab shown and the tab highlighted should both be Discover');
  });

  testWidgets('Discover enabled after the start searches the network it was reached from', (tester) async {
    final scope = SearchScopeStore();
    addTearDown(scope.destroy);
    final hackerNews = NavigationPage(
      pluginIdHackerNews,
      (_) => 'HN',
      const Icon(Icons.forum),
      const Icon(Icons.forum),
    );
    await _pumpTabs(tester, [hackerNews, subscriptions], scope: scope);
    await _pumpTabs(tester, [hackerNews, subscriptions, trending], scope: scope);

    await tester.tap(find.text(L10n.current.discover));
    await tester.pumpAndSettle();

    expect(find.text('page trending'), findsOneWidget);
    expect(scope.state, pluginIdHackerNews);
  });

  testWidgets('a tab disabled after the start drops its focus node', (tester) async {
    await _pumpTabs(tester, [feed, subscriptions, trending]);
    final focusNodes = await _pumpTabs(tester, [feed, subscriptions]);

    expect(focusNodes.keys, [0, 1]);
  });
}
