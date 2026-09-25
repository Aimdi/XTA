import 'package:xta/plugins/bluesky/bluesky_models.dart';

enum BlueskyReplyOrder { original, newest, oldest, popular }

class BlueskyReplyBranch {
  final BlueskyPost post;
  final List<BlueskyReplyBranch> children;
  final int descendants;
  final bool contextOnly;
  BlueskyReplyBranch(this.post, List<BlueskyReplyBranch> children, {this.contextOnly = false})
    : children = List.unmodifiable(children),
      descendants = children.fold(0, (count, child) => count + 1 + child.descendants);
}

bool _sameAuthor(BlueskyPost post, BlueskyPost focal) {
  if (post.did.isNotEmpty && focal.did.isNotEmpty) return post.did == focal.did;
  return post.handle.isNotEmpty && post.handle.toLowerCase() == focal.handle.toLowerCase();
}

int _compare(BlueskyPost left, BlueskyPost right, BlueskyReplyOrder order) {
  if (order == BlueskyReplyOrder.popular) return right.likeCount.compareTo(left.likeCount);
  final a = left.publishedAt;
  final b = right.publishedAt;
  if (a == null || b == null) return a == b ? 0 : (a == null ? 1 : -1);
  return order == BlueskyReplyOrder.newest ? b.compareTo(a) : a.compareTo(b);
}

/// Order siblings without pulling a reply away from its parent.
List<BlueskyPost> _ordered(List<BlueskyPost> posts, BlueskyReplyOrder order) {
  if (order == BlueskyReplyOrder.original) return posts;
  final positions = {for (var i = 0; i < posts.length; i++) posts[i].uri: i};
  return [...posts]..sort((a, b) {
    final compared = _compare(a, b, order);
    return compared == 0 ? positions[a.uri]!.compareTo(positions[b.uri]!) : compared;
  });
}

/// Canonical URIs deduplicate reposts and break cycles before building branches.
List<BlueskyReplyBranch> blueskyReplyBranches(
  BlueskyThread thread, {
  bool authorOnly = false,
  BlueskyReplyOrder order = BlueskyReplyOrder.original,
}) {
  final excluded = {thread.post.uri, ...thread.ancestors.map((post) => post.uri)};
  final posts = <String, BlueskyPost>{};
  for (final post in thread.replies) {
    if (post.uri.isNotEmpty && !excluded.contains(post.uri)) posts.putIfAbsent(post.uri, () => post);
  }
  final children = <String, List<String>>{};
  final roots = <String>[];
  for (final post in _ordered(posts.values.toList(), order)) {
    final parent = post.replyToUri;
    if (parent != null && parent != post.uri && posts.containsKey(parent)) {
      (children[parent] ??= []).add(post.uri);
    } else {
      roots.add(post.uri);
    }
  }
  return _assemble(thread.post, posts, children, [...roots, ...posts.keys], authorOnly);
}

List<BlueskyReplyBranch> _assemble(
  BlueskyPost focal,
  Map<String, BlueskyPost> posts,
  Map<String, List<String>> children,
  List<String> candidates,
  bool authorOnly,
) {
  final seen = <String>{};
  final ordered = <({String uri, String? parent})>[];
  final pending = <({String uri, String? parent})>[];
  for (final candidate in candidates) {
    if (seen.contains(candidate)) continue;
    pending.add((uri: candidate, parent: null));
    while (pending.isNotEmpty) {
      final entry = pending.removeLast();
      if (!seen.add(entry.uri)) continue;
      ordered.add(entry);
      for (final child in (children[entry.uri] ?? const <String>[]).reversed) {
        pending.add((uri: child, parent: entry.uri));
      }
    }
  }
  return _buildBranches(ordered, posts, authorOnly ? focal : null);
}

List<BlueskyReplyBranch> _buildBranches(
  List<({String uri, String? parent})> ordered,
  Map<String, BlueskyPost> posts,
  BlueskyPost? author,
) {
  final nested = <String?, List<BlueskyReplyBranch>>{};
  for (final entry in ordered.reversed) {
    final post = posts[entry.uri]!;
    final descendants = nested[entry.uri] ?? const <BlueskyReplyBranch>[];
    final contextOnly = author != null && !_sameAuthor(post, author);
    if (contextOnly && descendants.isEmpty) continue;
    final branch = BlueskyReplyBranch(post, descendants.reversed.toList(), contextOnly: contextOnly);
    (nested[entry.parent] ??= []).add(branch);
  }
  return List.unmodifiable((nested[null] ?? const <BlueskyReplyBranch>[]).reversed);
}

class BlueskyReplyRow {
  final BlueskyReplyBranch branch;
  final int depth;
  final bool collapsed;
  const BlueskyReplyRow(this.branch, this.depth, this.collapsed);
}

List<BlueskyReplyRow> blueskyVisibleReplies(List<BlueskyReplyBranch> branches, Set<String> collapsed) {
  final pending = [for (final branch in branches.reversed) (branch: branch, depth: 0)];
  final rows = <BlueskyReplyRow>[];
  while (pending.isNotEmpty) {
    final (:branch, :depth) = pending.removeLast();
    final hidden = branch.descendants > 0 && collapsed.contains(branch.post.uri);
    rows.add(BlueskyReplyRow(branch, depth, hidden));
    if (hidden) continue;
    for (final child in branch.children.reversed) {
      pending.add((branch: child, depth: depth + 1));
    }
  }
  return rows;
}
