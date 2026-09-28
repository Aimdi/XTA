import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/home_timeline_picker.dart';
import 'package:xta/plugins/plugin_marks.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/ui/x_look_theme.dart';

const _groupedSources = ['following', 'x', 'pixiv', 'substack', 'booru', 'rss', 'ehviewer'];

Widget groupedPickerFixture({
  String selected = 'following',
  List<String> sources = _groupedSources,
  double scale = 1,
  Locale locale = const Locale('en'),
  ValueChanged<HomeTimelineSelection?>? onPicked,
}) => MaterialApp(
  theme: xLookLightsOutTheme(null),
  locale: locale,
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
  home: Scaffold(body: Builder(builder: (context) => TextButton(
    onPressed: () async {
      final picked = await showModalBottomSheet<HomeTimelineSelection>(
        context: context,
        isScrollControlled: true,
        useSafeArea: true,
        showDragHandle: true,
        builder: (context) => HomeTimelinePicker(
          selected: selected,
          options: [
            for (final id in sources)
              HomeTimelineOption(
                id: id,
                label: id == 'following' ? L10n.of(context).following : pluginById(id)!.title(context),
                mark: id == 'following' ? const Icon(Icons.home_outlined) : pluginMark(pluginById(id)!, size: 16),
                plugin: id != 'following',
                unread: id == 'ehviewer',
              ),
          ],
        ),
      );
      onPicked?.call(picked);
    },
    child: const Text('Open'),
  ))),
);

Widget pickerFixture(int count) => MaterialApp(
  localizationsDelegates: const [
    L10n.delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
    GlobalCupertinoLocalizations.delegate,
  ],
  supportedLocales: L10n.delegate.supportedLocales,
  home: Scaffold(
    body: HomeTimelinePicker(
      selected: 'source-0',
      options: [
        for (var i = 0; i < count; i++)
          HomeTimelineOption(
            id: 'source-$i',
            label: 'Source $i',
            mark: const Icon(Icons.rss_feed),
            plugin: true,
            unread: i == 0,
          ),
      ],
    ),
  ),
);

void main() {
  setUpAll(() async {
    autoUpdateGoldenFiles = const bool.fromEnvironment('RENDER_HOME_SOURCES');
    if (autoUpdateGoldenFiles) {
      await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-Regular.ttf'))).load();
      await (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
    }
  });

  for (final source in ['pixiv', 'booru', 'ehviewer', 'substack', 'rss']) {
    testWidgets('grouped source $source remains directly selectable', (tester) async {
      HomeTimelineSelection? picked;
      await tester.pumpWidget(groupedPickerFixture(onPicked: (value) => picked = value));
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();
      final section = ['substack', 'rss'].contains(source) ? 'reading' : 'art';
      final category = find.byKey(ValueKey('home-source-$section'));
      expect(category, findsOneWidget);
      expect(find.byKey(ValueKey('home-source-$source')), findsNothing);
      await tester.tap(category);
      await tester.pumpAndSettle();
      final member = find.byKey(ValueKey('home-source-$source'));
      await tester.ensureVisible(member);
      await tester.tap(member);
      await tester.pumpAndSettle();
      expect(picked?.id, source);
      expect(picked?.groupId, isNull);
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('single available source stays direct and hidden sources stay hidden', (tester) async {
    await tester.pumpWidget(groupedPickerFixture(sources: ['following', 'pixiv', 'rss']));
    await tester.tap(find.text('Open'));
    await tester.pumpAndSettle();
    expect(find.byType(ExpansionTile), findsNothing);
    expect(find.byKey(const ValueKey('home-source-pixiv')), findsOneWidget);
    expect(find.byKey(const ValueKey('home-source-rss')), findsOneWidget);
    expect(find.text('EH'), findsNothing);
  });

  for (final config in [(locale: 'de', scale: 1.0), (locale: 'de', scale: 2.0), (locale: 'ar', scale: 2.0)]) {
    testWidgets('group state and children survive text scale $config', (tester) async {
      tester.view.physicalSize = Size(config.scale == 1 ? 390 : 320, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final semantics = tester.ensureSemantics();
      addTearDown(semantics.dispose);
      await tester.pumpWidget(groupedPickerFixture(selected: 'booru', scale: config.scale, locale: Locale(config.locale)));
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();
      final art = find.byKey(const ValueKey('home-source-art'));
      await tester.ensureVisible(art);
      expect(find.descendant(of: art, matching: find.byIcon(Icons.check)), findsOneWidget);
      expect(tester.getSemantics(art).label, contains(L10n.current.group_has_unread));
      if (const bool.fromEnvironment('RENDER_HOME_SOURCES')) {
        await expectLater(find.byType(Overlay).first,
          matchesGoldenFile('../review-artifacts/renders/home-sources-${config.locale}-${config.scale}.png'));
      }
      await tester.tap(art);
      await tester.pumpAndSettle();
      final booru = find.byKey(const ValueKey('home-source-booru'));
      await tester.ensureVisible(booru);
      expect(find.descendant(of: booru, matching: find.byIcon(Icons.check)), findsOneWidget);
      expect(tester.getSize(booru).height, greaterThanOrEqualTo(48));
      expect(find.byKey(const ValueKey('home-add-timeline')).hitTestable(), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('search reaches grouped sources and clear restores compact rows', (tester) async {
    await tester.pumpWidget(groupedPickerFixture(sources: [..._groupedSources, 'reddit', 'bluesky']));
    await tester.tap(find.text('Open'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'Pixiv');
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('home-source-pixiv')), findsOneWidget);
    expect(find.byKey(const ValueKey('home-source-art')), findsNothing);
    await tester.tap(find.byTooltip(L10n.current.plugin_mastodon_clear_search));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('home-source-art')), findsOneWidget);
    expect(find.byKey(const ValueKey('home-source-pixiv')), findsNothing);
  });

  testWidgets('ordinary source rows use at most 56px without shrinking targets', (tester) async {
    await tester.pumpWidget(pickerFixture(3));
    await tester.pumpAndSettle();
    final row = find.byKey(const ValueKey('home-source-source-0'));
    expect(tester.getSize(row).height, inInclusiveRange(48, 56));
    expect(find.byType(TextField), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('large chooser searches locally and preserves Add', (tester) async {
    await tester.pumpWidget(pickerFixture(12));
    await tester.pumpAndSettle();
    expect(find.byType(TextField), findsOneWidget);
    await tester.enterText(find.byType(TextField), 'Source 11');
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('home-source-source-11')), findsOneWidget);
    expect(find.byKey(const ValueKey('home-source-source-0')), findsNothing);
    expect(find.byKey(const ValueKey('home-add-timeline')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('search matches grouped services and clearing restores category', (tester) async {
    const sources = ['bluesky', 'mastodon', 'threads', 'rss', 'hn', 'substack', 'reddit', 'youtube', 'pixiv'];
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: L10n.delegate.supportedLocales,
        home: Scaffold(
          body: HomeTimelinePicker(
            selected: 'bluesky',
            groupMicroblogs: true,
            options: [
              for (final id in sources)
                HomeTimelineOption(
                  id: id,
                  label: id,
                  mark: const Icon(Icons.rss_feed),
                  plugin: true,
                  unread: id == 'mastodon',
                ),
            ],
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('home-source-alt-microblogging')), findsOneWidget);
    await tester.enterText(find.byType(TextField), 'MASTO');
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('home-source-mastodon')), findsOneWidget);
    expect(find.byKey(const ValueKey('home-source-bluesky')), findsNothing);
    await tester.enterText(find.byType(TextField), 'unmatched-query');
    await tester.pumpAndSettle();
    expect(find.text(L10n.current.no_results), findsOneWidget);
    await tester.tap(find.byTooltip(L10n.current.plugin_mastodon_clear_search));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('home-source-alt-microblogging')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
