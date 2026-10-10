import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/poll.dart';
import 'package:xta/tweet/poll_results.dart';
import 'package:xta/ui/x_look_theme.dart';

Map<String, dynamic> _card(Map<String, String> values) => {
      'binding_values': {for (final entry in values.entries) entry.key: {'string_value': entry.value}}
    };

void main() {
  group('reading a poll', () {
    test('shares add up and the leader is the highest count', () {
      final poll = TweetPoll.fromCard(
          _card({
            'choice1_label': 'Yes',
            'choice1_count': '30',
            'choice2_label': 'No',
            'choice2_count': '10',
            'end_datetime_utc': '2026-01-01T12:00:00Z',
          }),
          2)!;

      expect(poll.total, 40);
      expect(poll.choices.map((c) => c.label), ['Yes', 'No']);
      expect(poll.choices.first.share, closeTo(0.75, 0.001));
      expect(poll.choices.last.share, closeTo(0.25, 0.001));
      expect(poll.leadingCount, 30);
      expect(poll.endsAt, DateTime.parse('2026-01-01T12:00:00Z'));
    });

    test('a poll nobody has voted in divides by nothing', () {
      final poll = TweetPoll.fromCard(
          _card({
            'choice1_label': 'Yes',
            'choice1_count': '0',
            'choice2_label': 'No',
            'choice2_count': '0',
          }),
          2)!;

      expect(poll.total, 0);
      expect(poll.choices.every((c) => c.share == 0), isTrue);
      expect(poll.leadingCount, 0, reason: 'with no votes no bar is the leader');
    });

    test('four choices are all read', () {
      final poll = TweetPoll.fromCard(
          _card({
            for (var i = 1; i <= 4; i++) ...{'choice${i}_label': 'Option $i', 'choice${i}_count': '$i'}
          }),
          4)!;

      expect(poll.choices, hasLength(4));
      expect(poll.total, 10);
      expect(poll.leadingCount, 4);
    });
  });

  group('a payload that does not fit', () {
    test('a missing choice gives up rather than throwing mid-timeline', () {
      final card = _card({'choice1_label': 'Yes', 'choice1_count': '3'});

      expect(TweetPoll.fromCard(card, 2), isNull);
    });

    test('binding values of the wrong shape give up', () {
      expect(TweetPoll.fromCard({'binding_values': 'nope'}, 2), isNull);
      expect(TweetPoll.fromCard(const {}, 2), isNull);
    });

    test('an unparseable count is no votes, not a crash', () {
      final poll = TweetPoll.fromCard(
          _card({
            'choice1_label': 'Yes',
            'choice1_count': 'lots',
            'choice2_label': 'No',
            'choice2_count': '4',
          }),
          2)!;

      expect(poll.choices.first.count, 0);
      expect(poll.total, 4);
    });

    test('an unparseable end date leaves the poll without one', () {
      final poll = TweetPoll.fromCard(
          _card({
            'choice1_label': 'Yes',
            'choice1_count': '1',
            'choice2_label': 'No',
            'choice2_count': '1',
            'end_datetime_utc': 'soon',
          }),
          2)!;

      expect(poll.endsAt, isNull);
    });
  });

  group('who leads', () {
    test('the most voted option leads, ties included', () {
      final poll = TweetPoll.fromCard(
          _card({
            'choice1_label': 'Yes',
            'choice1_count': '3',
            'choice2_label': 'No',
            'choice2_count': '3',
            'choice3_label': 'Maybe',
            'choice3_count': '1',
          }),
          3)!;

      expect(poll.choices.map(poll.leads), [true, true, false]);
    });

    test('nothing leads a poll nobody voted in', () {
      final poll = TweetPoll.fromCard(
          _card({'choice1_label': 'Yes', 'choice1_count': '0', 'choice2_label': 'No', 'choice2_count': '0'}), 2)!;

      expect(poll.choices.any(poll.leads), isFalse,
          reason: 'every count equals the zero leading count, which used to make every option bold');
    });
  });

  group('drawing the results', () {
    TweetPoll poll(String yes, String no, {String? ends}) => TweetPoll.fromCard(
        _card({
          'choice1_label': 'Yes',
          'choice1_count': yes,
          'choice2_label': 'Absolutely not, under no circumstances whatsoever',
          'choice2_count': no,
          'end_datetime_utc': ?ends,
        }),
        2)!;

    Future<void> pump(WidgetTester tester, TweetPoll poll,
        {double width = 411, double textScale = 1, bool dark = false}) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = Size(width, 800);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetPhysicalSize);
      await tester.pumpWidget(MaterialApp(
        theme: dark ? xLookLightsOutTheme(null) : xLookLightTheme(null),
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: const [Locale('en')],
        home: MediaQuery(
          data: MediaQueryData(size: Size(width, 800), textScaler: TextScaler.linear(textScale)),
          child: Scaffold(body: SingleChildScrollView(child: TweetPollResults(poll: poll))),
        ),
      ));
      await tester.pumpAndSettle();
    }

    FontWeight? weightOf(WidgetTester tester, String text) => tester.widget<Text>(find.text(text)).style?.fontWeight;

    double fillOf(WidgetTester tester, int option) =>
        tester.widgetList<FractionallySizedBox>(find.byType(FractionallySizedBox)).elementAt(option).widthFactor!;

    testWidgets('fills each bar to its share and picks out the leader', (tester) async {
      await pump(tester, poll('5', '3', ends: '2020-01-01T00:00:00Z'));

      expect(find.text('62.5%'), findsOneWidget);
      expect(find.text('37.5%'), findsOneWidget);
      expect(fillOf(tester, 0), closeTo(0.625, 0.001));
      expect(fillOf(tester, 1), closeTo(0.375, 0.001));
      expect(weightOf(tester, 'Yes'), FontWeight.w700);
      expect(weightOf(tester, '62.5%'), FontWeight.w700);
      expect(weightOf(tester, '37.5%'), FontWeight.w400);
      expect(find.textContaining(RegExp(r'^8 votes · Ended ')), findsOneWidget);
    });

    testWidgets('shares sit at the right end of their bars', (tester) async {
      await pump(tester, poll('1', '0'));

      final bars = find.byType(PollOptionBar);
      for (final (index, share) in [(0, '100%'), (1, '0%')]) {
        final bar = tester.getRect(bars.at(index));
        final text = tester.getRect(find.descendant(of: bars.at(index), matching: find.text(share)));
        expect(bar.right - text.right, closeTo(12, 0.5), reason: '$share is right-aligned in its bar');
      }
    });

    testWidgets('a poll with no votes has no leader', (tester) async {
      await pump(tester, poll('0', '0'));

      expect(weightOf(tester, 'Yes'), FontWeight.w400);
      expect(find.text('0%'), findsNWidgets(2));
      expect(find.textContaining('No votes'), findsOneWidget);
    });

    for (final dark in [false, true]) {
      testWidgets('fits 320dp at twice the text size (${dark ? 'dark' : 'light'})', (tester) async {
        await pump(tester, poll('120', '7'), width: 320, textScale: 2, dark: dark);

        expect(tester.takeException(), isNull, reason: 'no overflow');
        for (final bar in tester.widgetList<PollOptionBar>(find.byType(PollOptionBar))) {
          final barRect = tester.getRect(find.byWidget(bar));
          final label = tester.getRect(find.text(bar.label));
          expect(barRect.top <= label.top && label.bottom <= barRect.bottom, isTrue,
              reason: 'the bar grows with the text instead of clipping "${bar.label}"');
          expect(barRect.right, lessThanOrEqualTo(320));
        }
      });
    }
  });
}
