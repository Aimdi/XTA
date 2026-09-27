import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_compact_header.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_home_dock.dart';
import 'package:xta/plugins/rss/rss_plugin.dart';

Widget _app(Widget child, {double scale = 1, bool dark = false}) => MaterialApp(
  theme: dark ? ThemeData.dark() : ThemeData.light(),
  localizationsDelegates: const [
    L10n.delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
    GlobalCupertinoLocalizations.delegate,
  ],
  supportedLocales: L10n.delegate.supportedLocales,
  builder: (context, child) => MediaQuery(
    data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(scale)),
    child: child!,
  ),
  home: Scaffold(body: child),
);

void main() {
  for (final width in [320.0, 390.0, 840.0]) {
    testWidgets('compact host preserves source, sections and primary action at $width', (tester) async {
      tester.view.physicalSize = Size(width, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final store = PluginHomeDockStore();
      final calls = <String>[];
      final tabs = [
        PluginHomeTab(icon: Icons.home_outlined, label: 'Home', selected: true, onTap: () => calls.add('home')),
        PluginHomeTab(icon: Icons.rss_feed, label: 'Feeds', selected: false, onTap: () => calls.add('feeds')),
      ];
      store.publish(
        'rss',
        'navigation',
        Object(),
        PluginDockContent(
          tabs: tabs,
          actions: [IconButton(tooltip: 'Add feed', icon: const Icon(Icons.add), onPressed: () => calls.add('add'))],
        ),
      );
      await tester.pumpWidget(
        _app(
          PluginHomeDockScope(
            store: store,
            source: 'rss',
            enabled: true,
            compact: true,
            openClientLabel: '',
            onOpenClient: null,
            child: Column(
              children: [
                PluginCompactHeader(
                  plugin: RssPlugin(),
                  store: store,
                  onPickSource: () => calls.add('source'),
                  unread: true,
                ),
                const Text('First post'),
              ],
            ),
          ),
          scale: width == 320 ? 2 : 1,
          dark: width == 320,
        ),
      );
      final source = find.byKey(const ValueKey('plugin-source-picker-rss'));
      expect(tester.getSize(source), const Size(48, 48));
      expect(tester.getTopLeft(find.text('First post')).dy, lessThanOrEqualTo(52));
      await tester.tap(source);
      await tester.tap(find.byTooltip('Feeds'));
      await tester.tap(find.byTooltip('Add feed'));
      expect(calls, ['source', 'feeds', 'add']);
      await tester.tap(find.byKey(const ValueKey('home-plugin-options')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('home-section-0')));
      await tester.pumpAndSettle();
      expect(calls.last, 'home');
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox());
      await store.destroy();
    });
  }

  for (final (width, scale) in <(double, double)>[(320, 1), (320, 2), (840, 2)]) {
    testWidgets('crowded standalone navigation leaves room for posts and every section at $width/$scale', (
      tester,
    ) async {
      tester.view.physicalSize = Size(width, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final selected = <int>[];
      var searches = 0;
      await tester.pumpWidget(
        _app(
          Column(
            children: [
              PluginHomeChrome(
                title: 'Hacker News',
                mark: const Icon(Icons.newspaper),
                tabs: [
                  for (var i = 0; i < 8; i++)
                    PluginHomeTab(
                      icon: Icons.article_outlined,
                      label: 'Section $i',
                      selected: i == 0,
                      onTap: () => selected.add(i),
                    ),
                ],
                actions: [IconButton(tooltip: 'Search', icon: const Icon(Icons.search), onPressed: () => searches++)],
              ),
              const Text('First post'),
            ],
          ),
          scale: scale,
        ),
      );
      expect(tester.getTopLeft(find.text('First post')).dy, lessThanOrEqualTo(52));
      await tester.tap(find.byTooltip('Search'));
      expect(searches, 1);
      for (var i = 0; i < 8; i++) {
        if (find.byType(PluginSectionPicker).evaluate().isNotEmpty) {
          await tester.tap(find.byType(PluginSectionPicker));
          await tester.pumpAndSettle();
          expect(selected, List.generate(i, (index) => index));
          await tester.tap(find.byType(PopupMenuItem<int>).at(i));
        } else {
          await tester.tap(find.byTooltip('Section $i'));
        }
        await tester.pumpAndSettle();
        expect(selected, List.generate(i + 1, (index) => index));
      }
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('combined options preserve primary, secondary and wrapped actions and full client', (tester) async {
    final store = PluginHomeDockStore();
    final calls = <String>[];
    store.publish(
      'rss',
      'navigation',
      Object(),
      PluginDockContent(
        actions: [
          IconButton(tooltip: 'Add feed', icon: const Icon(Icons.add), onPressed: () => calls.add('add')),
          PluginHomeSecondaryAction(
            label: 'Manage feeds',
            icon: const Icon(Icons.list),
            onPressed: () => calls.add('manage'),
          ),
          Builder(
            builder: (_) => PluginHomeMenu(
              itemBuilder: (_) => [const PopupMenuItem(value: 'read', child: Text('Mark all read'))],
              onSelected: (_) => calls.add('read'),
            ),
          ),
        ],
      ),
    );
    await tester.pumpWidget(
      _app(
        PluginHomeDockScope(
          store: store,
          source: 'rss',
          enabled: true,
          compact: true,
          openClientLabel: 'Full reader',
          onOpenClient: () => calls.add('client'),
          child: PluginDockOptionsButton(store: store, source: 'rss', includeActions: true),
        ),
      ),
    );
    for (final (label, action) in [('Add feed', 'add'), ('Manage feeds', 'manage'), ('Full reader', 'client')]) {
      await tester.tap(find.byKey(const ValueKey('home-plugin-options')));
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text(label));
      await tester.tap(find.text(label));
      await tester.pumpAndSettle();
      expect(calls.last, action);
      expect(find.byKey(const ValueKey('home-plugin-options-close')), findsNothing);
    }
    await tester.tap(find.byKey(const ValueKey('home-plugin-options')));
    await tester.pumpAndSettle();
    await tester.tap(find.byType(PopupMenuButton<String>));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Mark all read'));
    await tester.pumpAndSettle();
    expect(calls.last, 'read');
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
    await store.destroy();
  });
}
