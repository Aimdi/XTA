import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_likes_store.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/plugin_profile_tabs.dart';

class BlueskyProfileFeed {
  final List<BlueskyPost> posts;
  final String? cursor;
  final bool loaded;
  final bool loading;
  final Object? error;

  const BlueskyProfileFeed({
    this.posts = const [],
    this.cursor,
    this.loaded = false,
    this.loading = false,
    this.error,
  });
}

class BlueskyProfileState {
  final BlueskyProfile? profile;
  final PluginProfileFeedTab selected;
  final Map<PluginProfileFeedTab, BlueskyProfileFeed> feeds;
  final bool loading;
  final Object? error;

  const BlueskyProfileState({
    this.profile,
    this.selected = PluginProfileFeedTab.posts,
    this.feeds = const {},
    this.loading = false,
    this.error,
  });

  BlueskyProfileFeed get feed => feeds[selected] ?? const BlueskyProfileFeed();
}

class BlueskyProfileStore extends Store<BlueskyProfileState> {
  final BlueskyClient client;
  final BlueskyLikesStore likes;
  final String actor;
  final _requests = <PluginProfileFeedTab, int>{};
  var _profileRequest = 0;
  var _closed = false;

  BlueskyProfileStore(this.client, this.likes, this.actor)
      : super(const BlueskyProfileState());

  Future<void> refresh() async {
    final request = ++_profileRequest;
    for (final tab in PluginProfileFeedTab.values) {
      _requests[tab] = (_requests[tab] ?? 0) + 1;
    }
    update(BlueskyProfileState(
      profile: state.profile,
      selected: state.selected,
      feeds: {for (final entry in state.feeds.entries)
        entry.key: BlueskyProfileFeed(posts: entry.value.posts, cursor: entry.value.cursor,
          loaded: entry.value.loaded, error: entry.value.error)},
      loading: true,
    ));
    try {
      final profile = await client.getProfile(actor);
      if (_closed || request != _profileRequest) return;
      update(BlueskyProfileState(profile: profile, selected: state.selected, feeds: state.feeds));
      await load(state.selected);
    } catch (error) {
      if (_closed || request != _profileRequest) return;
      update(BlueskyProfileState(
        profile: state.profile, selected: state.selected, feeds: state.feeds, error: error,
      ));
    }
  }

  Future<void> select(PluginProfileFeedTab tab) async {
    if (tab == state.selected) return;
    update(BlueskyProfileState(
      profile: state.profile, selected: tab, feeds: state.feeds,
      loading: state.loading, error: state.error,
    ));
    if (tab == PluginProfileFeedTab.saved || !(state.feed.loaded || state.feed.loading)) {
      await load(tab);
    }
  }

  String _filter(PluginProfileFeedTab tab) => switch (tab) {
    PluginProfileFeedTab.replies => kBlueskyAuthorFeedReplies,
    PluginProfileFeedTab.media => kBlueskyAuthorFeedMedia,
    _ => kBlueskyAuthorFeedPosts,
  };

  List<BlueskyPost> _visible(PluginProfileFeedTab tab, Iterable<BlueskyPost> posts) {
    final seen = <String>{};
    return [for (final post in posts)
      if (seen.add(post.uri) && (switch (tab) {
        PluginProfileFeedTab.replies => post.isReply,
        PluginProfileFeedTab.media => post.hasMedia,
        _ => true,
      })) post,
    ];
  }

  void _setFeed(PluginProfileFeedTab tab, BlueskyProfileFeed feed) {
    update(BlueskyProfileState(
      profile: state.profile, selected: state.selected,
      feeds: {...state.feeds, tab: feed},
      loading: state.loading, error: state.error,
    ));
  }

  Future<void> load(PluginProfileFeedTab tab, {bool more = false}) async {
    final profile = state.profile;
    final previous = state.feeds[tab] ?? const BlueskyProfileFeed();
    if (profile == null || previous.loading || (more && previous.cursor == null)) return;
    final request = (_requests[tab] ?? 0) + 1;
    _requests[tab] = request;
    _setFeed(tab, BlueskyProfileFeed(
      posts: previous.posts, cursor: previous.cursor, loaded: previous.loaded, loading: true,
    ));
    try {
      final page = tab == PluginProfileFeedTab.saved
          ? await _localLikes(profile)
          : await client.getAuthorFeed(
              profile.did.isEmpty ? profile.handle : profile.did,
              filter: _filter(tab), cursor: more ? previous.cursor : null,
            );
      if (_closed || _requests[tab] != request) return;
      _setFeed(tab, BlueskyProfileFeed(
        posts: _visible(tab, [...(more ? previous.posts : <BlueskyPost>[]), ...page.posts]),
        cursor: more && page.cursor == previous.cursor ? null : page.cursor,
        loaded: true,
      ));
    } catch (error) {
      if (_closed || _requests[tab] != request) return;
      _setFeed(tab, BlueskyProfileFeed(
        posts: previous.posts, cursor: previous.cursor, loaded: previous.loaded, error: error,
      ));
    }
  }

  Future<BlueskyFeedPage> _localLikes(BlueskyProfile profile) async {
    if (likes.state.isEmpty) await likes.load();
    return BlueskyFeedPage(posts: blueskyLikesByAuthor(
      likes.likedPosts, did: profile.did, handle: profile.handle,
    ));
  }

  Future<void> loadMore() async {
    if (!state.feed.loading && state.feed.error == null) {
      await load(state.selected, more: true);
    }
  }

  @override
  Future<void> destroy() {
    _closed = true;
    _profileRequest++;
    return super.destroy();
  }
}
