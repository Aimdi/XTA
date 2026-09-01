import 'package:xta/client/client.dart';

/// Whether [chain] opens with a repost of another post.
bool chainIsBoost(TweetChain chain) {
  final first = chain.tweets.firstOrNull;
  return first?.retweetedStatusWithCard != null;
}

/// A single chain, or a run of consecutive boosts (length ≥ 2).
sealed class CollapsedFeedItem {}

class SingleChain extends CollapsedFeedItem {
  final TweetChain chain;
  SingleChain(this.chain);
}

class BoostRun extends CollapsedFeedItem {
  final List<TweetChain> chains;
  BoostRun(this.chains);
}

/// Collapses consecutive boost chains into [BoostRun]s (Phanpy-style).
List<CollapsedFeedItem> collapseBoostRuns(List<TweetChain> chains) {
  if (chains.isEmpty) {
    return const [];
  }

  final out = <CollapsedFeedItem>[];
  var i = 0;
  while (i < chains.length) {
    if (!chainIsBoost(chains[i])) {
      out.add(SingleChain(chains[i]));
      i++;
      continue;
    }

    final run = <TweetChain>[chains[i]];
    var j = i + 1;
    while (j < chains.length && chainIsBoost(chains[j])) {
      run.add(chains[j]);
      j++;
    }

    if (run.length >= 2) {
      out.add(BoostRun(run));
    } else {
      out.add(SingleChain(run.single));
    }
    i = j;
  }
  return out;
}

/// True when [index] is not the first chain of a boost run (already rendered).
bool isContinuationOfBoostRun(List<TweetChain> chains, int index) =>
    index > 0 && chainIsBoost(chains[index]) && chainIsBoost(chains[index - 1]);

/// Length of a consecutive boost run starting at [index], or 0 when [index] is
/// not the start of a run of two or more boosts.
int boostRunLengthAt(List<TweetChain> chains, int index) {
  if (index < 0 || index >= chains.length) {
    return 0;
  }
  if (!chainIsBoost(chains[index]) || isContinuationOfBoostRun(chains, index)) {
    return 0;
  }

  var end = index;
  while (end < chains.length && chainIsBoost(chains[end])) {
    end++;
  }
  final length = end - index;
  return length >= 2 ? length : 0;
}
