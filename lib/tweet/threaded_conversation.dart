import 'package:flutter/material.dart';
import 'package:xta/client/client.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/thread_rail.dart';

/// A conversation chain placed at its depth in the reply tree.
class ThreadNode {
  final TweetChain chain;
  final int depth;

  const ThreadNode(this.chain, this.depth);
}

/// One row in a capped status-thread list.
sealed class ThreadDisplayItem {}

class ThreadDisplayNode extends ThreadDisplayItem {
  final ThreadNode node;
  final int visualDepth;
  final bool connectTop;
  final bool connectBottom;

  ThreadDisplayNode({
    required this.node,
    required this.visualDepth,
    this.connectTop = false,
    this.connectBottom = false,
  });
}

/// Points at the first reply hidden by the visual depth cap.
class ThreadContinueMarker extends ThreadDisplayItem {
  final ThreadNode target;
  final int indentDepth;

  ThreadContinueMarker(this.target, {required this.indentDepth});
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
    final parentId = chain.tweets.isEmpty ? null : chain.tweets.first.inReplyToStatusIdStr;
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
    return ai.length == bi.length ? ai.compareTo(bi) : ai.length.compareTo(bi.length);
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

/// Index after the subtree rooted at [start] in a pre-order [nodes] list.
int skipThreadSubtree(List<ThreadNode> nodes, int start) {
  if (start < 0 || start >= nodes.length) {
    return start;
  }
  final rootDepth = nodes[start].depth;
  var i = start + 1;
  while (i < nodes.length && nodes[i].depth > rootDepth) {
    i++;
  }
  return i;
}

/// Flattens [nodes] for display, capping visual depth at [maxDepth] and
/// inserting [ThreadContinueMarker] rows for deeper branches.
List<ThreadDisplayItem> buildCappedThreadList(
  List<ThreadNode> nodes, {
  int maxDepth = kThreadMaxVisualDepth,
}) {
  final raw = <ThreadDisplayItem>[];
  var i = 0;
  while (i < nodes.length) {
    final node = nodes[i];
    if (node.depth > maxDepth) {
      i++;
      continue;
    }
    raw.add(ThreadDisplayNode(node: node, visualDepth: node.depth));
    if (i + 1 < nodes.length && nodes[i + 1].depth > maxDepth) {
      raw.add(ThreadContinueMarker(nodes[i + 1], indentDepth: maxDepth));
      i = skipThreadSubtree(nodes, i + 1);
      continue;
    }
    i++;
  }
  return _withThreadConnectors(raw);
}

List<ThreadDisplayItem> _withThreadConnectors(List<ThreadDisplayItem> items) {
  final out = <ThreadDisplayItem>[];
  for (var i = 0; i < items.length; i++) {
    final item = items[i];
    if (item is! ThreadDisplayNode) {
      out.add(item);
      continue;
    }
    final connectTop = item.visualDepth > 0 && i > 0 && items[i - 1] is ThreadDisplayNode;
    final connectBottom = _hasThreadChildBelow(item, i, items);
    out.add(ThreadDisplayNode(
      node: item.node,
      visualDepth: item.visualDepth,
      connectTop: connectTop,
      connectBottom: connectBottom,
    ));
  }
  return out;
}

bool _hasThreadChildBelow(ThreadDisplayNode node, int index, List<ThreadDisplayItem> items) {
  if (index + 1 >= items.length) {
    return false;
  }
  final next = items[index + 1];
  if (next is ThreadContinueMarker) {
    return node.node.depth == next.indentDepth;
  }
  if (next is ThreadDisplayNode) {
    return next.node.depth > node.node.depth;
  }
  return false;
}

/// Wraps a reply with avatar-column rails for its [depth] on the status screen.
/// Direct replies (depth 1) are flush with the opened tweet; only deeper
/// nesting steps in.
class ThreadIndent extends StatelessWidget {
  final int depth;
  final bool connectTop;
  final bool connectBottom;
  final Widget child;

  const ThreadIndent({
    super.key,
    required this.depth,
    required this.child,
    this.connectTop = false,
    this.connectBottom = false,
  });

  @override
  Widget build(BuildContext context) {
    final indent = threadNestedIndent(depth);
    if (indent <= 0 && !connectTop && !connectBottom) {
      return child;
    }
    return Padding(
      padding: EdgeInsets.only(left: indent),
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          // Positioned for the same reason as the feed's thread rail: left
          // unpositioned it is the only thing sizing the Stack, and in a list
          // that means infinitely tall.
          Positioned.fill(child: ThreadRailLines(connectTop: connectTop, connectBottom: connectBottom)),
          child,
        ],
      ),
    );
  }
}

/// Tappable row that opens a reply branch trimmed by the depth cap.
class ThreadContinueRow extends StatelessWidget {
  final int indentDepth;
  final VoidCallback onTap;

  const ThreadContinueRow({super.key, required this.indentDepth, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final color = theme.colorScheme.primary;
    final style = theme.textTheme.labelLarge?.copyWith(color: color);
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: EdgeInsets.only(
          left: threadNestedIndent(indentDepth) + kThreadRailLeft,
          top: 8,
          bottom: 8,
          right: 16,
        ),
        child: Row(
          children: [
            Icon(Icons.subdirectory_arrow_right, size: 18, color: color),
            const SizedBox(width: 8),
            Text(L10n.of(context).continue_thread, style: style),
          ],
        ),
      ),
    );
  }
}
