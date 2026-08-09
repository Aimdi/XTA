import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/group/feed_first_page_action.dart';
import 'package:xta/group/feed_read_position.dart';

TweetChain _chain(String id, DateTime? at) {
  final tweet = TweetWithCard()
    ..idStr = id
    ..createdAt = at;
  return TweetChain(id: id, tweets: [tweet], isPinned: false);
}

DateTime _day(int day) => DateTime.utc(2026, 1, day);

FeedReadPosition _seen(String id, int day) => FeedReadPosition(chainId: id, chainCreatedAt: _day(day));

void main() {
  group('what a finalized first page should do', () {
    test('unread posts above the last-read one restore the reader to it', () {
      final action = firstPageAction(
        chains: [_chain('c', _day(5)), _chain('b', _day(3)), _chain('a', _day(1))],
        lastSeen: _seen('b', 3),
        caughtUpAlreadyEvaluated: false,
        sessionOffset: null,
        atTop: true,
      );

      expect(action, isA<RestoreToBoundary>());
      expect((action as RestoreToBoundary).index, 1);
    });

    test('a restored session offset wins, so the reader keeps their place', () {
      final action = firstPageAction(
        chains: [_chain('c', _day(5)), _chain('b', _day(3))],
        lastSeen: _seen('b', 3),
        caughtUpAlreadyEvaluated: false,
        sessionOffset: 900,
        atTop: false,
      );

      expect(action, isNot(isA<RestoreToBoundary>()));
    });

    test('nothing new above the last-read post records instead of restoring', () {
      final action = firstPageAction(
        chains: [_chain('b', _day(3)), _chain('a', _day(1))],
        lastSeen: _seen('b', 3),
        caughtUpAlreadyEvaluated: false,
        sessionOffset: null,
        atTop: true,
      );

      expect(action, isA<RecordPosition>());
      expect((action as RecordPosition).chain.id, 'b');
    });

    test('a reader who has never read this feed records the top of it', () {
      final action = firstPageAction(
        chains: [_chain('c', _day(5))],
        lastSeen: null,
        caughtUpAlreadyEvaluated: false,
        sessionOffset: null,
        atTop: true,
      );

      expect(action, isA<RecordPosition>());
    });

    // The bug this guards: an app-bar refresh fired mid-scroll used to mark
    // everything above the reader as read.
    test('a later page scrolled away from the top records nothing', () {
      final action = firstPageAction(
        chains: [_chain('c', _day(5))],
        lastSeen: _seen('a', 1),
        caughtUpAlreadyEvaluated: true,
        sessionOffset: null,
        atTop: false,
      );

      expect(action, isA<DoNothing>());
    });

    test('the caught-up boundary is decided once, not on every refresh', () {
      final action = firstPageAction(
        chains: [_chain('c', _day(5)), _chain('b', _day(3))],
        lastSeen: _seen('b', 3),
        caughtUpAlreadyEvaluated: true,
        sessionOffset: null,
        atTop: true,
      );

      expect(action, isA<RecordPosition>());
    });

    test('an empty page has nothing to record', () {
      final action = firstPageAction(
        chains: const [],
        lastSeen: null,
        caughtUpAlreadyEvaluated: false,
        sessionOffset: null,
        atTop: true,
      );

      expect(action, isA<DoNothing>());
    });

    test('a page of undated posts has nothing recordable in it', () {
      final action = firstPageAction(
        chains: [_chain('c', null)],
        lastSeen: null,
        caughtUpAlreadyEvaluated: false,
        sessionOffset: null,
        atTop: true,
      );

      expect(action, isA<DoNothing>());
    });
  });
}
