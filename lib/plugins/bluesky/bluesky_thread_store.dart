import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';

class BlueskyReplyBranch {
  final BlueskyPost post;
  final List<BlueskyReplyBranch> children;
  const BlueskyReplyBranch(this.post, this.children);
  int get descendants => children.fold(0, (count, child) => count + 1 + child.descendants);
}

/// Rebuild reply branches from canonical AT URIs, preserving sibling order.
List<BlueskyReplyBranch> blueskyReplyBranches(BlueskyThread thread) {
  final posts = <String, BlueskyPost>{};
  for (final post in thread.replies) {
    if (post.uri.isNotEmpty && post.uri != thread.post.uri) posts.putIfAbsent(post.uri, () => post);
  }
  final children = <String, List<String>>{};
  final roots = <String>[];
  for (final post in posts.values) {
    final parent = post.replyToUri;
    if (parent != null && parent != post.uri && posts.containsKey(parent)) {
      (children[parent] ??= []).add(post.uri);
    } else {
      roots.add(post.uri);
    }
  }
  final visited = <String>{};
  BlueskyReplyBranch build(String uri) {
    visited.add(uri);
    final nested = <BlueskyReplyBranch>[];
    for (final child in children[uri] ?? const <String>[]) {
      if (!visited.contains(child)) nested.add(build(child));
    }
    return BlueskyReplyBranch(posts[uri]!, nested);
  }

  final result = <BlueskyReplyBranch>[];
  for (final uri in [...roots, ...posts.keys]) {
    if (!visited.contains(uri)) result.add(build(uri));
  }
  return result;
}

class BlueskyReplyRow {
  final BlueskyReplyBranch branch;
  final int depth;
  final bool collapsed;
  const BlueskyReplyRow(this.branch, this.depth, this.collapsed);
}

List<BlueskyReplyRow> blueskyVisibleReplies(List<BlueskyReplyBranch> branches, Set<String> collapsed) {
  final rows = <BlueskyReplyRow>[];
  void append(BlueskyReplyBranch branch, int depth) {
    final hidden = collapsed.contains(branch.post.uri);
    rows.add(BlueskyReplyRow(branch, depth, hidden));
    if (!hidden) {
      for (final child in branch.children) {
        append(child, depth + 1);
      }
    }
  }

  for (final branch in branches) {
    append(branch, 0);
  }
  return rows;
}

class BlueskyThreadState {
  final BlueskyThread thread;
  final List<BlueskyReplyBranch> branches;
  final Set<String> collapsed;
  final bool contextOpen;
  final bool loading;
  final Object? error;
  const BlueskyThreadState({
    required this.thread,
    this.branches = const [],
    this.collapsed = const {},
    this.contextOpen = false,
    this.loading = false,
    this.error,
  });

  BlueskyThreadState copyWith({
    BlueskyThread? thread,
    Set<String>? collapsed,
    bool? contextOpen,
    bool loading = false,
    Object? error,
  }) => BlueskyThreadState(
    thread: thread ?? this.thread,
    branches: thread == null ? branches : blueskyReplyBranches(thread),
    collapsed: collapsed ?? this.collapsed,
    contextOpen: contextOpen ?? this.contextOpen,
    loading: loading,
    error: error,
  );
}

class BlueskyThreadStore extends Store<BlueskyThreadState> {
  final BlueskyClient client;
  var _request = 0;
  var _closed = false;
  BlueskyThreadStore(this.client, BlueskyPost post) : super(BlueskyThreadState(thread: BlueskyThread(post: post)));

  void toggleContext() =>
      update(state.copyWith(contextOpen: !state.contextOpen, loading: state.loading, error: state.error));

  void toggle(String uri) {
    final collapsed = {...state.collapsed};
    if (!collapsed.remove(uri)) collapsed.add(uri);
    update(state.copyWith(collapsed: collapsed, loading: state.loading, error: state.error));
  }

  Future<void> refresh() async {
    final request = ++_request;
    update(state.copyWith(loading: true));
    try {
      final thread = await client.getPostThread(state.thread.post.uri);
      if (!_closed && request == _request) update(state.copyWith(thread: thread));
    } catch (error) {
      if (!_closed && request == _request) update(state.copyWith(error: error));
    }
  }

  @override
  Future<void> destroy() {
    _closed = true;
    _request++;
    return super.destroy();
  }
}
