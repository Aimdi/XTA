import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';

class MastodonTagState {
  final List<MastodonPost> posts;
  final String? instance;
  final String? cursor;
  final bool loading;
  final bool loadingMore;
  final bool hasMore;
  final Object? error;
  final Object? moreError;

  const MastodonTagState({
    this.posts = const [],
    this.instance,
    this.cursor,
    this.loading = false,
    this.loadingMore = false,
    this.hasMore = false,
    this.error,
    this.moreError,
  });

  bool get canLoadMore => hasMore && !loading && !loadingMore && instance != null && cursor != null;

  MastodonTagState copyWith({
    List<MastodonPost>? posts,
    String? cursor,
    bool? loading,
    bool? loadingMore,
    bool? hasMore,
    Object? error,
    Object? moreError,
    bool clearErrors = false,
  }) => MastodonTagState(
    posts: posts == null ? this.posts : List.unmodifiable(posts),
    instance: instance,
    cursor: cursor ?? this.cursor,
    loading: loading ?? this.loading,
    loadingMore: loadingMore ?? this.loadingMore,
    hasMore: hasMore ?? this.hasMore,
    error: clearErrors ? null : error ?? this.error,
    moreError: clearErrors ? null : moreError ?? this.moreError,
  );
}

class MastodonTagStore extends Store<MastodonTagState> {
  final MastodonClient client;
  final List<String> instances;
  final String tag;
  int _request = 0;
  bool _closed = false;
  static const pageSize = 30;

  MastodonTagStore(this.client, this.instances, this.tag) : super(const MastodonTagState());

  bool _active(int request) => !_closed && request == _request;

  Future<void> refresh() async {
    if (_closed) return;
    final request = ++_request;
    update(state.copyWith(loading: true, loadingMore: false, clearErrors: true));
    try {
      final page = await client.firstInstanceThat(instances, (instance) async {
        final posts = await client.getTagTimeline(instance, tag, limit: pageSize);
        return (posts: posts, instance: instance);
      });
      if (!_active(request)) return;
      update(
        MastodonTagState(
          posts: _unique([], page.posts),
          instance: page.instance,
          cursor: page.posts.isEmpty ? null : page.posts.last.pagingId,
          hasMore: page.posts.length >= pageSize,
        ),
      );
    } catch (error) {
      if (_active(request)) update(state.copyWith(loading: false, error: error));
    }
  }

  Future<void> loadMore() async {
    if (_closed || !state.canLoadMore) return;
    final request = _request;
    final before = state;
    update(state.copyWith(loadingMore: true, clearErrors: true));
    try {
      final more = await client.getTagTimeline(before.instance!, tag, limit: pageSize, maxId: before.cursor);
      if (!_active(request)) return;
      final cursor = more.isEmpty ? before.cursor : more.last.pagingId;
      update(
        state.copyWith(
          posts: _unique(before.posts, more),
          cursor: cursor,
          loadingMore: false,
          hasMore: more.length >= pageSize && cursor != before.cursor,
        ),
      );
    } catch (error) {
      if (_active(request)) update(state.copyWith(loadingMore: false, moreError: error));
    }
  }

  List<MastodonPost> _unique(List<MastodonPost> before, List<MastodonPost> more) {
    final seen = before.map(canonicalMastodonPostKey).toSet();
    return List.unmodifiable([
      ...before,
      for (final post in more)
        if (seen.add(canonicalMastodonPostKey(post))) post,
    ]);
  }

  @override
  Future<void> destroy() {
    _closed = true;
    _request++;
    return super.destroy();
  }
}
