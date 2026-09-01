import 'package:flutter_test/flutter_test.dart';
import 'package:xta/tweet/poll.dart';

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
}
