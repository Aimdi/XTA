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

  testWidgets('switching to a tab builds it once and keeps it', (tester) async {
    var notesBuilds = 0;
    var index = 0;

    await tester.pumpWidget(
      MaterialApp(
        home: StatefulBuilder(
          builder: (context, setState) => Column(
            children: [
              TextButton(
                onPressed: () => setState(() => index = 1),
                child: const Text('Open notes'),
              ),
              Expanded(
                child: PluginLazyTabs(
                  index: index,
                  children: [
                    (_) => const Text('Home'),
                    (_) {
                      notesBuilds++;
                      return const Text('Notes');
                    },
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );

    await tester.tap(find.text('Open notes'));
    await tester.pump();

    expect(find.text('Home'), findsOneWidget);
    expect(find.text('Notes'), findsOneWidget);
    expect(notesBuilds, 1);
  });
}
