import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/saved/saved_chrome.dart';
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
      expect(store.state.mediaOnly, isTrue);
      expect(store.state.query, 'flutter');

      store.toggleSearch();
      expect(store.state.searching, isFalse);
      expect(store.state.query, isEmpty);

      store.selectFolder('deleted');
      store.reconcileFolders(const [], showUnfiled: false, showFavorites: true);
      expect(store.state.folder, savedTabAll);
    },
  );

  testWidgets(
    'Saved controls expose folder and media state in one compact row',
    (tester) async {
      String? selected;
      var mediaToggles = 0;
      await tester.pumpWidget(
        _app(
          SavedControlBar(
            selectedFolder: 'reading',
            mediaOnly: true,
            likesByGroup: false,
            folders: const [
              SavedFolderOption(
                value: savedTabAll,
                label: 'All',
                icon: Icons.bookmarks_outlined,
              ),
              SavedFolderOption(
                value: 'reading',
                label: 'Long-form reading',
                icon: Icons.folder_outlined,
                editable: true,
              ),
            ],
            onFolderSelected: (value) => selected = value,
            onFolderLongPress: (_) {},
            onMediaToggle: () => mediaToggles++,
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(
        tester.getSize(find.byType(SavedControlBar)).height,
        kSavedControlBarHeight,
      );
      expect(find.text('Long-form reading'), findsOneWidget);
      expect(find.text('Media'), findsOneWidget);
      expect(
        tester
            .getSize(find.widgetWithText(SavedChoiceChip, 'Long-form reading'))
            .height,
        greaterThanOrEqualTo(kTweetTouchTarget),
      );

      await tester.tap(find.text('All'));
      await tester.tap(find.text('Media'));
      expect(selected, savedTabAll);
      expect(mediaToggles, 1);
    },
  );

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

  testWidgets('Saved search grows for large translated text', (tester) async {
    final focusNode = FocusNode();
    addTearDown(focusNode.dispose);
    await tester.pumpWidget(
      _app(
        MediaQuery(
          data: const MediaQueryData(textScaler: TextScaler.linear(2)),
          child: SavedSearchField(
            focusNode: focusNode,
            onChanged: (_) {},
            onClose: () {},
          ),
        ),
      ),
    );

    expect(tester.takeException(), isNull);
    expect(
      tester.getSize(find.byType(TextField)).height,
      kSavedLargeTextSearchHeight,
    );
  });

  testWidgets('Saved search uses the shared inset surface', (tester) async {
    final focusNode = FocusNode();
    addTearDown(focusNode.dispose);
    await tester.pumpWidget(
      _app(
        SavedSearchField(
          focusNode: focusNode,
          onChanged: (_) {},
          onClose: () {},
        ),
      ),
    );

    final context = tester.element(find.byType(TextField));
    final tokens = XLookTokens.of(context);
    final field = tester.widget<TextField>(find.byType(TextField));
    expect(field.decoration?.fillColor, xLookInsetSurface(tokens));
  });
}
