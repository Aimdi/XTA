import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/home_screen.dart';
import 'package:xta/trends/_list.dart';
import 'package:xta/trends/discover_shortcuts.dart';
import 'package:xta/trends/trends_model.dart';

Widget _app({required Widget home}) {
  final prefs = PrefServiceCache();
  return PrefService(
    service: prefs,
    child: MultiProvider(
      providers: [
        Provider(create: (_) => UserTrendLocationModel(prefs)),
        ProxyProvider<UserTrendLocationModel, TrendsModel>(
          update: (_, locations, previous) =>
              previous ?? TrendsModel(locations),
        ),
      ],
      child: MaterialApp(
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: L10n.delegate.supportedLocales,
        home: home,
      ),
    ),
  );
}

void main() {
  testWidgets('the home Search tab is labeled Discover', (tester) async {
    await tester.pumpWidget(
      _app(
        home: Builder(
          builder: (context) => Text(defaultHomePages[2].titleBuilder(context)),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Discover'), findsOneWidget);
    expect(find.text('Search'), findsNothing);
  });

  testWidgets('an empty trends list explains itself instead of going blank', (
    tester,
  ) async {
    await tester.pumpWidget(
      _app(
        home: Scaffold(body: TrendsList(scrollController: ScrollController())),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.text(
        'Nothing trending for this place yet. Search above, or add another location.',
      ),
      findsOneWidget,
    );
    expect(find.text('Add a location'), findsOneWidget);
  });

  testWidgets('Discover offers people search and antennas', (tester) async {
    await tester.pumpWidget(
      _app(home: const Scaffold(body: DiscoverShortcuts())),
    );
    await tester.pumpAndSettle();

    expect(find.text('Find people'), findsOneWidget);
    expect(find.text('Antennas'), findsOneWidget);
  });
}
