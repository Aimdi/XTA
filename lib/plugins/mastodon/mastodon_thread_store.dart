import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';

class MastodonReplyRow {
  final MastodonPost post;
  final int depth;
  final int descendants;
  final bool collapsed;
  const MastodonReplyRow(this.post, this.depth, this.descendants, this.collapsed);
}

/// Preserve API sibling order, keep orphans readable and tolerate cycles.
List<MastodonReplyRow> mastodonReplyRows(MastodonThread thread, Set<String> collapsed) {
  final posts = {
    for (final post in thread.descendants)
      if (post.id != thread.status.id) post.id: post,
  };
  final children = <String?, List<String>>{};
  for (final post in posts.values) {
    final parent = posts.containsKey(post.replyToId) && post.replyToId != post.id ? post.replyToId : null;
    (children[parent] ??= []).add(post.id);
  }
  int countBelow(String id) {
    final seen = <String>{id};
    final pending = [...?children[id]];
    while (pending.isNotEmpty) {
      final next = pending.removeLast();
      if (seen.add(next)) pending.addAll(children[next] ?? const []);
    }
    return seen.length - 1;
  }

  final rows = <MastodonReplyRow>[];
  final visited = <String>{};
  void visit(String root) {
    final pending = [(root, 0, false)];
    while (pending.isNotEmpty) {
      final (id, depth, hidden) = pending.removeLast();
      if (!visited.add(id)) continue;
      if (!hidden) rows.add(MastodonReplyRow(posts[id]!, depth, countBelow(id), collapsed.contains(id)));
      for (final child in (children[id] ?? const <String>[]).reversed) {
        pending.add((child, depth + 1, hidden || collapsed.contains(id)));
      }
    }
  }

  for (final root in children[null] ?? const <String>[]) {
    visit(root);
  }
  // Cyclic or disconnected data still has a readable representation.
  for (final id in posts.keys) {
    if (!visited.contains(id)) visit(id);
  }
  return rows;
}

class MastodonThreadState {
  final MastodonThread thread;
  final Set<String> collapsed;
  final bool ancestorsOpen;
  final bool loading;
  final Object? error;
  const MastodonThreadState(
    this.thread, {
    this.collapsed = const {},
    this.ancestorsOpen = false,
    this.loading = false,
    this.error,
  });
}

class MastodonThreadStore extends Store<MastodonThreadState> {
  final MastodonClient client;
  final List<String> instances;
  int _request = 0;
  bool _closed = false;
  MastodonThreadStore(this.client, this.instances, MastodonPost seed)
    : super(MastodonThreadState(MastodonThread(status: seed)));

  void toggle(String id) {
    final collapsed = {...state.collapsed};
    if (!collapsed.remove(id)) collapsed.add(id);
    update(
      MastodonThreadState(
        state.thread,
        collapsed: collapsed,
        ancestorsOpen: state.ancestorsOpen,
        loading: state.loading,
        error: state.error,
      ),
    );
  }

  void toggleAncestors() => update(
    MastodonThreadState(
      state.thread,
      collapsed: state.collapsed,
      ancestorsOpen: !state.ancestorsOpen,
      loading: state.loading,
      error: state.error,
    ),
  );

  Future<void> refresh() async {
    final request = ++_request;
    update(
      MastodonThreadState(state.thread, collapsed: state.collapsed, ancestorsOpen: state.ancestorsOpen, loading: true),
    );
    try {
      final thread = await client.fetchThreadAnywhere(instances, state.thread.status);
      if (_closed || request != _request) return;
      update(MastodonThreadState(thread, collapsed: state.collapsed, ancestorsOpen: state.ancestorsOpen));
    } catch (error) {
      if (!_closed && request == _request)
        update(
          MastodonThreadState(
            state.thread,
            collapsed: state.collapsed,
            ancestorsOpen: state.ancestorsOpen,
            error: error,
          ),
        );
    }
  }

  @override
  Future<void> destroy() {
    _closed = true;
    _request++;
    return super.destroy();
  }
}
