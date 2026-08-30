import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/saved/library_on_device.dart';
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

  testWidgets('the notice names likes or saves by which chip is open', (
    tester,
  ) async {
    await tester.pumpWidget(
      _wrap(const SavedLibraryOnDeviceNotice(filter: savedTabFavorites)),
    );
    await tester.pumpAndSettle();
    expect(
      find.text('Likes stay on this device. Nothing is sent anywhere.'),
      findsOneWidget,
    );

    await tester.pumpWidget(
      _wrap(const SavedLibraryOnDeviceNotice(filter: savedTabAll)),
    );
    await tester.pumpAndSettle();
    expect(
      find.text('Saves stay on this device. Nothing is sent anywhere.'),
      findsOneWidget,
    );

    await tester.pumpWidget(
      _wrap(const SavedLibraryOnDeviceNotice(filter: savedTabNotes)),
    );
    await tester.pumpAndSettle();
    expect(find.textContaining('Backup and Nextcloud'), findsOneWidget);
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
