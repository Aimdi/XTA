import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/home/_feed.dart';
import 'package:xta/home/feed_strip_store.dart';
import 'package:xta/home/home_source_picker.dart';
import 'package:xta/home/home_timeline_picker.dart';
import 'package:xta/plugins/plugin_marks.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/ui/x_look_theme.dart';

import 'support/painted_bounds.dart';

const _shot = ValueKey('picker-shot');

final _stripPlugins = [
  for (final plugin in builtInPlugins)
    if (plugin.supportsFeedStrip) plugin.id,
];

SubscriptionGroup _group(String id, String name) => SubscriptionGroup(
  id: id,
  name: name,
  icon: 'rss_feed',
  color: null,
  numberOfMembers: 2,
  createdAt: DateTime(2026),
  pinned: false,
);

PrefServiceCache _prefs({required bool grouped}) => PrefServiceCache(
  cache: {
    optionHomeFeedStripPlugins: List<String>.from(_stripPlugins),
    optionAltMicrobloggingGrouped: grouped,
    for (final plugin in builtInPlugins) plugin.enabledPrefKey: true,
  },
);

Widget _app(BasePrefService prefs, List<Provider> providers) => RepaintBoundary(
  key: _shot,
  child: PrefService(
    service: prefs,
    child: MaterialApp(
      theme: xLookLightTheme(null),
      localizationsDelegates: const [
        L10n.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: L10n.delegate.supportedLocales,
      home: MultiProvider(
        providers: providers,
        child: Scaffold(
          body: Builder(
            builder: (context) => TextButton(onPressed: () => showHomeSourcePicker(context), child: const Text('open')),
          ),
        ),
      ),
    ),
  ),
);

Future<void> _openPicker(WidgetTester tester, {required bool grouped}) async {
  tester.view.physicalSize = const Size(412, 1800);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  final prefs = _prefs(grouped: grouped);
  final tabs = FeedTabStore(FeedTab.following);
  final strip = FeedStripStore(prefs);
  final groups = GroupsModel(prefs)..update([_group('art', 'Art'), _group('news', 'News')]);
  addTearDown(tabs.destroy);
  addTearDown(strip.destroy);
  addTearDown(groups.destroy);
  await tester.pumpWidget(
    _app(prefs, [
      Provider<GroupsModel>.value(value: groups),
      Provider<FeedTabStore>.value(value: tabs),
      Provider<FeedStripStore>.value(value: strip),
    ]),
  );
  await tester.tap(find.text('open'));
  await tester.pumpAndSettle();
}

/// Each row's leading box, keyed by the row's key.
Map<String, Rect> _markBoxes(WidgetTester tester) {
  final rows = find.byWidgetPredicate((widget) {
    final key = widget.key;
    return widget is ListTile && key is ValueKey<String> && key.value.startsWith('home-');
  });
  return {
    for (final row in tester.widgetList<ListTile>(rows))
      (row.key! as ValueKey<String>).value: tester.getRect(
        find
            .descendant(
              of: find.byKey(row.key!),
              matching: find.byWidgetPredicate(
                (widget) =>
                    widget is SizedBox && widget.width == homeTimelineMarkSize && widget.height == homeTimelineMarkSize,
              ),
            )
            .first,
      ),
  };
}

void main() {
  setUpAll(loadMaterialIcons);

  for (final grouped in [true, false]) {
    testWidgets('every Home source picker mark fills the same box at the same size (grouped $grouped)', (tester) async {
      await _openPicker(tester, grouped: grouped);

      final boxes = _markBoxes(tester);
      expect(boxes.keys, containsAll(['home-source-following', 'home-source-x', 'home-group-art']));
      expect(boxes.keys.where((key) => key.startsWith('home-source-')), hasLength(grouped ? 13 : 15));
      for (final box in boxes.values) {
        expect(box.size, const Size.square(homeTimelineMarkSize));
        expect(box.center.dx, boxes.values.first.center.dx);
      }

      // Brand paths, Material icons, the Mastodon & Bluesky pair and group discs all span the live area, centred.
      final live = homeTimelineMarkSize * pluginMarkLiveShare;
      final painted = await paintedBounds(tester, find.byKey(_shot), boxes);
      for (final MapEntry(key: row, value: glyph) in painted.entries) {
        expect(glyph.longestSide, closeTo(live, 1), reason: row);
        expect((glyph.center - boxes[row]!.center).distance, lessThan(1), reason: row);
      }
      expect(tester.takeException(), isNull);
    });
  }
}
