import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/saved/saved_chrome.dart';
import 'package:xta/saved/saved_header.dart';
import 'package:xta/saved/saved_tab_order.dart';
import 'package:xta/saved/saved_view_store.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/x_look_theme.dart';

Widget _app(Widget child) => MaterialApp(
  theme: xLookLightTheme(null),
  localizationsDelegates: const [
    L10n.delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
    GlobalCupertinoLocalizations.delegate,
  ],
  supportedLocales: L10n.delegate.supportedLocales,
  home: Scaffold(body: child),
);

void main() {
  test(
    'Saved view selection, media, search, and reconciliation are Store-backed',
    () {
      final store = SavedViewStore();
      addTearDown(store.destroy);

      store.selectFolder(savedTabFavorites);
      expect(store.state.folder, savedTabFavorites);
      expect(store.state.likesByGroup, isFalse);

      store.selectFolder(savedTabFavorites);
      expect(store.state.likesByGroup, isTrue);

      store.toggleMedia();
      store.toggleSearch();
      store.setQuery(' flutter ');
      store.setSort(SavedSort.oldest);
      store.beginSelection('one');
      store.toggleSelected('two');
      expect(store.state.mediaOnly, isTrue);
      expect(store.state.query, 'flutter');
      expect(store.state.sort, SavedSort.oldest);
      expect(store.state.selecting, isTrue);
      expect(store.state.selectedIds, {'one', 'two'});

      store.selectVisible(['three', 'four']);
      expect(store.state.selectedIds, {'three', 'four'});
      store.finishSelection();
      expect(store.state.selecting, isFalse);
      expect(store.state.selectedIds, isEmpty);

      store.toggleSearch();
      expect(store.state.searching, isFalse);
      expect(store.state.query, isEmpty);

      store.selectFolder('deleted');
      store.reconcileFolders(const [], showUnfiled: false, showFavorites: true);
      expect(store.state.folder, savedTabAll);
    },
  );

  testWidgets('the chip row holds the network picker, plain folder chips and a dashed +', (tester) async {
    final semantics = tester.ensureSemantics();
    String? selected;
    SavedFolderOption? pressed;
    var created = 0;
    await tester.pumpWidget(
      _app(
        SavedChipRow(
          networkPicker: const Text('Networks'),
          selectedFolder: 'reading',
          likesByGroup: false,
          folders: const [
            SavedFolderOption(value: savedTabAll, label: 'All'),
            SavedFolderOption(value: 'reading', label: 'Long-form reading', editable: true),
          ],
          onFolderSelected: (value) => selected = value,
          onFolderLongPress: (option) => pressed = option,
          onNewFolder: () => created++,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(tester.getSize(find.byType(SavedChipRow)).height, kTweetTouchTarget);
    final chip = find.widgetWithText(SavedFolderChip, 'Long-form reading');
    expect(tester.widget<SavedFolderChip>(chip).selected, isTrue);
    expect(tester.widget<SavedFolderChip>(find.widgetWithText(SavedFolderChip, 'All')).selected, isFalse);
    expect(find.byType(Icon), findsOneWidget, reason: 'only the + carries an icon');
    expect(tester.getSemantics(find.text('Long-form reading')), isSemantics(isSelected: true, isButton: true));

    await tester.tap(find.text('All'));
    expect(selected, savedTabAll);
    await tester.longPress(find.text('Long-form reading'));
    expect(pressed?.value, 'reading');
    await tester.tap(find.byTooltip('Create new folder'));
    expect(created, 1);
    await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
    semantics.dispose();
  });

  testWidgets('Filter and sort offers what applies to the open chip', (tester) async {
    SavedLibraryAction? action;
    Future<void> open({required bool sortable, required bool showMedia}) async {
      await tester.pumpWidget(
        _app(
          SavedFilterButton(
            sort: SavedSort.newest,
            sortable: sortable,
            showMedia: showMedia,
            mediaOnly: true,
            onSelected: (value) => action = value,
          ),
        ),
      );
      await tester.pumpAndSettle();
    }

    await open(sortable: true, showMedia: true);
    expect(find.byTooltip('Filter and sort'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('saved-library-actions')));
    await tester.pumpAndSettle();
    for (final label in ['Newest saved first', 'Oldest saved first', 'Media only', 'Select']) {
      expect(find.text(label), findsOneWidget, reason: label);
    }
    final media = tester.widget<CheckedPopupMenuItem<SavedLibraryAction>>(
      find.widgetWithText(CheckedPopupMenuItem<SavedLibraryAction>, 'Media only'),
    );
    expect(media.checked, isTrue);
    await tester.tap(find.text('Select'));
    await tester.pumpAndSettle();
    expect(action, SavedLibraryAction.select);

    await open(sortable: false, showMedia: true);
    await tester.tap(find.byKey(const ValueKey('saved-library-actions')));
    await tester.pumpAndSettle();
    expect(find.text('Newest saved first'), findsNothing, reason: 'likes keep their own order');
    await tester.tap(find.widgetWithText(CheckedPopupMenuItem<SavedLibraryAction>, 'Media only'));
    await tester.pumpAndSettle();
    expect(action, SavedLibraryAction.mediaOnly);

    await open(sortable: false, showMedia: false);
    final button = tester.widget<PopupMenuButton<SavedLibraryAction>>(
      find.byKey(const ValueKey('saved-library-actions')),
    );
    expect(button.enabled, isFalse, reason: 'notes have nothing to filter or sort');
  });

  test('saved sorting preserves newest order and reverses for oldest', () {
    const items = ['new', 'middle', 'old'];
    expect(applySavedSort(items, SavedSort.newest), items);
    expect(
      applySavedSort(items, SavedSort.oldest),
      ['old', 'middle', 'new'],
    );
  });

  testWidgets('selection bar disables destructive actions with no selection', (
    tester,
  ) async {
    await tester.pumpWidget(
      _app(
        SavedSelectionBar(
          selectedCount: 0,
          allSelected: false,
          onClose: () {},
          onSelectAll: () {},
          onMove: () {},
          onDelete: () {},
        ),
      ),
    );

    expect(
      tester.widget<IconButton>(
        find.byKey(const ValueKey('saved-move-selected')),
      ).onPressed,
      isNull,
    );
    expect(
      tester.widget<IconButton>(
        find.byKey(const ValueKey('saved-delete-selected')),
      ).onPressed,
      isNull,
    );
  });

  test('folder reconciliation keeps a valid custom folder', () {
    final store = SavedViewStore();
    addTearDown(store.destroy);
    store.selectFolder('folder');
    store.reconcileFolders(
      [
        SavedTweetFolder(
          id: 'folder',
          name: 'Folder',
          position: 0,
          createdAt: DateTime.utc(2026),
          autoDownload: false,
        ),
      ],
      showUnfiled: true,
      showFavorites: true,
    );
    expect(store.state.folder, 'folder');
  });

  Future<TextEditingController> pumpSearch(WidgetTester tester, {double scale = 1, VoidCallback? onClear}) async {
    final controller = TextEditingController();
    final focusNode = FocusNode();
    addTearDown(controller.dispose);
    addTearDown(focusNode.dispose);
    await tester.pumpWidget(
      _app(
        MediaQuery(
          data: MediaQueryData(textScaler: TextScaler.linear(scale)),
          child: SavedSearchRow(
            controller: controller,
            focusNode: focusNode,
            onChanged: (_) {},
            onClear: onClear ?? controller.clear,
            filter: const SizedBox(width: kTweetTouchTarget, height: kTweetTouchTarget),
          ),
        ),
      ),
    );
    return controller;
  }

  testWidgets('the search field is round, on the shared inset surface, and says what it searches', (tester) async {
    await pumpSearch(tester);
    final context = tester.element(find.byType(TextField));
    final box = tester.widget<DecoratedBox>(
      find.ancestor(of: find.byType(TextField), matching: find.byType(DecoratedBox)).first,
    );
    final decoration = box.decoration as ShapeDecoration;
    expect(decoration.color, xLookInsetSurface(XLookTokens.of(context)));
    expect(decoration.shape, isA<StadiumBorder>());
    expect(find.text('Search saved'), findsOneWidget);
    expect(tester.getSize(find.byType(SavedSearchRow)).height, greaterThanOrEqualTo(44 + 2 * kTweetSpace1));
  });

  testWidgets('a clear button appears once something is typed', (tester) async {
    var cleared = 0;
    final controller = await pumpSearch(tester, onClear: () => cleared++);
    expect(find.byTooltip('Clear text'), findsNothing);
    await tester.enterText(find.byType(TextField), 'cats');
    await tester.pump();
    expect(controller.text, 'cats');
    await tester.tap(find.byTooltip('Clear text'));
    expect(cleared, 1);
  });

  testWidgets('the search field grows for large translated text', (tester) async {
    await pumpSearch(tester, scale: 2);
    expect(tester.takeException(), isNull);
    expect(tester.getSize(find.byType(SavedSearchRow)).height, greaterThan(44 + 2 * kTweetSpace1));
  });
}
