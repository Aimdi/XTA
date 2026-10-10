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

  testWidgets('the mode button keeps the five icon tabs on a phone, the mark making room', (tester) async {
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    tester.view.devicePixelRatio = 1;
    final chrome = PixivHomeChrome(index: 0, onSelect: (_) {}, onMode: (_) {});
    for (final (width, pushed, mark) in [(320.0, false, false), (360.0, true, false), (390.0, false, true)]) {
      tester.view.physicalSize = Size(width, 700);
      await tester.pumpWidget(_app(pushed ? const SizedBox() : chrome));
      if (pushed) {
        tester
            .state<NavigatorState>(find.byType(Navigator))
            .push(MaterialPageRoute<void>(builder: (_) => Scaffold(body: chrome)));
        await tester.pumpAndSettle();
        expect(find.byType(BackButton), findsOneWidget);
      }
      final at = '$width dp${pushed ? ' with a back button' : ''}';
      expect(find.byType(PluginSectionPicker), findsNothing, reason: at);
      expect(find.byKey(const ValueKey('pixiv-mode-toggle')), findsOneWidget, reason: at);
      expect(find.byTooltip('Pixiv'), mark ? findsOneWidget : findsNothing, reason: at);
    }
    expect(tester.takeException(), isNull);
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
