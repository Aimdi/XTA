import 'dart:async';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_thread_outline.dart';

export 'package:xta/plugins/bluesky/bluesky_thread_outline.dart';

class BlueskyThreadState {
  final BlueskyThread thread;
  final List<BlueskyReplyBranch> branches;
  final Set<String> collapsed;
  final bool contextOpen;
  final bool authorOnly;
  final BlueskyReplyOrder order;
  final bool loading;
  final Object? error;
  const BlueskyThreadState({
    required this.thread,
    this.branches = const [],
    this.collapsed = const {},
    this.contextOpen = false,
    this.authorOnly = false,
    this.order = BlueskyReplyOrder.original,
    this.loading = false,
    this.error,
  });

  BlueskyThreadState copyWith({
    BlueskyThread? thread,
    Set<String>? collapsed,
    bool? contextOpen,
    bool? authorOnly,
    BlueskyReplyOrder? order,
    bool? loading,
    Object? error,
    bool clearError = false,
  }) => BlueskyThreadState(
    thread: thread ?? this.thread,
    branches: thread == null && authorOnly == null && order == null
        ? branches
        : blueskyReplyBranches(
            thread ?? this.thread,
            authorOnly: authorOnly ?? this.authorOnly,
            order: order ?? this.order,
          ),
    collapsed: collapsed == null ? this.collapsed : Set.unmodifiable(collapsed),
    contextOpen: contextOpen ?? this.contextOpen,
    authorOnly: authorOnly ?? this.authorOnly,
    order: order ?? this.order,
    loading: loading ?? this.loading,
    error: clearError ? null : error ?? this.error,
  );
}

class BlueskyThreadStore extends Store<BlueskyThreadState> {
  final BlueskyClient client;
  var _request = 0;
  var _closed = false;
  BlueskyThreadStore(this.client, BlueskyPost post) : super(BlueskyThreadState(thread: BlueskyThread(post: post)));

  void toggleContext() {
    if (!_closed) update(state.copyWith(contextOpen: !state.contextOpen));
  }

  void toggle(String uri) {
    if (_closed) return;
    final collapsed = {...state.collapsed};
    if (!collapsed.remove(uri)) collapsed.add(uri);
    update(state.copyWith(collapsed: collapsed));
  }

  void selectAuthor(bool selected) {
    if (!_closed && selected != state.authorOnly) update(state.copyWith(authorOnly: selected, collapsed: {}));
  }

  void selectOrder(BlueskyReplyOrder order) {
    if (!_closed && order != state.order) update(state.copyWith(order: order));
  }

  void setAllExpanded(bool expanded) {
    if (_closed) return;
    update(
      state.copyWith(
        collapsed: expanded
            ? {}
            : {
                for (final row in blueskyVisibleReplies(state.branches, {}))
                  if (row.branch.descendants > 0) row.branch.post.uri,
              },
      ),
    );
  }

  void focusSelected() {
    if (!_closed) update(state.copyWith(contextOpen: false));
  }

  Future<void> refresh() async {
    if (_closed) return;
    final request = ++_request;
    final source = client.baseUrl;
    update(state.copyWith(loading: true, clearError: true));
    try {
      final thread = await client.getPostThread(state.thread.post.uri);
      if (!_accept(request, source)) return;
      final known = thread.replies.map((post) => post.uri).toSet();
      update(
        state.copyWith(
          thread: thread,
          collapsed: state.collapsed.intersection(known),
          loading: false,
          clearError: true,
        ),
      );
    } catch (error) {
      if (_accept(request, source)) update(state.copyWith(loading: false, error: error));
    }
  }

  bool _accept(int request, String source) {
    if (_closed || request != _request) return false;
    if (source == client.baseUrl) return true;
    unawaited(refresh());
    return false;
  }

  @override
  Future<void> destroy() {
    _closed = true;
    _request++;
    return super.destroy();
  }
}
