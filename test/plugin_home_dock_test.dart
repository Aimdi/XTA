import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_home_dock.dart';

void main() {
  test('late disposal cannot remove replacement or another source', () async {
    final store = PluginHomeDockStore();
    final first = Object();
    final replacement = Object();
    const one = PluginDockContent(section: Text('One'));
    const two = PluginDockContent(section: Text('Two'));
    store.publish('blue', 'navigation', first, one);
    store.publish('masto', 'navigation', first, one);
    store.publish('blue', 'navigation', replacement, two);
    store.remove('blue', 'navigation', first);
    expect(store.content('blue', 'navigation'), same(two));
    expect(store.content('masto', 'navigation'), same(one));
    store.remove('blue', 'navigation', replacement);
    expect(store.content('blue', 'navigation'), isNull);
    await store.destroy();
    expect(() => store.publish('blue', 'navigation', first, one), returnsNormally);
  });

  testWidgets('hosted contributions disappear with owner; fallback stays standalone', (tester) async {
    final store = PluginHomeDockStore();
    Widget contribution() => const PluginDockContribution(
      slot: 'navigation',
      content: PluginDockContent(section: Text('Section')),
      fallback: Text('Standalone'),
    );
    await tester.pumpWidget(MaterialApp(home: contribution()));
    await tester.pumpAndSettle();
    expect(find.text('Standalone'), findsOneWidget);
    await tester.pumpWidget(
      MaterialApp(
        home: PluginHomeDockScope(
          store: store,
          source: 'blue',
          enabled: true,
          openClientLabel: 'Full client',
          onOpenClient: () {},
          child: Column(
            children: [
              PluginDockRow(store: store, source: 'blue'),
              contribution(),
            ],
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Section'), findsOneWidget);
    expect(find.text('Standalone'), findsNothing);
    await tester.pumpWidget(const SizedBox());
    await tester.pumpAndSettle();
    expect(store.state, isEmpty);
    expect(tester.takeException(), isNull);
    await store.destroy();
  });

  for (final (width, scale, rtl) in <(double, double, bool)>[
    (320, 1, false),
    (390, 1, false),
    (320, 2, true),
    (840, 2, false),
  ]) {
    testWidgets('single header keeps section and right-side actions reachable $width/$scale/$rtl', (tester) async {
      tester.view.physicalSize = Size(width, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final store = PluginHomeDockStore();
      var section = 0;
      var settings = 0;
      var search = 0;
      var fullClient = 0;
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: const [
            L10n.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: L10n.delegate.supportedLocales,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(scale)),
            child: Directionality(textDirection: rtl ? TextDirection.rtl : TextDirection.ltr, child: child!),
          ),
          home: PluginHomeDockScope(
            store: store,
            source: 'test',
            enabled: true,
            compact: true,
            openClientLabel: 'Open full client',
            onOpenClient: () => fullClient++,
            child: Scaffold(
              appBar: AppBar(
                title: const Text('Reader'),
                actions: [PluginDockActions(store: store, source: 'test')],
              ),
              body: PluginEmbedded(
                child: Column(
                  children: [
                    PluginHomeChrome(
                      title: 'Reader',
                      tabs: [
                        PluginHomeTab(
                          icon: Icons.home_outlined,
                          label: 'Home',
                          selected: true,
                          onTap: () => section = 0,
                        ),
                        PluginHomeTab(
                          icon: Icons.people_outline,
                          label: 'Subscriptions and publications',
                          selected: false,
                          onTap: () => section = 1,
                        ),
                      ],
                      actions: [
                        IconButton(tooltip: 'Search', icon: const Icon(Icons.search), onPressed: () => search++),
                        PluginHomeSecondaryAction(
                          label: 'Settings',
                          icon: const Icon(Icons.settings),
                          onPressed: () => settings++,
                        ),
                      ],
                    ),
                    const Text('First post'),
                  ],
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.getTopLeft(find.text('First post')).dy, 56);
      final options = find.byKey(const ValueKey('home-plugin-options'));
      expect(tester.getSize(options), const Size(48, 48));
      expect(tester.getTopLeft(options).dy, lessThan(56));
      await tester.tap(find.byTooltip('Search'));
      expect(search, 1);
      await tester.tap(options);
      await tester.pumpAndSettle();
      expect(section, 0, reason: 'Opening options must not load a different section.');
      await tester.tap(find.byKey(const ValueKey('home-section-1')));
      await tester.pumpAndSettle();
      expect(section, 1);
      expect(find.byKey(const ValueKey('home-plugin-options-close')), findsNothing);
      await tester.tap(find.byType(PopupMenuButton<String>));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Settings'));
      await tester.pumpAndSettle();
      expect(settings, 1);
      await tester.tap(find.byType(PopupMenuButton<String>));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Open full client'));
      await tester.pumpAndSettle();
      expect(fullClient, 1);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox());
      await tester.pump();
      await store.destroy();
    });
  }

  for (final width in [320.0, 840.0]) {
    testWidgets('large-text reading labels receive their own row only when needed at $width', (tester) async {
      final store = PluginHomeDockStore();
      store.publish(
        'reader',
        'navigation',
        Object(),
        const PluginDockContent(section: Text('Home'), sectionWidth: 240),
      );
      store.publish(
        'reader',
        'reading',
        Object(),
        const PluginDockContent(leading: Text('Books'), trailing: [SizedBox.square(dimension: 48)]),
      );
      try {
        await tester.pumpWidget(
          MaterialApp(
            home: Builder(
              builder: (context) => MediaQuery(
                data: MediaQuery.of(context).copyWith(textScaler: const TextScaler.linear(2)),
                child: Scaffold(
                  body: Align(
                    alignment: Alignment.topCenter,
                    child: SizedBox(
                      width: width,
                      child: PluginDockRow(store: store, source: 'reader'),
                    ),
                  ),
                ),
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        final sectionY = tester.getTopLeft(find.text('Home')).dy;
        final readingY = tester.getTopLeft(find.text('Books')).dy;
        expect(readingY, width < 400 ? greaterThan(sectionY) : sectionY);
        expect(tester.takeException(), isNull);
      } finally {
        await tester.pumpWidget(const SizedBox());
        await tester.pump();
        await store.destroy();
      }
    });
  }
}
