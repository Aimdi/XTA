import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:visibility_detector/visibility_detector.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/_feed.dart';
import 'package:xta/home/chrome_avatar.dart';
import 'package:xta/plugins/plugin_marks.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/ui/x_look_theme.dart';

import 'support/home_sources_harness.dart';

const _options = ValueKey('home-plugin-options');

typedef _Variant = ({double width, double scale, bool rtl, bool dark});

const _variants = <_Variant>[
  (width: 390, scale: 1, rtl: false, dark: false),
  (width: 320, scale: 2, rtl: true, dark: true),
];

Future<HomeSourcesHarness> _pumpHome(WidgetTester tester, FeedTab tab, _Variant variant) async {
  tester.view.physicalSize = Size(variant.width, 844);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  final harness = HomeSourcesHarness(tab);
  addTearDown(() => harness.close(tester));
  await tester.pumpWidget(
    harness.app(
      theme: variant.dark ? xLookLightsOutTheme(null) : xLookLightTheme(null),
      scale: variant.scale,
      rtl: variant.rtl,
    ),
  );
  await _settle(tester);
  return harness;
}

/// SQLite opens on the real event loop; keep both clocks moving until it has.
Future<void> _settle(WidgetTester tester) async {
  for (var frame = 0; frame < 6; frame++) {
    await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 10)));
    await tester.pump(const Duration(milliseconds: 100));
  }
  await tester.pumpAndSettle();
}

bool _attention(WidgetTester tester) =>
    tester.widget<Badge>(find.descendant(of: find.byKey(_options), matching: find.byType(Badge))).isLabelVisible;

Future<void> _openOptions(WidgetTester tester) async {
  await tester.tap(find.byKey(_options));
  await tester.pumpAndSettle();
}

Future<void> _tapTile(WidgetTester tester, String label) async {
  final tile = find.widgetWithText(ListTile, label);
  await tester.ensureVisible(tile);
  await tester.pumpAndSettle();
  await tester.tap(tile);
  await tester.pumpAndSettle();
}

void _expectSameRow(WidgetTester tester, Finder a, Finder b) {
  expect(tester.getCenter(a).dy, closeTo(tester.getCenter(b).dy, 1));
}

void main() {
  setUpAll(() => setUpHomeSourcesDatabase('xta-home-x-reddit-headers'));
  setUp(() => VisibilityDetectorController.instance.updateInterval = Duration.zero);
  tearDown(() => VisibilityDetectorController.instance.updateInterval = const Duration(milliseconds: 500));

  for (final variant in _variants) {
    final name = '${variant.width} ${variant.scale} rtl=${variant.rtl} dark=${variant.dark}';

    testWidgets('X uses the compact Home header and moves its actions into options $name', (tester) async {
      final h = await _pumpHome(tester, FeedTab.x, variant);
      final l10n = L10n.current;
      final picker = find.byKey(const ValueKey('plugin-source-picker-x'));
      final search = find.byKey(const ValueKey('x-reader-search'));
      expect(tester.getSize(picker), const Size(48, 48));
      expect(find.descendant(of: picker, matching: find.byType(PluginBrandMark)), findsOneWidget);
      expect(search.hitTestable(), findsOneWidget);
      _expectSameRow(tester, picker, search);
      _expectSameRow(tester, picker, find.byKey(_options));
      expect(find.byType(DrawerAvatarButton), findsNothing);
      expect(find.byKey(const ValueKey('home-source-picker')), findsNothing);
      expect(find.byKey(const ValueKey('x-reader-library-menu')), findsNothing, reason: 'No second row above For you.');
      expect(find.byTooltip(l10n.home_feed_accounts), findsNothing);
      expect(_attention(tester), isFalse);

      await tester.tap(picker);
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('home-source-following')), findsOneWidget);
      await tester.tap(find.byTooltip(l10n.close));
      await tester.pumpAndSettle();

      await _openOptions(tester);
      for (final label in [
        l10n.search_in_plugin(l10n.source_x),
        l10n.subscriptions,
        l10n.saved,
        l10n.account,
        l10n.reader_search_loaded,
        'Refresh',
        l10n.home_feed_accounts,
      ]) {
        expect(find.widgetWithText(ListTile, label), findsOneWidget, reason: label);
      }
      expect(find.byKey(const ValueKey('open-client-x')), findsNothing);
      expect(find.text(l10n.plugin_open_client(l10n.source_x)), findsNothing);
      final drawer = find.widgetWithText(TextButton, 'Open navigation menu');
      await tester.ensureVisible(drawer);
      await tester.pumpAndSettle();
      await tester.tap(drawer);
      await tester.pumpAndSettle();
      expect(find.text('Drawer fixture'), findsOneWidget);
      expect(h.selected.state, FeedTab.x);
      expect(tester.takeException(), isNull);
    });

    testWidgets('Reddit uses the compact Home header with its sections and options $name', (tester) async {
      final h = await _pumpHome(tester, FeedTab.reddit, variant);
      final l10n = L10n.current;
      final picker = find.byKey(const ValueKey('plugin-source-picker-reddit'));
      final search = find.byTooltip(l10n.plugin_reddit_search_hint);
      expect(tester.getSize(picker), const Size(48, 48));
      for (final section in [
        l10n.plugin_reddit_feed_following,
        l10n.plugin_reddit_feed_popular,
        l10n.plugin_reddit_feed_all,
      ]) {
        expect(find.byTooltip(section).hitTestable(), findsOneWidget, reason: section);
        _expectSameRow(tester, picker, find.byTooltip(section));
      }
      expect(search.hitTestable(), findsOneWidget);
      _expectSameRow(tester, picker, search);
      expect(find.byTooltip(l10n.plugin_reddit_sort), findsNothing);
      expect(find.byType(DrawerAvatarButton), findsNothing);
      expect(find.byKey(const ValueKey('reddit-community-flutter')), findsNothing, reason: 'Chips live in options.');
      expect(find.textContaining('r/flutter about reading'), findsWidgets);
      expect(_attention(tester), isFalse);

      await _openOptions(tester);
      for (final label in [
        l10n.plugin_reddit_feed_popular,
        l10n.plugin_reddit_search_hint,
        l10n.plugin_reddit_sort,
        l10n.saved,
        l10n.subscriptions,
        l10n.plugin_reddit_source_auto,
        l10n.plugin_reddit_source_public,
        '${l10n.plugin_reddit_title} · ${l10n.settings}',
      ]) {
        expect(find.widgetWithText(ListTile, label), findsOneWidget, reason: label);
      }
      expect(find.text(l10n.plugin_open_client(l10n.plugin_reddit_title)), findsOneWidget);
      expect(find.byKey(const ValueKey('open-client-reddit')), findsOneWidget);
      expect(find.widgetWithText(ListTile, l10n.settings), findsNothing, reason: 'App settings stay in the drawer.');
      expect(find.widgetWithText(TextButton, 'Open navigation menu'), findsOneWidget);

      final community = find.byKey(const ValueKey('reddit-community-flutter'));
      await tester.ensureVisible(community);
      await tester.pumpAndSettle();
      await tester.tap(community);
      await _settle(tester);
      final close = find.byKey(const ValueKey('home-plugin-options-close'));
      await tester.ensureVisible(close);
      await tester.pumpAndSettle();
      await tester.tap(close);
      await tester.pumpAndSettle();
      expect(close, findsNothing);
      expect(_attention(tester), isTrue, reason: 'An open community narrows the sections shown above.');
      expect(h.prefs.get<String>(optionPluginRedditSelectedSubreddit), 'flutter');
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('Reddit sort and route changes in Home refetch the visible feed', (tester) async {
    final h = await _pumpHome(tester, FeedTab.reddit, _variants.first);
    expect(h.reddit.requests.map((request) => request.sort).toSet(), {RedditSort.hot});
    await _openOptions(tester);
    await _tapTile(tester, L10n.current.plugin_reddit_sort);
    await tester.tap(find.text(L10n.current.plugin_reddit_sort_new));
    await _settle(tester);
    expect(
      h.reddit.requests.where((request) => request.sort == RedditSort.newest).map((request) => request.subreddit),
      unorderedEquals(['flutter', 'technology']),
    );
    final before = h.reddit.requests.length;
    await _openOptions(tester);
    await _tapTile(tester, L10n.current.plugin_reddit_source_public);
    await _settle(tester);
    expect(h.prefs.get<String>(optionPluginRedditSource), redditSourcePublic);
    expect(h.reddit.requests.length, before + 2);
    await _openOptions(tester);
    final public = tester.widget<ListTile>(find.widgetWithText(ListTile, L10n.current.plugin_reddit_source_public));
    expect(public.trailing, isA<Icon>());
    expect(tester.takeException(), isNull);
  });

  testWidgets('a filtered account marks X options without adding a row', (tester) async {
    final h = await _pumpHome(tester, FeedTab.x, _variants.first);
    final header = tester.getRect(find.byKey(const ValueKey('plugin-source-picker-x')));
    h.accountFilter.publishDisabled({'spare-account'});
    addTearDown(() => h.accountFilter.publishDisabled(const {}));
    await _settle(tester);
    expect(_attention(tester), isTrue);
    expect(tester.getRect(find.byKey(const ValueKey('plugin-source-picker-x'))), header);
    await _openOptions(tester);
    final accounts = tester.widget<ListTile>(find.widgetWithText(ListTile, L10n.current.home_feed_accounts));
    expect((accounts.leading! as Badge).isLabelVisible, isTrue);
    await _tapTile(tester, 'Refresh');
    expect(find.byKey(_options).hitTestable(), findsOneWidget, reason: 'Refresh closes the sheet and keeps X.');
    expect(h.selected.state, FeedTab.x);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Following keeps its own title, picker and actions next to docked sources', (tester) async {
    final h = await _pumpHome(tester, FeedTab.following, _variants.first);
    void expectFollowing() {
      expect(find.byKey(const ValueKey('home-source-picker')).hitTestable(), findsOneWidget);
      expect(find.byTooltip(L10n.current.home_feed_accounts).hitTestable(), findsOneWidget);
      expect(find.byTooltip(L10n.current.reader_search_loaded).hitTestable(), findsOneWidget);
      expect(find.byType(DrawerAvatarButton), findsOneWidget);
      expect(find.byKey(_options), findsNothing);
      expect(find.byTooltip('Refresh'), findsNothing);
    }

    expectFollowing();
    for (final tab in [FeedTab.x, FeedTab.reddit, const FeedTab(pluginIdPixiv)]) {
      h.selected.select(tab);
      await _settle(tester);
      expect(find.byKey(ValueKey('plugin-source-picker-${tab.id}')), findsOneWidget, reason: tab.id);
      expect(find.byKey(const ValueKey('home-source-picker')), findsNothing, reason: tab.id);
    }
    h.selected.select(FeedTab.following);
    await _settle(tester);
    expectFollowing();
    expect(tester.takeException(), isNull);
  });
}
