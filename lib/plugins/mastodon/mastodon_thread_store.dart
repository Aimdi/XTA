import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_thread_outline.dart';

export 'package:xta/plugins/mastodon/mastodon_thread_outline.dart';

class MastodonThreadState {
  final MastodonThread thread;
  final Set<String> collapsed;
  final bool ancestorsOpen;
  final bool authorOnly;
  final bool loading;
  final Object? error;
  const MastodonThreadState(
    this.thread, {
    this.collapsed = const {},
    this.ancestorsOpen = false,
    this.authorOnly = false,
    this.loading = false,
    this.error,
  });

  MastodonThreadState copyWith({
    MastodonThread? thread,
    Set<String>? collapsed,
    bool? ancestorsOpen,
    bool? authorOnly,
    bool? loading,
    Object? error,
    bool clearError = false,
  }) => MastodonThreadState(
    thread ?? this.thread,
    collapsed: collapsed == null ? this.collapsed : Set.unmodifiable(collapsed),
    ancestorsOpen: ancestorsOpen ?? this.ancestorsOpen,
    authorOnly: authorOnly ?? this.authorOnly,
    loading: loading ?? this.loading,
    error: clearError ? null : error ?? this.error,
  );
}

class MastodonThreadStore extends Store<MastodonThreadState> {
  final MastodonClient client;
  final List<String> instances;
  int _request = 0;
  bool _closed = false;
  MastodonThreadStore(this.client, this.instances, MastodonPost seed)
    : super(MastodonThreadState(MastodonThread(status: seed)));

  void toggle(String id) {
    if (_closed) return;
    final collapsed = {...state.collapsed};
    if (!collapsed.remove(id)) collapsed.add(id);
    update(state.copyWith(collapsed: collapsed));
  }

  void toggleAncestors() {
    if (!_closed) update(state.copyWith(ancestorsOpen: !state.ancestorsOpen));
  }

  void selectAuthor(bool selected) {
    if (!_closed && selected != state.authorOnly) update(state.copyWith(authorOnly: selected, collapsed: {}));
  }

  void setAllExpanded(bool expanded) {
    if (_closed) return;
    final branches = mastodonReplyRows(state.thread, {}, authorOnly: state.authorOnly);
    update(
      state.copyWith(
        collapsed: expanded
            ? {}
            : {
                for (final row in branches)
                  if (row.descendants > 0) row.post.id,
              },
      ),
    );
  }

  void focusSelected() {
    if (!_closed) update(state.copyWith(ancestorsOpen: false));
  }

  Future<void> refresh() async {
    if (_closed) return;
    final request = ++_request;
    update(state.copyWith(loading: true, clearError: true));
    try {
      final thread = await client.fetchThreadAnywhere(instances, state.thread.status);
      if (_closed || request != _request) return;
      final ids = thread.descendants.map((post) => post.id).toSet();
      update(
        state.copyWith(thread: thread, collapsed: state.collapsed.intersection(ids), loading: false, clearError: true),
      );
    } catch (error) {
      if (!_closed && request == _request) update(state.copyWith(loading: false, error: error));
    }
  }

  @override
  Future<void> destroy() {
    _closed = true;
    _request++;
    return super.destroy();
  }
}
