import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/search/recent_searches_store.dart';
import 'package:xta/search/search.dart';
import 'package:xta/search/advanced_search.dart';
import 'package:xta/search/search_chrome.dart';

void main() {
  testWidgets('empty X Search reuses recent history and library search access', (tester) async {
    final prefs = PrefServiceCache();
    await prefs.set(
      recentSearchesPreference,
      jsonEncode({
        'x': ['flutter'],
      }),
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
          home: Builder(
            builder: (context) => TextButton(
              onPressed: () => Navigator.push(
                context,
                MaterialPageRoute<void>(
                  settings: RouteSettings(arguments: SearchArguments(0)),
                  builder: (_) => const ResultsScreen(),
                ),
              ),
              child: const Text('Open Search'),
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.text('Open Search'));
    await tester.pumpAndSettle();

    expect(find.text('flutter'), findsOneWidget);
    expect(find.text('Search X'), findsOneWidget);
    expect(find.byTooltip('Search your library'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('compact enlarged search keeps input and actions reachable', (tester) async {
    tester.view.physicalSize = const Size(320, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      PrefService(
        service: PrefServiceCache(),
        child: MaterialApp(
          localizationsDelegates: const [
            L10n.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: L10n.delegate.supportedLocales,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context).copyWith(textScaler: const TextScaler.linear(2)),
            child: child!,
          ),
          home: Builder(
            builder: (context) => TextButton(
              onPressed: () => Navigator.push(
                context,
                MaterialPageRoute<void>(
                  settings: RouteSettings(arguments: SearchArguments(0)),
                  builder: (_) => const ResultsScreen(),
                ),
              ),
              child: const Text('Open Search'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Open Search'));
    await tester.pumpAndSettle();
    expect(tester.getSize(find.byType(XtaSearchField)).width, greaterThan(280));
    expect(find.byTooltip('Search your library').hitTestable(), findsOneWidget);
    expect(find.byTooltip('Antennas').hitTestable(), findsOneWidget);
    expect(find.byTooltip('Advanced search').hitTestable(), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.tap(find.byTooltip('Advanced search'));
    await tester.pumpAndSettle();
    expect(find.byType(AdvancedSearchScreen), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.tap(find.byType(CloseButton));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'draft');
    await tester.pump();
    final clear = find.descendant(of: find.byType(XtaSearchField), matching: find.byIcon(Icons.close));
    expect(clear.hitTestable(), findsOneWidget);
    await tester.tap(clear);
    await tester.pump();
    expect(tester.widget<TextField>(find.byType(TextField)).controller!.text, isEmpty);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox.shrink());
  });
}
