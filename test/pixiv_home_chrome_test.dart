import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_screen.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';

Widget _app(Widget child) {
  return MaterialApp(
    localizationsDelegates: const [
      L10n.delegate,
      GlobalMaterialLocalizations.delegate,
      GlobalWidgetsLocalizations.delegate,
      GlobalCupertinoLocalizations.delegate,
    ],
    supportedLocales: L10n.delegate.supportedLocales,
    home: Scaffold(body: child),
  );
}

void main() {
  testWidgets('Pixiv chrome is Home / Rankings / Favorites / Search / More', (
    tester,
  ) async {
    var index = 0;
    await tester.pumpWidget(
      _app(
        PixivHomeChrome(
          index: index,
          onSelect: (next) => index = next,
        ),
      ),
    );

    expect(find.byType(PluginHomeChrome), findsOneWidget);
    expect(find.byType(AppBar), findsNothing);
    expect(find.byIcon(Icons.home_outlined), findsOneWidget);
    expect(find.byIcon(Icons.bar_chart), findsOneWidget);
    expect(find.byIcon(Icons.favorite_border), findsOneWidget);
    expect(find.byIcon(Icons.search), findsOneWidget);
    expect(find.byIcon(Icons.menu), findsOneWidget);

    await tester.tap(find.byIcon(Icons.bar_chart));
    expect(index, 1);
    await tester.tap(find.byIcon(Icons.menu));
    expect(index, 4);
  });

  testWidgets('embedded Pixiv chrome skips a second SafeArea', (tester) async {
    await tester.pumpWidget(
      _app(
        PluginEmbedded(
          child: PixivHomeChrome(index: 0, onSelect: (_) {}),
        ),
      ),
    );

    expect(find.byType(SafeArea), findsNothing);
    expect(find.byType(PluginHomeChrome), findsOneWidget);
  });
}
