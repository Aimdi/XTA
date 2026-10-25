import 'package:flutter_test/flutter_test.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/group/feed_search_query.dart';

UserSubscription _user(String screenName) => UserSubscription(
  id: 'id-$screenName',
  screenName: screenName,
  name: screenName,
  profileImageUrlHttps: null,
  verified: false,
  createdAt: DateTime.utc(2026),
  inFeed: true,
);

String _query(List<String> terms, {bool replies = true, bool reposts = true}) =>
    buildFeedSearchQuery(terms, includeReplies: replies, includeRetweets: reposts);

List<List<String>> _pack(List<String> terms, {bool replies = true, bool reposts = true}) =>
    packFeedChunks(terms, (term) => term, includeReplies: replies, includeRetweets: reposts);

void main() {
  group('search terms', () {
    test('an account is searched by its name', () {
      expect(feedSearchTermFor(_user('nasa')), 'from:nasa');
    });

    test('a saved search keeps its operators in brackets instead of quotes', () {
      expect(feedSearchTermFor(SearchSubscription(id: 'list:123', createdAt: DateTime.utc(2026))), '(list:123)');
      expect(savedSearchTerm('from:nasa OR from:esa'), '(from:nasa OR from:esa)');
    });
  });

  group('buildFeedSearchQuery', () {
    test('brackets the members so the filters cover all of them', () {
      expect(
        _query(['from:a', 'from:b'], replies: false, reposts: false),
        '(from:a OR from:b) -filter:replies -filter:retweets',
      );
    });

    test('asks for native reposts when the feed shows reposts', () {
      expect(_query(['from:a']), '(from:a) include:nativeretweets');
      expect(_query(['from:a'], replies: false), '(from:a) -filter:replies include:nativeretweets');
    });

    test('mixes accounts and saved searches', () {
      expect(_query(['from:a', savedSearchTerm('list:1')]), '(from:a OR (list:1)) include:nativeretweets');
    });

    test('leaves the brackets out when there is no member', () {
      expect(_query(const [], reposts: false), '-filter:retweets');
    });
  });

  group('packFeedChunks', () {
    final terms = [for (var i = 0; i < 100; i++) userSearchTerm('account${'$i'.padLeft(3, '0')}')];

    for (final (replies, reposts) in [(true, true), (true, false), (false, true), (false, false)]) {
      test('fills every search as far as X reads it (replies $replies, reposts $reposts)', () {
        final chunks = _pack(terms, replies: replies, reposts: reposts);
        expect(chunks.expand((chunk) => chunk), terms);
        for (final (index, chunk) in chunks.indexed) {
          expect(_query(chunk, replies: replies, reposts: reposts).length, lessThanOrEqualTo(feedSearchQueryLimit));
          if (index < chunks.length - 1) {
            final withNext = [...chunk, chunks[index + 1].first];
            expect(_query(withNext, replies: replies, reposts: reposts).length, greaterThan(feedSearchQueryLimit));
          }
        }
      });
    }

    test('puts more typical accounts in one search than the old 16', () {
      expect(_pack(terms).first.length, greaterThan(16));
    });

    test('a new member only changes the last chunk', () {
      final before = _pack(terms);
      final after = _pack([...terms, userSearchTerm('newcomer')]);
      expect(after.sublist(0, before.length - 1), before.sublist(0, before.length - 1));
    });

    test('a member too long to share a search gets its own chunk', () {
      final long = savedSearchTerm('x' * 600);
      expect(_pack(['from:a', long, 'from:b']), [
        ['from:a'],
        [long],
        ['from:b'],
      ]);
    });
  });
}
