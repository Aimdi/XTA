import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_brand.dart';
import 'package:xta/plugins/threads/threads_plugin.dart';
import 'package:xta/settings/_plugin_row.dart';
import 'package:xta/settings/_plugin_store.dart';
import 'package:xta/settings/plugin_store_tile.dart';
import 'package:xta/settings/settings_chrome.dart';

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
  test('a non-matching store query produces no result', () {
    expect(
      pluginMatchesStoreQuery(
        query: 'weather',
        id: 'threads',
        title: 'Threads',
        description: 'Public social reader',
        category: 'Social',
      ),
      isFalse,
    );
  });

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

  testWidgets('an installed plugin keeps Open and settings, with tab visibility in its menu', (
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
    expect(find.text('Open'), findsOneWidget);
    expect(find.byTooltip('Settings'), findsOneWidget);
    expect(find.byType(PopupMenuButton<String>), findsOneWidget);
    await tester.tap(find.byType(PopupMenuButton<String>));
    await tester.pumpAndSettle();
    expect(find.text('Show as a tab'), findsOneWidget);
    expect(find.text('Uninstall'), findsOneWidget);
  });

  testWidgets('installed plugin icon actions keep 48dp touch targets', (
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

    final settings = tester.getSize(find.byTooltip('Settings'));
    final menu = tester.getSize(find.byType(PopupMenuButton<String>));
    expect(settings.width, greaterThanOrEqualTo(kPluginStoreTouchTarget));
    expect(settings.height, greaterThanOrEqualTo(kPluginStoreTouchTarget));
    expect(menu.width, greaterThanOrEqualTo(kPluginStoreTouchTarget));
    expect(menu.height, greaterThanOrEqualTo(kPluginStoreTouchTarget));
  });

  testWidgets('available plugins start open so a new plugin is visible', (
    tester,
  ) async {
    final groups = groupPluginsByCategory([ThreadsPlugin()]);
    await tester.pumpWidget(
      _wrap(PluginAvailableSection(groups: groups, onInstall: (_) async {})),
    );
    await tester.pump();

    expect(find.text('Available'), findsOneWidget);
    expect(find.text('Install'), findsOneWidget);
  });

  testWidgets('plugin tile stacks actions on a narrow large-text layout', (
    tester,
  ) async {
    await tester.pumpWidget(
      _wrap(
        Center(
          child: SizedBox(
            width: 320,
            child: MediaQuery(
              data: const MediaQueryData(textScaler: TextScaler.linear(2)),
              child: PluginStoreTile(
                leading: const Icon(Icons.extension),
                title: const Text('A long plugin title'),
                subtitle: const Text('A description that needs room to read'),
                actions: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    TextButton(onPressed: () {}, child: const Text('Open')),
                    IconButton(
                      onPressed: () {},
                      icon: const Icon(Icons.tune),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );

    expect(find.byKey(const Key('plugin-store-tile-stacked')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('plugin tile keeps actions inline when enough width is available', (
    tester,
  ) async {
    await tester.pumpWidget(
      _wrap(
        Center(
          child: SizedBox(
            width: 600,
            child: PluginStoreTile(
              leading: const Icon(Icons.extension),
              title: const Text('Plugin'),
              subtitle: const Text('Description'),
              actions: TextButton(
                onPressed: () {},
                child: const Text('Install'),
              ),
            ),
          ),
        ),
      ),
    );

    expect(find.byKey(const Key('plugin-store-tile-inline')), findsOneWidget);
  });

  testWidgets('store body is centered, width-bounded, and lazy', (tester) async {
    var offscreenBuilds = 0;
    await tester.pumpWidget(
      _wrap(
        PluginStoreBody(
          onRefresh: () async {},
          entries: [
            const SizedBox(height: 1200),
            Builder(
              builder: (context) {
                offscreenBuilds++;
                return const Text('Offscreen plugin');
              },
            ),
          ],
        ),
      ),
    );

    expect(tester.getSize(find.byType(ListView)).width, kSettingsContentWidth);
    expect(offscreenBuilds, 0);
    await tester.drag(find.byType(ListView), const Offset(0, -1100));
    await tester.pump();
    expect(offscreenBuilds, 1);
  });

  testWidgets('empty store search state announces the localized result', (
    tester,
  ) async {
    await tester.pumpWidget(
      _wrap(const PluginStoreEmptyState(label: 'No results')),
    );

    expect(find.byIcon(Icons.search_off), findsOneWidget);
    expect(find.text('No results'), findsOneWidget);
    final semantics = tester.widget<Semantics>(
      find.descendant(
        of: find.byType(PluginStoreEmptyState),
        matching: find.byType(Semantics),
      ),
    );
    expect(semantics.properties.liveRegion, isTrue);
  });
}
