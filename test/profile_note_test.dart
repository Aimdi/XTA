import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/profile/profile_note.dart';
import 'package:xta/ui/x_look_theme.dart';

Widget _app(Widget child, {Locale locale = const Locale('de')}) {
  return MaterialApp(
    theme: xLookLightsOutTheme(null),
    locale: locale,
    localizationsDelegates: const [
      L10n.delegate,
      GlobalMaterialLocalizations.delegate,
      GlobalWidgetsLocalizations.delegate,
      GlobalCupertinoLocalizations.delegate,
    ],
    supportedLocales: L10n.delegate.supportedLocales,
    home: Scaffold(body: child),
  );
}

Future<void> _pumpNote(
  WidgetTester tester, {
  String userId = 'u1',
  String? initial,
  ProfileNoteSaver? saver,
  Locale locale = const Locale('de'),
}) async {
  await tester.pumpWidget(
    _app(
      ProfileNoteCard(
        userId: userId,
        loader: (_) async => initial == null
            ? null
            : ProfileNote(id: userId, note: initial, updatedAt: DateTime(2026)),
        saver: saver ?? (_, __) async {},
      ),
      locale: locale,
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  setUpAll(() async {
    // Whether the label fits one line is measured from real glyphs: the test
    // font draws every one 1 em wide.
    for (final weight in ['Regular', 'Medium', 'SemiBold', 'Bold']) {
      await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-$weight.ttf'))).load();
    }
  });

  testWidgets('the empty row asks for a note in full German and stays one flat line', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(360, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await _pumpNote(tester);

    expect(find.text('Private Notiz hinzufügen'), findsOneWidget);
    expect(tester.takeException(), isNull);

    final size = tester.getSize(find.byType(ProfileNoteCard));
    expect(size.width, 360, reason: 'the row spans the header');
    expect(size.height, greaterThanOrEqualTo(48));
    expect(
      size.height,
      lessThan(56),
      reason: 'the profile header row must stay one line',
    );
    expect(
      find.descendant(
        of: find.byType(ProfileNoteCard),
        matching: find.byWidgetPredicate(
          (widget) => widget is Material && widget.shape != null,
        ),
      ),
      findsNothing,
      reason: 'flat: no box or border of its own',
    );

    final paragraph = tester.renderObject<RenderParagraph>(
      find.text('Private Notiz hinzufügen'),
    );
    expect(paragraph.didExceedMaxLines, isFalse);
  });

  testWidgets('a saved note replaces the label on the row', (tester) async {
    await _pumpNote(tester, initial: 'met them at 37c3');

    expect(find.text('met them at 37c3'), findsOneWidget);
    expect(find.text('Private Notiz hinzufügen'), findsNothing);
  });

  testWidgets('tapping the row opens a sheet that actually saves', (
    tester,
  ) async {
    String? saved;

    await _pumpNote(
      tester,
      saver: (id, note) async {
        expect(id, 'u1');
        saved = note;
      },
    );

    await tester.tap(find.byKey(const Key('profile_note_row')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('profile_note_field')), findsOneWidget);
    expect(find.byKey(const Key('profile_note_save')), findsOneWidget);
    expect(find.text('Private Notiz'), findsWidgets);

    await tester.enterText(
      find.byKey(const Key('profile_note_field')),
      '  hallo  ',
    );
    await tester.tap(find.byKey(const Key('profile_note_save')));
    await tester.pumpAndSettle();

    expect(saved, 'hallo');
    expect(find.byKey(const Key('profile_note_field')), findsNothing);
    expect(find.text('hallo'), findsOneWidget);
  });

  testWidgets('clearing the sheet deletes the note and restores the label', (
    tester,
  ) async {
    String? saved;

    await _pumpNote(
      tester,
      initial: 'old note',
      saver: (_, note) async => saved = note,
    );

    await tester.tap(find.byKey(const Key('profile_note_row')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('profile_note_field')), '   ');
    await tester.tap(find.byKey(const Key('profile_note_save')));
    await tester.pumpAndSettle();

    expect(saved, '');
    expect(find.text('Private Notiz hinzufügen'), findsOneWidget);
    expect(find.text('old note'), findsNothing);
  });
}
