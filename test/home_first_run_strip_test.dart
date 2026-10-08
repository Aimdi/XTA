import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/home/_feed.dart';
import 'package:xta/home/feed_strip_store.dart';
import 'package:xta/home/home_model.dart';
import 'package:xta/home/home_source_picker.dart';
import 'package:xta/home/home_timeline_picker.dart';
import 'package:xta/plugins/plugin_registry.dart';

const _installed = [pluginIdReddit, pluginIdBluesky, pluginIdSubstack, pluginIdPixiv];

/// The plugin defaults `main` writes on every launch.
Map<String, dynamic> _launchDefaults() => {
  optionHomePages: ['feed', 'subscriptions', 'trending', 'saved'],
  optionSeededPluginTabs: <String>[],
  optionSeededStripPlugins: <String>[],
  for (final plugin in builtInPlugins) plugin.enabledPrefKey: false,
  optionPluginRedditShowTab: false,
};

/// Same order as `main`: look for any key, write the defaults, migrate, then
/// build the bottom bar the way the first frame does.
Future<void> _launch(BasePrefService prefs) async {
  final firstLaunch = prefs.getKeys().isEmpty;
  await prefs.setDefaultValues(_launchDefaults());
  await migrateFeedStripPins(prefs, firstLaunch: firstLaunch);
  await HomeModel(prefs, GroupsModel(prefs)).loadPages();
}

/// What Install in the plugin store does.
Future<void> _install(BasePrefService prefs, String id) async {
  await pluginById(id)!.setEnabled(prefs, true);
  await HomeModel(prefs, GroupsModel(prefs)).loadPages();
}

List<String> _homeSources(BasePrefService prefs) => [
  for (final tab in availableFeedTabsFromIds(feedStripPluginIds(prefs), prefs)) tab.id.id,
];

List<String> _offered(BasePrefService prefs) => [
  for (final plugin in feedStripCandidates(prefs, feedStripPluginIds(prefs))) plugin.id,
];

Future<BasePrefService> _freshInstallWithPlugins() async {
  final prefs = PrefServiceCache();
  await _launch(prefs);
  for (final id in _installed) {
    await _install(prefs, id);
  }
  return prefs;
}

class _PickerHost {
  HomeTimelineSelection? picked;

  Future<void> open(WidgetTester tester, BasePrefService prefs) async {
    final tabs = FeedTabStore(FeedTab.following);
    final strip = FeedStripStore(prefs);
    final groups = GroupsModel(prefs);
    addTearDown(tabs.destroy);
    addTearDown(strip.destroy);
    addTearDown(groups.destroy);
    await tester.pumpWidget(
      PrefService(
        service: prefs,
        child: MultiProvider(
          providers: [
            Provider<GroupsModel>.value(value: groups),
            Provider<FeedTabStore>.value(value: tabs),
            Provider<FeedStripStore>.value(value: strip),
          ],
          child: MaterialApp(
            locale: const Locale('en'),
            localizationsDelegates: const [
              L10n.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
              GlobalCupertinoLocalizations.delegate,
            ],
            supportedLocales: L10n.delegate.supportedLocales,
            home: Scaffold(
              body: Builder(
                builder: (context) => TextButton(
                  onPressed: () async => picked = await showHomeSourcePicker(context),
                  child: const Text('open'),
                ),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();
  }
}

Finder _source(String id) => find.byKey(ValueKey('home-source-$id'));

void main() {
  group('first launch', () {
    test('persists an empty strip, so Home offers only Following and X', () async {
      final prefs = PrefServiceCache();
      await _launch(prefs);

      expect(prefs.getStringList(optionHomeFeedStripPlugins), isEmpty);
      expect(prefs.get<bool>(optionHomeFeedStripHandPicked), isTrue);
      expect(_homeSources(prefs), [FeedTab.following.id, FeedTab.x.id]);
    });

    test('installing plugins afterwards offers them instead of adding them', () async {
      final prefs = await _freshInstallWithPlugins();
      await _launch(prefs);

      expect(_homeSources(prefs), [FeedTab.following.id, FeedTab.x.id]);
      expect(_offered(prefs), unorderedEquals(_installed));
    });

    test('starts empty even if a plugin were already switched on', () async {
      final prefs = PrefServiceCache(cache: {optionPluginRedditEnabled: true});
      await migrateFeedStripPins(prefs, firstLaunch: true);

      expect(prefs.getStringList(optionHomeFeedStripPlugins), isEmpty);
    });
  });

  group('existing installs', () {
    test('a saved strip is kept as it is and later installs are not added', () async {
      final prefs = PrefServiceCache(
        cache: {
          optionHomeFeedStripPlugins: [pluginIdPixiv, pluginIdMastodon],
          optionSeededStripPlugins: [pluginIdPixiv, pluginIdMastodon, pluginIdBluesky],
          optionPluginPixivEnabled: true,
          optionPluginMastodonEnabled: true,
          optionPluginBlueskyEnabled: true,
        },
      );
      await _launch(prefs);
      await _install(prefs, pluginIdSubstack);
      await _launch(prefs);

      expect(prefs.getStringList(optionHomeFeedStripPlugins), [pluginIdPixiv, pluginIdMastodon]);
      expect(_offered(prefs), unorderedEquals([pluginIdBluesky, pluginIdSubstack]));
    });

    test('a strip that was never saved keeps every timeline it showed', () async {
      final prefs = PrefServiceCache(
        cache: {
          optionSeededStripPlugins: [pluginIdReddit, pluginIdRss],
          optionPluginRedditEnabled: true,
          optionPluginRssEnabled: true,
        },
      );
      final shown = legacyFeedStripIds(prefs);
      await _launch(prefs);
      await _install(prefs, pluginIdBluesky);
      await _launch(prefs);

      expect(shown, [pluginIdReddit, pluginIdRss]);
      expect(prefs.getStringList(optionHomeFeedStripPlugins), shown);
      expect(_homeSources(prefs), [FeedTab.following.id, FeedTab.x.id, ...shown]);
    });

    test('a hidden-tab plugin Home added on its own is written into the strip', () async {
      final prefs = PrefServiceCache(
        cache: {
          optionHomeFeedStripPlugins: [pluginIdMastodon],
          optionPluginMastodonEnabled: true,
          optionPluginSubstackEnabled: true,
          optionPluginSubstackShowTab: false,
        },
      );
      await _launch(prefs);

      expect(prefs.getStringList(optionHomeFeedStripPlugins), [pluginIdMastodon, pluginIdSubstack]);
    });
  });

  group('reachability', () {
    test('a plugin installed with its bottom tab hidden waits under Add timeline', () async {
      final prefs = PrefServiceCache();
      await _launch(prefs);
      final home = HomeModel(prefs, GroupsModel(prefs));
      await pluginById(pluginIdReddit)!.setEnabled(prefs, true);
      await home.loadPages();

      expect(home.state.map((page) => page.id), isNot(contains(pluginIdReddit)));
      expect(_homeSources(prefs), isNot(contains(pluginIdReddit)));
      expect(_offered(prefs), [pluginIdReddit]);
    });

    test('hiding a bottom tab still moves that plugin to Home', () async {
      final prefs = await _freshInstallWithPlugins();
      await prefs.set(optionPluginPixivShowTab, false);
      await pinPluginOnFeedStrip(prefs, pluginIdPixiv);

      expect(feedStripPluginIds(prefs), [pluginIdPixiv]);
    });
  });

  testWidgets('fresh install: the picker adds plugin timelines one at a time', (tester) async {
    final prefs = (await tester.runAsync(_freshInstallWithPlugins))!;
    final host = _PickerHost();
    await host.open(tester, prefs);

    expect(_source(FeedTab.following.id), findsOneWidget);
    expect(_source(FeedTab.x.id), findsOneWidget);
    for (final id in _installed) {
      expect(_source(id), findsNothing, reason: id);
    }

    await tester.tap(find.byKey(const ValueKey('home-add-timeline')));
    await tester.pumpAndSettle();
    expect(find.text('Plugin timelines'), findsOneWidget);
    expect(find.byTooltip('Add timeline'), findsNWidgets(_installed.length));

    await tester.tap(find.text('Pixiv'));
    await tester.pumpAndSettle();
    expect(prefs.getStringList(optionHomeFeedStripPlugins), [pluginIdPixiv]);
    expect(host.picked?.id, pluginIdPixiv);

    await tester.runAsync(() => _launch(prefs));
    await host.open(tester, prefs);
    expect(_source(pluginIdPixiv), findsOneWidget);
    for (final id in _installed.where((id) => id != pluginIdPixiv)) {
      expect(_source(id), findsNothing, reason: id);
    }
  });
}
