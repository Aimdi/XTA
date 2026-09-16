import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/link_stack.dart';

void main() {
  for (final dark in [false, true]) {
    for (final scale in [1.0, 2.0]) {
      testWidgets('link stack preserves each commentary, dark=$dark scale=$scale', (tester) async {
        tester.view.physicalSize = const Size(390, 844);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        await tester.pumpWidget(
          MaterialApp(
            theme: ThemeData(brightness: dark ? Brightness.dark : Brightness.light),
            localizationsDelegates: const [
              L10n.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
              GlobalCupertinoLocalizations.delegate,
            ],
            supportedLocales: const [Locale('en')],
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(scale)),
              child: child!,
            ),
            home: Scaffold(
              body: ListView(
                children: [
                  LinkStack(
                    posts: [
                      (_) => const ListTile(title: Text('Original commentary'), subtitle: Text('Source A')),
                      (_) => const ListTile(title: Text('A different perspective'), subtitle: Text('Source B')),
                    ],
                  ),
                ],
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        expect(find.text('Original commentary'), findsOneWidget);
        expect(find.text('A different perspective'), findsNothing);
        await tester.tap(find.byType(TextButton));
        await tester.pumpAndSettle();
        expect(find.text('Original commentary'), findsOneWidget);
        expect(find.text('A different perspective'), findsOneWidget);
        expect(tester.takeException(), isNull);
        await tester.tap(find.byType(TextButton));
        await tester.pumpAndSettle();
        expect(find.text('A different perspective'), findsNothing);
      });
    }
  }
}
