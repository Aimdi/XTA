import 'package:xta/plugins/mastodon/mastodon_models.dart';

class MastodonReplyRow {
  final MastodonPost post;
  final int depth;
  final int descendants;
  final bool collapsed;
  final bool contextOnly;

  const MastodonReplyRow(this.post, this.depth, this.descendants, this.collapsed, {this.contextOnly = false});
}

typedef _Branch = ({MastodonPost post, String? parent, int depth});

/// Preserve sibling order, break cycles, and retain replies with missing parents.
List<_Branch> _replyForest(MastodonThread thread) {
  final excluded = {thread.status.id, ...thread.ancestors.map((post) => post.id)};
  final posts = {
    for (final post in thread.descendants)
      if (!excluded.contains(post.id)) post.id: post,
  };
  final children = <String?, List<String>>{};
  for (final post in posts.values) {
    final parent = post.replyToId != post.id && posts.containsKey(post.replyToId) ? post.replyToId : null;
    (children[parent] ??= []).add(post.id);
  }
  final branches = <_Branch>[];
  final visited = <String>{};
  for (final root in [...?children[null], ...posts.keys]) {
    if (visited.contains(root)) continue;
    branches.addAll(_walkBranch(root, posts, children, visited));
  }
  return branches;
}

Iterable<_Branch> _walkBranch(
  String root,
  Map<String, MastodonPost> posts,
  Map<String?, List<String>> children,
  Set<String> visited,
) sync* {
  final pending = <({String id, String? parent, int depth})>[(id: root, parent: null, depth: 0)];
  while (pending.isNotEmpty) {
    final branch = pending.removeLast();
    if (!visited.add(branch.id)) continue;
    yield (post: posts[branch.id]!, parent: branch.parent, depth: branch.depth);
    for (final child in (children[branch.id] ?? const <String>[]).reversed) {
      pending.add((id: child, parent: branch.id, depth: branch.depth + 1));
    }
  }
}

Set<String> _authorContext(List<_Branch> branches, String author) {
  final parents = {for (final branch in branches) branch.post.id: branch.parent};
  final included = <String>{};
  for (final branch in branches) {
    if (branch.post.acct.toLowerCase() != author) continue;
    String? current = branch.post.id;
    while (current != null && included.add(current)) {
      current = parents[current];
    }
  }
  return included;
}

/// Author focus keeps the reply chain leading to each of the author's posts.
List<MastodonReplyRow> mastodonReplyRows(MastodonThread thread, Set<String> collapsed, {bool authorOnly = false}) {
  final forest = _replyForest(thread);
  final author = thread.status.acct.toLowerCase();
  final included = authorOnly ? _authorContext(forest, author) : null;
  final branches = included == null ? forest : forest.where((branch) => included.contains(branch.post.id)).toList();
  return _visibleReplies(branches, collapsed, contextAuthor: authorOnly ? author : null);
}

Map<String, int> _descendantCounts(List<_Branch> branches) {
  final counts = <String, int>{};
  for (final branch in branches.reversed) {
    final parent = branch.parent;
    if (parent != null) counts[parent] = (counts[parent] ?? 0) + (counts[branch.post.id] ?? 0) + 1;
  }
  return counts;
}

List<MastodonReplyRow> _visibleReplies(List<_Branch> branches, Set<String> collapsed, {String? contextAuthor}) {
  final counts = _descendantCounts(branches);
  final hidden = <String>{};
  final rows = <MastodonReplyRow>[];
  for (final branch in branches) {
    final id = branch.post.id;
    if (hidden.contains(branch.parent)) {
      hidden.add(id);
      continue;
    }
    final count = counts[id] ?? 0;
    rows.add(
      MastodonReplyRow(
        branch.post,
        branch.depth,
        count,
        count > 0 && collapsed.contains(id),
        contextOnly: contextAuthor != null && branch.post.acct.toLowerCase() != contextAuthor,
      ),
    );
    if (count > 0 && collapsed.contains(id)) hidden.add(id);
  }
  return rows;
}

/// Known reply links take precedence over the order supplied by a server.
List<MastodonPost> mastodonThreadAncestors(MastodonThread thread) {
  final posts = {
    for (final post in thread.ancestors)
      if (post.id != thread.status.id) post.id: post,
  };
  final chain = <MastodonPost>[];
  final seen = <String>{};
  var parent = thread.status.replyToId;
  while (parent != null && posts.containsKey(parent) && seen.add(parent)) {
    final post = posts[parent]!;
    chain.add(post);
    parent = post.replyToId;
  }
  return [
    for (final post in posts.values)
      if (!seen.contains(post.id)) post,
    ...chain.reversed,
  ];
}
