import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';

/// Tweet ids present in [chains], skipping unparseable id strings.
Iterable<BigInt> tweetIdsOf(Iterable<TweetChain> chains) =>
    chains.expand((c) => c.tweets).map((t) => t.idStr).whereType<String>().map(BigInt.tryParse).whereType<BigInt>();

BigInt? newestTweetIdOf(Iterable<TweetChain> chains) =>
    tweetIdsOf(chains).fold<BigInt?>(null, (max, id) => max == null || id > max ? id : max);

BigInt? oldestTweetIdOf(Iterable<TweetChain> chains) =>
    tweetIdsOf(chains).fold<BigInt?>(null, (min, id) => min == null || id < min ? id : min);

/// Whether another page should be fetched to close the hole between freshly
/// loaded posts and what was already stored for this chunk.
///
/// A single search page only returns the newest window; after a long absence
/// that window sits above the stored posts with a gap between. Keep paging
/// down while the oldest just-fetched id is still newer than the newest
/// stored one, bounded by [maxGapFills].
bool shouldContinueGapFill({
  required BigInt? storedNewestId,
  required BigInt? oldestFetchedId,
  required bool pageNonEmpty,
  required bool hasCursor,
  required int gapFillsSoFar,
  int maxGapFills = maxFeedGapFillPages,
}) {
  if (storedNewestId == null || !pageNonEmpty || !hasCursor) {
    return false;
  }
  if (gapFillsSoFar >= maxGapFills) {
    return false;
  }
  return (oldestFetchedId ?? BigInt.zero) > storedNewestId;
}
