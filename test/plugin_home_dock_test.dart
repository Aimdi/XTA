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
}
