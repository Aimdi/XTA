import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/plugin_lazy_tabs.dart';

void main() {
  testWidgets('only the selected tab is built on the first frame', (
    tester,
  ) async {
    var homeBuilds = 0;
    var notesBuilds = 0;

    await tester.pumpWidget(
      MaterialApp(
        home: PluginLazyTabs(
          index: 0,
          children: [
            (_) {
              homeBuilds++;
              return const Text('Home');
            },
            (_) {
              notesBuilds++;
              return const Text('Notes');
            },
          ],
        ),
      ),
    );

    expect(find.text('Home'), findsOneWidget);
    expect(find.text('Notes'), findsNothing);
    expect(homeBuilds, 1);
    expect(notesBuilds, 0);
  });

  testWidgets('switching away unmounts the previous tab', (tester) async {
    var homeBuilds = 0;
    var notesBuilds = 0;
    var index = 0;

    Future<void> pumpAt(int next) async {
      index = next;
      await tester.pumpWidget(
        MaterialApp(
          home: PluginLazyTabs(
            index: index,
            children: [
              (_) {
                homeBuilds++;
                return const Text('Home');
              },
              (_) {
                notesBuilds++;
                return const Text('Notes');
              },
            ],
          ),
        ),
      );
    }

    await pumpAt(0);
    expect(homeBuilds, 1);
    expect(notesBuilds, 0);

    await pumpAt(1);
    expect(find.text('Notes'), findsOneWidget);
    expect(find.text('Home', skipOffstage: false), findsNothing);
    expect(notesBuilds, 1);
    expect(homeBuilds, 1);

    await pumpAt(1);
    expect(homeBuilds, 1);
    expect(notesBuilds, 2);
  });
}
