import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_store.dart';

class MastodonProfileState {
  final MastodonProfile? profile;
  final String? instance;
  final List<MastodonPost> posts;
  final List<MastodonPost> media;
  final Set<String> pinnedIds;
  final bool mediaSelected;
  final bool loading;
  final bool loadingMore;
  final bool mediaLoaded;
  final bool morePosts;
  final bool moreMedia;
  final Object? error;

  const MastodonProfileState({this.profile, this.instance, this.posts = const [],
    this.media = const [], this.pinnedIds = const {}, this.mediaSelected = false,
    this.loading = false, this.loadingMore = false, this.mediaLoaded = false,
    this.morePosts = true, this.moreMedia = true, this.error});

  List<MastodonPost> get visible => mediaSelected ? media : posts;

  MastodonProfileState copy({MastodonProfile? profile, String? instance,
    List<MastodonPost>? posts, List<MastodonPost>? media, Set<String>? pinnedIds,
    bool? mediaSelected, bool? loading, bool? loadingMore, bool? mediaLoaded,
    bool? morePosts, bool? moreMedia, Object? error}) => MastodonProfileState(
      profile: profile ?? this.profile, instance: instance ?? this.instance,
      posts: posts ?? this.posts, media: media ?? this.media, pinnedIds: pinnedIds ?? this.pinnedIds,
      mediaSelected: mediaSelected ?? this.mediaSelected, loading: loading ?? this.loading,
      loadingMore: loadingMore ?? this.loadingMore, mediaLoaded: mediaLoaded ?? this.mediaLoaded,
      morePosts: morePosts ?? this.morePosts, moreMedia: moreMedia ?? this.moreMedia, error: error);
}

class MastodonProfileStore extends Store<MastodonProfileState> {
  final MastodonClient client;
  final List<String> instances;
  final String acct;
  int _generation = 0;
  bool _closed = false;

  MastodonProfileStore(this.client, this.instances, this.acct) : super(const MastodonProfileState());

  Future<void> refresh() async {
    final generation = ++_generation;
    update(state.copy(loading: true, loadingMore: false));
    try {
      final page = await client.profileAnywhere(instances, acct);
      if (_closed || generation != _generation) return;
      update(state.copy(profile: page.profile, instance: page.instance, posts: page.posts,
        pinnedIds: page.pinnedIds, morePosts: page.posts.length >= 20, loading: false,
        media: const [], mediaLoaded: false, moreMedia: true));
      if (state.mediaSelected) await loadMore();
    } catch (error) {
      if (!_closed && generation == _generation) update(state.copy(loading: false, error: error));
    }
  }

  void selectMedia(bool media) {
    update(state.copy(mediaSelected: media));
    if (media && !state.mediaLoaded) loadMore();
  }

  Future<void> loadMore() async {
    final profile = state.profile;
    final instance = state.instance;
    final media = state.mediaSelected;
    if (_closed || state.loading || state.loadingMore || profile == null || instance == null) return;
    if (media ? !state.moreMedia : !state.morePosts) return;
    final generation = _generation;
    final current = media ? state.media : state.posts;
    update(state.copy(loadingMore: true));
    try {
      final page = await client.getStatuses(instance, profile.id, onlyMedia: media,
        maxId: current.isEmpty ? null : current.last.id);
      if (_closed || generation != _generation) return;
      final combined = appendUniqueMastodonPosts(current, page);
      update(media
        ? state.copy(media: combined, mediaLoaded: true, moreMedia: page.length >= 20, loadingMore: false)
        : state.copy(posts: combined, morePosts: page.length >= 20, loadingMore: false));
      // A tab change during a posts request must still load the media tab.
      if (!media && state.mediaSelected && !state.mediaLoaded) await loadMore();
    } catch (error) {
      if (!_closed && generation == _generation) update(state.copy(loadingMore: false, error: error));
    }
  }

  @override
  Future<void> destroy() {
    _closed = true;
    _generation++;
    return super.destroy();
  }
}
