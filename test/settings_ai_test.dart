import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/settings/_ai.dart';

Widget _app() {
  return PrefService(
    service: PrefServiceCache(
      cache: {optionAiBaseUrl: '', optionAiApiKey: '', optionAiModel: ''},
    ),
    child: const MaterialApp(
      localizationsDelegates: [
        L10n.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: L10n.delegate.supportedLocales,
      locale: Locale('en'),
      home: SettingsAiFragment(),
    ),
  );
}

void main() {
  testWidgets('the Grok chip fills the xAI server and model', (tester) async {
    await tester.pumpWidget(_app());
    await tester.pumpAndSettle();

    expect(find.text('Grok'), findsOneWidget);
    await tester.tap(find.text('Grok'));
    await tester.pump();

    expect(find.text(aiGrokBaseUrl), findsWidgets);
    expect(find.text(aiGrokModel), findsWidgets);
    expect(find.textContaining('console.x.ai'), findsOneWidget);
  });
}
