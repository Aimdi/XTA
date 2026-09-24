import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/search/recent_searches_bar.dart';
import 'package:xta/search/recent_searches_store.dart';
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
    expect(tabBar.indicatorColor, xLookLightTheme(null).colorScheme.primary);
    expect(
      tester.getSize(find.byType(SearchResultsTabBar)).height,
      kSearchTabsHeight,
    );
  });

  testWidgets('active filter chips expose a full touch target', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: xLookLightTheme(null),
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: L10n.delegate.supportedLocales,
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

  testWidgets('search input grows instead of clipping at large text', (
    tester,
  ) async {
    final controller = TextEditingController(text: 'A long query');
    final focusNode = FocusNode();
    addTearDown(controller.dispose);
    addTearDown(focusNode.dispose);

    await tester.pumpWidget(
      MaterialApp(
        theme: xLookLightTheme(null),
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: L10n.delegate.supportedLocales,
        home: MediaQuery(
          data: const MediaQueryData(textScaler: TextScaler.linear(2)),
          child: Scaffold(
            body: XtaSearchField(
              controller: controller,
              focusNode: focusNode,
              activeFilterCount: 2,
              onSubmitted: (_) {},
              onClear: () {},
              onAdvanced: () {},
            ),
          ),
        ),
      ),
    );

    expect(tester.takeException(), isNull);
    expect(
      tester.getSize(find.byType(XtaSearchField)).height,
      kSearchLargeTextFieldHeight,
    );
  });

  testWidgets('search start state makes the X scope explicit', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: L10n.delegate.supportedLocales,
        home: const Scaffold(body: SearchStartState()),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Search X'), findsOneWidget);
  });

  testWidgets('recent search clear only removes the current network', (
    tester,
  ) async {
    final prefs = PrefServiceCache();
    final store = RecentSearchesStore(prefs);
    addTearDown(store.destroy);
    await store.remember('x', 'flutter');
    await store.remember('reddit', 'dart');

    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: L10n.delegate.supportedLocales,
        home: Scaffold(
          body: RecentSearchesBar(
            store: store,
            scope: 'x',
            onSelected: (_) {},
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('flutter'), findsOneWidget);
    await tester.tap(
      find.byKey(const ValueKey('recent-searches-clear-x')),
    );
    await tester.pumpAndSettle();

    expect(find.text('flutter'), findsNothing);
    expect(store.state['x'], isEmpty);
    expect(store.state['reddit'], ['dart']);
  });

  testWidgets('advanced query action stays reachable above system insets', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: xLookLightTheme(null),
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: L10n.delegate.supportedLocales,
        home: Scaffold(
          bottomNavigationBar: SearchApplyBar(
            enabled: true,
            onPressed: () {},
          ),
        ),
      ),
    );

    expect(
      tester.getSize(find.byType(FilledButton)).height,
      greaterThanOrEqualTo(kTweetTouchTarget),
    );
    expect(find.byIcon(Icons.search), findsOneWidget);
  });

  testWidgets('advanced fields use the shared inset surface', (tester) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(
        theme: xLookLightsOutTheme(null),
        home: Scaffold(
          body: AdvancedSearchField(
            controller: controller,
            label: 'Words',
            onChanged: (_) {},
            onClear: () {},
          ),
        ),
      ),
    );

    final context = tester.element(find.byType(TextField));
    final tokens = XLookTokens.of(context);
    final field = tester.widget<TextField>(find.byType(TextField));
    expect(field.decoration?.fillColor, xLookInsetSurface(tokens));
  });
}
