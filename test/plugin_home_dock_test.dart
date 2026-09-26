import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
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
                    child: SizedBox(width: width, child: PluginDockRow(store: store, source: 'reader')),
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
