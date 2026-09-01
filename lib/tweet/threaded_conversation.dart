import 'package:flutter/material.dart';
import 'package:quax/client/client.dart';
import 'package:quax/tweet/tweet_chrome.dart';

/// A conversation chain placed at its depth in the reply tree.
class ThreadNode {
  final TweetChain chain;
  final int depth;

  const ThreadNode(this.chain, this.depth);
}

/// Orders the loaded conversation [chains] into a Reddit-style reply tree.
///
/// The chain containing the opened tweet (and the tweets above it) comes first
/// at depth 0; every other chain nests under the chain holding the tweet its
/// first tweet replies to (`in_reply_to_status_id`). Replies to the opened
/// tweet — or whose parent isn't loaded — are roots at depth 1. Pre-order,
/// siblings oldest-first, with cycle and orphan guards so the result always
/// contains each chain exactly once.
List<ThreadNode> buildThreadTree(List<TweetChain> chains, String focalId) {
  if (chains.isEmpty) {
    return const [];
  }

  final idToChain = <String, TweetChain>{};
  for (final chain in chains) {
    for (final tweet in chain.tweets) {
      final id = tweet.idStr;
      if (id != null) {
        idToChain[id] = chain;
      }
    }
  }

  TweetChain? head;
  for (final chain in chains) {
    if (chain.tweets.any((t) => t.idStr == focalId)) {
      head = chain;
      break;
    }
  }

  TweetChain? parentOf(TweetChain chain) {
    final parentId = chain.tweets.isEmpty
        ? null
        : chain.tweets.first.inReplyToStatusIdStr;
    if (parentId == null) {
      return null;
    }
    final parent = idToChain[parentId];
    return (parent == null || identical(parent, chain)) ? null : parent;
  }

  final children = <TweetChain, List<TweetChain>>{};
  final roots = <TweetChain>[];
  for (final chain in chains) {
    if (identical(chain, head)) {
      continue;
    }
    final parent = parentOf(chain);
    if (parent == null || identical(parent, head)) {
      roots.add(chain);
    } else {
      (children[parent] ??= <TweetChain>[]).add(chain);
    }
  }

  int byOldest(TweetChain a, TweetChain b) {
    final ai = a.tweets.isEmpty ? '' : (a.tweets.first.idStr ?? '');
    final bi = b.tweets.isEmpty ? '' : (b.tweets.first.idStr ?? '');
    return ai.length == bi.length
        ? ai.compareTo(bi)
        : ai.length.compareTo(bi.length);
  }

  final out = <ThreadNode>[];
  final visited = <TweetChain>{};

  void visit(TweetChain chain, int depth) {
    if (!visited.add(chain)) {
      return; // guard against reply cycles
    }
    out.add(ThreadNode(chain, depth));
    final kids = [...(children[chain] ?? const <TweetChain>[])]..sort(byOldest);
    for (final kid in kids) {
      visit(kid, depth + 1);
    }
  }

  if (head != null) {
    out.add(ThreadNode(head, 0));
    visited.add(head);
  }
  roots.sort(byOldest);
  for (final root in roots) {
    visit(root, 1);
  }
  // Anything unreached (orphaned parents, broken links) is appended flat.
  for (final chain in chains) {
    if (!visited.contains(chain)) {
      out.add(ThreadNode(chain, identical(chain, head) ? 0 : 1));
    }
  }
  return out;
}

/// Wraps a reply [child] with left indentation and a vertical connector line
/// per its depth, so nested replies read as a thread. Depth 0 (the opened
/// tweet) is returned unchanged; deeper levels are capped so long chains don't
/// run off-screen.
class ThreadIndent extends StatelessWidget {
  final int depth;
  final Widget child;

  const ThreadIndent({super.key, required this.depth, required this.child});

  static const _maxDepth = 5;
  static const _indentPerLevel = kTweetSpace2;

  @override
  Widget build(BuildContext context) {
    if (depth <= 0) {
      return child;
    }
    final level = depth.clamp(1, _maxDepth);
    final lineColor = tweetSecondaryColor(context).withValues(alpha: 0.42);
    return Padding(
      padding: EdgeInsets.only(left: _indentPerLevel * level),
      child: Container(
        padding: const EdgeInsets.only(left: 6),
        decoration: BoxDecoration(
          border: Border(
            left: BorderSide(color: lineColor, width: kTweetThreadRailWidth),
          ),
        ),
        child: child,
      ),
    );
  }
}
