import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';

enum BlueskySearchTab { people, posts }

enum BlueskySearchSort { latest, top }

/// A public post locator. A profile link with additional path segments is not one.
({String actor, String rkey})? blueskySearchPostTarget(String input) {
  final value = input.trim();
  final at = RegExp(r'^at://([^/?#]+)/app\.bsky\.feed\.post/([A-Za-z0-9._~:-]{1,512})$').firstMatch(value);
  if (at != null) {
    final actor = at.group(1)!;
    final rkey = at.group(2)!;
    if (blueskySearchActor(actor) == null || rkey == '.' || rkey == '..') return null;
    return (actor: actor, rkey: rkey);
  }
  final uri = Uri.tryParse(value);
  if (uri == null || uri.userInfo.isNotEmpty || uri.hasPort) return null;
  final path = uri.pathSegments.toList();
  if (path.isNotEmpty && path.last.isEmpty) path.removeLast();
  final String actor;
  final String rkey;
  if (uri.scheme == 'https' &&
      const ['bsky.app', 'www.bsky.app'].contains(uri.host) &&
      path.length == 4 &&
      path[0] == 'profile' &&
      path[2] == 'post') {
    actor = path[1];
    rkey = path[3];
  } else {
    return null;
  }
  if (blueskySearchActor(actor) == null ||
      rkey == '.' ||
      rkey == '..' ||
      !RegExp(r'^[A-Za-z0-9._~:-]{1,512}$').hasMatch(rkey)) {
    return null;
  }
  return (actor: actor, rkey: rkey);
}

String? blueskySearchActor(String input) {
  final value = input.trim();
  if (RegExp(r'^did:[a-z0-9]+:[A-Za-z0-9._:%-]+$').hasMatch(value)) return value;
  final uri = Uri.tryParse(value);
  if (uri != null && uri.hasScheme) {
    final path = uri.pathSegments.where((part) => part.isNotEmpty).toList();
    if (uri.scheme != 'https' ||
        uri.userInfo.isNotEmpty ||
        uri.hasPort ||
        !const ['bsky.app', 'www.bsky.app'].contains(uri.host) ||
        path.length != 2 ||
        path.first != 'profile') {
      return null;
    }
    return blueskySearchActor(path[1]);
  }
  return normaliseBlueskyHandle(value);
}

class BlueskySearchPage<T> {
  final List<T> items;
  final String? cursor;
  final bool attempted;
  final bool loading;
  final bool loadingMore;
  final Object? error;
  final bool retryMore;
  const BlueskySearchPage({
    this.items = const [],
    this.cursor,
    this.attempted = false,
    this.loading = false,
    this.loadingMore = false,
    this.error,
    this.retryMore = false,
  });

  BlueskySearchPage<T> pending({bool more = false}) =>
      BlueskySearchPage(items: items, cursor: cursor, attempted: true, loading: !more, loadingMore: more);
  BlueskySearchPage<T> failed(Object error, {bool more = false}) =>
      BlueskySearchPage(items: items, cursor: cursor, attempted: true, error: error, retryMore: more);
}

class BlueskySearchState {
  final String query;
  final BlueskySearchTab tab;
  final BlueskySearchSort sort;
  final String author;
  final String tag;
  final BlueskySearchPage<BlueskyProfile> people;
  final BlueskySearchPage<BlueskyPost> posts;
  final BlueskySearchPage<BlueskyProfile> suggestions;
  const BlueskySearchState({
    this.query = '',
    this.tab = BlueskySearchTab.people,
    this.sort = BlueskySearchSort.latest,
    this.author = '',
    this.tag = '',
    this.people = const BlueskySearchPage(),
    this.posts = const BlueskySearchPage(),
    this.suggestions = const BlueskySearchPage(),
  });

  BlueskySearchState copyWith({
    String? query,
    BlueskySearchTab? tab,
    BlueskySearchSort? sort,
    String? author,
    String? tag,
    BlueskySearchPage<BlueskyProfile>? people,
    BlueskySearchPage<BlueskyPost>? posts,
    BlueskySearchPage<BlueskyProfile>? suggestions,
  }) => BlueskySearchState(
    query: query ?? this.query,
    tab: tab ?? this.tab,
    sort: sort ?? this.sort,
    author: author ?? this.author,
    tag: tag ?? this.tag,
    people: people ?? this.people,
    posts: posts ?? this.posts,
    suggestions: suggestions ?? this.suggestions,
  );
}

/// Independent result pages; query epochs prevent late responses crossing searches.
class BlueskySearchStore extends Store<BlueskySearchState> {
  final BlueskyClient client;
  var _peopleRequest = 0;
  var _postRequest = 0;
  var _suggestionsRequest = 0;
  var _closed = false;
  late String _source = client.baseUrl;
  final _peopleCursors = <String>{};
  final _postCursors = <String>{};

  BlueskySearchStore(this.client, {BlueskySearchTab initialTab = BlueskySearchTab.people, String initialAuthor = ''})
    : super(BlueskySearchState(tab: initialTab, author: initialAuthor));

  Future<void> search(String input) async {
    if (_closed) return;
    _syncSource();
    final query = input.trim();
    if (query != state.query) {
      _peopleRequest++;
      _postRequest++;
      _peopleCursors.clear();
      _postCursors.clear();
      update(state.copyWith(query: query, people: const BlueskySearchPage(), posts: const BlueskySearchPage()));
    }
    if (blueskySearchPostTarget(query) != null) update(state.copyWith(tab: BlueskySearchTab.posts));
    if (query.isEmpty) {
      await loadSuggestions();
    } else {
      await refresh();
    }
  }

  Future<void> select(BlueskySearchTab tab) async {
    if (_closed) return;
    _syncSource();
    if (tab == state.tab) return;
    update(state.copyWith(tab: tab));
    if (state.query.isEmpty) return;
    if (tab == BlueskySearchTab.people && !state.people.attempted ||
        tab == BlueskySearchTab.posts && !state.posts.attempted) {
      await refresh();
    }
  }

  Future<void> setFilters({BlueskySearchSort? sort, String? author, String? tag}) async {
    if (_closed) return;
    _syncSource();
    if ((sort ?? state.sort) == state.sort &&
        (author ?? state.author) == state.author &&
        (tag ?? state.tag) == state.tag) {
      return;
    }
    _postRequest++;
    _postCursors.clear();
    update(state.copyWith(sort: sort, author: author, tag: tag, posts: const BlueskySearchPage()));
    if (state.query.isNotEmpty && state.tab == BlueskySearchTab.posts) await _loadPosts();
  }

  Future<void> refresh() async {
    if (_closed || state.query.isEmpty) return;
    _syncSource();
    if (state.tab == BlueskySearchTab.people) {
      _peopleCursors.clear();
      await _loadPeople();
    } else {
      _postCursors.clear();
      await _loadPosts();
    }
  }

  Future<void> loadMore() async {
    if (_closed || state.query.isEmpty) return;
    _syncSource();
    if (state.tab == BlueskySearchTab.people) {
      if (state.people.cursor != null && !state.people.loading && !state.people.loadingMore) {
        await _loadPeople(more: true);
      }
    } else if (state.posts.cursor != null && !state.posts.loading && !state.posts.loadingMore) {
      await _loadPosts(more: true);
    }
  }

  Future<void> loadSuggestions() async {
    if (_closed) return;
    _syncSource();
    if (state.suggestions.loading) return;
    final source = client.baseUrl;
    final request = ++_suggestionsRequest;
    update(state.copyWith(suggestions: state.suggestions.pending()));
    try {
      final people = await client.getSuggestions(limit: 20);
      if (_closed || request != _suggestionsRequest || !_acceptSource(source)) return;
      update(state.copyWith(suggestions: BlueskySearchPage(items: people, attempted: true)));
    } catch (error) {
      if (!_closed && request == _suggestionsRequest && _acceptSource(source)) {
        update(state.copyWith(suggestions: state.suggestions.failed(error)));
      }
    }
  }

  Future<void> _loadPeople({bool more = false}) async {
    final source = client.baseUrl;
    final request = ++_peopleRequest;
    final query = state.query;
    final previous = state.people;
    final cursor = more ? previous.cursor : null;
    update(state.copyWith(people: previous.pending(more: more)));
    try {
      final direct = blueskySearchActor(query);
      final page = direct == null
          ? await client.searchActorsPage(query, limit: 25, cursor: cursor)
          : BlueskyActorsPage(actors: [await client.getProfile(direct)]);
      if (_closed || request != _peopleRequest || !_acceptSource(source)) return;
      final items = _unique([
        if (more) ...previous.items,
        ...page.actors,
      ], (person) => person.did.isNotEmpty ? person.did : person.handle.toLowerCase());
      update(
        state.copyWith(
          people: BlueskySearchPage(
            items: items,
            attempted: true,
            cursor: _nextCursor(page.cursor, cursor, _peopleCursors),
          ),
        ),
      );
    } catch (error) {
      if (!_closed && request == _peopleRequest && _acceptSource(source)) {
        update(state.copyWith(people: previous.failed(error, more: more)));
      }
    }
  }

  Future<void> _loadPosts({bool more = false}) async {
    final source = client.baseUrl;
    final request = ++_postRequest;
    final snapshot = state;
    final previous = snapshot.posts;
    final cursor = more ? previous.cursor : null;
    update(state.copyWith(posts: previous.pending(more: more)));
    try {
      final page = await _fetchPosts(snapshot, cursor);
      if (_closed || request != _postRequest || !_acceptSource(source)) return;
      final items = _unique([if (more) ...previous.items, ...page.posts], (post) => post.uri);
      update(
        state.copyWith(
          posts: BlueskySearchPage(
            items: items,
            attempted: true,
            cursor: _nextCursor(page.cursor, cursor, _postCursors),
          ),
        ),
      );
    } catch (error) {
      if (!_closed && request == _postRequest && _acceptSource(source)) {
        update(state.copyWith(posts: previous.failed(error, more: more)));
      }
    }
  }

  Future<BlueskyFeedPage> _fetchPosts(BlueskySearchState snapshot, String? cursor) async {
    final target = blueskySearchPostTarget(snapshot.query);
    if (target != null) {
      final actor = target.actor.startsWith('did:') ? target.actor : (await client.getProfile(target.actor)).did;
      if (actor.isEmpty) throw BlueskyException(BlueskyErrorKind.notFound, 'Missing actor DID');
      final posts = await client.getPosts(['at://$actor/app.bsky.feed.post/${target.rkey}']);
      if (posts.isEmpty) throw BlueskyException(BlueskyErrorKind.notFound, 'Post unavailable');
      return BlueskyFeedPage(posts: posts);
    }
    final query = snapshot.query;
    final hashtag = RegExp(r'^#[^\s#]+$').hasMatch(query) ? query.substring(1) : null;
    final tags = {if (snapshot.tag.isNotEmpty) snapshot.tag, ?hashtag};
    return client.searchPosts(
      hashtag == null ? query : '',
      limit: 25,
      cursor: cursor,
      sort: snapshot.sort.name,
      author: snapshot.author.isEmpty ? null : snapshot.author,
      tags: tags.toList(),
    );
  }

  void _syncSource() {
    if (_source == client.baseUrl) return;
    _source = client.baseUrl;
    _peopleRequest++;
    _postRequest++;
    _suggestionsRequest++;
    _peopleCursors.clear();
    _postCursors.clear();
    update(
      state.copyWith(
        people: const BlueskySearchPage(),
        posts: const BlueskySearchPage(),
        suggestions: const BlueskySearchPage(),
      ),
    );
  }

  bool _acceptSource(String source) {
    if (source == client.baseUrl) return true;
    _syncSource();
    final error = BlueskyException(BlueskyErrorKind.network, 'AppView changed during search');
    update(
      state.copyWith(
        people: state.people.failed(error),
        posts: state.posts.failed(error),
        suggestions: state.suggestions.failed(error),
      ),
    );
    return false;
  }

  @override
  Future<void> destroy() {
    _closed = true;
    _peopleRequest++;
    _postRequest++;
    _suggestionsRequest++;
    return super.destroy();
  }
}

List<T> _unique<T>(Iterable<T> items, String Function(T) identity) {
  final seen = <String>{};
  return items.where((item) => identity(item).isNotEmpty && seen.add(identity(item))).toList(growable: false);
}

String? _nextCursor(String? value, String? current, Set<String> seen) {
  final next = value?.trim();
  if (current != null) seen.add(current);
  return next == null || next.isEmpty || next == current || seen.contains(next) ? null : next;
}
