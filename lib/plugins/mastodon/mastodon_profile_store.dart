import 'dart:async';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_store.dart';

enum MastodonProfileTab { posts, replies, media }

class MastodonProfileTimeline {
  final List<MastodonPost> posts;
  final String? cursor;
  final bool loaded;
  final bool loading;
  final bool more;
  final Object? error;

  const MastodonProfileTimeline({
    this.posts = const [],
    this.cursor,
    this.loaded = false,
    this.loading = false,
    this.more = true,
    this.error,
  });

  MastodonProfileTimeline copy({bool? loading, bool? more, Object? error}) => MastodonProfileTimeline(
    posts: posts,
    cursor: cursor,
    loaded: loaded,
    loading: loading ?? this.loading,
    more: more ?? this.more,
    error: error,
  );
}

class MastodonProfileState {
  final MastodonProfile? profile;
  final String? instance;
  final Map<MastodonProfileTab, MastodonProfileTimeline> timelines;
  final Set<String> pinnedIds;
  final MastodonProfileTab selected;
  final bool loading;
  final Object? profileError;

  const MastodonProfileState({
    this.profile,
    this.instance,
    this.timelines = const {},
    this.pinnedIds = const {},
    this.selected = MastodonProfileTab.posts,
    this.loading = false,
    this.profileError,
  });

  MastodonProfileTimeline timeline(MastodonProfileTab tab) => timelines[tab] ?? const MastodonProfileTimeline();
  MastodonProfileTimeline get current => timeline(selected);
  List<MastodonPost> get posts => timeline(MastodonProfileTab.posts).posts;
  List<MastodonPost> get media => timeline(MastodonProfileTab.media).posts;
  List<MastodonPost> get visible => current.posts;
  bool get mediaSelected => selected == MastodonProfileTab.media;
  bool get loadingMore => current.loading;
  bool get mediaLoaded => timeline(MastodonProfileTab.media).loaded;
  bool get morePosts => timeline(MastodonProfileTab.posts).more;
  bool get moreMedia => timeline(MastodonProfileTab.media).more;
  Object? get error => profileError ?? current.error;

  MastodonProfileState copy({
    MastodonProfile? profile,
    String? instance,
    Map<MastodonProfileTab, MastodonProfileTimeline>? timelines,
    Set<String>? pinnedIds,
    MastodonProfileTab? selected,
    bool? loading,
    bool? morePosts,
    Object? profileError,
  }) => MastodonProfileState(
    profile: profile ?? this.profile,
    instance: instance ?? this.instance,
    timelines: morePosts == null
        ? timelines ?? this.timelines
        : {...this.timelines, MastodonProfileTab.posts: timeline(MastodonProfileTab.posts).copy(more: morePosts)},
    pinnedIds: pinnedIds ?? this.pinnedIds,
    selected: selected ?? this.selected,
    loading: loading ?? this.loading,
    profileError: profileError,
  );
}

class MastodonProfileStore extends Store<MastodonProfileState> {
  final MastodonClient client;
  final List<String> instances;
  final String acct;
  int _generation = 0;
  bool _closed = false;

  MastodonProfileStore(this.client, this.instances, this.acct) : super(const MastodonProfileState());

  Future<void> refresh() async {
    if (_closed) return;
    final generation = ++_generation;
    update(
      state.copy(
        loading: true,
        timelines: {for (final entry in state.timelines.entries) entry.key: entry.value.copy(loading: false)},
      ),
    );
    try {
      final page = await client.profileAnywhere(instances, acct);
      if (_closed || generation != _generation) return;
      update(
        state.copy(
          profile: page.profile,
          instance: page.instance,
          pinnedIds: page.pinnedIds,
          loading: false,
          timelines: {
            MastodonProfileTab.posts: MastodonProfileTimeline(
              posts: page.posts,
              cursor: page.rawPosts.lastOrNull?.pagingId,
              loaded: true,
              more: page.rawPosts.length >= 20,
            ),
          },
        ),
      );
      if (state.selected != MastodonProfileTab.posts) await loadMore();
    } catch (error) {
      if (!_closed && generation == _generation) update(state.copy(loading: false, profileError: error));
    }
  }

  void select(MastodonProfileTab tab) {
    if (_closed) return;
    update(state.copy(selected: tab, profileError: state.profileError));
    if (!state.current.loaded && !state.loading) unawaited(loadMore());
  }

  void selectMedia(bool media) => select(media ? MastodonProfileTab.media : MastodonProfileTab.posts);

  Future<void> retry() => state.profileError == null ? loadMore() : refresh();

  Future<void> loadMore() async {
    final profile = state.profile;
    final instance = state.instance;
    final tab = state.selected;
    final current = state.current;
    if (_closed || state.loading || current.loading || !current.more || profile == null || instance == null) return;
    final generation = _generation;
    _updateTimeline(tab, current.copy(loading: true));
    try {
      final page = await client.getStatuses(
        instance,
        profile.id,
        excludeReplies: tab == MastodonProfileTab.posts,
        onlyMedia: tab == MastodonProfileTab.media,
        maxId: current.cursor,
      );
      if (_closed || generation != _generation) return;
      final combined = appendUniqueMastodonPosts(current.posts, page);
      final cursor = page.lastOrNull?.pagingId;
      _updateTimeline(
        tab,
        MastodonProfileTimeline(
          posts: combined,
          cursor: cursor,
          loaded: true,
          more: page.length >= 20 && cursor != current.cursor,
        ),
      );
    } catch (error) {
      if (!_closed && generation == _generation) _updateTimeline(tab, current.copy(error: error, loading: false));
    }
  }

  void _updateTimeline(MastodonProfileTab tab, MastodonProfileTimeline timeline) =>
      update(state.copy(timelines: {...state.timelines, tab: timeline}, profileError: state.profileError));

  @override
  Future<void> destroy() {
    _closed = true;
    _generation++;
    return super.destroy();
  }
}
