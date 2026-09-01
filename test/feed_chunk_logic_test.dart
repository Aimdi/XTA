import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/group/feed_cache.dart';
import 'package:xta/group/feed_gap.dart';

TweetChain chain(String id, {String? tweetId, DateTime? createdAt}) {
  final tweet = TweetWithCard()
    ..idStr = tweetId ?? id
    ..createdAt = createdAt;
  return TweetChain(id: id, tweets: [tweet], isPinned: false);
}

void main() {
  group('dedupeChainsById', () {
    test('keeps the first chain for a repeated chain id', () {
      final a = chain('a', tweetId: '1');
      final dup = chain('a', tweetId: '1');
      final b = chain('b', tweetId: '2');
      expect(dedupeChainsById([a, dup, b]).map((c) => c.id), ['a', 'b']);
    });

    test('drops a later chain whose leading tweet id already appeared', () {
      final first = chain('chain-1', tweetId: '99');
      final sameTweet = chain('chain-2', tweetId: '99');
      final other = chain('chain-3', tweetId: '100');
      expect(dedupeChainsById([first, sameTweet, other]).map((c) => c.id), ['chain-1', 'chain-3']);
    });
  });

  group('gap fill', () {
    test('newest and oldest tweet ids', () {
      final newer = chain('n', tweetId: '200');
      final older = chain('o', tweetId: '50');
      expect(newestTweetIdOf([older, newer]), BigInt.from(200));
      expect(oldestTweetIdOf([older, newer]), BigInt.from(50));
      expect(newestTweetIdOf(const []), isNull);
    });

    test('continues while fetched window is entirely above stored posts', () {
      expect(
        shouldContinueGapFill(
          storedNewestId: BigInt.from(100),
          oldestFetchedId: BigInt.from(150),
          pageNonEmpty: true,
          hasCursor: true,
          gapFillsSoFar: 0,
        ),
        isTrue,
      );
    });

    test('stops once fetched content overlaps stored posts', () {
      expect(
        shouldContinueGapFill(
          storedNewestId: BigInt.from(100),
          oldestFetchedId: BigInt.from(80),
          pageNonEmpty: true,
          hasCursor: true,
          gapFillsSoFar: 0,
        ),
        isFalse,
      );
    });

    test('stops at the gap-fill page budget', () {
      expect(
        shouldContinueGapFill(
          storedNewestId: BigInt.from(100),
          oldestFetchedId: BigInt.from(500),
          pageNonEmpty: true,
          hasCursor: true,
          gapFillsSoFar: 4,
          maxGapFills: 4,
        ),
        isFalse,
      );
    });

    test('does not gap-fill without stored posts or a cursor', () {
      expect(
        shouldContinueGapFill(
          storedNewestId: null,
          oldestFetchedId: BigInt.from(150),
          pageNonEmpty: true,
          hasCursor: true,
          gapFillsSoFar: 0,
        ),
        isFalse,
      );
      expect(
        shouldContinueGapFill(
          storedNewestId: BigInt.from(100),
          oldestFetchedId: BigInt.from(150),
          pageNonEmpty: true,
          hasCursor: false,
          gapFillsSoFar: 0,
        ),
        isFalse,
      );
    });
  });
}
