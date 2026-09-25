import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_models.dart';

enum SubstackCommentOrder { original, newest, oldest }

class SubstackCommentRow {
  final SubstackComment comment;
  final int depth;
  final int descendants;
  final bool collapsed;
  final bool contextOnly;
  const SubstackCommentRow(this.comment, this.depth, this.descendants, this.collapsed, this.contextOnly);
}

typedef _CommentEntry = ({SubstackComment comment, String? parent, int depth});

Map<String, String?> _commentParents(List<SubstackComment> comments) {
  final ids = comments.map((comment) => comment.id).toSet();
  final parents = <String, String?>{};
  final ancestry = <SubstackComment>[];
  for (final comment in comments) {
    while (ancestry.isNotEmpty && ancestry.last.depth >= comment.depth) {
      ancestry.removeLast();
    }
    final parent = comment.parentId;
    parents[comment.id] = parent != null
        ? (parent != comment.id && ids.contains(parent) ? parent : null)
        : (ancestry.isEmpty ? null : ancestry.last.id);
    ancestry.add(comment);
  }
  return parents;
}

int _compareComments(SubstackComment a, SubstackComment b, SubstackCommentOrder order) {
  final left = a.at;
  final right = b.at;
  if (left == null || right == null) return left == right ? 0 : (left == null ? 1 : -1);
  return order == SubstackCommentOrder.newest ? right.compareTo(left) : left.compareTo(right);
}

List<_CommentEntry> _commentOutline(List<SubstackComment> comments, SubstackCommentOrder order) {
  final unique = <String, SubstackComment>{};
  for (final comment in comments) {
    if (comment.id.isNotEmpty) unique.putIfAbsent(comment.id, () => comment);
  }
  final parents = _commentParents(unique.values.toList());
  final children = <String?, List<String>>{};
  for (final id in unique.keys) {
    (children[parents[id]] ??= []).add(id);
  }
  final ids = unique.keys.toList();
  final positions = {for (var i = 0; i < ids.length; i++) ids[i]: i};
  if (order != SubstackCommentOrder.original) {
    for (final siblings in children.values) {
      siblings.sort((a, b) {
        final compared = _compareComments(unique[a]!, unique[b]!, order);
        return compared == 0 ? positions[a]!.compareTo(positions[b]!) : compared;
      });
    }
  }
  return _walkComments(unique, children);
}

List<_CommentEntry> _walkComments(Map<String, SubstackComment> comments, Map<String?, List<String>> children) {
  final visited = <String>{};
  final result = <_CommentEntry>[];
  for (final id in [...?children[null], ...comments.keys]) {
    if (visited.contains(id)) continue;
    final pending = [(id: id, parent: null as String?, depth: 0)];
    while (pending.isNotEmpty) {
      final next = pending.removeLast();
      if (!visited.add(next.id)) continue;
      result.add((comment: comments[next.id]!, parent: next.parent, depth: next.depth));
      for (final child in (children[next.id] ?? const <String>[]).reversed) {
        pending.add((id: child, parent: next.id, depth: next.depth + 1));
      }
    }
  }
  return result;
}

/// Search includes a matching comment's ancestors so replies retain their context.
List<SubstackCommentRow> substackCommentRows(
  List<SubstackComment> comments, {
  Set<String> collapsed = const {},
  SubstackCommentOrder order = SubstackCommentOrder.original,
  String query = '',
}) {
  final outline = _commentOutline(comments, order);
  final terms = query.trim().toLowerCase().split(RegExp(r'\s+')).where((term) => term.isNotEmpty).toList();
  final matched = {
    for (final entry in outline)
      if (terms.every('${entry.comment.author ?? ''}\n${entry.comment.body}'.toLowerCase().contains)) entry.comment.id,
  };
  final included = {...matched};
  for (final entry in outline.reversed) {
    if (included.contains(entry.comment.id) && entry.parent != null) included.add(entry.parent!);
  }
  final filtered = outline.where((entry) => included.contains(entry.comment.id)).toList();
  return _visibleComments(filtered, collapsed, matched);
}

List<SubstackCommentRow> _visibleComments(List<_CommentEntry> entries, Set<String> collapsed, Set<String> matched) {
  final counts = <String, int>{};
  for (final entry in entries.reversed) {
    if (entry.parent != null) {
      counts[entry.parent!] = (counts[entry.parent!] ?? 0) + 1 + (counts[entry.comment.id] ?? 0);
    }
  }
  final hidden = <String>{};
  final rows = <SubstackCommentRow>[];
  for (final entry in entries) {
    final id = entry.comment.id;
    if (hidden.contains(entry.parent)) {
      hidden.add(id);
      continue;
    }
    final count = counts[id] ?? 0;
    final isCollapsed = count > 0 && collapsed.contains(id);
    rows.add(SubstackCommentRow(entry.comment, entry.depth, count, isCollapsed, !matched.contains(id)));
    if (isCollapsed) hidden.add(id);
  }
  return rows;
}

class SubstackCommentsState {
  final List<SubstackComment> comments;
  final Set<String> collapsed;
  final SubstackCommentOrder order;
  final String query;
  final bool loading;
  final bool loaded;
  final Object? error;
  const SubstackCommentsState({
    this.comments = const [],
    this.collapsed = const {},
    this.order = SubstackCommentOrder.original,
    this.query = '',
    this.loading = false,
    this.loaded = false,
    this.error,
  });
  List<SubstackCommentRow> get rows => substackCommentRows(comments, collapsed: collapsed, order: order, query: query);
  SubstackCommentsState copy({
    List<SubstackComment>? comments,
    Set<String>? collapsed,
    SubstackCommentOrder? order,
    String? query,
    bool? loading,
    bool? loaded,
    Object? error,
    bool clearError = false,
  }) => SubstackCommentsState(
    comments: comments == null ? this.comments : List.unmodifiable(comments),
    collapsed: collapsed == null ? this.collapsed : Set.unmodifiable(collapsed),
    order: order ?? this.order,
    query: query ?? this.query,
    loading: loading ?? this.loading,
    loaded: loaded ?? this.loaded,
    error: clearError ? null : error ?? this.error,
  );
}

class SubstackCommentsStore extends Store<SubstackCommentsState> {
  final SubstackClient client;
  final SubstackPost post;
  bool _closed = false;
  int _request = 0;
  SubstackCommentsStore(this.client, this.post) : super(const SubstackCommentsState());

  void search(String query) {
    if (!_closed) update(state.copy(query: query, collapsed: {}));
  }

  void sort(SubstackCommentOrder order) {
    if (!_closed) update(state.copy(order: order));
  }

  void toggle(String id) {
    if (_closed) return;
    final collapsed = {...state.collapsed};
    if (!collapsed.remove(id)) collapsed.add(id);
    update(state.copy(collapsed: collapsed));
  }

  void setExpanded(bool expanded) {
    if (_closed) return;
    update(
      state.copy(
        collapsed: expanded
            ? {}
            : {
                for (final row in substackCommentRows(state.comments, order: state.order, query: state.query))
                  if (row.descendants > 0) row.comment.id,
              },
      ),
    );
  }

  Future<void> refresh() async {
    if (_closed) return;
    final request = ++_request;
    update(state.copy(loading: true, clearError: true));
    try {
      final comments = await client.fetchComments(post.publication, post.id);
      if (_closed || request != _request) return;
      update(
        state.copy(
          comments: comments,
          collapsed: state.collapsed.intersection(comments.map((e) => e.id).toSet()),
          loading: false,
          loaded: true,
          clearError: true,
        ),
      );
    } catch (error) {
      if (!_closed && request == _request) update(state.copy(loading: false, error: error));
    }
  }

  @override
  Future<void> destroy() {
    _closed = true;
    _request++;
    return super.destroy();
  }
}
