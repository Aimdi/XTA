import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_favorite_tags_store.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_history_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_history_store.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_more_pane.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_card.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_open.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_search_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_plugin.dart';
import 'package:xta/plugins/pixiv/pixiv_view_state.dart';
import 'package:xta/utils/json.dart';

import 'support/memory_json_store.dart';
import 'support/pixiv_novel_fakes.dart';
import 'support/pixiv_reader_harness.dart';

/// Records the pages `openUri` hands the browser.
List<String> _recordLaunches() {
  final launched = <String>[];
  final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  for (final name in ['plugins.flutter.io/url_launcher', 'browser_resolver']) {
    final channel = MethodChannel(name);
    messenger.setMockMethodCallHandler(channel, (call) async {
      if (call.arguments case {'url': final String url}) launched.add(url);
      return name == 'browser_resolver' ? null : true;
    });
    addTearDown(() => messenger.setMockMethodCallHandler(channel, null));
  }
  return launched;
}

PixivHistoryEntry _novelEntry(int id, String title, String author) => PixivHistoryEntry.ofNovel(
  pixivNovel(id: id, title: title, userName: author, textLength: 2000),
  DateTime.utc(2026, 10, id),
);

void main() {
  group('novel entries', () {
    test('keep the card fields and round-trip, a novel without a cover included', () {
      final novel = pixivNovel(
        id: 31,
        title: 'Letters',
        textLength: 4321,
        bookmarked: true,
        bookmarks: 12,
        tags: const [PixivTag(name: '秋')],
      );
      final entry = PixivHistoryEntry.ofNovel(novel, DateTime.utc(2026));
      final back = PixivHistoryEntry.fromJson(Json(entry.toJson()), needsThumb: false)!.toNovel();
      expect(
        (back.id, back.title, back.user.id, back.user.name, back.coverUrl, back.textLength),
        (31, 'Letters', 42, 'Mika', novel.coverUrl, 4321),
      );
      expect((back.tags.single.name, back.totalBookmarks, back.isBookmarked), ('秋', 12, true));
      expect((back.xRestrict, back.isAi), (0, false));

      final rated = PixivHistoryEntry.ofNovel(pixivNovel(id: 32, xRestrict: 2, ai: true), DateTime.utc(2026));
      final ratedBack = PixivHistoryEntry.fromJson(Json(rated.toJson()), needsThumb: false)!.toNovel();
      expect((ratedBack.isR18, ratedBack.isR18G, ratedBack.isAi), (true, true, true));
      expect(entry.toJson().keys, isNot(contains('xRestrict')), reason: 'an all-ages novel stores no rating');

      const bare = Json({'id': 8, 'title': 'No cover'});
      expect(PixivHistoryEntry.fromJson(bare), isNull, reason: 'a work cannot show without its thumbnail');
      expect(PixivHistoryEntry.fromJson(bare, needsThumb: false)!.toNovel().coverUrl, isNull);
      expect(PixivHistoryEntry.fromJson(const Json({'title': 'no id'}), needsThumb: false), isNull);
    });

    test('the novel history keeps its own file and reloads novels without covers', () async {
      final storage = MemoryJsonStore()
        ..values[pixivNovelHistoryKey] = [
          {'id': 2, 'title': 'Bare'},
          'junk',
          _novelEntry(1, 'Letters', 'Mika').toJson(),
        ];
      final store = PixivNovelHistoryStore(storage: storage);
      addTearDown(store.destroy);
      await store.load();
      expect([for (final entry in store.state) entry.id], [2, 1]);

      await store.record(_novelEntry(3, 'Rain', 'Haru'));
      expect(storage.values.keys, [pixivNovelHistoryKey]);
      expect([for (final entry in store.state) entry.id], [3, 2, 1]);
    });
  });

  group('opening a novel', () {
    Future<(PixivHarness, PixivNovelHistoryStore, List<String>)> pump(WidgetTester tester) async {
      final launched = _recordLaunches();
      final history = PixivNovelHistoryStore(storage: MemoryJsonStore());
      addTearDown(history.destroy);
      final api = FakePixivNovelApi(PixivClient(PrefServiceCache()), details: {5: pixivNovel(id: 5, title: 'Found')});
      final harness = await pumpPixiv(
        tester,
        Scaffold(
          body: Builder(
            builder: (context) => Column(
              children: [
                TextButton(onPressed: () => openPixivNovel(context, pixivNovel(id: 4)), child: const Text('card')),
                TextButton(onPressed: () => openPixivNovelById(context, 5), child: const Text('link')),
              ],
            ),
          ),
        ),
        extraProviders: [
          ...api.providers,
          Provider<PixivNovelHistoryStore>.value(value: history),
        ],
      );
      return (harness, history, launched);
    }

    testWidgets('records it in the novel history and opens its page', (tester) async {
      final (_, history, launched) = await pump(tester);
      await tester.tap(find.text('card'));
      await settlePixiv(tester);
      await tester.tap(find.text('link'));
      await settlePixiv(tester);

      expect([for (final entry in history.state) (entry.id, entry.title)], [(5, 'Found'), (4, 'Autumn Letters')]);
      expect(launched, ['https://www.pixiv.net/novel/show.php?id=4', 'https://www.pixiv.net/novel/show.php?id=5']);
      await disposePixiv(tester);
    });

    testWidgets('a paused history records nothing', (tester) async {
      final (harness, history, launched) = await pump(tester);
      await harness.prefs.set(optionPluginPixivHistoryPaused, true);
      await tester.tap(find.text('card'));
      await settlePixiv(tester);
      expect(history.state, isEmpty);
      expect(launched, hasLength(1));
      await disposePixiv(tester);
    });
  });

  group('history screen', () {
    Future<(PixivHistoryStore, PixivNovelHistoryStore)> pump(
      WidgetTester tester, {
      PixivContentMode initialKind = PixivContentMode.illust,
      double textScale = 1,
      Size size = const Size(390, 844),
      Widget? home,
    }) async {
      final storage = MemoryJsonStore()
        ..values[pixivIllustHistoryKey] = [PixivHistoryEntry.of(pixivWork(id: 1), DateTime.utc(2026)).toJson()]
        ..values[pixivNovelHistoryKey] = [
          for (final (id, title, author) in [(3, 'Rain', 'Haru'), (2, 'Letters', 'Mika')])
            _novelEntry(id, title, author).toJson(),
        ];
      final works = PixivHistoryStore(storage: storage);
      final novels = PixivNovelHistoryStore(storage: storage);
      addTearDown(works.destroy);
      addTearDown(novels.destroy);
      await pumpPixiv(
        tester,
        home ?? PixivHistoryScreen(initialKind: initialKind),
        textScale: textScale,
        size: size,
        extraProviders: [
          ...FakePixivNovelApi(PixivClient(PrefServiceCache())).providers,
          Provider<PixivHistoryStore>.value(value: works),
          Provider<PixivNovelHistoryStore>.value(value: novels),
        ],
      );
      return (works, novels);
    }

    List<String> titles(WidgetTester tester) => [
      for (final card in tester.widgetList<PixivNovelCard>(find.byType(PixivNovelCard))) card.novel.title,
    ];

    testWidgets('switches to the novels, newest first, and the filter carries over', (tester) async {
      await pump(tester);
      expect(find.byType(PixivIllustTile), findsOneWidget);
      expect(find.byType(PixivNovelCard), findsNothing);

      await tester.tap(find.text('Novels'));
      await settlePixiv(tester);
      expect(titles(tester), ['Rain', 'Letters']);
      expect(find.text('Filter by title or author'), findsOneWidget);
      expect(find.text('2K characters'), findsNWidgets(2));

      await tester.enterText(find.byKey(const ValueKey('pixiv-history-filter')), 'mika');
      await tester.pump();
      expect(titles(tester), ['Letters']);
      await tester.enterText(find.byKey(const ValueKey('pixiv-history-filter')), 'nobody');
      await tester.pump();
      expect(find.text('No novel in the history matches'), findsOneWidget);

      await tester.tap(find.text('Illustrations'));
      await settlePixiv(tester);
      expect(find.text('No work in the history matches'), findsOneWidget);
      expect(tester.widget<TextField>(find.byKey(const ValueKey('pixiv-history-filter'))).controller!.text, 'nobody');
      await disposePixiv(tester);
    });

    testWidgets('a long press forgets one novel and Clear all empties only the novels', (tester) async {
      final (works, novels) = await pump(tester, initialKind: PixivContentMode.novel);
      await tester.longPress(find.text('Rain'));
      await settlePixiv(tester);
      await tester.tap(find.descendant(of: find.byType(AlertDialog), matching: find.text('Delete')));
      await settlePixiv(tester);
      expect([for (final entry in novels.state) entry.id], [2]);
      expect(titles(tester), ['Letters']);

      await tester.tap(find.byKey(const ValueKey('pixiv-history-clear')));
      await settlePixiv(tester);
      expect(find.text('Clear the novel history?'), findsOneWidget);
      await tester.tap(find.descendant(of: find.byType(AlertDialog), matching: find.text('Clear history')));
      await settlePixiv(tester);
      expect(novels.state, isEmpty);
      expect(works.state, hasLength(1));
      expect(find.text('Novels you open appear here. The history stays on this device.'), findsOneWidget);

      await tester.tap(find.text('Illustrations'));
      await settlePixiv(tester);
      await tester.tap(find.byKey(const ValueKey('pixiv-history-clear')));
      await settlePixiv(tester);
      expect(find.text('Clear the illustration history?'), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('a novel keeps its R-18 and AI marks in the history', (tester) async {
      final storage = MemoryJsonStore()
        ..values[pixivNovelHistoryKey] = [
          PixivHistoryEntry.ofNovel(
            pixivNovel(id: 7, title: 'Night', xRestrict: 1, ai: true),
            DateTime.utc(2026),
          ).toJson(),
        ];
      final novels = PixivNovelHistoryStore(storage: storage);
      final works = PixivHistoryStore(storage: storage);
      addTearDown(novels.destroy);
      addTearDown(works.destroy);
      await pumpPixiv(
        tester,
        const PixivHistoryScreen(initialKind: PixivContentMode.novel),
        extraProviders: [
          ...FakePixivNovelApi(PixivClient(PrefServiceCache())).providers,
          Provider<PixivHistoryStore>.value(value: works),
          Provider<PixivNovelHistoryStore>.value(value: novels),
        ],
      );
      expect(find.widgetWithText(PixivNovelCard, 'Night'), findsOneWidget);
      expect(find.widgetWithText(PixivNovelRating, 'R-18'), findsOneWidget);
      expect(find.widgetWithText(PixivNovelRating, 'AI'), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('a muted novel stays in the history, and tapping one opens it and moves it up', (tester) async {
      final launched = _recordLaunches();
      final (_, novels) = await pump(tester, initialKind: PixivContentMode.novel);
      final mute = Provider.of<PixivMuteStore>(tester.element(find.byType(PixivHistoryScreen)), listen: false);
      await mute.muteNovel(3);
      await tester.pump();
      expect(titles(tester), ['Rain', 'Letters']);

      await tester.tap(find.text('Letters'));
      await settlePixiv(tester);
      expect(launched, ['https://www.pixiv.net/novel/show.php?id=2']);
      expect([for (final entry in novels.state) entry.id], [2, 3]);
      await disposePixiv(tester);
    });

    testWidgets('More opens the history on the kind the sections show', (tester) async {
      await pump(
        tester,
        home: Scaffold(
          body: PixivMorePane(onAuthChanged: () {}, mode: PixivContentMode.novel),
        ),
      );
      await tester.tap(find.byKey(const ValueKey('pixiv-more-history')));
      await settlePixiv(tester);
      expect(titles(tester), ['Rain', 'Letters']);
      expect(find.byType(PixivIllustTile), findsNothing);
      await disposePixiv(tester);

      await pump(
        tester,
        home: Scaffold(body: PixivMorePane(onAuthChanged: () {})),
      );
      await tester.tap(find.byKey(const ValueKey('pixiv-more-history')));
      await settlePixiv(tester);
      expect(find.byType(PixivIllustTile), findsOneWidget);
      expect(find.byType(PixivNovelCard), findsNothing);
      await disposePixiv(tester);
    });

    testWidgets('the switch and the novels fit a narrow phone at large text', (tester) async {
      await pump(tester, initialKind: PixivContentMode.novel, textScale: 2, size: const Size(320, 640));
      expect(find.byKey(const ValueKey('pixiv-history-kind')), findsOneWidget);
      expect(tester.takeException(), isNull);
      await disposePixiv(tester);
    });
  });
  testWidgets('forgetting what was loaded empties the novel history and reloads the novel searches', (tester) async {
    final storage = MemoryJsonStore()..values[pixivNovelHistoryKey] = [_novelEntry(1, 'Letters', 'Mika').toJson()];
    final works = PixivHistoryStore(storage: storage);
    final novels = PixivNovelHistoryStore(storage: storage);
    addTearDown(works.destroy);
    addTearDown(novels.destroy);
    final harness = await pumpPixiv(
      tester,
      Scaffold(
        body: Builder(
          builder: (context) =>
              TextButton(onPressed: () => PixivPlugin().forgetLoadedData(context), child: const Text('forget')),
        ),
      ),
      extraProviders: [
        Provider<PixivNovelSearchHistory>(
          create: (context) => PixivNovelSearchHistory(PrefService.of(context, listen: false)),
          dispose: (_, store) => store.destroy(),
        ),
        Provider<PixivFavoriteTagsStore>(
          create: (context) => PixivFavoriteTagsStore(PrefService.of(context, listen: false)),
          dispose: (_, store) => store.destroy(),
        ),
        Provider<PixivHistoryStore>.value(value: works),
        Provider<PixivNovelHistoryStore>.value(value: novels),
      ],
    );
    novels.load();
    await settlePixiv(tester);
    expect(novels.state, hasLength(1));
    final searches = Provider.of<PixivNovelSearchHistory>(tester.element(find.text('forget')), listen: false);
    await searches.remember('rain');
    await harness.prefs.set(optionPluginPixivNovelSearchHistory, '[]');
    expect(searches.state, ['rain'], reason: 'the reset preferences are not read back on their own');

    await tester.tap(find.text('forget'));
    await settlePixiv(tester);
    expect(searches.state, isEmpty);
    expect(novels.state, isEmpty);
    expect(storage.values[pixivNovelHistoryKey], isEmpty);
    await disposePixiv(tester);
  });
}
