import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/saved/saved_note_editor.dart';
import 'package:xta/ui/x_look_theme.dart';

const _reviewRender = ValueKey('saved-note-review-render');

Finder _saveButton() => find.ancestor(
  of: find.text('Save'),
  matching: find.byWidgetPredicate((widget) => widget is FilledButton),
);

Future<void> openEditor(
  WidgetTester tester, {
  String? note = 'Original note',
  required Future<void> Function(String?) onSave,
  double keyboard = 0,
  double textScale = 1,
  bool dark = false,
}) async {
  await tester.pumpWidget(RepaintBoundary(
    key: _reviewRender,
    child: MaterialApp(
    debugShowCheckedModeBanner: false,
    theme: dark ? xLookLightsOutTheme(null) : xLookLightTheme(null),
    localizationsDelegates: const [
      L10n.delegate,
      GlobalMaterialLocalizations.delegate,
      GlobalWidgetsLocalizations.delegate,
      GlobalCupertinoLocalizations.delegate,
    ],
    supportedLocales: const [Locale('en')],
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(context).copyWith(
        viewInsets: EdgeInsets.only(bottom: keyboard),
        textScaler: TextScaler.linear(textScale),
        disableAnimations: true,
      ),
      child: child!,
    ),
    home: Scaffold(body: Builder(builder: (context) => TextButton(
      onPressed: () => openSavedNoteEditor(context, note: note, onSave: onSave),
      child: const Text('Open editor'),
    ))),
    ),
  ));
  await tester.tap(find.text('Open editor'));
  await tester.pumpAndSettle();
}

void main() {
  setUpAll(() async {
    autoUpdateGoldenFiles = true;
    await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-Regular.ttf'))).load();
    await (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
  });

  testWidgets('canceling a changed note leaves the stored note untouched', (tester) async {
    var writes = 0;
    await openEditor(tester, onSave: (_) async { writes++; });
    await tester.enterText(find.byType(TextField), 'Discard this');
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Close'));
    await tester.pumpAndSettle();
    expect(find.text('Discard changes?'), findsOneWidget);
    await tester.tap(find.text('Discard'));
    await tester.pumpAndSettle();
    expect(find.byType(TextField), findsNothing);
    expect(writes, 0);
  });

  testWidgets('saving stays reachable above the keyboard and removes a cleared note', (tester) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(320, 844);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetPhysicalSize);
    String? saved = 'Original note';
    await openEditor(tester, keyboard: 300, textScale: 1.6, dark: true, onSave: (value) async { saved = value; });
    await tester.enterText(find.byType(TextField), 'Ideas from this stream:\n\nCompare the new illustrations with the earlier sketches.');
    await tester.pumpAndSettle();
    expect(tester.getBottomRight(_saveButton()).dy, lessThanOrEqualTo(844 - 300));
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(_reviewRender),
      matchesGoldenFile('../review-artifacts/renders/archive-note-keyboard-large.png'),
    );
    await tester.enterText(find.byType(TextField), '');
    await tester.pumpAndSettle();
    final save = _saveButton();
    expect(tester.getBottomRight(save).dy, lessThanOrEqualTo(844 - 300));
    expect(tester.takeException(), isNull);
    await tester.tap(save);
    await tester.pumpAndSettle();
    expect(saved, isNull);
    expect(find.byType(TextField), findsNothing);
  });

  testWidgets('write failure leaves editable draft and save can be retried', (tester) async {
    var attempts = 0;
    await openEditor(tester, onSave: (_) async {
      if (attempts++ == 0) throw StateError('disk full');
    });
    await tester.enterText(find.byType(TextField), 'Keep my changes');
    await tester.pumpAndSettle();
    await tester.tap(_saveButton());
    await tester.pumpAndSettle();
    expect(find.textContaining('Could not save your note.'), findsOneWidget);
    expect(tester.widget<TextField>(find.byType(TextField)).controller!.text, 'Keep my changes');
    await tester.tap(_saveButton());
    await tester.pumpAndSettle();
    expect(attempts, 2);
    expect(find.byType(TextField), findsNothing);
  });
}
