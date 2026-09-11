import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/settings/_ai.dart';

Widget _app({PrefServiceCache? prefs, String locale = 'en'}) {
  return PrefService(
    service: prefs ?? PrefServiceCache(cache: {optionAiBaseUrl: '', optionAiApiKey: '', optionAiModel: ''}),
    child: MaterialApp(
      localizationsDelegates: [
        L10n.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: L10n.delegate.supportedLocales,
      locale: Locale(locale),
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
  testWidgets('OpenRouter saves a custom model ID and restores it', (tester) async {
    final prefs = PrefServiceCache(cache: {optionAiBaseUrl: '', optionAiApiKey: '', optionAiModel: ''});
    await tester.pumpWidget(_app(prefs: prefs));
    await tester.pumpAndSettle();
    await tester.tap(find.text('OpenRouter'));
    await tester.pump();
    expect(find.text(aiOpenRouterBaseUrl), findsOneWidget);
    expect(find.text(aiOpenRouterModel), findsWidgets);
    final fields = find.byType(TextField);
    await tester.enterText(fields.at(1), 'router-key');
    await tester.enterText(fields.at(2), 'provider/custom-model');
    await tester.ensureVisible(find.text('Save'));
    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();
    expect(prefs.get<String>(optionAiBaseUrl), aiOpenRouterBaseUrl);
    expect(prefs.get<String>(optionAiApiKey), 'router-key');
    expect(prefs.get<String>(optionAiModel), 'provider/custom-model');
    await tester.pumpWidget(const SizedBox());
    await tester.pumpWidget(_app(prefs: prefs, locale: 'de'));
    await tester.pumpAndSettle();
    expect(find.text('provider/custom-model'), findsOneWidget);
    expect(find.textContaining('vollständige Modell-ID'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('switching provider clears the previous provider key and model', (tester) async {
    await tester.pumpWidget(
      _app(
        prefs: PrefServiceCache(
          cache: {optionAiBaseUrl: aiGrokBaseUrl, optionAiApiKey: 'grok-key', optionAiModel: aiGrokModel},
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('OpenRouter'));
    await tester.pump();
    final fields = tester.widgetList<TextField>(find.byType(TextField)).toList();
    expect(fields[1].controller!.text, isEmpty);
    expect(fields[2].controller!.text, aiOpenRouterModel);
    expect(tester.takeException(), isNull);
  });
}
