import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/home/edge_swipe.dart';
import 'package:xta/home/home_chrome.dart';
import 'package:xta/home/home_screen.dart';

NavigationPage _page(String id, IconData icon) =>
    NavigationPage(id, (_) => id, Icon(icon), Icon(icon));

Widget _scaffold({
  int pages = 4,
  bool disableAnimations = false,
  Widget Function(int index)? pageBuilder,
}) {
  final prefs = PrefServiceCache(
    cache: {
      optionShowNavigationLabels: false,
      optionDisableAnimations: disableAnimations,
    },
  );

  return PrefService(
    service: prefs,
    child: MaterialApp(
      // The scaffold's drawer reads L10n and lists the groups, so the
      // delegates and a GroupsModel have to be present. The model is never
      // loaded here — its initial empty state is enough for the drawer.
      localizationsDelegates: const [
        L10n.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: L10n.delegate.supportedLocales,
      home: Provider<GroupsModel>(
        create: (_) => GroupsModel(prefs),
        child: ScaffoldWithBottomNavigation(
          pages: [
            for (var i = 0; i < pages; i++) _page('page$i', Icons.circle),
          ],
          prefs: prefs,
          initialPage: 0,
          builder: (index, _, _) =>
              pageBuilder?.call(index) ?? Center(child: Text('body$index')),
        ),
      ),
    ),
  );
}

Future<void> _swipeBar(WidgetTester tester, Offset offset) async {
  // The bar sits at the bottom of the scaffold; drag across it.
  final bar = find.byType(NavigationBar);
  await tester.fling(bar, offset, 600);
  await tester.pumpAndSettle();
}

void main() {
  group('pageAfterNavigationSwipe', () {
    test('a leftward swipe advances, a rightward one goes back', () {
      expect(
        pageAfterNavigationSwipe(current: 1, pageCount: 4, velocity: -800),
        2,
      );
      expect(
        pageAfterNavigationSwipe(current: 1, pageCount: 4, velocity: 800),
        0,
      );
    });

    test('the ends clamp rather than wrapping around', () {
      expect(
        pageAfterNavigationSwipe(current: 0, pageCount: 4, velocity: 800),
        0,
      );
      expect(
        pageAfterNavigationSwipe(current: 3, pageCount: 4, velocity: -800),
        3,
      );
    });

    test('a mis-tap, going nowhere at no speed, cannot change tab', () {
      expect(
        pageAfterNavigationSwipe(current: 1, pageCount: 4, velocity: -50),
        1,
      );
      expect(
        pageAfterNavigationSwipe(current: 1, pageCount: 4, velocity: 0),
        1,
      );
      expect(
        pageAfterNavigationSwipe(
          current: 1,
          pageCount: 4,
          velocity: -50,
          distance: -4,
        ),
        1,
      );
    });

    // The bar used to be gated on speed alone, so a deliberate drag — and any
    // drag paused before the finger lifted, which ends at no speed at all —
    // did nothing however far it went.
    test('a slow but long drag counts, even at no speed', () {
      expect(
        pageAfterNavigationSwipe(
          current: 1,
          pageCount: 4,
          velocity: 0,
          distance: -120,
        ),
        2,
      );
      expect(
        pageAfterNavigationSwipe(
          current: 1,
          pageCount: 4,
          velocity: 0,
          distance: 120,
        ),
        0,
      );
    });

    test('a long drag still clamps at the ends', () {
      expect(
        pageAfterNavigationSwipe(
          current: 0,
          pageCount: 4,
          velocity: 0,
          distance: 120,
        ),
        0,
      );
      expect(
        pageAfterNavigationSwipe(
          current: 3,
          pageCount: 4,
          velocity: 0,
          distance: -120,
        ),
        3,
      );
    });

    test(
      'a flick decides the direction when it disagrees with where the finger stopped',
      () {
        // Dragged back to the right, then flicked left: the flick is the intent.
        expect(
          pageAfterNavigationSwipe(
            current: 1,
            pageCount: 4,
            velocity: -800,
            distance: 60,
          ),
          2,
        );
      },
    );

    test('a single tab has nowhere to go', () {
      expect(
        pageAfterNavigationSwipe(current: 0, pageCount: 1, velocity: -800),
        0,
      );
      expect(
        pageAfterNavigationSwipe(
          current: 0,
          pageCount: 1,
          velocity: 0,
          distance: -400,
        ),
        0,
      );
    });
  });

  group('swiping the navigation bar', () {
    testWidgets('uses the shared non-overlaying Home navigation surface', (
      tester,
    ) async {
      await tester.pumpWidget(_scaffold());
      await tester.pumpAndSettle();

      expect(find.byType(HomeNavigationBar), findsOneWidget);
      final scaffold = tester
          .widgetList<Scaffold>(find.byType(Scaffold))
          .firstWhere((candidate) => candidate.bottomNavigationBar != null);
      expect(scaffold.extendBody, isFalse);
    });

    testWidgets('publishes edge-swipe handoff to nested page content', (
      tester,
    ) async {
      await tester.pumpWidget(
        _scaffold(
          pages: 2,
          pageBuilder: (index) => Builder(
            builder: (context) => TextButton(
              key: ValueKey('edge-page-$index'),
              onPressed: () =>
                  HomePageSwiper.maybeOf(context)!.movePage(1),
              child: Text('edge$index'),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const ValueKey('edge-page-0')));
      await tester.pumpAndSettle();

      expect(find.byKey(const ValueKey('edge-page-1')), findsOneWidget);
    });

    testWidgets('moves to the next tab and back', (tester) async {
      await tester.pumpWidget(_scaffold());
      await tester.pumpAndSettle();
      expect(find.text('body0'), findsOneWidget);

      await _swipeBar(tester, const Offset(-300, 0));
      expect(find.text('body1'), findsOneWidget);

      await _swipeBar(tester, const Offset(300, 0));
      expect(find.text('body0'), findsOneWidget);
    });

    testWidgets('does nothing at the first tab', (tester) async {
      await tester.pumpWidget(_scaffold());
      await tester.pumpAndSettle();

      await _swipeBar(tester, const Offset(300, 0));

      expect(find.text('body0'), findsOneWidget);
    });

    testWidgets('still switches when animations are turned off', (
      tester,
    ) async {
      await tester.pumpWidget(_scaffold(disableAnimations: true));
      await tester.pumpAndSettle();

      await _swipeBar(tester, const Offset(-300, 0));

      expect(find.text('body1'), findsOneWidget);
    });

    test('pagesForNavigationBar keeps two-or-more lists', () {
      final pages = [_page('feed', Icons.home), _page('saved', Icons.bookmark)];
      expect(pagesForNavigationBar(pages), pages);
    });

    test('pagesForNavigationBar replaces a single leftover tab', () {
      final pages = pagesForNavigationBar([_page('feed', Icons.home)]);
      expect(pages, hasLength(defaultHomePages.length));
      expect(pages.map((e) => e.id), [
        'feed',
        'subscriptions',
        'trending',
        'saved',
      ]);
    });

    testWidgets('a single leftover page still builds the default bar', (
      tester,
    ) async {
      final prefs = PrefServiceCache(
        cache: {optionShowNavigationLabels: false},
      );
      await tester.pumpWidget(
        PrefService(
          service: prefs,
          child: MaterialApp(
            localizationsDelegates: const [
              L10n.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
              GlobalCupertinoLocalizations.delegate,
            ],
            supportedLocales: L10n.delegate.supportedLocales,
            home: Provider<GroupsModel>(
              create: (_) => GroupsModel(prefs),
              child: ScaffoldWithBottomNavigation(
                pages: [_page('feed', Icons.home)],
                prefs: prefs,
                initialPage: 0,
                builder: (index, _, _) => Center(child: Text('fallback$index')),
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byType(NavigationBar), findsOneWidget);
      expect(find.text('fallback0'), findsOneWidget);
    });

    testWidgets('an empty page list still builds the default tabs', (
      tester,
    ) async {
      final prefs = PrefServiceCache(
        cache: {optionShowNavigationLabels: false},
      );
      await tester.pumpWidget(
        PrefService(
          service: prefs,
          child: MaterialApp(
            localizationsDelegates: const [
              L10n.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
              GlobalCupertinoLocalizations.delegate,
            ],
            supportedLocales: L10n.delegate.supportedLocales,
            home: Provider<GroupsModel>(
              create: (_) => GroupsModel(prefs),
              child: ScaffoldWithBottomNavigation(
                pages: const [],
                prefs: prefs,
                initialPage: 0,
                builder: (index, _, _) => Center(child: Text('fallback$index')),
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byType(NavigationBar), findsOneWidget);
      expect(find.text('fallback0'), findsOneWidget);
    });

    testWidgets('tapping a destination still works', (tester) async {
      await tester.pumpWidget(_scaffold());
      await tester.pumpAndSettle();

      await tester.tap(find.byIcon(Icons.circle).last);
      await tester.pumpAndSettle();

      expect(find.text('body3'), findsOneWidget);
    });

    testWidgets('the pager builds only the visible destination', (
      tester,
    ) async {
      final built = <int>[];
      final prefs = PrefServiceCache(
        cache: {optionShowNavigationLabels: false},
      );

      await tester.pumpWidget(
        PrefService(
          service: prefs,
          child: MaterialApp(
            localizationsDelegates: const [
              L10n.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
              GlobalCupertinoLocalizations.delegate,
            ],
            supportedLocales: L10n.delegate.supportedLocales,
            home: Provider<GroupsModel>(
              create: (_) => GroupsModel(prefs),
              child: ScaffoldWithBottomNavigation(
                pages: [
                  for (var i = 0; i < 4; i++) _page('page$i', Icons.circle),
                ],
                prefs: prefs,
                initialPage: 0,
                builder: (index, _, _) {
                  built.add(index);
                  return Center(child: Text('body$index'));
                },
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(built, [0]);
      expect(find.text('body0'), findsOneWidget);
      expect(find.text('body1'), findsNothing);
    });

    testWidgets('a pref write does not rebuild the visible page', (
      tester,
    ) async {
      var builds = 0;
      final prefs = PrefServiceCache(
        cache: {optionShowNavigationLabels: false, optionZenMode: false},
      );

      await tester.pumpWidget(
        PrefService(
          service: prefs,
          child: MaterialApp(
            localizationsDelegates: const [
              L10n.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
              GlobalCupertinoLocalizations.delegate,
            ],
            supportedLocales: L10n.delegate.supportedLocales,
            home: Provider<GroupsModel>(
              create: (_) => GroupsModel(prefs),
              child: ScaffoldWithBottomNavigation(
                pages: [
                  for (var i = 0; i < 3; i++) _page('page$i', Icons.circle),
                ],
                prefs: prefs,
                initialPage: 0,
                builder: (index, _, _) {
                  builds++;
                  return Center(child: Text('body$index'));
                },
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      final afterFirst = builds;

      await prefs.set(optionZenMode, true);
      await prefs.set(optionShowNavigationLabels, true);
      await tester.pump();

      expect(builds, afterFirst);
      expect(find.text('body0'), findsOneWidget);
    });
  });
}
