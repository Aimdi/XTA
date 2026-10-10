import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_api.dart';
import 'package:xta/plugins/pixiv/pixiv_ranking_modes.dart';
import 'package:xta/plugins/pixiv/pixiv_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';

import 'support/pixiv_discovery_fakes.dart';
import 'support/pixiv_reader_harness.dart';

List<String> _ids(List<PixivRankingMode> modes) => [for (final mode in modes) mode.id];

/// The Following feed's own client, so the Home tab loads without the network.
class _FeedClient extends PixivClient {
  _FeedClient() : super(PrefServiceCache());

  @override
  Future<PixivIllustPage> following({String? nextUrl}) async => const PixivIllustPage(illusts: []);
}

void main() {
  group('the board table', () {
    test('offers R-18 boards only while Show R-18 is on', () {
      final r18 = ['day_r18', 'day_r18_ai', 'week_r18', 'week_r18g'];
      final hidden = _ids(pixivRankingModesOffered(pixivIllustRankingModes, showR18: false));
      final shown = _ids(pixivRankingModesOffered(pixivIllustRankingModes, showR18: true));
      expect(hidden, isNot(containsAll(r18)));
      expect(hidden.where(r18.contains), isEmpty);
      expect(shown, containsAll(r18));
      expect(hidden, containsAll(['day', 'week', 'month', 'day_manga', 'day_ai']));
    });

    test('marks the AI boards, which keep AI works under Hide AI', () {
      expect(pixivRankingModeIsAi('day_ai'), isTrue);
      expect(pixivRankingModeIsAi('day_r18_ai'), isTrue);
      expect(pixivRankingModeIsAi('day'), isFalse);
      expect(pixivRankingModeIsAi('unknown'), isFalse);
    });
  });

  group('pins', () {
    test('read back without unknown or repeated boards, and fall back to the defaults', () {
      final table = pixivIllustRankingModes;
      expect(parsePixivRankingPins('["week","nope","week","day_ai"]', table, pixivDefaultRankingPins), [
        'week',
        'day_ai',
      ]);
      for (final raw in [null, '', '[]', '{"day":1}', 'not json', '[1,2]']) {
        expect(parsePixivRankingPins(raw, table, pixivDefaultRankingPins), pixivDefaultRankingPins, reason: '$raw');
      }
    });

    test('show in table order, R-18 ones only with Show R-18, and never none', () {
      final pins = ['week_r18', 'day_ai', 'day'];
      final table = pixivIllustRankingModes;
      expect(_ids(pixivVisibleRankingModes(pins, table, showR18: false)), ['day', 'day_ai']);
      expect(_ids(pixivVisibleRankingModes(pins, table, showR18: true)), ['day', 'day_ai', 'week_r18']);
      expect(_ids(pixivVisibleRankingModes(['week_r18'], table, showR18: false)), ['day']);
    });

    test('the shown board falls back to the first chip when its own is gone', () {
      final visible = pixivVisibleRankingModes(['week', 'month'], pixivIllustRankingModes, showR18: false);
      expect(pixivEffectiveRankingMode('month', visible), 'month');
      expect(pixivEffectiveRankingMode('day_r18', visible), 'week');
    });

    test('are saved as JSON in table order and survive a new store', () async {
      final prefs = PrefServiceCache(defaults: {optionPluginPixivRankingModes: jsonEncode(pixivDefaultRankingPins)});
      final store = PixivRankingPinsStore(prefs);
      await store.toggle('day_ai');
      await store.toggle('week');
      expect(jsonDecode(prefs.get<String>(optionPluginPixivRankingModes)!), [
        'day',
        'month',
        'day_male',
        'day_female',
        'week_original',
        'week_rookie',
        'day_manga',
        'day_ai',
      ]);
      expect(PixivRankingPinsStore(prefs).state, store.state);
      await store.destroy();
    });
  });

  group('the Rankings section', () {
    late FakePixivDiscoveryApi api;
    late PixivFeedStore feed;
    late ScrollController scroll;

    setUp(() {
      api = FakePixivDiscoveryApi(PixivClient(PrefServiceCache()), rankingWorks: [pixivWork(id: 900, pages: 1)]);
      feed = PixivFeedStore(_FeedClient());
      scroll = ScrollController();
    });

    tearDown(() {
      feed.destroy();
      scroll.dispose();
    });

    Future<PixivHarness> pumpRankings(WidgetTester tester) async {
      final harness = await pumpPixiv(
        tester,
        PixivScreen(scrollController: scroll),
        client: FakePixivScreenClient.new,
        extraProviders: [
          Provider<PixivFeedStore>.value(value: feed),
          Provider<PixivDiscoveryApi>.value(value: api),
        ],
      );
      await tester.tap(find.descendant(of: find.byType(PluginHomeChrome), matching: find.byTooltip('Ranking')));
      await settlePixiv(tester);
      return harness;
    }

    Future<void> openEditor(WidgetTester tester) async {
      await tester.ensureVisible(find.byKey(const ValueKey('pixiv-ranking-edit')));
      await tester.tap(find.byKey(const ValueKey('pixiv-ranking-edit')));
      await settlePixiv(tester);
    }

    testWidgets('pins a board from the editor; the chip stays for the next visit', (tester) async {
      final harness = await pumpRankings(tester);
      expect(api.calls.last, 'rank:day:null');
      expect(find.byKey(const ValueKey('pixiv-ranking-mode-day_ai')), findsNothing);

      await openEditor(tester);
      expect(find.byKey(const ValueKey('pixiv-ranking-pin-day_r18')), findsNothing);
      await tester.tap(find.byKey(const ValueKey('pixiv-ranking-pin-day_ai')));
      await settlePixiv(tester);
      expect(jsonDecode(harness.prefs.get<String>(optionPluginPixivRankingModes)!), contains('day_ai'));
      await tester.tapAt(const Offset(20, 20));
      await settlePixiv(tester);
      expect(find.byKey(const ValueKey('pixiv-ranking-mode-day_ai')), findsOneWidget);

      await tester.pumpWidget(const SizedBox());
      final pins = PixivRankingPinsStore(harness.prefs);
      expect(pins.state, contains('day_ai'));
      await pins.destroy();
      await disposePixiv(tester);
    });

    testWidgets('offers R-18 boards with Show R-18 on and keeps the last chip pinned', (tester) async {
      final prefs = PrefServiceCache(defaults: {optionPluginPixivRankingModes: '["day","week_r18"]'});
      final pins = PixivRankingPinsStore(prefs);
      addTearDown(pins.destroy);
      await pumpPixiv(
        tester,
        Scaffold(
          body: Builder(
            builder: (context) => TextButton(
              onPressed: () => showPixivRankingModeSheet(
                context,
                pins: pins,
                offered: pixivRankingModesOffered(pixivIllustRankingModes, showR18: false),
              ),
              child: const Text('edit'),
            ),
          ),
        ),
      );
      await tester.tap(find.text('edit'));
      await settlePixiv(tester);
      expect(find.byKey(const ValueKey('pixiv-ranking-pin-week_r18')), findsNothing);
      final day = tester.widget<FilterChip>(find.byKey(const ValueKey('pixiv-ranking-pin-day')));
      expect((day.selected, day.onSelected), (true, null));
      expect(pins.state, ['day', 'week_r18'], reason: 'the hidden R-18 pin stays saved');
      await disposePixiv(tester);
    });

    testWidgets('unpinning the board on show moves to the first chip and reloads', (tester) async {
      await pumpRankings(tester);
      await tester.tap(find.byKey(const ValueKey('pixiv-ranking-mode-week')));
      await settlePixiv(tester);
      expect(api.calls.last, 'rank:week:null');

      await openEditor(tester);
      await tester.tap(find.byKey(const ValueKey('pixiv-ranking-pin-week')));
      await settlePixiv(tester);
      await tester.tapAt(const Offset(20, 20));
      await settlePixiv(tester);
      expect(find.byKey(const ValueKey('pixiv-ranking-mode-week')), findsNothing);
      expect(api.calls.last, 'rank:day:null');
      final day = tester.widget<ChoiceChip>(find.byKey(const ValueKey('pixiv-ranking-mode-day')));
      expect(day.selected, isTrue);
      await disposePixiv(tester);
    });
  });
}
