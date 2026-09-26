import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/_feed.dart';
import 'package:xta/home/alt_microblogging.dart';
import 'package:xta/home/alt_microblogging_selector.dart';
import 'package:xta/home/home_timeline_picker.dart';
import 'package:xta/home/network_switcher.dart';
import 'package:xta/settings/alt_microblogging_setting.dart';
import 'package:xta/ui/x_look_theme.dart';

const _pins = ['following', 'x', 'bluesky', 'substack', 'threads', 'mastodon', 'pixiv'];

List<HomeTimelineOption> _options(BuildContext context) => [
  for (final id in _pins)
    HomeTimelineOption(
      id: id,
      label: switch (id) {
        'following' => L10n.of(context).following,
        'x' => L10n.of(context).source_x,
        'bluesky' => L10n.of(context).plugin_bluesky_title,
        'mastodon' => L10n.of(context).plugin_mastodon_title,
        'threads' => L10n.of(context).plugin_threads_title,
        'substack' => L10n.of(context).plugin_substack_title,
        _ => L10n.of(context).plugin_pixiv_title,
      },
      mark: const Icon(Icons.public),
      plugin: id != 'following',
      unread: id == 'threads',
    ),
];

Widget _app(Widget child, {Locale locale = const Locale('en'), double scale = 1}) => MaterialApp(
  debugShowCheckedModeBanner: false,
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
  home: Scaffold(body: child),
);

void _viewport(WidgetTester tester, double width) {
  tester.view.physicalSize = Size(width, 844);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
}

Future<void> _render(String name, Finder finder) async {
  if (!const bool.fromEnvironment('RENDER_ALT_MICROBLOGGING')) return;
  await expectLater(finder, matchesGoldenFile('../review-artifacts/renders/alt-microblogging-$name.png'));
}

void main() {
  setUpAll(() async {
    if (!const bool.fromEnvironment('RENDER_ALT_MICROBLOGGING')) return;
    autoUpdateGoldenFiles = true;
    await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-Regular.ttf'))).load();
    await (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
  });

  test('grouping round trip preserves plugin preferences, credentials and ordered pins', () async {
    final data = <String, Object>{
      optionHomeFeedStripPlugins: _pins.skip(2).toList(),
      optionHomePages: ['feed', 'bluesky', 'saved', 'mastodon', 'threads'],
      optionPluginBlueskyEnabled: true,
      optionPluginMastodonEnabled: true,
      optionPluginThreadsEnabled: true,
      optionPluginThreadsDirectCookies: 'fixture-session',
      optionPluginMastodonInstance: 'https://example.test',
      optionPluginBlueskyLikedPosts: '["fixture-like"]',
    };
    final prefs = PrefServiceCache(cache: Map.from(data));
    final store = AltMicrobloggingStore(prefs);
    expect(store.state, isTrue);
    await store.remember(pluginIdThreads);
    await store.remember(pluginIdSubstack);
    expect(prefs.get<String>(optionAltMicrobloggingLastSource), pluginIdThreads);
    await store.setGrouped(false);
    final restored = AltMicrobloggingStore(prefs);
    expect(restored.state, isFalse);
    await restored.setGrouped(true);
    for (final entry in data.entries) {
      expect(prefs.get(entry.key), entry.value, reason: entry.key);
    }
    expect(groupedMicrobloggingIds(_pins, grouped: false), _pins);
    expect(groupedMicrobloggingIds(_pins, grouped: true), [
      'following',
      'x',
      altMicrobloggingSectionId,
      'substack',
      'pixiv',
    ]);
    expect(groupedMicrobloggingIds(['x', 'substack'], grouped: true), ['x', 'substack']);
    await store.destroy();
    await restored.destroy();
  });

  test('disabled, unpinned and missing remembered sources cannot be reintroduced', () {
    final prefs = PrefServiceCache(
      cache: {
        optionPluginBlueskyEnabled: true,
        optionPluginBlueskyShowTab: true,
        optionPluginMastodonEnabled: false,
        optionPluginThreadsEnabled: true,
        optionPluginThreadsShowTab: true,
      },
    );
    final ids = availableFeedTabsFromIds(['bluesky', 'mastodon'], prefs).map((option) => option.id.id).toList();
    expect(ids, ['following', 'x', 'bluesky']);
    expect(altMicrobloggingDestination(ids, remembered: 'mastodon'), 'bluesky');
    expect(altMicrobloggingDestination(_pins, selected: 'threads', remembered: 'mastodon'), 'threads');
    expect(altMicrobloggingDestination(_pins, selected: 'substack', remembered: 'mastodon'), 'mastodon');
    expect(altMicrobloggingDestination(['x']), isNull);
  });

  for (final large in [false, true]) {
    testWidgets('one Home row retains unread state and opens a real reader, large RTL $large', (tester) async {
      _viewport(tester, large ? 320 : 390);
      final semantics = tester.ensureSemantics();
      addTearDown(semantics.dispose);
      HomeTimelineSelection? picked;
      await tester.pumpWidget(
        _app(
          Builder(
            builder: (context) => TextButton(
              onPressed: () async => picked = await showModalBottomSheet<HomeTimelineSelection>(
                context: context,
                isScrollControlled: true,
                useSafeArea: true,
                showDragHandle: true,
                builder: (context) => HomeTimelinePicker(
                  options: _options(context),
                  selected: 'substack',
                  groupMicroblogs: true,
                  rememberedMicroblog: 'mastodon',
                ),
              ),
              child: const Text('Open'),
            ),
          ),
          locale: Locale(large ? 'ar' : 'de'),
          scale: large ? 2 : 1,
        ),
      );
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();
      final row = find.byKey(const ValueKey('home-source-alt-microblogging'));
      await tester.ensureVisible(row);
      expect(find.byKey(const ValueKey('home-source-bluesky')), findsNothing);
      expect(find.byKey(const ValueKey('home-source-mastodon')), findsNothing);
      expect(find.byKey(const ValueKey('home-source-threads')), findsNothing);
      expect(find.byKey(const ValueKey('home-add-timeline')).hitTestable(), findsOneWidget);
      expect(tester.getSemantics(row).label, contains(L10n.current.group_has_unread));
      expect(tester.takeException(), isNull);
      await _render(large ? 'picker-large-rtl' : 'picker-de', find.byType(Overlay).first);
      await tester.tap(row);
      await tester.pumpAndSettle();
      expect(picked?.id, 'mastodon');
    });
  }

  testWidgets('Plugin Store switch immediately restores every original row without changing selection', (tester) async {
    final prefs = PrefServiceCache();
    final store = AltMicrobloggingStore(prefs);
    addTearDown(store.destroy);
    await tester.pumpWidget(
      PrefService(
        service: prefs,
        child: Provider<AltMicrobloggingStore>.value(
          value: store,
          child: _app(
            Column(
              children: [
                AltMicrobloggingSetting(store: store),
                Expanded(
                  child: AltMicrobloggingScope(
                    builder: (context, grouped) => HomeTimelinePicker(
                      options: _options(context),
                      selected: 'threads',
                      groupMicroblogs: grouped,
                      showAdd: false,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    final toggle = find.byKey(const ValueKey('plugin-store-group-microblogging'));
    await tester.tap(toggle);
    await tester.pumpAndSettle();
    expect(store.state, isFalse);
    expect(find.byKey(const ValueKey('home-source-alt-microblogging')), findsNothing);
    for (final id in ['bluesky', 'threads', 'mastodon']) {
      await tester.ensureVisible(find.byKey(ValueKey('home-source-$id')));
      expect(find.byKey(ValueKey('home-source-$id')), findsOneWidget);
    }
    final selected = find.byKey(const ValueKey('home-source-threads'));
    expect(find.descendant(of: selected, matching: find.byIcon(Icons.check)), findsOneWidget);
    await tester.tap(toggle);
    await tester.pumpAndSettle();
    expect(store.state, isTrue);
    expect(find.byKey(const ValueKey('home-source-alt-microblogging')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  for (final locale in ['de', 'ar']) {
    testWidgets('all service chips remain reachable at 320dp with double text in $locale', (tester) async {
      _viewport(tester, 320);
      final selected = <String>[];
      final semantics = tester.ensureSemantics();
      await tester.pumpWidget(
        _app(
          Align(
            alignment: Alignment.topCenter,
            child: AltMicrobloggingSelector(
              sourceIds: const ['bluesky', 'threads', 'mastodon'],
              selected: 'bluesky',
              unread: const {'threads'},
              onSelected: selected.add,
            ),
          ),
          locale: Locale(locale),
          scale: 2,
        ),
      );
      await tester.pumpAndSettle();
      for (final id in ['bluesky', 'threads', 'mastodon']) {
        final chip = find.byKey(ValueKey('alt-microblogging-service-$id'));
        await tester.ensureVisible(chip);
        await tester.pumpAndSettle();
        expect(chip.hitTestable(), findsOneWidget);
        expect(tester.getSize(chip).height, greaterThanOrEqualTo(48));
        await tester.tap(chip);
      }
      expect(selected, ['bluesky', 'threads', 'mastodon']);
      expect(tester.takeException(), isNull);
      semantics.dispose();
    });
  }

  testWidgets('Networks groups across recent and all sections and can still open Threads', (tester) async {
    String? picked;
    await tester.pumpWidget(
      _app(
        Builder(
          builder: (context) => TextButton(
            onPressed: () async => picked = await showNetworkSwitcherSheet(
              context,
              plugins: pluginsForSwitcher(['bluesky', 'substack', 'threads', 'mastodon']),
              currentId: 'substack',
              recentIds: ['bluesky'],
              groupMicroblogs: true,
            ),
            child: const Text('Open'),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Open'));
    await tester.pumpAndSettle();
    expect(find.text(L10n.current.alt_microblogging), findsOneWidget);
    await tester.tap(find.byType(ExpansionTile));
    await tester.pumpAndSettle();
    final threads = find.byKey(networkSwitcherRowKey('threads'));
    await tester.ensureVisible(threads);
    await tester.tap(threads);
    await tester.pumpAndSettle();
    expect(picked, 'threads');
  });
}
