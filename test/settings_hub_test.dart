import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/settings/settings.dart';
import 'package:xta/ui/x_controls.dart';

Widget _app() {
  return PrefService(
    service: PrefServiceCache(cache: {}),
    child: const MaterialApp(
      localizationsDelegates: [
        L10n.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: L10n.delegate.supportedLocales,
      locale: Locale('en'),
      home: SettingsScreen(),
    ),
  );
}

Future<void> _pumpHub(WidgetTester tester) async {
  await tester.pumpWidget(_app());
  await tester.pump();
}

void main() {
  testWidgets('the hub shows everyday tiles and a collapsed Advanced fold', (
    tester,
  ) async {
    await _pumpHub(tester);

    expect(find.byType(XSearchField), findsOneWidget);
    expect(find.text('General'), findsOneWidget);
    expect(find.text('Posts'), findsOneWidget);
    expect(find.text('Media'), findsOneWidget);
    expect(find.text('Accounts'), findsOneWidget);
    expect(find.text('Home'), findsOneWidget);
    expect(find.text('Theme'), findsOneWidget);
    expect(find.text('Accessibility'), findsOneWidget);
    expect(find.text('Plugin store'), findsOneWidget);
    expect(find.text('Data'), findsOneWidget);
    expect(find.text('Language, updates, and sharing'), findsOneWidget);
    expect(find.text('Advanced'), findsOneWidget);

    expect(find.text('AI provider'), findsNothing);
    expect(find.text('Diagnostics'), findsNothing);
    expect(find.text('Import'), findsNothing);
    expect(find.text('Export'), findsNothing);
    expect(find.text('Sync'), findsNothing);
  });

  testWidgets('search flattens Advanced so Diagnostics is findable', (
    tester,
  ) async {
    await _pumpHub(tester);

    await tester.enterText(find.byType(TextField), 'diag');
    await tester.pump();

    expect(find.text('Diagnostics'), findsOneWidget);
    expect(find.text('Advanced'), findsNothing);
    expect(find.text('General'), findsNothing);
  });

  testWidgets('search matches a short hint, not the old pref-name dump', (
    tester,
  ) async {
    await _pumpHub(tester);

    await tester.enterText(find.byType(TextField), 'timestamps');
    await tester.pump();

    expect(find.text('Posts'), findsOneWidget);
    expect(find.text('always_show_full_tweet_contents'), findsNothing);
  });

  testWidgets('Data is one tile that opens import, export, and sync', (
    tester,
  ) async {
    await _pumpHub(tester);

    await tester.tap(find.text('Data'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    expect(find.text('Import'), findsOneWidget);
    expect(find.text('Export'), findsOneWidget);
    expect(find.text('Sync'), findsOneWidget);
  });

  testWidgets('expanding Advanced reveals AI and Diagnostics', (tester) async {
    await _pumpHub(tester);

    await tester.tap(find.byKey(const Key('settings-advanced')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    expect(find.text('AI provider'), findsOneWidget);
    expect(find.text('Diagnostics'), findsOneWidget);
  });
}
