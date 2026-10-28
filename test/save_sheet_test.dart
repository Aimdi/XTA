import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/constants.dart';
import 'package:xta/saved/save_sheet_model.dart';
import 'package:xta/ui/quick_pick_sheet.dart';

import 'support/save_sheet_harness.dart';

Finder _chip(String id) => find.byKey(ValueKey('save-folder-$id'));

String _status(WidgetTester tester) => tester.widget<Text>(find.byKey(kQuickPickStatusKey)).data!;

bool _checked(WidgetTester tester, String id) => tester.widget<QuickPickChip>(_chip(id)).selected;

void main() {
  group('the order the chips open in', () {
    final folders = [for (var i = 0; i < 8; i++) savedFolder('f$i', 'Folder $i', position: i)];

    test('the latest saved posts lead with their folders, each once, then the rest', () {
      final order = saveSheetFolderOrder([
        savedPost('a', 'f5'),
        savedPost('b', null),
        savedPost('c', 'f2'),
        savedPost('d', 'f5'),
      ], folders);
      expect(order.map((folder) => folder.id), ['f5', 'f2', 'f0', 'f1', 'f3', 'f4', 'f6', 'f7']);
    });

    test('a post in a folder that is gone is passed over', () {
      final order = saveSheetFolderOrder([savedPost('a', 'gone'), savedPost('b', 'f1')], folders.take(2).toList());
      expect(order.map((folder) => folder.id), ['f1', 'f0']);
    });
  });

  group('the sheet', () {
    final folders = [savedFolder('art', 'Art', position: 0), savedFolder('news', 'News', position: 1)];

    testWidgets('a tap saves the post in that folder and the status line follows', (tester) async {
      final harness = SaveSheetHarness(folders: folders);
      await harness.open(tester);
      expect(find.text('Save to'), findsOneWidget);
      expect(_status(tester), 'Not saved yet');

      await tester.tap(_chip('art'));
      await tester.pumpAndSettle();
      expect(harness.saves.isSaved('t1'), isTrue);
      expect(harness.folderOfPost, 'art');
      expect(_checked(tester, 'art'), isTrue);
      expect(_status(tester), 'Saved to Art');
      expect(find.byType(BottomSheet), findsOneWidget);

      await tester.tap(_chip('news'));
      await tester.pumpAndSettle();
      expect(harness.folderOfPost, 'news');
      expect(_checked(tester, 'art'), isFalse);
      expect(_checked(tester, 'news'), isTrue);
      expect(_status(tester), 'Saved to News');
    });

    testWidgets('a tap on the checked folder takes the post out of it, still saved', (tester) async {
      final harness = SaveSheetHarness(saved: [savedPost('t1', 'art')], folders: folders);
      await harness.open(tester);
      expect(_checked(tester, 'art'), isTrue);
      expect(_status(tester), 'Saved to Art');

      await tester.tap(_chip('art'));
      await tester.pumpAndSettle();
      expect(harness.saves.isSaved('t1'), isTrue);
      expect(harness.folderOfPost, isNull);
      expect(_status(tester), 'Saved without a folder');
    });

    testWidgets('a typed name creates its folder and files the post in it', (tester) async {
      final harness = SaveSheetHarness(folders: folders);
      await harness.open(tester);
      await tester.enterText(find.byType(TextField), 'Kyoto');
      await tester.pumpAndSettle();
      expect(find.text('No folder found'), findsOneWidget);
      expect(_chip('art'), findsNothing);

      await tester.tap(find.byKey(kQuickPickCreateKey));
      await tester.pumpAndSettle();
      expect(harness.folders.state.map((folder) => folder.name), contains('Kyoto'));
      expect(harness.folderOfPost, 'new-Kyoto');
      expect(tester.widget<TextField>(find.byType(TextField)).controller!.text, isEmpty);
      expect(find.text('Recently used'), findsOneWidget);
      expect(tester.getTopLeft(_chip('new-Kyoto')).dx, lessThan(tester.getTopLeft(_chip('art')).dx));
      expect(_checked(tester, 'new-Kyoto'), isTrue);
      expect(_status(tester), 'Saved to Kyoto');
    });

    testWidgets('a name a folder already has offers no create row', (tester) async {
      final harness = SaveSheetHarness(folders: folders);
      await harness.open(tester);
      await tester.enterText(find.byType(TextField), 'news');
      await tester.pumpAndSettle();
      expect(find.text('Matches'), findsOneWidget);
      expect(_chip('news'), findsOneWidget);
      expect(find.byKey(kQuickPickCreateKey), findsNothing);
    });

    testWidgets('All folders shows the folders beyond the first six', (tester) async {
      final many = [for (var i = 0; i < 8; i++) savedFolder('f$i', 'Folder $i', position: i)];
      final harness = SaveSheetHarness(saved: [savedPost('old', 'f7')], folders: many);
      await harness.open(tester);
      expect(_chip('f7'), findsOneWidget);
      expect(_chip('f6'), findsNothing);
      expect(_chip('f5'), findsNothing);

      await tester.tap(find.byKey(kQuickPickAllKey));
      await tester.pumpAndSettle();
      for (var i = 0; i < 8; i++) {
        expect(_chip('f$i'), findsOneWidget);
      }
      expect(find.text('All folders'), findsOneWidget);
      expect(find.byKey(kQuickPickAllKey), findsNothing);
    });

    testWidgets('a pick is remembered while Remember this folder is on', (tester) async {
      final harness = SaveSheetHarness(folders: folders, cache: {optionSavedStickyFolderEnabled: true});
      await harness.open(tester);
      await tester.tap(_chip('news'));
      await tester.pumpAndSettle();
      expect(harness.prefs.get<String>(optionSavedStickyFolderId), 'news');
      expect(tester.widget<SwitchListTile>(find.byType(SwitchListTile)).value, isTrue);
    });

    testWidgets('filing clicks once', (tester) async {
      final taps = <Object?>[];
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
        if (call.method == 'HapticFeedback.vibrate') taps.add(call.arguments);
        return null;
      });
      addTearDown(() => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, null));
      final harness = SaveSheetHarness(folders: folders);
      await harness.open(tester);
      taps.clear();
      await tester.tap(_chip('art'));
      await tester.pumpAndSettle();
      expect(taps, ['HapticFeedbackType.selectionClick']);
    });
  });
}
