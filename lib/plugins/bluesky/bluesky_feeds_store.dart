import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_source_reader.dart';

class BlueskyAlgoState {
  final List<BlueskyFeedGenerator> pinned;
  final List<BlueskyFeedGenerator> popular;
  final List<BlueskyFeedGenerator> created;
  final String? selectedUri;
  final String selectedName;
  final BlueskySourcePage page;
  final String query;
  final String? catalogCursor;
  final bool catalogLoading;
  final Object? catalogError;
  final Object? createdError;

  const BlueskyAlgoState({
    this.pinned = const [],
    this.popular = const [],
    this.created = const [],
    this.selectedUri,
    this.selectedName = '',
    this.page = const BlueskySourcePage(),
    this.query = '',
    this.catalogCursor,
    this.catalogLoading = false,
    this.catalogError,
    this.createdError,
  });

  List<BlueskyPost> get posts => page.posts;
  String? get cursor => page.cursor;
  bool get hasMore => page.hasMore;
  bool isPinned(String uri) => pinned.any((feed) => feed.uri == uri);

  BlueskyAlgoState copyWith({
    List<BlueskyFeedGenerator>? pinned,
    List<BlueskyFeedGenerator>? popular,
    List<BlueskyFeedGenerator>? created,
    String? selectedUri,
    String? selectedName,
    BlueskySourcePage? page,
    String? query,
    String? catalogCursor,
    bool clearCatalogCursor = false,
    bool? catalogLoading,
    Object? catalogError,
    bool clearCatalogError = false,
    Object? createdError,
    bool clearCreatedError = false,
  }) => BlueskyAlgoState(
    pinned: pinned ?? this.pinned,
    popular: popular ?? this.popular,
    created: created ?? this.created,
    selectedUri: selectedUri ?? this.selectedUri,
    selectedName: selectedName ?? this.selectedName,
    page: page ?? this.page,
    query: query ?? this.query,
    catalogCursor: clearCatalogCursor ? catalogCursor : catalogCursor ?? this.catalogCursor,
    catalogLoading: catalogLoading ?? this.catalogLoading,
    catalogError: clearCatalogError ? catalogError : catalogError ?? this.catalogError,
    createdError: clearCreatedError ? createdError : createdError ?? this.createdError,
  );
}

/// Guest custom feeds. Catalog search never owns the current reading selection.
class BlueskyAlgoStore extends BlueskySourceStore<BlueskyAlgoState> {
  var _pinsLoaded = false;
  String? _catalogContext;
  String? _catalogServer;
  var _catalogRequest = 0;
  var _createdRequest = 0;
  var _catalogLoaded = false;
  final _catalogCursors = <String>{};

  BlueskyAlgoStore(BlueskyClient client, BasePrefService prefs)
    : super(client, prefs, 'plugin.bluesky.selectedFeeds', const BlueskyAlgoState());

  @override
  String? get sourceUri => state.selectedUri;
  @override
  String get sourceName => state.selectedName;
  @override
  BlueskySourcePage get sourcePage => state.page;
  @override
  Future<BlueskyFeedPage> fetchPage(String uri, {String? cursor}) => client.getFeed(uri, cursor: cursor);
  @override
  void selectSource(String uri, String name, BlueskySourcePage page) =>
      update(state.copyWith(selectedUri: uri, selectedName: name, page: page));

  Future<void> ensureLoaded({bool force = false, String? discoverName}) async {
    if (closed) return;
    _hydratePins();
    final serverChanged = sourceServer != client.baseUrl;
    final saved = serverChanged ? rememberedSource() : null;
    final uri = saved?.$1 ?? (serverChanged ? null : state.selectedUri) ?? kBlueskyDiscoverFeedUri;
    final name = saved?.$2 ?? (uri == kBlueskyDiscoverFeedUri ? discoverName : nameOf(uri));
    await Future.wait([loadCatalog(force: force), open(uri, name: name, force: force)]);
  }

  void _hydratePins() {
    if (_pinsLoaded) return;
    _pinsLoaded = true;
    update(state.copyWith(pinned: blueskyGeneratorsFromPrefs(prefs.get<String>(optionPluginBlueskyPinnedFeeds))));
  }

  Future<void> loadCatalog({bool force = false}) async {
    if (closed) return;
    _hydratePins();
    final handle = (prefs.get<String>(optionPluginBlueskyHandle) ?? '').trim();
    final context = '${client.baseUrl}\n$handle';
    if (!force && _catalogLoaded && _catalogContext == context) return;
    if (_catalogContext != null && _catalogContext != context) {
      update(state.copyWith(popular: [], created: [], clearCatalogError: true, clearCreatedError: true));
    }
    _catalogContext = context;
    await Future.wait([searchCatalog(state.query), _loadCreated(handle)]);
  }

  Future<void> _loadCreated(String handle) async {
    final request = ++_createdRequest;
    final server = client.baseUrl;
    try {
      final feeds = handle.isEmpty ? const <BlueskyFeedGenerator>[] : (await client.getActorFeeds(handle)).feeds;
      if (!closed && request == _createdRequest && server == client.baseUrl) {
        update(state.copyWith(created: feeds, clearCreatedError: true));
      }
    } catch (error) {
      if (!closed && request == _createdRequest && server == client.baseUrl) {
        update(state.copyWith(createdError: error));
      }
    }
  }

  Future<void> searchCatalog(String query, {bool more = false}) async {
    if (closed) return;
    if (more && _catalogServer != client.baseUrl) return searchCatalog(query);
    if (more && (state.catalogLoading || state.catalogCursor == null)) return;
    final request = ++_catalogRequest;
    final server = client.baseUrl;
    final serverChanged = _catalogServer != server;
    _catalogServer = server;
    final text = query.trim();
    final cursor = more ? state.catalogCursor : null;
    if (!more) _catalogCursors.clear();
    update(
      state.copyWith(
        query: text,
        popular: !serverChanged && (more || text == state.query) ? null : [],
        catalogLoading: true,
        clearCatalogError: true,
        clearCatalogCursor: !more,
      ),
    );
    try {
      final page = await client.getPopularFeedGenerators(query: text, cursor: cursor);
      if (closed || request != _catalogRequest || server != client.baseUrl) return;
      if (cursor != null) _catalogCursors.add(cursor);
      final seen = <String>{};
      final feeds = [
        for (final feed in [...(more ? state.popular : <BlueskyFeedGenerator>[]), ...page.feeds])
          if (feed.uri.isNotEmpty && seen.add(feed.uri)) feed,
      ];
      final next = page.cursor;
      update(
        state.copyWith(
          popular: feeds,
          catalogLoading: false,
          clearCatalogCursor: true,
          catalogCursor: next == null || next.isEmpty || _catalogCursors.contains(next) ? null : next,
        ),
      );
      _catalogLoaded = true;
    } catch (error) {
      if (!closed && request == _catalogRequest && server == client.baseUrl) {
        update(state.copyWith(catalogLoading: false, catalogError: error));
      }
    }
  }

  Future<void> loadMoreCatalog() => searchCatalog(state.query, more: true);

  Future<void> pin(BlueskyFeedGenerator feed) async {
    if (closed) return;
    _hydratePins();
    if (feed.uri.isEmpty || state.isPinned(feed.uri)) return;
    final pinned = [...state.pinned, feed];
    update(state.copyWith(pinned: pinned));
    await writePreference(optionPluginBlueskyPinnedFeeds, blueskyGeneratorsToPrefs(pinned));
  }

  Future<void> unpin(String uri) async {
    if (closed) return;
    _hydratePins();
    final pinned = [
      for (final feed in state.pinned)
        if (feed.uri != uri) feed,
    ];
    update(state.copyWith(pinned: pinned));
    await writePreference(optionPluginBlueskyPinnedFeeds, blueskyGeneratorsToPrefs(pinned));
  }

  @override
  String nameOf(String uri) {
    for (final feed in [...state.pinned, ...state.created, ...state.popular]) {
      if (feed.uri == uri) return feed.displayName;
    }
    if (state.selectedUri == uri && state.selectedName.isNotEmpty) return state.selectedName;
    return blueskyRkeyOf(uri) ?? uri;
  }
}

class BlueskyListsState {
  final List<BlueskyListInfo> pinned;
  final List<BlueskyListInfo> actorLists;
  final String? selectedUri;
  final String selectedName;
  final BlueskySourcePage page;
  final String actor;
  final String? listsCursor;
  final bool listsLoading;
  final bool listsLoaded;
  final Object? listsError;

  const BlueskyListsState({
    this.pinned = const [],
    this.actorLists = const [],
    this.selectedUri,
    this.selectedName = '',
    this.page = const BlueskySourcePage(),
    this.actor = '',
    this.listsCursor,
    this.listsLoading = false,
    this.listsLoaded = false,
    this.listsError,
  });

  List<BlueskyPost> get posts => page.posts;
  String? get cursor => page.cursor;
  bool get hasMore => page.hasMore;
  bool isPinned(String uri) => pinned.any((list) => list.uri == uri);

  BlueskyListsState copyWith({
    List<BlueskyListInfo>? pinned,
    List<BlueskyListInfo>? actorLists,
    String? selectedUri,
    String? selectedName,
    BlueskySourcePage? page,
    String? actor,
    String? listsCursor,
    bool clearListsCursor = false,
    bool? listsLoading,
    bool? listsLoaded,
    Object? listsError,
    bool clearListsError = false,
  }) => BlueskyListsState(
    pinned: pinned ?? this.pinned,
    actorLists: actorLists ?? this.actorLists,
    selectedUri: selectedUri ?? this.selectedUri,
    selectedName: selectedName ?? this.selectedName,
    page: page ?? this.page,
    actor: actor ?? this.actor,
    listsCursor: clearListsCursor ? listsCursor : listsCursor ?? this.listsCursor,
    listsLoading: listsLoading ?? this.listsLoading,
    listsLoaded: listsLoaded ?? this.listsLoaded,
    listsError: clearListsError ? listsError : listsError ?? this.listsError,
  );
}

class BlueskyListsStore extends BlueskySourceStore<BlueskyListsState> {
  var _pinsLoaded = false;
  String? _settingsHandle;
  String? _listsServer;
  var _listsRequest = 0;
  final _listsCursors = <String>{};

  BlueskyListsStore(BlueskyClient client, BasePrefService prefs)
    : super(client, prefs, 'plugin.bluesky.selectedLists', const BlueskyListsState());

  @override
  String? get sourceUri => state.selectedUri;
  @override
  String get sourceName => state.selectedName;
  @override
  BlueskySourcePage get sourcePage => state.page;
  @override
  Future<BlueskyFeedPage> fetchPage(String uri, {String? cursor}) => client.getListFeed(uri, cursor: cursor);
  @override
  void selectSource(String uri, String name, BlueskySourcePage page) =>
      update(state.copyWith(selectedUri: uri, selectedName: name, page: page));

  void _hydratePins() {
    if (_pinsLoaded) return;
    _pinsLoaded = true;
    update(state.copyWith(pinned: blueskyListsFromPrefs(prefs.get<String>(optionPluginBlueskyPinnedLists))));
  }

  Future<void> ensureLoaded({bool force = false}) async {
    if (closed) return;
    _hydratePins();
    final serverChanged = sourceServer != null && sourceServer != client.baseUrl;
    final listsServerChanged = _listsServer != null && _listsServer != client.baseUrl;
    final handle = (prefs.get<String>(optionPluginBlueskyHandle) ?? '').trim();
    final actor = _settingsHandle == null && state.actor.isNotEmpty
        ? state.actor
        : _settingsHandle != handle
        ? handle
        : state.actor;
    if (_settingsHandle != null && _settingsHandle != handle) {
      _listsRequest++;
      update(
        state.copyWith(
          actor: handle,
          actorLists: [],
          listsLoaded: false,
          listsLoading: false,
          clearListsCursor: true,
          clearListsError: true,
        ),
      );
    }
    _settingsHandle = handle;
    final saved = state.selectedUri == null || serverChanged ? rememberedSource() : null;
    final selected = serverChanged ? saved?.$1 : state.selectedUri ?? saved?.$1;
    if (serverChanged) update(BlueskyListsState(pinned: state.pinned));
    final uri = selected ?? (state.pinned.isEmpty ? null : state.pinned.first.uri);
    await Future.wait([
      if (actor.isNotEmpty &&
          (force || serverChanged || listsServerChanged || !state.listsLoaded || state.actor != actor))
        lookupActor(actor),
      if (uri != null) open(uri, name: saved?.$2, force: force),
    ]);
  }

  Future<void> lookupActor(String actor, {bool more = false}) async {
    if (closed || actor.trim().isEmpty) return;
    if (more && _listsServer != client.baseUrl) return lookupActor(actor);
    if (more && (state.listsLoading || state.listsCursor == null)) return;
    final request = ++_listsRequest;
    final server = client.baseUrl;
    final serverChanged = _listsServer != server;
    _listsServer = server;
    final cursor = more ? state.listsCursor : null;
    if (!more) _listsCursors.clear();
    update(
      state.copyWith(
        actor: actor,
        actorLists: !serverChanged && actor == state.actor ? null : [],
        listsLoading: true,
        listsLoaded: false,
        clearListsError: true,
        clearListsCursor: !more,
      ),
    );
    try {
      final page = await client.getLists(actor, cursor: cursor);
      if (closed || request != _listsRequest || server != client.baseUrl) return;
      if (cursor != null) _listsCursors.add(cursor);
      final seen = <String>{};
      final lists = [
        for (final list in [...(more ? state.actorLists : <BlueskyListInfo>[]), ...page.lists])
          if (list.uri.isNotEmpty && seen.add(list.uri)) list,
      ];
      final next = page.cursor;
      update(
        state.copyWith(
          actorLists: lists,
          listsLoading: false,
          listsLoaded: true,
          clearListsCursor: true,
          listsCursor: next == null || next.isEmpty || _listsCursors.contains(next) ? null : next,
        ),
      );
    } catch (error) {
      if (!closed && request == _listsRequest && server == client.baseUrl) {
        update(state.copyWith(listsLoading: false, listsError: error));
      }
    }
  }

  Future<void> loadMoreLists() => lookupActor(state.actor, more: true);

  Future<void> pin(BlueskyListInfo list) async {
    if (closed) return;
    _hydratePins();
    if (list.uri.isEmpty || state.isPinned(list.uri)) return;
    final pinned = [...state.pinned, list];
    update(state.copyWith(pinned: pinned));
    await writePreference(optionPluginBlueskyPinnedLists, blueskyListsToPrefs(pinned));
  }

  Future<void> unpin(String uri) async {
    if (closed) return;
    _hydratePins();
    final pinned = [
      for (final list in state.pinned)
        if (list.uri != uri) list,
    ];
    update(state.copyWith(pinned: pinned));
    await writePreference(optionPluginBlueskyPinnedLists, blueskyListsToPrefs(pinned));
  }

  @override
  String nameOf(String uri) {
    for (final list in [...state.pinned, ...state.actorLists]) {
      if (list.uri == uri) return list.name;
    }
    if (state.selectedUri == uri && state.selectedName.isNotEmpty) return state.selectedName;
    return blueskyRkeyOf(uri) ?? uri;
  }
}
