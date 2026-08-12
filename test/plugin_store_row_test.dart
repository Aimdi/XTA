import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/threads/threads_plugin.dart';
import 'package:xta/settings/_plugin_row.dart';

Widget _wrap(Widget child, {BasePrefService? prefs}) {
  return PrefService(
    service: prefs ?? PrefServiceCache(cache: {}),
    child: MaterialApp(
      localizationsDelegates: const [
        L10n.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: L10n.delegate.supportedLocales,
      home: Scaffold(body: child),
    ),
  );
}

void main() {
  testWidgets('an available plugin is one compact row with Install', (
    tester,
  ) async {
    await tester.pumpWidget(
      _wrap(AvailablePluginRow(plugin: ThreadsPlugin(), onInstall: () {})),
    );
    await tester.pumpAndSettle();

    expect(find.text('Install'), findsOneWidget);
    expect(find.byType(SwitchListTile), findsNothing);
    expect(find.byType(ListTile), findsNothing);
  });

  testWidgets('an installed plugin keeps tab and settings on the same row', (
    tester,
  ) async {
    await tester.pumpWidget(
      _wrap(
        InstalledPluginRow(
          plugin: ThreadsPlugin(),
          onUninstall: () {},
          onChanged: () {},
        ),
      ),
    );
    await tester.pump();

    expect(find.byType(SwitchListTile), findsNothing);
    expect(find.text('Show as a tab'), findsNothing);
    expect(find.byTooltip('Show as a tab'), findsOneWidget);
    expect(find.byTooltip('Settings'), findsOneWidget);
    expect(find.byType(PopupMenuButton<String>), findsOneWidget);
  });
}
