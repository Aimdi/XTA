import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_feed.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';

/// Discover / algo feeds: official What's Hot, popular generators, local pins.
class BlueskyAlgoState {
  final List<BlueskyFeedGenerator> pinned;
  final List<BlueskyFeedGenerator> popular;
  final List<BlueskyFeedGenerator> created;
  final String? selectedUri;
  final String selectedName;
  final List<BlueskyPost> posts;
  final String? cursor;

  const BlueskyAlgoState({
    this.pinned = const [],
    this.popular = const [],
    this.created = const [],
    this.selectedUri,
    this.selectedName = '',
    this.posts = const [],
    this.cursor,
  });

  bool get hasMore => cursor != null && cursor!.isNotEmpty;

  bool isPinned(String uri) => pinned.any((e) => e.uri == uri);

  BlueskyAlgoState copyWith({
    List<BlueskyFeedGenerator>? pinned,
    List<BlueskyFeedGenerator>? popular,
    List<BlueskyFeedGenerator>? created,
    String? selectedUri,
    String? selectedName,
    List<BlueskyPost>? posts,
    String? cursor,
    bool clearCursor = false,
  }) => BlueskyAlgoState(
    pinned: pinned ?? this.pinned,
    popular: popular ?? this.popular,
    created: created ?? this.created,
    selectedUri: selectedUri ?? this.selectedUri,
    selectedName: selectedName ?? this.selectedName,
    posts: posts ?? this.posts,
    cursor: clearCursor ? cursor : (cursor ?? this.cursor),
  );
}

class BlueskyAlgoStore extends Store<BlueskyAlgoState> {
  final BlueskyClient client;
  final BasePrefService prefs;

  BlueskyAlgoStore(this.client, this.prefs) : super(const BlueskyAlgoState());

  var _catalogLoaded = false;
  var _feedFetches = 0;

  /// How many times [getFeed] ran — tests assert remounts do not loop.
  int get feedFetches => _feedFetches;

  /// First open: catalog + Discover. Later opens reuse the cache.
  Future<void> ensureLoaded({bool force = false, String? discoverName}) async {
    if (!force && _catalogLoaded && state.posts.isNotEmpty) {
      return;
    }
    await loadCatalog(force: force);
    await open(
      state.selectedUri ?? kBlueskyDiscoverFeedUri,
      force: force,
      name: state.selectedName.isNotEmpty ? state.selectedName : discoverName,
    );
  }

  Future<void> loadCatalog({bool force = false}) async {
    if (!force && _catalogLoaded) {
      return;
    }
    final pinned = blueskyGeneratorsFromPrefs(
      prefs.get<String>(optionPluginBlueskyPinnedFeeds),
    );
    update(state.copyWith(pinned: pinned));
    try {
      final popular = await client.getPopularFeedGenerators(limit: 20);
      final handle = (prefs.get<String>(optionPluginBlueskyHandle) ?? '')
          .trim();
      final created = handle.isEmpty
          ? const <BlueskyFeedGenerator>[]
          : (await client.getActorFeeds(handle)).feeds;
      update(
        state.copyWith(
          popular: popular.feeds,
          created: created,
          pinned: pinned,
        ),
      );
    } catch (_) {
      update(state.copyWith(pinned: pinned));
    }
    _catalogLoaded = true;
  }

  Future<void> open(String feedUri, {bool force = false, String? name}) async {
    if (!force &&
        state.selectedUri == feedUri &&
        state.posts.isNotEmpty &&
        !isLoading) {
      return;
    }
    final title = name ?? _nameOf(feedUri);
    if (state.posts.isEmpty) {
      await execute(() => _loadFeed(feedUri, title: title));
      return;
    }
    try {
      update(await _loadFeed(feedUri, title: title));
    } catch (_) {
      update(state.copyWith(selectedUri: feedUri, selectedName: title));
    }
  }

  Future<void> loadMore() async {
    final uri = state.selectedUri;
    final cursor = state.cursor;
    if (uri == null || cursor == null || cursor.isEmpty || isLoading) {
      return;
    }
    try {
      final page = await client.getFeed(uri, cursor: cursor);
      _feedFetches++;
      final posts = stabilizeBlueskyFeed([...state.posts, ...page.posts]);
      update(
        state.copyWith(posts: posts, cursor: page.cursor, clearCursor: true),
      );
    } catch (_) {}
  }

  Future<void> pin(BlueskyFeedGenerator feed) async {
    if (feed.uri.isEmpty || state.isPinned(feed.uri)) {
      return;
    }
    final pinned = [...state.pinned, feed];
    await prefs.set(
      optionPluginBlueskyPinnedFeeds,
      blueskyGeneratorsToPrefs(pinned),
    );
    update(state.copyWith(pinned: pinned));
  }

  Future<void> unpin(String uri) async {
    final pinned = [
      for (final feed in state.pinned)
        if (feed.uri != uri) feed,
    ];
    await prefs.set(
      optionPluginBlueskyPinnedFeeds,
      blueskyGeneratorsToPrefs(pinned),
    );
    update(state.copyWith(pinned: pinned));
  }

  Future<BlueskyAlgoState> _loadFeed(
    String feedUri, {
    required String title,
  }) async {
    final page = await client.getFeed(feedUri);
    _feedFetches++;
    return state.copyWith(
      selectedUri: feedUri,
      selectedName: title,
      posts: stabilizeBlueskyFeed(page.posts),
      cursor: page.cursor,
      clearCursor: true,
    );
  }

  String _nameOf(String uri) {
    for (final feed in [...state.pinned, ...state.created, ...state.popular]) {
      if (feed.uri == uri) {
        return feed.displayName;
      }
    }
    return blueskyRkeyOf(uri) ?? uri;
  }
}

/// Public lists: pinned AT-URIs, lists by a handle, and that list's posts.
class BlueskyListsState {
  final List<BlueskyListInfo> pinned;
  final List<BlueskyListInfo> actorLists;
  final String? selectedUri;
  final String selectedName;
  final List<BlueskyPost> posts;
  final String? cursor;

  const BlueskyListsState({
    this.pinned = const [],
    this.actorLists = const [],
    this.selectedUri,
    this.selectedName = '',
    this.posts = const [],
    this.cursor,
  });

  bool get hasMore => cursor != null && cursor!.isNotEmpty;

  bool isPinned(String uri) => pinned.any((e) => e.uri == uri);

  BlueskyListsState copyWith({
    List<BlueskyListInfo>? pinned,
    List<BlueskyListInfo>? actorLists,
    String? selectedUri,
    String? selectedName,
    List<BlueskyPost>? posts,
    String? cursor,
    bool clearCursor = false,
  }) => BlueskyListsState(
    pinned: pinned ?? this.pinned,
    actorLists: actorLists ?? this.actorLists,
    selectedUri: selectedUri ?? this.selectedUri,
    selectedName: selectedName ?? this.selectedName,
    posts: posts ?? this.posts,
    cursor: clearCursor ? cursor : (cursor ?? this.cursor),
  );
}

class BlueskyListsStore extends Store<BlueskyListsState> {
  final BlueskyClient client;
  final BasePrefService prefs;

  BlueskyListsStore(this.client, this.prefs) : super(const BlueskyListsState());

  var _hydrated = false;
  var _feedFetches = 0;

  int get feedFetches => _feedFetches;

  Future<void> ensureLoaded({bool force = false}) async {
    if (!force && _hydrated) {
      return;
    }
    final pinned = blueskyListsFromPrefs(
      prefs.get<String>(optionPluginBlueskyPinnedLists),
    );
    update(state.copyWith(pinned: pinned));
    final handle = (prefs.get<String>(optionPluginBlueskyHandle) ?? '').trim();
    if (handle.isNotEmpty) {
      try {
        await lookupActor(handle);
      } catch (_) {}
    }
    _hydrated = true;
    if (state.selectedUri != null && force) {
      await open(state.selectedUri!, force: true);
    } else if (state.selectedUri == null && pinned.isNotEmpty) {
      await open(pinned.first.uri, name: pinned.first.name, force: force);
    }
  }

  Future<void> lookupActor(String actor) async {
    final page = await client.getLists(actor);
    update(state.copyWith(actorLists: page.lists));
  }

  Future<void> open(String listUri, {bool force = false, String? name}) async {
    if (!force &&
        state.selectedUri == listUri &&
        state.posts.isNotEmpty &&
        !isLoading) {
      return;
    }
    final title = name ?? _nameOf(listUri);
    if (state.posts.isEmpty) {
      await execute(() => _loadFeed(listUri, title: title));
      return;
    }
    try {
      update(await _loadFeed(listUri, title: title));
    } catch (_) {
      update(state.copyWith(selectedUri: listUri, selectedName: title));
    }
  }

  Future<void> loadMore() async {
    final uri = state.selectedUri;
    final cursor = state.cursor;
    if (uri == null || cursor == null || cursor.isEmpty || isLoading) {
      return;
    }
    try {
      final page = await client.getListFeed(uri, cursor: cursor);
      _feedFetches++;
      final posts = stabilizeBlueskyFeed([...state.posts, ...page.posts]);
      update(
        state.copyWith(posts: posts, cursor: page.cursor, clearCursor: true),
      );
    } catch (_) {}
  }

  Future<void> pin(BlueskyListInfo list) async {
    if (list.uri.isEmpty || state.isPinned(list.uri)) {
      return;
    }
    final pinned = [...state.pinned, list];
    await prefs.set(
      optionPluginBlueskyPinnedLists,
      blueskyListsToPrefs(pinned),
    );
    update(state.copyWith(pinned: pinned));
  }

  Future<void> unpin(String uri) async {
    final pinned = [
      for (final list in state.pinned)
        if (list.uri != uri) list,
    ];
    await prefs.set(
      optionPluginBlueskyPinnedLists,
      blueskyListsToPrefs(pinned),
    );
    update(state.copyWith(pinned: pinned));
  }

  Future<BlueskyListsState> _loadFeed(
    String listUri, {
    required String title,
  }) async {
    final page = await client.getListFeed(listUri);
    _feedFetches++;
    return state.copyWith(
      selectedUri: listUri,
      selectedName: title,
      posts: stabilizeBlueskyFeed(page.posts),
      cursor: page.cursor,
      clearCursor: true,
    );
  }

  String _nameOf(String uri) {
    for (final list in [...state.pinned, ...state.actorLists]) {
      if (list.uri == uri) {
        return list.name;
      }
    }
    return blueskyRkeyOf(uri) ?? uri;
  }
}
