import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/plugin_top_bar_pins.dart';
import 'package:xta/utils/pref_lists.dart';
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

  testWidgets('entries pinned from the options sheet join the top bar and persist', (tester) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final store = PluginHomeDockStore();
    final prefs = PrefServiceCache();
    final pins = PluginTopBarPinsStore(prefs);
    final calls = <String>[];
    store.publish(
      'rss',
      'navigation',
      Object(),
      PluginDockContent(
        tabs: [
          for (final label in ['Explore', 'Home', 'Local', 'Federated'])
            PluginHomeTab(icon: Icons.circle_outlined, label: label, selected: label == 'Home', onTap: () {}),
        ],
        actions: [
          IconButton(tooltip: 'Search', icon: const Icon(Icons.search), onPressed: () => calls.add('search')),
          PluginHomeMenu(
            onSelected: calls.add,
            itemBuilder: (_) => [
              const PopupMenuItem(value: 'saved', child: Text('Saved')),
              const PopupMenuItem(
                value: 'refresh',
                child: ListTile(leading: Icon(Icons.refresh), title: Text('Refresh')),
              ),
            ],
          ),
        ],
      ),
    );
    await tester.pumpWidget(
      _app(
        Provider<PluginTopBarPinsStore>.value(
          value: pins,
          child: PluginHomeDockScope(
            store: store,
            source: 'rss',
            enabled: true,
            compact: true,
            openClientLabel: '',
            onOpenClient: null,
            child: PluginCompactHeader(plugin: RssPlugin(), store: store, onPickSource: () {}),
          ),
        ),
      ),
    );
    expect(find.byKey(const ValueKey('top-bar-pin-menu:saved')), findsNothing);

    await tester.tap(find.byKey(const ValueKey('home-plugin-options')));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.byKey(const ValueKey('top-bar-pins')));
    await tester.tap(find.byKey(const ValueKey('top-bar-pins')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('top-bar-pin-option-menu:saved')));
    await tester.tap(find.byKey(const ValueKey('top-bar-pin-option-menu:refresh')));
    await tester.pumpAndSettle();
    expect(stringListPref(prefs, pluginTopBarPinsKey('rss')), ['menu:saved', 'menu:refresh']);
    await tester.tapAt(const Offset(10, 10));
    await tester.pumpAndSettle();
    await tester.tapAt(const Offset(10, 10));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('top-bar-pin-menu:saved')));
    await tester.tap(find.byKey(const ValueKey('top-bar-pin-menu:refresh')));
    expect(calls, ['saved', 'refresh']);
    expect(find.byTooltip('Search'), findsOneWidget);
    expect(tester.getSize(find.byKey(const ValueKey('top-bar-pin-menu:refresh'))), const Size(48, 48));
    expect(PluginTopBarPinsStore(prefs).pinned('rss'), ['menu:saved', 'menu:refresh']);

    await pins.toggle('rss', 'menu:saved');
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('top-bar-pin-menu:saved')), findsNothing);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
    await store.destroy();
  });

  testWidgets('pins that do not fit stay in the sheet instead of crowding the bar', (tester) async {
    tester.view.physicalSize = const Size(320, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final store = PluginHomeDockStore();
    final prefs = PrefServiceCache();
    final ids = [for (var i = 0; i < 8; i++) 'action-$i'];
    await prefs.set(pluginTopBarPinsKey('rss'), ids);
    store.publish(
      'rss',
      'navigation',
      Object(),
      PluginDockContent(
        tabs: [PluginHomeTab(icon: Icons.home_outlined, label: 'Home', selected: true, onTap: () {})],
        actions: [
          for (final id in ids)
            IconButton(key: ValueKey(id), tooltip: id, icon: const Icon(Icons.star_border), onPressed: () {}),
        ],
      ),
    );
    await tester.pumpWidget(
      _app(
        Provider<PluginTopBarPinsStore>.value(
          value: PluginTopBarPinsStore(prefs),
          child: PluginHomeDockScope(
            store: store,
            source: 'rss',
            enabled: true,
            compact: true,
            openClientLabel: '',
            onOpenClient: null,
            child: PluginCompactHeader(plugin: RssPlugin(), store: store, onPickSource: () {}),
          ),
        ),
      ),
    );
    final shown = ids.where((id) => find.byKey(ValueKey('top-bar-pin-$id')).evaluate().isNotEmpty).toList();
    // The first action is already the bar's primary button, so it is never repeated as a pin.
    expect(find.byKey(const ValueKey('top-bar-pin-action-0')), findsNothing);
    expect(shown, isNotEmpty);
    expect(shown, ids.skip(1).take(shown.length));
    expect(shown.length, lessThan(ids.length));
    expect(find.byKey(const ValueKey('home-plugin-options')).hitTestable(), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
    await store.destroy();
  });
}
