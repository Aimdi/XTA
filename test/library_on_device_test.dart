import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/saved/library_on_device.dart';
import 'package:xta/saved/saved_header.dart';
import 'package:xta/saved/saved_tab_order.dart';
import 'package:xta/ui/empty_pane.dart';

Widget _wrap(Widget child) => MaterialApp(
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
  test('empty kind follows search, likes, all, then folder', () {
    expect(
      savedLibraryEmptyKind(query: 'cats', filter: savedTabAll),
      SavedLibraryEmptyKind.search,
    );
    expect(
      savedLibraryEmptyKind(query: '', filter: savedTabFavorites),
      SavedLibraryEmptyKind.likes,
    );
    expect(
      savedLibraryEmptyKind(query: '', filter: savedTabAll),
      SavedLibraryEmptyKind.saved,
    );
    expect(
      savedLibraryEmptyKind(query: '', filter: savedTabNotes),
      SavedLibraryEmptyKind.notes,
    );
    expect(
      savedLibraryEmptyKind(query: '', filter: 'folder-1'),
      SavedLibraryEmptyKind.folder,
    );
  });

  testWidgets('likes empty state says hearts stay on this device', (
    tester,
  ) async {
    await tester.pumpWidget(
      _wrap(const SavedLibraryEmpty(kind: SavedLibraryEmptyKind.likes)),
    );
    await tester.pumpAndSettle();

    expect(find.byType(EmptyPane), findsOneWidget);
    expect(find.textContaining('this device'), findsOneWidget);
  });

  testWidgets('saves empty state says bookmarks stay on this device', (
    tester,
  ) async {
    await tester.pumpWidget(
      _wrap(const SavedLibraryEmpty(kind: SavedLibraryEmptyKind.saved)),
    );
    await tester.pumpAndSettle();

    expect(find.textContaining('this device'), findsOneWidget);
  });

  testWidgets('the lock beside the title says what stays on this device, by which chip is open', (tester) async {
    Future<void> lockFor(String filter) async {
      await tester.pumpWidget(
        _wrap(
          Builder(
            builder: (context) => SavedTitleRow(
              title: 'Saved',
              notice: savedOnDeviceNotice(L10n.of(context), filter),
              menu: const SizedBox.shrink(),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
    }

    await lockFor(savedTabFavorites);
    expect(find.byTooltip('Likes stay on this device. Nothing is sent anywhere.'), findsOneWidget);
    await lockFor(savedTabAll);
    expect(find.byTooltip('Saves stay on this device. Nothing is sent anywhere.'), findsOneWidget);
    await lockFor(savedTabNotes);
    final notes = find.byWidgetPredicate((w) => w is Tooltip && (w.message ?? '').contains('Backup and Nextcloud'));
    expect(notes, findsOneWidget);

    await tester.tap(find.byIcon(Icons.lock_outline));
    await tester.pump();
    expect(find.descendant(of: find.byType(SnackBar), matching: find.textContaining('Backup and Nextcloud')), findsOneWidget);
  });

  testWidgets('notes empty state offers a way to write', (tester) async {
    var wrote = false;
    await tester.pumpWidget(
      _wrap(
        SavedLibraryEmpty(
          kind: SavedLibraryEmptyKind.notes,
          onWriteNote: () => wrote = true,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.textContaining('No notes yet'), findsOneWidget);
    await tester.tap(find.text('Note'));
    expect(wrote, isTrue);
  });
}
