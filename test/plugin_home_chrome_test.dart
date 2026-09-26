import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_filter_row.dart';
import 'package:xta/plugins/plugin_feed_people.dart';
import 'package:xta/ui/contrast.dart';

Widget _app(Widget child) {
  return MaterialApp(home: Scaffold(body: child));
}

void main() {
  testWidgets('chrome has readable sections in a 48dp row', (tester) async {
    var tapped = 0;
    await tester.pumpWidget(
      _app(
        PluginHomeChrome(
          tabs: [
            PluginHomeTab(icon: Icons.home_outlined, label: 'Home', selected: true, onTap: () => tapped++),
            PluginHomeTab(icon: Icons.inbox_outlined, label: 'Inbox', selected: false, onTap: () {}),
          ],
          actions: [IconButton(tooltip: 'Discover', icon: const Icon(Icons.explore_outlined), onPressed: () {})],
        ),
      ),
    );

    final chrome = tester.getSize(find.byType(PluginHomeChrome));
    expect(chrome.height, 48);
    expect(find.text('Home'), findsOneWidget);
    expect(find.text('Inbox'), findsOneWidget);
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
            actions: [IconButton(tooltip: 'Add', icon: const Icon(Icons.add), onPressed: () {})],
          ),
        ),
      ),
    );

    expect(find.byType(SafeArea), findsNothing);
    expect(find.byTooltip('Add'), findsOneWidget);
  });

  testWidgets('standalone chrome keeps a top SafeArea for the status bar', (tester) async {
    await tester.pumpWidget(
      _app(
        PluginHomeChrome(
          actions: [IconButton(tooltip: 'Add', icon: const Icon(Icons.add), onPressed: () {})],
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
          tabs: [PluginHomeTab(icon: Icons.home_outlined, label: 'Home', selected: true, onTap: () {})],
        ),
      ),
    );

    final icon = tester.widget<Icon>(find.byIcon(Icons.home_outlined));
    expect(
      contrastRatio(icon.color!, Theme.of(tester.element(find.byType(PluginHomeChrome))).scaffoldBackgroundColor),
      greaterThanOrEqualTo(4.5),
    );
  });

  testWidgets('tab AppBar has no plugin-name title', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: DefaultTabController(
          length: 2,
          child: Scaffold(
            appBar: pluginHomeTabAppBar(
              tabs: const TabBar(
                tabs: [Tab(text: 'Latest'), Tab(text: 'Following')],
              ),
              actions: [IconButton(tooltip: 'Search', icon: const Icon(Icons.search), onPressed: () {})],
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

  testWidgets('crowded Home sections stay labelled and every destination dispatches once', (tester) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final taps = [0, 0, 0, 0];
    var searches = 0;
    const labels = ['Following', 'Discover', 'Custom lists', 'Liked posts'];
    await tester.pumpWidget(_app(PluginEmbedded(child: PluginHomeChrome(
      title: 'Bluesky',
      tabs: [
        for (var index = 0; index < labels.length; index++)
          PluginHomeTab(icon: Icons.article_outlined, label: labels[index], selected: index == 0, onTap: () => taps[index]++),
      ],
      actions: [
        IconButton(tooltip: 'Search', icon: const Icon(Icons.search), onPressed: () => searches++),
        IconButton(tooltip: 'Options', icon: const Icon(Icons.more_vert), onPressed: () {}),
      ],
    ))));
    expect(find.byType(PluginSectionPicker), findsOneWidget);
    expect(find.text('Bluesky'), findsNothing);
    expect(find.text('Following'), findsOneWidget);
    expect(tester.getSize(find.byType(PluginHomeChrome)).height, 48);
    expect(tester.getSize(find.byType(PluginSectionPicker)).width, greaterThanOrEqualTo(48));
    await tester.tap(find.byTooltip('Search'));
    expect(searches, 1);
    for (var index = 0; index < labels.length; index++) {
      await tester.tap(find.byType(PluginSectionPicker));
      await tester.pumpAndSettle();
      expect(taps[index], 0, reason: 'Opening a picker must not select or load a feed.');
      expect(find.byType(PopupMenuItem<int>), findsNWidgets(4));
      await tester.tap(find.byType(PopupMenuItem<int>).at(index));
      await tester.pumpAndSettle();
      expect(taps[index], 1);
    }
    expect(tester.takeException(), isNull);
  });

  testWidgets('wide Home keeps direct tabs and responds to external section selection', (tester) async {
    Widget chrome(int selected) => _app(PluginEmbedded(child: PluginHomeChrome(
      title: 'Reader',
      tabs: [
        for (var index = 0; index < 3; index++)
          PluginHomeTab(icon: Icons.article, label: 'Section $index', selected: selected == index, onTap: () {}),
      ],
    )));
    await tester.pumpWidget(chrome(0));
    expect(find.byType(PluginSectionPicker), findsNothing);
    tester.view.physicalSize = const Size(240, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(chrome(2));
    expect(find.byType(PluginSectionPicker), findsOneWidget);
    expect(find.text('Section 2'), findsOneWidget);
    expect(find.text('Section 0'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Home filter row saves padding but retains 48dp controls', (tester) async {
    Widget filters() => PluginFilterRow(children: [
      TextButton(style: TextButton.styleFrom(minimumSize: const Size(48, 48)), onPressed: () {}, child: const Text('All')),
    ]);
    await tester.pumpWidget(_app(Column(children: [filters()])));
    final standaloneHeight = tester.getSize(find.byType(PluginFilterRow)).height;
    await tester.pumpWidget(_app(Column(children: [PluginEmbedded(child: filters())])));
    final homeHeight = tester.getSize(find.byType(PluginFilterRow)).height;
    expect(standaloneHeight - homeHeight, 8);
    expect(tester.getSize(find.byType(TextButton)).height, greaterThanOrEqualTo(48));
    await tester.pumpWidget(_app(const Column(children: [PluginFilterRow(children: [])])));
    expect(tester.getSize(find.byType(PluginFilterRow)).height, 0);
  });

  testWidgets('Home suggestions collapse without losing profile or follow actions', (tester) async {
    var opens = 0;
    var follows = 0;
    Widget people() => PluginFeedPeopleStrip(
      title: 'People from this feed',
      followLabel: 'Follow',
      people: const [PluginFeedPerson(handle: 'reader', name: 'Reader')],
      avatar: (_) => const Icon(Icons.person, size: 20),
      onOpen: (_) => opens++,
      onFollow: (_) => follows++,
    );
    await tester.pumpWidget(_app(Column(children: [people()])));
    final expandedHeight = tester.getSize(find.byType(PluginFeedPeopleStrip)).height;
    expect(find.text('@reader'), findsOneWidget);
    await tester.pumpWidget(_app(Column(children: [PluginEmbedded(child: people())])));
    final compactHeight = tester.getSize(find.byType(PluginFeedPeopleStrip)).height;
    expect(compactHeight, lessThan(expandedHeight));
    expect(compactHeight, greaterThanOrEqualTo(48));
    expect(find.byType(ActionChip), findsNothing);
    await tester.tap(find.text('People from this feed'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('@reader'));
    await tester.tap(find.text('Follow'));
    expect(opens, 1);
    expect(follows, 1);
    await tester.tap(find.text('People from this feed'));
    await tester.pumpAndSettle();
    expect(find.byType(ActionChip), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('expanded people and compact filters allow large RTL text', (tester) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(_app(MediaQuery(
      data: const MediaQueryData(size: Size(320, 640), textScaler: TextScaler.linear(2)),
      child: Directionality(
        textDirection: TextDirection.rtl,
        child: PluginEmbedded(child: Column(children: [
          PluginFilterRow(children: [TextButton(onPressed: () {}, child: const Text('Alle Beiträge'))]),
          PluginFeedPeopleStrip(
            title: 'Personen aus diesem Feed',
            followLabel: 'Folgen',
            people: const [PluginFeedPerson(handle: 'sehr.langer.name.example', name: 'Reader')],
            avatar: (_) => const Icon(Icons.person, size: 20),
            onOpen: (_) {},
            onFollow: (_) {},
          ),
        ])),
      ),
    )));
    await tester.tap(find.text('Personen aus diesem Feed'));
    await tester.pumpAndSettle();
    expect(find.byType(ActionChip), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
