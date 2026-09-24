import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/search/recent_searches_store.dart';
import 'package:xta/search/search.dart';

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
}
