import 'dart:async';

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
  final bool failedMore;
  final Object? error;

  const BlueskyProfileFeed({
    this.posts = const [],
    this.cursor,
    this.loaded = false,
    this.loading = false,
    this.failedMore = false,
    this.error,
  });
}

class BlueskyProfileState {
  final BlueskyProfile? profile;
  final PluginProfileFeedTab selected;
  final Map<PluginProfileFeedTab, BlueskyProfileFeed> feeds;
  final bool loading;
  final Object? error;
  final BlueskyPost? pinnedPost;
  final bool loadingPin;
  final Object? pinError;

  const BlueskyProfileState({
    this.profile,
    this.selected = PluginProfileFeedTab.posts,
    this.feeds = const {},
    this.loading = false,
    this.error,
    this.pinnedPost,
    this.loadingPin = false,
    this.pinError,
  });

  BlueskyProfileFeed get feed => feeds[selected] ?? const BlueskyProfileFeed();
  List<BlueskyPost> get visiblePosts {
    final pin = pinnedPost;
    return selected != PluginProfileFeedTab.posts || pin == null
        ? feed.posts
        : [pin, ...feed.posts.where((post) => post.uri != pin.uri)];
  }

  BlueskyProfileState copy({
    BlueskyProfile? profile,
    PluginProfileFeedTab? selected,
    Map<PluginProfileFeedTab, BlueskyProfileFeed>? feeds,
    bool? loading,
    Object? error,
    BlueskyPost? pinnedPost,
    bool clearPin = false,
    bool? loadingPin,
    Object? pinError,
  }) => BlueskyProfileState(
    profile: profile ?? this.profile,
    selected: selected ?? this.selected,
    feeds: feeds ?? this.feeds,
    loading: loading ?? this.loading,
    error: error,
    pinnedPost: clearPin ? null : pinnedPost ?? this.pinnedPost,
    loadingPin: loadingPin ?? this.loadingPin,
    pinError: pinError,
  );
}

class BlueskyProfileStore extends Store<BlueskyProfileState> {
  final BlueskyClient client;
  final BlueskyLikesStore likes;
  final String actor;
  final _requests = <PluginProfileFeedTab, int>{};
  final _visitedCursors = <PluginProfileFeedTab, Set<String>>{};
  var _profileRequest = 0;
  var _closed = false;

  BlueskyProfileStore(this.client, this.likes, this.actor) : super(const BlueskyProfileState());

  Future<void> refresh() async {
    if (_closed) return;
    final request = ++_profileRequest;
    final selected = state.selected;
    final canReadFeed = state.profile != null;
    for (final tab in PluginProfileFeedTab.values) {
      _requests[tab] = (_requests[tab] ?? 0) + 1;
    }
    update(
      state.copy(
        feeds: {
          for (final entry in state.feeds.entries)
            entry.key: BlueskyProfileFeed(
              posts: entry.value.posts,
              cursor: entry.value.cursor,
              loaded: entry.value.loaded,
              failedMore: entry.value.failedMore,
              error: entry.value.error,
            ),
        },
        loading: true,
        loadingPin: false,
      ),
    );
    await Future.wait([_refreshProfile(request, loadFeed: !canReadFeed), if (canReadFeed) load(selected)]);
  }

  Future<void> _refreshProfile(int request, {required bool loadFeed}) async {
    final source = client.baseUrl;
    try {
      final profile = await client.getProfile(actor);
      if (!_accept(request, source)) return;
      final changed = _differentIdentity(state.profile, profile);
      if (changed) {
        _visitedCursors.clear();
        for (final tab in PluginProfileFeedTab.values) {
          _requests[tab] = (_requests[tab] ?? 0) + 1;
        }
      }
      update(
        state.copy(
          profile: profile,
          loading: false,
          feeds: changed ? {} : null,
          clearPin: changed || profile.pinnedPostUri != state.pinnedPost?.uri,
        ),
      );
      await Future.wait([_loadPin(profile, request), if (loadFeed || changed) load(state.selected)]);
    } catch (error) {
      if (!_accept(request, source)) return;
      update(state.copy(loading: false, error: error, pinError: state.pinError));
    }
  }

  bool _differentIdentity(BlueskyProfile? previous, BlueskyProfile next) =>
      previous != null &&
      (previous.did.isNotEmpty && next.did.isNotEmpty
          ? previous.did != next.did
          : previous.handle.toLowerCase() != next.handle.toLowerCase());

  bool _accept(int request, String source, {PluginProfileFeedTab? tab}) {
    if (_closed || request != (tab == null ? _profileRequest : _requests[tab])) return false;
    if (source == client.baseUrl) return true;
    unawaited(refresh());
    return false;
  }

  Future<void> select(PluginProfileFeedTab tab) async {
    if (_closed || tab == state.selected) return;
    update(state.copy(selected: tab, error: state.error, pinError: state.pinError));
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
    return [
      for (final post in posts)
        if (seen.add(post.uri) &&
            (switch (tab) {
              PluginProfileFeedTab.replies => post.isReply,
              PluginProfileFeedTab.media => post.hasMedia,
              _ => true,
            }))
          post,
    ];
  }

  void _setFeed(PluginProfileFeedTab tab, BlueskyProfileFeed feed) {
    update(state.copy(feeds: {...state.feeds, tab: feed}, error: state.error, pinError: state.pinError));
  }

  Future<void> load(PluginProfileFeedTab tab, {bool more = false}) async {
    final profile = state.profile;
    final previous = state.feeds[tab] ?? const BlueskyProfileFeed();
    if (_closed || profile == null || previous.loading || (more && previous.cursor == null)) return;
    final request = (_requests[tab] ?? 0) + 1;
    final source = client.baseUrl;
    _requests[tab] = request;
    _setFeed(
      tab,
      BlueskyProfileFeed(posts: previous.posts, cursor: previous.cursor, loaded: previous.loaded, loading: true),
    );
    try {
      final page = tab == PluginProfileFeedTab.saved
          ? await _localLikes(profile)
          : await client.getAuthorFeed(
              profile.did.isEmpty ? profile.handle : profile.did,
              filter: _filter(tab),
              cursor: more ? previous.cursor : null,
            );
      if (!_accept(request, source, tab: tab)) return;
      _setFeed(
        tab,
        BlueskyProfileFeed(
          posts: _visible(tab, [...(more ? previous.posts : <BlueskyPost>[]), ...page.posts]),
          cursor: _nextCursor(tab, page.cursor, more ? previous.cursor : null),
          loaded: true,
        ),
      );
    } catch (error) {
      if (!_accept(request, source, tab: tab)) return;
      _setFeed(
        tab,
        BlueskyProfileFeed(
          posts: previous.posts,
          cursor: previous.cursor,
          loaded: previous.loaded,
          failedMore: more,
          error: error,
        ),
      );
    }
  }

  String? _nextCursor(PluginProfileFeedTab tab, String? value, String? previous) {
    final visited = previous == null ? <String>{} : {...?_visitedCursors[tab], previous};
    _visitedCursors[tab] = visited;
    final cursor = value?.trim();
    return cursor == null || cursor.isEmpty || visited.contains(cursor) ? null : cursor;
  }

  Future<void> retry() => state.error != null ? refresh() : load(state.selected, more: state.feed.failedMore);

  Future<void> retryPin() async {
    final profile = state.profile;
    if (!_closed && profile != null && !state.loadingPin) await _loadPin(profile, _profileRequest);
  }

  Future<void> _loadPin(BlueskyProfile profile, int request) async {
    final uri = profile.pinnedPostUri;
    final source = client.baseUrl;
    if (uri == null || uri.isEmpty || _closed) return;
    update(state.copy(loadingPin: true, error: state.error));
    try {
      final posts = await client.getPosts([uri]);
      if (!_accept(request, source)) return;
      final post = posts.where((post) => post.uri == uri).firstOrNull;
      update(state.copy(pinnedPost: post, clearPin: post == null, loadingPin: false, error: state.error));
    } catch (error) {
      if (!_accept(request, source)) return;
      update(state.copy(loadingPin: false, error: state.error, pinError: error));
    }
  }

  Future<BlueskyFeedPage> _localLikes(BlueskyProfile profile) async {
    if (likes.state.isEmpty) await likes.load();
    return BlueskyFeedPage(
      posts: blueskyLikesByAuthor(likes.likedPosts, did: profile.did, handle: profile.handle),
    );
  }

  Future<void> loadMore() async {
    if (!_closed && !state.feed.loading && state.feed.error == null) {
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
