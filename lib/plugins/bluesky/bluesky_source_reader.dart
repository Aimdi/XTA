import 'dart:convert';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_feed.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';

class BlueskySourcePage {
  final List<BlueskyPost> posts;
  final String? cursor;
  final bool loaded;
  final bool loading;
  final bool loadingMore;
  final Object? error;
  final Object? moreError;
  final Set<String> visitedCursors;

  const BlueskySourcePage({
    this.posts = const [],
    this.cursor,
    this.loaded = false,
    this.loading = false,
    this.loadingMore = false,
    this.error,
    this.moreError,
    this.visitedCursors = const {},
  });

  bool get hasMore => cursor != null && cursor!.isNotEmpty;

  BlueskySourcePage busy({bool more = false}) => BlueskySourcePage(
    posts: posts,
    cursor: cursor,
    loaded: loaded,
    loading: !more,
    loadingMore: more,
    visitedCursors: visitedCursors,
  );

  BlueskySourcePage failed(Object failure, {bool more = false}) => BlueskySourcePage(
    posts: posts,
    cursor: cursor,
    loaded: loaded,
    error: more ? error : failure,
    moreError: more ? failure : moreError,
    visitedCursors: visitedCursors,
  );
}

/// Shared paging policy for ranked generators and public lists. Catalogs stay
/// independent, so a failed discovery request cannot block a pinned source.
abstract class BlueskySourceStore<S> extends Store<S> {
  final BlueskyClient client;
  final BasePrefService prefs;
  final String selectionKey;
  final _pages = <(String, String), BlueskySourcePage>{};
  var _request = 0;
  var _closed = false;
  var _feedFetches = 0;
  String? _selectedServer;
  Future<void> _preferenceWrite = Future.value();

  BlueskySourceStore(this.client, this.prefs, this.selectionKey, S initial) : super(initial);

  String? get sourceUri;
  String get sourceName;
  BlueskySourcePage get sourcePage;
  bool get closed => _closed;
  int get feedFetches => _feedFetches;
  String? get sourceServer => _selectedServer;
  void selectSource(String uri, String name, BlueskySourcePage page);
  String nameOf(String uri);
  Future<BlueskyFeedPage> fetchPage(String uri, {String? cursor});

  (String, String)? rememberedSource() {
    try {
      final raw = jsonDecode(prefs.get<String>(selectionKey) ?? '{}');
      final saved = raw is Map ? raw[client.baseUrl] : null;
      final uri = saved is Map ? saved['uri'] : null;
      final name = saved is Map ? saved['name'] : null;
      if (uri is String && uri.startsWith('at://') && name is String) return (uri, name);
    } catch (_) {}
    return null;
  }

  Future<void> _enqueuePreference(Future<void> Function() write) {
    _preferenceWrite = _preferenceWrite.catchError((_) {}).then((_) => write());
    return _preferenceWrite;
  }

  Future<void> writePreference(String key, String value) => _enqueuePreference(() async {
    await prefs.set(key, value);
  });

  Future<void> _remember(String server, String uri, String name) async {
    try {
      await _enqueuePreference(() async {
        final saved = _savedSelections();
        saved[server] = {'uri': uri, 'name': name};
        await prefs.set(selectionKey, jsonEncode(saved));
      });
    } catch (_) {}
  }

  Map<String, dynamic> _savedSelections() {
    try {
      final value = jsonDecode(prefs.get<String>(selectionKey) ?? '{}');
      if (value is Map<String, dynamic>) return value;
    } catch (_) {}
    return {};
  }

  Future<void> open(String uri, {bool force = false, String? name}) async {
    if (_closed || uri.isEmpty) return;
    final server = client.baseUrl;
    final key = (server, uri);
    final title = name ?? nameOf(uri);
    final cached = _pages[key] ?? const BlueskySourcePage();
    if (!force && _selectedServer == server && sourceUri == uri && (sourcePage.loading || sourcePage.loadingMore)) {
      return;
    }
    _selectedServer = server;
    final request = ++_request;
    selectSource(uri, title, cached);
    _remember(server, uri, title);
    if (!force && cached.loaded) return;
    selectSource(uri, title, cached.busy());
    try {
      _feedFetches++;
      final result = await fetchPage(uri);
      if (!_current(request, server, uri)) return;
      _publish(
        key,
        title,
        BlueskySourcePage(posts: dedupeBlueskyPosts(result.posts), cursor: _cursor(result.cursor), loaded: true),
      );
    } catch (error) {
      if (_current(request, server, uri)) _publish(key, title, cached.failed(error));
    }
  }

  Future<void> loadMore() async {
    final uri = sourceUri;
    if (uri != null && _selectedServer != client.baseUrl) {
      await open(uri, force: true);
      return;
    }
    final before = sourcePage;
    if (_closed || uri == null || !before.hasMore || before.loading || before.loadingMore) return;
    final request = _request;
    final server = client.baseUrl;
    final title = sourceName;
    final key = (server, uri);
    selectSource(uri, title, before.busy(more: true));
    try {
      _feedFetches++;
      final result = await fetchPage(uri, cursor: before.cursor);
      if (!_current(request, server, uri)) return;
      final visited = {...before.visitedCursors, before.cursor!};
      final next = _cursor(result.cursor);
      _publish(
        key,
        title,
        BlueskySourcePage(
          posts: dedupeBlueskyPosts([...before.posts, ...result.posts]),
          cursor: visited.contains(next) ? null : next,
          loaded: true,
          visitedCursors: visited,
        ),
      );
    } catch (error) {
      if (_current(request, server, uri)) _publish(key, title, before.failed(error, more: true));
    }
  }

  String? _cursor(String? value) => value == null || value.trim().isEmpty ? null : value;

  bool _current(int request, String server, String uri) =>
      !_closed && request == _request && server == client.baseUrl && uri == sourceUri;

  void _publish((String, String) key, String name, BlueskySourcePage page) {
    _pages.remove(key);
    _pages[key] = page;
    if (_pages.length > 16) _pages.remove(_pages.keys.first);
    selectSource(key.$2, name, page);
  }

  @override
  Future<void> destroy() {
    _closed = true;
    _request++;
    return super.destroy();
  }
}
