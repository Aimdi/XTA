import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/endpoints.dart';

import 'live/support/x_query_id_record.dart';

const _shipped = {
  'TweetDetail': XEndpoint(
    name: 'TweetDetail',
    operation: 'TweetDetail',
    queryId: 'shippedTweetDetail01',
    host: 'x.com',
  ),
  'SearchTimeline': XEndpoint(
    name: 'SearchTimeline',
    operation: 'SearchTimeline',
    queryId: 'shippedSearchTimeline1',
    host: 'x.com',
  ),
};

RecordVersion _version(String day, Map<String, String> ids) => (day: DateTime.utc(2026, 10, int.parse(day)), ids: ids);

void main() {
  test("the record's ids are read by operation; entries without one are left out", () {
    final raw = {
      'graphql': {
        'TweetDetail': {'queryId': 'recordTweetDetail0001', 'features': <String, bool>{}},
        'SearchTimeline': {'features': <String, bool>{}},
        'Malformed': {'queryId': 'not/an/id'},
        'NotAnEntry': 'TweetDetail',
      },
    };

    expect(recordedQueryIds(raw), {'TweetDetail': 'recordTweetDetail0001'});
    expect(recordedQueryIds({'graphql': []}), isEmpty);
    expect(recordedQueryIds(null), isEmpty);
  });

  test('a published id replaces a shipped one; an unknown endpoint or a malformed id changes nothing', () {
    final inForce = idsInForce(_shipped, {
      'TweetDetail': 'publishedTweetDetail1',
      'SearchTimeline': '../../evil',
      'UserTweets': 'publishedUserTweets01',
    });

    expect(inForce, {'TweetDetail': 'publishedTweetDetail1', 'SearchTimeline': 'shippedSearchTimeline1'});
  });

  test('each id in force names who applies it', () {
    final readers = readersOfIds(_shipped, {
      'shipped': const {},
      'current': const {'TweetDetail': 'shippedTweetDetail01'},
      'older': const {'TweetDetail': 'olderTweetDetail00001'},
    });

    expect(readers, {
      'TweetDetail': {
        'shippedTweetDetail01': ['shipped', 'current'],
        'olderTweetDetail00001': ['older'],
      },
      'SearchTimeline': {
        'shippedSearchTimeline1': ['shipped', 'current', 'older'],
      },
    });
  });

  group('recordedAge', () {
    final today = DateTime.utc(2026, 10, 29);
    final versions = [
      _version('28', {'TweetDetail': 'newest'}),
      _version('20', {'TweetDetail': 'older'}),
      _version('9', {'TweetDetail': 'oldest'}),
    ];

    test('is current when the newest version has the id', () {
      expect(recordedAge('TweetDetail', 'newest', versions, today: today), isA<CurrentInRecord>());
    });

    test('counts the days since the newest version that has it', () {
      expect(
        recordedAge('TweetDetail', 'older', versions, today: today),
        isA<DroppedFromRecord>().having((age) => age.days, 'days', 9),
      );
    });

    test('is older than the record when no version read has it', () {
      expect(
        recordedAge('TweetDetail', 'ancient', versions, today: today),
        isA<OlderThanRecord>().having((age) => age.since, 'since', DateTime.utc(2026, 10, 9)),
      );
      expect(recordedAge('TweetDetail', 'newest', const [], today: today), isNull);
    });
  });

  test('the published files must match: a differing entry is named', () {
    expect(publishedDifferences({'TweetDetail': 'a'}, {'TweetDetail': 'a'}), isEmpty);
    expect(publishedDifferences({'TweetDetail': 'a', 'UserMedia': 'b'}, {'TweetDetail': 'c', 'HomeTimeline': 'd'}), [
      'HomeTimeline',
      'TweetDetail',
      'UserMedia',
    ]);
  });
}
