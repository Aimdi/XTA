import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_history_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_history_store.dart';
import 'package:xta/plugins/pixiv/pixiv_illust_screen.dart';

import 'support/memory_json_store.dart';
import 'support/pixiv_reader_harness.dart';

void main() {
  Future<(PixivHarness, PixivHistoryStore)> pump(
    WidgetTester tester, {
    double textScale = 1,
    Size size = const Size(390, 844),
  }) async {
    final storage = MemoryJsonStore()
      ..values[pixivIllustHistoryKey] = [
        for (final (id, title, user) in [(3, 'Regen', 'Mika'), (2, 'Winterabend', 'Haru'), (1, 'Sommerfest', 'Mika')])
          PixivHistoryEntry.of(pixivWork(id: id, title: title), DateTime.utc(2026, 10, id)).copyUser(user).toJson(),
      ];
    final history = PixivHistoryStore(storage: storage);
    addTearDown(history.destroy);
    final harness = await pumpPixiv(
      tester,
      const PixivHistoryScreen(),
      textScale: textScale,
      size: size,
      extraProviders: [Provider<PixivHistoryStore>.value(value: history)],
    );
    return (harness, history);
  }

  testWidgets('lists the newest first and filters by title or artist', (tester) async {
    await pump(tester);
    final titles = [
      for (final tile in tester.widgetList<PixivIllustTile>(find.byType(PixivIllustTile))) tile.illust.title,
    ];
    expect(titles, ['Regen', 'Winterabend', 'Sommerfest']);

    await tester.enterText(find.byKey(const ValueKey('pixiv-history-filter')), 'haru');
    await tester.pump();
    expect(find.byType(PixivIllustTile), findsOneWidget);
    expect(find.text('Winterabend'), findsOneWidget);

    await tester.enterText(find.byKey(const ValueKey('pixiv-history-filter')), 'nobody');
    await tester.pump();
    expect(find.text('No work in the history matches'), findsOneWidget);
    final field = tester.widget<EditableText>(find.byType(EditableText));
    expect(field.controller.text, 'nobody', reason: 'the field keeps its text as the works under it go');
    expect(field.focusNode.hasFocus, isTrue);
    await disposePixiv(tester);
  });

  testWidgets('a long press forgets one work after asking', (tester) async {
    final (_, history) = await pump(tester);
    await tester.longPress(find.text('Winterabend'));
    await settlePixiv(tester);
    await tester.tap(find.text('Cancel'));
    await settlePixiv(tester);
    expect(history.state, hasLength(3));

    await tester.longPress(find.text('Winterabend'));
    await settlePixiv(tester);
    await tester.tap(find.descendant(of: find.byType(AlertDialog), matching: find.text('Delete')));
    await settlePixiv(tester);
    expect(history.state.map((entry) => entry.id), [3, 1]);
    await disposePixiv(tester);
  });

  testWidgets('Clear all asks first, then empties the history', (tester) async {
    final (_, history) = await pump(tester);
    await tester.tap(find.byKey(const ValueKey('pixiv-history-clear')));
    await settlePixiv(tester);
    await tester.tap(find.descendant(of: find.byType(AlertDialog), matching: find.text('Clear history')));
    await settlePixiv(tester);
    expect(history.state, isEmpty);
    expect(find.text('Works you open appear here. The history stays on this device.'), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('the pause switch stops recording, and a tile reopens its work', (tester) async {
    final (harness, history) = await pump(tester);
    await tester.tap(find.byKey(const ValueKey('pixiv-history-pause')));
    await tester.pump();
    expect(harness.prefs.get<bool>(optionPluginPixivHistoryPaused), isTrue);

    await tester.tap(find.text('Sommerfest'));
    await settlePixiv(tester);
    expect(find.byType(PixivIllustScreen), findsOneWidget);
    expect(history.state.first.id, 3, reason: 'paused: reopening did not move it to the top');
    await disposePixiv(tester);
  });

  testWidgets('large text keeps the tiles inside the screen', (tester) async {
    await pump(tester, textScale: 2);
    expect(tester.takeException(), isNull);
    await disposePixiv(tester);
  });

  testWidgets('a narrow screen with large text scrolls the filter away to reach the works', (tester) async {
    await pump(tester, textScale: 2, size: const Size(320, 640));
    expect(tester.takeException(), isNull);
    final grid = find.byType(CustomScrollView);
    await tester.drag(grid, const Offset(0, -500));
    await settlePixiv(tester);
    expect(tester.takeException(), isNull);
    expect(find.byType(PixivIllustTile), findsWidgets);
    expect(tester.getSize(find.byType(PixivIllustTile).first).height, greaterThan(100));
    await disposePixiv(tester);
  });
}

extension on PixivHistoryEntry {
  PixivHistoryEntry copyUser(String name) => PixivHistoryEntry(
    id: id,
    title: title,
    userId: userId,
    userName: name,
    thumbUrl: thumbUrl,
    viewedAt: viewedAt,
    tags: tags,
  );
}
