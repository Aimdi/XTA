import 'dart:async';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/group/deck_groups.dart';
import 'package:xta/group/future_pool.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:sqflite/sqflite.dart';
import 'package:xta/plugins/account_posts.dart';
import 'package:xta/plugins/plugin_feed_fresh.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_models.dart';

/// The publications the reader follows, kept in the database.
///
/// They used to live in a preferences blob, which is why they could never join
/// a subscription group. Anything still in that blob is imported on first load
/// and the blob cleared, so nobody has to re-add what they already followed.
class SubstackPublicationsStore extends Store<List<SubstackPublication>> {
  final BasePrefService prefs;

  Future<void> _pinWrites = Future.value();
  bool _closing = false;

  SubstackPublicationsStore(this.prefs) : super(const []);

  Future<void> _changePins(Future<List<SubstackPublication>> Function() work) {
    if (_closing) return Future.value();
    _pinWrites = _pinWrites.then((_) async {
      try {
        update(await work());
      } catch (error) {
        setError(error);
      }
    });
    return _pinWrites;
  }

  @override
  Future<void> destroy() async {
    _closing = true;
    await _pinWrites;
    await super.destroy();
  }

  Future<void> load() async {
    await execute(() async {
      await _importFromPrefs();
      return _read();
    });
  }

  Future<List<SubstackPublication>> _read() async {
    final database = await Repository.readOnly();
    final rows = await database.query(tableSubstackSubscription, orderBy: 'name COLLATE NOCASE');

    final publications = rows.map(SubstackSubscription.fromMap).map(publicationOf).toList(growable: false);
    return _withPins(publications);
  }

  List<String> get _pinnedIds => parseDeckGroupIds(prefs.get(optionPluginSubstackPinnedPublications) as String?);

  bool isPinned(String id) => _pinnedIds.contains(id);

  /// Pinned publications first (pin order), then the rest A–Z.
  List<SubstackPublication> _withPins(List<SubstackPublication> publications) {
    final alphabetical = publications.toList()..sort((a, b) => a.name.toLowerCase().compareTo(b.name.toLowerCase()));
    return sortSubstackPublicationsWithPins(alphabetical, _pinnedIds);
  }

  Future<void> togglePinned(String id) async {
    if (id.isEmpty) return;
    await _changePins(() async {
      final ids = _pinnedIds.toList();
      if (ids.contains(id)) {
        ids.remove(id);
      } else {
        ids.add(id);
      }
      if (!await prefs.set(optionPluginSubstackPinnedPublications, joinDeckGroupIds(ids))) {
        throw StateError('Substack preference write failed');
      }
      return _withPins(state);
    });
  }

  Future<void> _importFromPrefs() async {
    final raw = prefs.get<String>(optionPluginSubstackPublications) ?? '';
    if (raw.isEmpty) {
      return;
    }

    for (final publication in SubstackPublication.listFromPrefs(raw)) {
      await _write(publication);
    }
    await prefs.set(optionPluginSubstackPublications, '');
  }

  Future<void> _write(SubstackPublication publication) async {
    final database = await Repository.writable();
    await database.insert(
      tableSubstackSubscription,
      subscriptionOf(publication).toMap(),
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<void> add(SubstackPublication publication) async {
    await execute(() async {
      await _write(publication);
      return _read();
    });
  }

  Future<void> remove(String id) async {
    await _changePins(() async {
      final database = await Repository.writable();
      await database.delete(tableSubstackSubscription, where: 'id = ?', whereArgs: [id]);
      // A publication that is gone should not linger as a member of a group.
      await database.delete(tableSubscriptionGroupMember, where: 'profile_id = ?', whereArgs: [id]);
      final ids = _pinnedIds.toList()..remove(id);
      await prefs.set(optionPluginSubstackPinnedPublications, joinDeckGroupIds(ids));
      return _read();
    });
  }
}

/// The database row for a publication, and back again.
///
/// The plugin thinks in publications and the subscription tables think in
/// subscriptions; these keep the two from having to know each other's shape.
SubstackSubscription subscriptionOf(SubstackPublication publication) => SubstackSubscription(
  id: publication.id,
  baseUrl: publication.baseUrl,
  name: publication.name,
  logoUrl: publication.logoUrl,
  createdAt: DateTime.now(),
  inFeed: true,
);

SubstackPublication publicationOf(SubstackSubscription subscription) => SubstackPublication(
  subdomain: subscription.id,
  baseUrl: subscription.baseUrl,
  name: subscription.name,
  logoUrl: subscription.logoUrl,
);

/// Pinned ids first (in pin order), then the remaining publications unchanged.
List<SubstackPublication> sortSubstackPublicationsWithPins(
  List<SubstackPublication> publications,
  List<String> pinnedIds,
) {
  if (pinnedIds.isEmpty) return publications;

  final byId = {for (final pub in publications) pub.id: pub};
  final pinned = [
    for (final id in pinnedIds)
      if (byId.containsKey(id)) byId[id]!,
  ];
  final pinnedSet = {for (final pub in pinned) pub.id};
  final rest = [
    for (final pub in publications)
      if (!pinnedSet.contains(pub.id)) pub,
  ];
  return [...pinned, ...rest];
}

/// Preference mutations finish in order; a rejected write leaves state intact.
abstract class _SubstackLocalStore<T> extends Store<T> {
  final BasePrefService prefs;
  Future<void> _writes = Future.value();
  bool _closing = false;

  _SubstackLocalStore(this.prefs, super.initialState);

  Future<void> serial(Future<T> Function() work) {
    if (_closing) return Future.value();
    _writes = _writes.then((_) async {
      try {
        update(await work());
      } catch (error) {
        setError(error);
      }
    });
    return _writes;
  }

  Future<void> persist(String key, String value) async {
    if (!await prefs.set(key, value)) throw StateError('Substack preference write failed');
  }

  @override
  Future<void> destroy() async {
    _closing = true;
    await _writes;
    await super.destroy();
  }
}

class SubstackReadStore extends _SubstackLocalStore<Set<String>> {
  SubstackReadStore(BasePrefService prefs) : super(prefs, const {});

  Future<void> load() => serial(() async => readIdsFromPrefs(prefs.get(optionPluginSubstackReadIds)).toSet());
  Future<void> markRead(String id) => markAllRead([id]);
  Future<void> markUnread(String id) => markAllUnread([id]);

  Future<void> markAllRead(Iterable<String> ids) {
    final requested = ids.where((id) => id.isNotEmpty).toSet();
    return serial(() async {
      final next = [...requested, ...state.where((id) => !requested.contains(id))].take(substackReadIdsCap).toSet();
      await persist(optionPluginSubstackReadIds, readIdsToPrefs(next.toList()));
      return next;
    });
  }

  Future<void> markAllUnread(Iterable<String> ids) {
    final requested = ids.toSet();
    return serial(() async {
      final next = state.where((id) => !requested.contains(id)).toSet();
      await persist(optionPluginSubstackReadIds, readIdsToPrefs(next.toList()));
      return next;
    });
  }

  bool isRead(String id) => state.contains(id);
}

abstract class _SubstackPostLibrary extends _SubstackLocalStore<List<SubstackPost>> {
  final String preference;
  final int capacity;
  _SubstackPostLibrary(BasePrefService prefs, this.preference, this.capacity) : super(prefs, const []);

  Future<void> load() => serial(() async => SubstackPost.listFromPrefs(prefs.get(preference)));

  Future<void> toggle(SubstackPost post) {
    if (post.id.isEmpty) return Future.value();
    return serial(() async {
      final remaining = state.where((item) => item.id != post.id);
      final next = (state.any((item) => item.id == post.id) ? remaining : [post, ...remaining]).take(capacity).toList();
      await persist(preference, SubstackPost.listToPrefs(next));
      return next;
    });
  }
}

/// Hearts and bookmarks are local; no Substack write endpoint is used.
class SubstackLikesStore extends _SubstackPostLibrary {
  SubstackLikesStore(BasePrefService prefs) : super(prefs, optionPluginSubstackLikedPosts, substackLikedPostsCap);
  bool isLiked(String id) => state.any((post) => post.id == id);
}

class SubstackSavedStore extends _SubstackPostLibrary {
  SubstackSavedStore(BasePrefService prefs) : super(prefs, optionPluginSubstackSavedPosts, substackSavedPostsCap);
  bool isSaved(String id) => state.any((post) => post.id == id);
}

const substackPublicationsPerBatch = 24;
const substackPublicationConcurrency = 3;

class _PublicationPage {
  final List<SubstackPost> posts;
  final int offset;
  final bool loaded;
  final bool hasMore;
  final Object? error;
  final int? failedOffset;
  final int attempt;
  const _PublicationPage({
    this.posts = const [],
    this.offset = 0,
    this.loaded = false,
    this.hasMore = true,
    this.error,
    this.failedOffset,
    this.attempt = 0,
  });
}

String _sourceUrl(String value) {
  final uri = Uri.tryParse(value.trim());
  return uri == null ? value.trim() : uri.replace(path: uri.path.replaceFirst(RegExp(r'/+$'), '')).toString();
}

String _publicationKey(SubstackPublication publication) => '${publication.id}\n${_sourceUrl(publication.baseUrl)}';
String substackFeedPostKey(SubstackPost post) => '${_sourceUrl(post.publicationBaseUrl)}\n${post.id}';

List<SubstackPost> _mergeSubstackPosts(Iterable<SubstackPost> posts) {
  final unique = <String, SubstackPost>{};
  for (final post in posts) {
    if (post.id.isNotEmpty) unique[substackFeedPostKey(post)] = post;
  }
  return unique.values.toList();
}

class SubstackFeedStore extends Store<SubstackFeedSnapshot> {
  final SubstackClient client;
  final SubstackPublicationsStore publications;
  final _pages = <String, _PublicationPage>{};
  var _allPosts = const <SubstackPost>[];
  var _filter = SubstackFeedFilter.all;
  Set<String> _readIds = const {};
  DateTime? _fetchedAt;
  String? _loadedIdentity;
  String? _activeIdentity;
  Future<void>? _pending;
  var _generation = 0;
  var _attempt = 0;
  var _closed = false;
  bool _refreshing = false;
  bool _loadingMore = false;
  Object? _refreshError;
  Object? _loadMoreError;

  SubstackFeedStore(this.client, this.publications) : super(const SubstackFeedSnapshot());
  SubstackFeedFilter get filter => _filter;
  List<SubstackPost> get allPosts => _allPosts;
  DateTime? get fetchedAt => _fetchedAt;
  Object? get refreshError => _refreshError;
  Object? get loadMoreError => _loadMoreError;
  bool get refreshing => _refreshing;
  bool get loadingMore => _loadingMore;
  int get pendingCount => _sources.where((pub) => _pages[_publicationKey(pub)]?.loaded != true).length;

  List<SubstackPublication> get _sources =>
      {for (final pub in publications.state) _publicationKey(pub): pub}.values.toList();
  String get _identity {
    final keys = _sources.map(_publicationKey).toList()..sort();
    return keys.join('\n');
  }

  Future<void> refresh({bool force = false}) async {
    if (_closed) return;
    final identity = _identity;
    if (_sources.isEmpty) {
      _generation++;
      _pending = null;
      _loadedIdentity = _activeIdentity = identity;
      _refreshing = _loadingMore = false;
      _refreshError = _loadMoreError = null;
      _pages.clear();
      // There is no remote work to refresh, including on a forced refresh.
      _fetchedAt ??= DateTime.now();
      _publish();
      return;
    }
    if (!force && _refreshing && _activeIdentity == identity) return _pending;
    if (!force &&
        _loadedIdentity == identity &&
        pendingCount == 0 &&
        _refreshError == null &&
        pluginFeedIsFresh(_fetchedAt, ttl: kAccountPostsCacheTtl)) {
      return;
    }
    await _load(more: false);
  }

  Future<void> loadMore() async {
    if (_closed || _refreshing || _loadingMore || !state.canLoadMore) return;
    if (_loadedIdentity != null && _loadedIdentity != _identity) return refresh();
    await _load(more: true);
  }

  Future<void> retryLoadMore() => loadMore();

  void setFilter(SubstackFeedFilter filter, Set<String> readIds) {
    if (_closed) return;
    _filter = filter;
    _readIds = Set.of(readIds);
    _publish();
  }

  void syncReadIds(Set<String> readIds) {
    if (_closed) return;
    _readIds = Set.of(readIds);
    _publish();
  }

  Future<void> _load({required bool more}) async {
    final identity = _identity;
    final request = ++_generation;
    final done = Completer<void>();
    _pending = done.future;
    _activeIdentity = identity;
    _refreshing = !more;
    _loadingMore = more;
    if (more) {
      _loadMoreError = null;
    } else {
      _refreshError = null;
      _loadMoreError = null;
    }
    _retainSources();
    _publish();
    if (_allPosts.isEmpty) setLoading(true);
    try {
      final candidates = _candidates(more: more);
      await mapWithConcurrency(
        candidates.take(substackPublicationsPerBatch),
        substackPublicationConcurrency,
        (pub) => _readPublication(pub, request, identity, more: more),
      );
      if (!_current(request, identity)) return;
      _loadedIdentity = identity;
      final failure = _pages.values.where((page) => page.error != null).firstOrNull?.error;
      if (more) {
        _loadMoreError = failure;
        if (failure == null) _refreshError = null;
      } else {
        _refreshError = failure;
      }
      if (failure == null) _fetchedAt = DateTime.now();
      _publish();
      if (_allPosts.isEmpty && failure != null) setError(failure);
    } catch (error) {
      if (!_current(request, identity)) return;
      if (more) {
        _loadMoreError = error;
      } else {
        _refreshError = error;
      }
      if (_allPosts.isEmpty) setError(error);
    } finally {
      if (!_closed && request == _generation) {
        _refreshing = _loadingMore = false;
        setLoading(false);
        final failure = more ? _loadMoreError : _refreshError;
        if (_allPosts.isEmpty && failure != null) {
          setError(failure, force: true);
        } else {
          _publish();
        }
        _pending = null;
      }
      done.complete();
    }
  }

  void _retainSources() {
    final active = _sources.map(_publicationKey).toSet();
    _pages.removeWhere((key, _) => !active.contains(key));
  }

  List<SubstackPublication> _candidates({required bool more}) =>
      _sources.where((pub) {
        final page = _pages[_publicationKey(pub)];
        return !more || page == null || page.error != null || page.hasMore;
      }).toList()..sort((a, b) {
        final left = _pages[_publicationKey(a)] ?? const _PublicationPage();
        final right = _pages[_publicationKey(b)] ?? const _PublicationPage();
        final untouched = (left.attempt == 0 ? 0 : 1).compareTo(right.attempt == 0 ? 0 : 1);
        if (untouched != 0) return untouched;
        // Rotate attempted sources too: permanent failures must not starve
        // older pages from publications that are still readable.
        return left.attempt.compareTo(right.attempt);
      });

  Future<void> _readPublication(SubstackPublication pub, int request, String identity, {required bool more}) async {
    if (!_current(request, identity)) return;
    final key = _publicationKey(pub);
    final before = _pages[key] ?? const _PublicationPage();
    final offset = more ? before.failedOffset ?? before.offset : 0;
    final attempt = ++_attempt;
    try {
      final posts = await client.fetchPosts(pub, limit: substackFeedPageSize, offset: offset);
      if (!_current(request, identity)) return;
      final progressed =
          offset == 0 ||
          posts.any((post) => !before.posts.any((old) => substackFeedPostKey(old) == substackFeedPostKey(post)));
      _pages[key] = _PublicationPage(
        posts: _mergeSubstackPosts([if (offset > 0) ...before.posts, ...posts]),
        offset: offset + substackFeedPageSize,
        loaded: true,
        hasMore: progressed && posts.length >= substackFeedPageSize,
        attempt: attempt,
      );
    } catch (error) {
      if (!_current(request, identity)) return;
      _pages[key] = _PublicationPage(
        posts: before.posts,
        offset: before.offset,
        loaded: before.loaded,
        hasMore: before.hasMore,
        error: error,
        failedOffset: offset,
        attempt: attempt,
      );
    }
    _publish();
  }

  bool _current(int request, String identity) => !_closed && request == _generation && identity == _identity;

  void _publish() {
    _allPosts = _mergeSubstackPosts(_pages.values.expand((page) => page.posts))
      ..sort((a, b) {
        final date = (b.publishedAt ?? DateTime(0)).compareTo(a.publishedAt ?? DateTime(0));
        return date == 0 ? substackFeedPostKey(a).compareTo(substackFeedPostKey(b)) : date;
      });
    update(
      SubstackFeedSnapshot(
        posts: _allPosts.where((post) => postMatchesSubstackFilter(post, _filter, _readIds)).toList(),
        canLoadMore: pendingCount > 0 || _pages.values.any((page) => page.hasMore || page.error != null),
        failedCount: _pages.values.where((page) => page.error != null).length,
      ),
    );
  }

  @override
  Future<void> destroy() {
    _closed = true;
    _generation++;
    return super.destroy();
  }
}

/// Public Notes discovery keeps every cursor bound to its issuing host.
class SubstackNotesStore extends Store<SubstackNotesPage> {
  final SubstackClient client;
  final SubstackPublicationsStore publications;
  String? _host;
  String? _cursor;
  var _hostIndex = 0;
  var _generation = 0;
  var _closed = false;
  bool _refreshing = false;
  bool _loadingMore = false;
  DateTime? _fetchedAt;
  String? _loadedIdentity;
  String? _activeIdentity;
  final _cursors = <String>{};
  Object? _refreshError;
  Object? _loadMoreError;

  SubstackNotesStore(this.client, this.publications) : super(const SubstackNotesPage());
  bool get refreshing => _refreshing;
  bool get loadingMore => _loadingMore;
  Object? get refreshError => _refreshError;
  Object? get loadMoreError => _loadMoreError;
  String get _identity {
    final keys = publications.state.map(_publicationKey).toList()..sort();
    return keys.join('\n');
  }

  Future<void> refresh({bool force = false}) async {
    if (_closed || (_refreshing && !force && _activeIdentity == _identity)) return;
    if (!force &&
        _loadedIdentity == _identity &&
        _refreshError == null &&
        pluginFeedIsFresh(_fetchedAt, ttl: kAccountPostsCacheTtl)) {
      return;
    }
    await _load(more: false);
  }

  Future<void> loadMore() async {
    if (_closed || _refreshing || _loadingMore || _cursor == null) return;
    if (_loadedIdentity != _identity) return refresh();
    await _load(more: true);
  }

  Future<void> retryLoadMore() => loadMore();

  Future<void> _load({required bool more}) async {
    final request = ++_generation;
    final identity = _identity;
    _activeIdentity = identity;
    final host = more ? _host : _nextNotesHost();
    final cursor = more ? _cursor : null;
    _refreshing = !more;
    _loadingMore = more;
    if (more) {
      _loadMoreError = null;
    } else {
      _refreshError = null;
      _loadMoreError = null;
    }
    if (!more && _loadedIdentity != null && _loadedIdentity != identity) update(const SubstackNotesPage());
    if (state.notes.isEmpty) {
      setLoading(true);
    } else {
      update(state, force: true);
    }
    try {
      final page = await client.fetchReaderNotes(host: host, cursor: cursor);
      if (!_current(request, identity)) return;
      if (!more) _cursors.clear();
      if (cursor != null) _cursors.add(cursor);
      final next = page.nextCursor?.trim();
      _cursor = next == null || next.isEmpty || _cursors.contains(next) ? null : next;
      _host = host;
      _loadedIdentity = identity;
      _fetchedAt = DateTime.now();
      final notes = <String, SubstackNote>{};
      for (final note in [if (more) ...state.notes, ...page.notes]) {
        if (note.id.isNotEmpty) notes[note.id] = note;
      }
      update(SubstackNotesPage(notes: notes.values.toList(), nextCursor: _cursor));
    } catch (error) {
      if (!_current(request, identity)) return;
      if (more) {
        _loadMoreError = error;
      } else {
        _refreshError = error;
      }
      if (state.notes.isEmpty) {
        setError(error);
      } else {
        update(state, force: true);
      }
    } finally {
      if (!_closed && request == _generation) {
        _refreshing = _loadingMore = false;
        setLoading(false);
        final failure = more ? _loadMoreError : _refreshError;
        if (state.notes.isEmpty && failure != null) {
          setError(failure, force: true);
        } else {
          update(state, force: true);
        }
      }
    }
  }

  bool _current(int request, String identity) => !_closed && request == _generation && identity == _identity;

  String? _nextNotesHost() {
    final pubs = publications.state;
    if (pubs.isEmpty) return null;
    final host = Uri.tryParse(pubs[_hostIndex % pubs.length].baseUrl)?.host;
    _hostIndex = (_hostIndex + 1) % pubs.length;
    return host;
  }

  @override
  Future<void> destroy() {
    _closed = true;
    _generation++;
    return super.destroy();
  }
}

class SubstackArchiveStore extends Store<SubstackFeedSnapshot> {
  final SubstackClient client;
  final SubstackPublication publication;

  var _offset = 0;

  SubstackArchiveStore(this.client, this.publication)
    : super(const SubstackFeedSnapshot());

  Future<void> refresh() async {
    _offset = 0;
    await execute(() => _fetchPage(replace: true));
  }

  Future<void> loadMore() async {
    if (!state.canLoadMore) return;
    await execute(() => _fetchPage(replace: false));
  }

  Future<SubstackFeedSnapshot> _fetchPage({required bool replace}) async {
    final page = await client.fetchPosts(
      publication,
      limit: substackFeedPageSize,
      offset: _offset,
    );
    _offset += substackFeedPageSize;
    final posts = replace
        ? page
        : [
            ...state.posts,
            ...page.where((e) => !state.posts.any((p) => p.id == e.id)),
          ];
    return SubstackFeedSnapshot(
      posts: posts,
      canLoadMore: page.length >= substackFeedPageSize,
      failedCount: 0,
    );
  }
}

class SubstackAddPublicationStore extends Store<SubstackPublication?> {
  final SubstackClient client;

  SubstackAddPublicationStore(this.client) : super(null);

  Future<SubstackPublication> lookup(String input) async {
    if (input.trim().isEmpty) {
      final error = SubstackClientException('Invalid Substack URL or handle');
      setError(error);
      throw error;
    }
    await execute(() => client.resolvePublication(input));
    final result = state;
    if (result == null) {
      final error = SubstackNotPublicationException();
      setError(error);
      throw error;
    }
    return result;
  }
}
