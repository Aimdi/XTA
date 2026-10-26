import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/profile/profile_chrome.dart';
import 'package:xta/profile/profile_note.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/utils/formatting_locale.dart';
import 'package:xta/utils/rich_text.dart';

import 'support/profile_harness.dart';

Finder _faceOf() => find.byType(ProfileFollowFace);

void main() {
  setUpAll(loadProfileFonts);

  group('profileCount', () {
    test('groups a count the language writes out, and abbreviates as before', () {
      expect(profileCount(1379, 'de'), '1.379');
      expect(profileCount(62646, 'de'), '62.646');
      expect(profileCount(1234567, 'de'), '1,23\u00a0Mio.');
      expect(profileCount(1379, 'ja'), '1,379');
      expect(profileCount(1379, 'en'), '1.38K');
      expect(profileCount(62646, 'en'), '62.6K');
      expect(profileCount(999, 'de'), '999');
      expect(profileCount(12345, verifiedFormattingLocale('eo')), '12.3K');
    });
  });

  group('tightenBlankLines', () {
    const base = TextStyle(fontFamily: 'Inter', fontSize: 15, height: 1.45);

    Future<double> bioHeight(WidgetTester tester, String text, {double scale = 1}) async {
      await tester.pumpWidget(
        MediaQuery(
          data: MediaQueryData(textScaler: TextScaler.linear(scale)),
          child: Directionality(
            textDirection: TextDirection.ltr,
            child: Align(
              alignment: Alignment.topLeft,
              child: SelectableText.rich(
                TextSpan(
                  style: base,
                  children: tightenBlankLines([TextSpan(text: text)], kProfileBioBlankLine),
                ),
                strutStyle: kProfileBioStrut,
              ),
            ),
          ),
        ),
      );
      return tester.getSize(find.byType(SelectableText)).height;
    }

    testWidgets('a blank line is a third of a line, scaled with the text', (tester) async {
      for (final scale in [1.0, 2.0]) {
        final lines = await bioHeight(tester, 'A\nB', scale: scale);
        final tightened = await bioHeight(tester, 'A\n\nB', scale: scale);
        expect(tightened - lines, moreOrLessEquals(6 * scale, epsilon: 0.5), reason: 'text ×$scale');
      }
    });

    test('keeps the text, so a copy still holds the blank lines', () {
      const text = 'Reads everything twice.\n\nNotes:\n\n\nEnd';
      final spans = tightenBlankLines([const TextSpan(text: text)], kProfileBioBlankLine);
      expect(TextSpan(children: spans).toPlainText(), text);
      expect(spans, hasLength(5));
      expect(tightenBlankLines([const TextSpan(text: 'One line\nTwo')], kProfileBioBlankLine), hasLength(1));
    });
  });

  group('the follow button', () {
    testWidgets('says Subscribed with a check, and opens the same menu', (tester) async {
      final semantics = tester.ensureSemantics();
      await pumpXProfile(tester, subscribed: true);
      expect(tester.getSemantics(_faceOf()), isSemantics(label: 'Subscribed', tooltip: 'Unsubscribe', isButton: true));
      semantics.dispose();
      expect(find.descendant(of: _faceOf(), matching: find.text('Subscribed')), findsOneWidget);
      expect(find.descendant(of: _faceOf(), matching: find.byIcon(Icons.check)), findsOneWidget);
      expect(find.descendant(of: _faceOf(), matching: find.byIcon(Icons.expand_more)), findsOneWidget);
      expect(tester.getSize(_faceOf()).height, kTweetTouchTarget);

      await tester.tap(_faceOf());
      await tester.pumpAndSettle();
      for (final item in ['Unsubscribe', 'Add to group', 'Hide from main feed']) {
        expect(find.text(item), findsOneWidget, reason: item);
      }
      await tester.tapAt(const Offset(4, 400));
      await tester.pumpAndSettle();
      await settleProfile(tester);
    });

    testWidgets('says Subscribe in the accent when not subscribed', (tester) async {
      await pumpXProfile(tester);
      final label = tester.widget<Text>(find.descendant(of: _faceOf(), matching: find.text('Subscribe')));
      final context = tester.element(_faceOf());
      expect(label.style?.color, tweetReadableAccentColor(context));
      expect(find.descendant(of: _faceOf(), matching: find.byIcon(Icons.add)), findsOneWidget);
      await settleProfile(tester);
    });

    testWidgets('shows an eye-off icon for an account hidden from the main feed', (tester) async {
      await pumpXProfile(tester, subscribed: true, inFeed: false);
      expect(find.descendant(of: _faceOf(), matching: find.byIcon(Icons.visibility_off_outlined)), findsOneWidget);
      expect(find.descendant(of: _faceOf(), matching: find.text('Subscribed')), findsOneWidget);
      await settleProfile(tester);
    });

    testWidgets('drops its label at 320 dp with text ×2 and keeps its tooltip', (tester) async {
      final semantics = tester.ensureSemantics();
      await pumpXProfile(tester, width: 320, scale: 2, subscribed: true);
      expect(find.descendant(of: _faceOf(), matching: find.byType(Text)), findsNothing);
      expect(find.byTooltip('Unsubscribe'), findsOneWidget);
      expect(tester.getSemantics(_faceOf()), isSemantics(label: 'Subscribed', tooltip: 'Unsubscribe', isButton: true));
      final face = tester.getRect(_faceOf());
      final settings = tester.getRect(find.byTooltip('Filters'));
      expect(settings.right, lessThanOrEqualTo(face.left), reason: 'separate targets');
      expect(tester.takeException(), isNull);
      semantics.dispose();
      await settleProfile(tester);
    });
  });

  group('the header', () {
    testWidgets('draws no ring around settings and follow', (tester) async {
      await pumpXProfile(tester, subscribed: true);
      expect(
        find.descendant(
          of: find.byType(ProfileActionCluster),
          matching: find.byWidgetPredicate(
            (widget) => widget is DecoratedBox && (widget.decoration as BoxDecoration?)?.border != null,
          ),
        ),
        findsNothing,
      );
      await settleProfile(tester);
    });

    testWidgets('sets the bio\'s blank lines tight', (tester) async {
      await pumpXProfile(tester);
      final bio = find.byWidgetPredicate(
        (widget) =>
            widget is SelectableText && (widget.textSpan?.toPlainText().contains('Reads everything twice.') ?? false),
      );
      // Three lines of 15 × 1.45 and two blank lines of 6.
      expect(tester.getSize(bio).height, moreOrLessEquals(3 * 15 * 1.45 + 2 * 6, epsilon: 2));
      await settleProfile(tester);
    });

    testWidgets('puts the link and the join date on one row', (tester) async {
      await pumpXProfile(tester);
      final link = tester.getCenter(find.text('maralind.substack.com'));
      final joined = tester.getCenter(find.text('Joined August 2024'));
      expect(joined.dy, moreOrLessEquals(link.dy, epsilon: 1));
      expect(joined.dx, greaterThan(link.dx));
      expect(find.textContaining('1.38K Following', findRichText: true), findsOneWidget);
      await settleProfile(tester);
    });

    testWidgets('writes German counts in full and the date without Twitter', (tester) async {
      await pumpXProfile(tester, locale: const Locale('de'));
      expect(find.textContaining('1.379 Folgt', findRichText: true), findsOneWidget);
      expect(find.textContaining('62.646 Follower', findRichText: true), findsOneWidget);
      expect(find.text('Seit August 2024'), findsOneWidget);
      await settleProfile(tester);
    });

    testWidgets('lays the note across the width between hairlines', (tester) async {
      await pumpXProfile(tester);
      final note = tester.getRect(find.byType(ProfileNoteCard));
      expect(note.width, 390);
      expect(
        find.descendant(of: find.byType(ProfileNoteCard), matching: find.text('Add private note')),
        findsOneWidget,
      );
      final dividers = find.byType(Divider).evaluate().map((e) => tester.getRect(find.byWidget(e.widget)));
      expect(dividers.where((d) => (d.bottom - note.top).abs() < 1.5), isNotEmpty, reason: 'a hairline above');
      expect(dividers.where((d) => (d.top - note.bottom).abs() < 1.5), isNotEmpty, reason: 'a hairline below');
      await settleProfile(tester);
    });
  });

  group('tabs and filters', () {
    testWidgets('are names only, share the width, and the second says Replies', (tester) async {
      await pumpXProfile(tester);
      final tabBar = tester.widget<TabBar>(find.byType(TabBar));
      expect(tabBar.isScrollable, isFalse);
      expect(find.descendant(of: find.byType(TabBar), matching: find.byType(Icon)), findsNothing);
      for (final name in ['Posts', 'Replies', 'Media', 'Saved']) {
        expect(
          find.descendant(of: find.byType(TabBar), matching: find.text(name)),
          findsOneWidget,
          reason: name,
        );
      }
      for (final word in ['All', 'Posts', 'Retweets']) {
        expect(
          find.descendant(of: find.byType(TextButton), matching: find.text(word)),
          findsWidgets,
          reason: word,
        );
      }
      expect(find.byType(ChoiceChip), findsNothing);
      await settleProfile(tester);
    });

    testWidgets('scroll from the start when the names do not fit', (tester) async {
      await pumpXProfile(tester, locale: const Locale('de'), width: 320, scale: 2);
      // With text ×2 the header is taller than the screen: scroll the tabs up.
      await tester.drag(find.text('Mara Lindqvist'), const Offset(0, -900));
      for (var i = 0; i < 5; i++) {
        await tester.pump(const Duration(milliseconds: 100));
      }
      final tabBar = tester.widget<TabBar>(find.byType(TabBar));
      expect(tabBar.isScrollable, isTrue);
      expect(tabBar.tabAlignment, TabAlignment.start);
      expect(tester.takeException(), isNull);
      await settleProfile(tester);
    });
  });
}
