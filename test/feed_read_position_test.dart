import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/group/feed_read_position.dart';

TweetChain chain(String id, DateTime? createdAt) {
  final tweet = TweetWithCard()..createdAt = createdAt;
  return TweetChain(id: id, tweets: [tweet], isPinned: false);
}

void main() {
  final t0 = DateTime(2026, 7, 1, 12);

  test('feedReadPositionKey maps Following group id to following', () {
    expect(feedReadPositionKey(legacyFeedKeyFollowing), feedKeyFollowing);
    expect(feedReadPositionKey('-1'), 'following');
    expect(feedReadPositionKey('abc-uuid'), 'abc-uuid');
    expect(feedKeyFollowing, 'following');
    expect(feedKeyForYou, 'for_you');
    expect(feedKeyFollowing, isNot(feedKeyForYou));
  });

  test('isChainSeen matches by id and by timestamp', () {
    final position = FeedReadPosition(chainId: 'a', chainCreatedAt: t0);
    expect(isChainSeen(chain('a', null), position), isTrue);
    expect(isChainSeen(chain('b', t0.subtract(const Duration(minutes: 1))), position), isTrue);
    expect(isChainSeen(chain('b', t0), position), isTrue);
    expect(isChainSeen(chain('b', t0.add(const Duration(minutes: 1))), position), isFalse);
    expect(isChainSeen(chain('b', null), position), isFalse);
  });

  test('caughtUpBoundaryIndex finds the first seen chain below new ones', () {
    final position = FeedReadPosition(chainId: 'seen', chainCreatedAt: t0);
    final newer = chain('n', t0.add(const Duration(hours: 1)));
    final older = chain('o', t0.subtract(const Duration(hours: 1)));

    expect(caughtUpBoundaryIndex([newer, newer, older], position), 2);
    // Nothing new: the boundary would be the very top, so no divider.
    expect(caughtUpBoundaryIndex([older, older], position), isNull);
    // Boundary not loaded yet.
    expect(caughtUpBoundaryIndex([newer, newer], position), isNull);
    expect(caughtUpBoundaryIndex([], position), isNull);
  });

  test('newestRecordableChain skips chains without timestamps', () {
    final withTime = chain('a', t0);
    final without = chain('b', null);
    expect(newestRecordableChain([without, withTime]), same(withTime));
    expect(newestRecordableChain([without]), isNull);
    expect(newestRecordableChain([]), isNull);
  });

  test('caught-up boundary treats equal timestamps as seen', () {
    final position = FeedReadPosition(chainId: 'x', chainCreatedAt: t0);
    final equal = chain('eq', t0);
    final newer = chain('n', t0.add(const Duration(seconds: 1)));
    expect(caughtUpBoundaryIndex([newer, equal], position), 1);
  });
}
