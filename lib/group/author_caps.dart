import 'package:xta/client/client.dart';

/// Keeps at most [maxByAuthorId] chains per author id for this load.
///
/// Authors absent from the map, or mapped to null/0, are uncapped. Walks [chains]
/// in the given order so a Popular feed keeps its likes ranking and a Recent
/// feed keeps newest-first — callers sort before capping.
List<TweetChain> capChainsPerAuthor(List<TweetChain> chains, Map<String, int> maxByAuthorId) {
  if (maxByAuthorId.isEmpty) {
    return chains;
  }

  final byAuthorCount = <String, int>{};
  final kept = <TweetChain>[];

  for (final chain in chains) {
    final author = chain.tweets.firstOrNull?.user?.idStr;
    if (author == null) {
      kept.add(chain);
      continue;
    }

    final cap = maxByAuthorId[author];
    if (cap == null || cap <= 0) {
      kept.add(chain);
      continue;
    }

    final count = byAuthorCount[author] ?? 0;
    if (count < cap) {
      byAuthorCount[author] = count + 1;
      kept.add(chain);
    }
  }

  return kept;
}
