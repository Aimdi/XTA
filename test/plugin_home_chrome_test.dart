import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';

Widget _app(Widget child) {
  return MaterialApp(home: Scaffold(body: child));
}

void main() {
  testWidgets('chrome is one 48dp row of icon tabs and actions', (
    tester,
  ) async {
    var tapped = 0;
    await tester.pumpWidget(
      _app(
        PluginHomeChrome(
          tabs: [
            PluginHomeTab(
              icon: Icons.home_outlined,
              label: 'Home',
              selected: true,
              onTap: () => tapped++,
            ),
            PluginHomeTab(
              icon: Icons.inbox_outlined,
              label: 'Inbox',
              selected: false,
              onTap: () {},
            ),
          ],
          actions: [
            IconButton(
              tooltip: 'Discover',
              icon: const Icon(Icons.explore_outlined),
              onPressed: () {},
            ),
          ],
        ),
      ),
    );

    final chrome = tester.getSize(find.byType(PluginHomeChrome));
    expect(chrome.height, 48);

    // Labels are tooltips, not a second text row — long locales were wrapping.
    expect(find.text('Home'), findsNothing);
    expect(find.text('Inbox'), findsNothing);
    expect(find.byIcon(Icons.home_outlined), findsOneWidget);
    expect(find.byIcon(Icons.inbox_outlined), findsOneWidget);
    expect(find.byTooltip('Discover'), findsOneWidget);

    await tester.tap(find.byIcon(Icons.home_outlined));
    expect(tapped, 1);
  });

  testWidgets('embedded chrome skips a second SafeArea', (tester) async {
    await tester.pumpWidget(
      _app(
        PluginEmbedded(
          child: PluginHomeChrome(
            actions: [
              IconButton(
                tooltip: 'Add',
                icon: const Icon(Icons.add),
                onPressed: () {},
              ),
            ],
          ),
        ),
      ),
    );

    expect(find.byType(SafeArea), findsNothing);
    expect(find.byTooltip('Add'), findsOneWidget);
  });

  testWidgets('standalone chrome keeps a top SafeArea for the status bar', (
    tester,
  ) async {
    await tester.pumpWidget(
      _app(
        PluginHomeChrome(
          actions: [
            IconButton(
              tooltip: 'Add',
              icon: const Icon(Icons.add),
              onPressed: () {},
            ),
          ],
        ),
      ),
    );

    expect(find.byType(SafeArea), findsOneWidget);
  });

  testWidgets('selected tab uses accent when provided', (tester) async {
    const accent = Color(0xFF00C805);
    await tester.pumpWidget(
      _app(
        PluginHomeChrome(
          accent: accent,
          tabs: [
            PluginHomeTab(
              icon: Icons.home_outlined,
              label: 'Home',
              selected: true,
              onTap: () {},
            ),
          ],
        ),
      ),
    );

    final icon = tester.widget<Icon>(find.byIcon(Icons.home_outlined));
    expect(icon.color, accent);
  });

  testWidgets('tab AppBar has no plugin-name title', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: DefaultTabController(
          length: 2,
          child: Scaffold(
            appBar: pluginHomeTabAppBar(
              tabs: const TabBar(
                tabs: [
                  Tab(text: 'Latest'),
                  Tab(text: 'Following'),
                ],
              ),
              actions: [
                IconButton(
                  tooltip: 'Search',
                  icon: const Icon(Icons.search),
                  onPressed: () {},
                ),
              ],
            ),
          ),
        ),
      ),
    );

    expect(find.text('Latest'), findsOneWidget);
    expect(find.text('Following'), findsOneWidget);
    expect(find.byTooltip('Search'), findsOneWidget);
    expect(find.text('Pixiv'), findsNothing);
    expect(find.text('Booru'), findsNothing);
  });
}
