import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/group/deck_groups.dart';
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

  SubstackPublicationsStore(this.prefs) : super(const []);

  Future<void> load() async {
    await execute(() async {
      await _importFromPrefs();
      return _read();
    });
  }

  Future<List<SubstackPublication>> _read() async {
    final database = await Repository.readOnly();
    final rows = await database.query(
      tableSubstackSubscription,
      orderBy: 'name COLLATE NOCASE',
    );

    final publications = rows
        .map(SubstackSubscription.fromMap)
        .map(publicationOf)
        .toList(growable: false);
    return _withPins(publications);
  }

  List<String> get _pinnedIds => parseDeckGroupIds(
    prefs.get(optionPluginSubstackPinnedPublications) as String?,
  );

  bool isPinned(String id) => _pinnedIds.contains(id);

  /// Pinned publications first (pin order), then the rest A–Z.
  List<SubstackPublication> _withPins(List<SubstackPublication> publications) =>
      sortSubstackPublicationsWithPins(publications, _pinnedIds);

  Future<void> togglePinned(String id) async {
    if (id.isEmpty) return;
    await execute(() async {
      final ids = _pinnedIds.toList();
      if (ids.contains(id)) {
        ids.remove(id);
      } else {
        ids.add(id);
      }
      await prefs.set(
        optionPluginSubstackPinnedPublications,
        joinDeckGroupIds(ids),
      );
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
    await execute(() async {
      final database = await Repository.writable();
      await database.delete(
        tableSubstackSubscription,
        where: 'id = ?',
        whereArgs: [id],
      );
      // A publication that is gone should not linger as a member of a group.
      await database.delete(
        tableSubscriptionGroupMember,
        where: 'profile_id = ?',
        whereArgs: [id],
      );
      final ids = _pinnedIds.toList()..remove(id);
      await prefs.set(
        optionPluginSubstackPinnedPublications,
        joinDeckGroupIds(ids),
      );
      return _read();
    });
  }
}

/// The database row for a publication, and back again.
///
/// The plugin thinks in publications and the subscription tables think in
/// subscriptions; these keep the two from having to know each other's shape.
SubstackSubscription subscriptionOf(SubstackPublication publication) =>
    SubstackSubscription(
      id: publication.id,
      baseUrl: publication.baseUrl,
      name: publication.name,
      logoUrl: publication.logoUrl,
      createdAt: DateTime.now(),
      inFeed: true,
    );

SubstackPublication publicationOf(SubstackSubscription subscription) =>
    SubstackPublication(
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

class SubstackReadStore extends Store<Set<String>> {
  final BasePrefService prefs;

  SubstackReadStore(this.prefs) : super(const {});

  Future<void> load() async {
    await execute(() async {
      return readIdsFromPrefs(prefs.get(optionPluginSubstackReadIds)).toSet();
    });
  }

  Future<void> markRead(String id) async {
    if (id.isEmpty || state.contains(id)) return;
    await execute(() async {
      final next = [id, ...state];
      final capped = next.take(substackReadIdsCap).toList();
      await prefs.set(optionPluginSubstackReadIds, readIdsToPrefs(capped));
      return capped.toSet();
    });
  }

  /// Marks every id in [ids] as read (newest first in the capped list).
  Future<void> markAllRead(Iterable<String> ids) async {
    final fresh = ids
        .where((id) => id.isNotEmpty && !state.contains(id))
        .toList();
    if (fresh.isEmpty) return;
    await execute(() async {
      final next = [...fresh, ...state];
      final capped = next.take(substackReadIdsCap).toList();
      await prefs.set(optionPluginSubstackReadIds, readIdsToPrefs(capped));
      return capped.toSet();
    });
  }

  bool isRead(String id) => state.contains(id);
}

/// Hearts that stay on this device — Substack is never told (like Threads likes).
class SubstackLikesStore extends Store<List<SubstackPost>> {
  final BasePrefService prefs;

  SubstackLikesStore(this.prefs) : super(const []);

  Future<void> load() async {
    await execute(
      () async =>
          SubstackPost.listFromPrefs(prefs.get(optionPluginSubstackLikedPosts)),
    );
  }

  bool isLiked(String id) => state.any((p) => p.id == id);

  Future<void> toggle(SubstackPost post) async {
    if (post.id.isEmpty) return;
    await execute(() async {
      final next = isLiked(post.id)
          ? state.where((p) => p.id != post.id).toList()
          : [post, ...state.where((p) => p.id != post.id)];
      final capped = next.take(substackLikedPostsCap).toList();
      await prefs.set(
        optionPluginSubstackLikedPosts,
        SubstackPost.listToPrefs(capped),
      );
      return capped;
    });
  }
}

/// Bookmarks that stay on this device — the Substack-app Save stand-in.
class SubstackSavedStore extends Store<List<SubstackPost>> {
  final BasePrefService prefs;

  SubstackSavedStore(this.prefs) : super(const []);

  Future<void> load() async {
    await execute(
      () async =>
          SubstackPost.listFromPrefs(prefs.get(optionPluginSubstackSavedPosts)),
    );
  }

  bool isSaved(String id) => state.any((p) => p.id == id);

  Future<void> toggle(SubstackPost post) async {
    if (post.id.isEmpty) return;
    await execute(() async {
      final next = isSaved(post.id)
          ? state.where((p) => p.id != post.id).toList()
          : [post, ...state.where((p) => p.id != post.id)];
      final capped = next.take(substackSavedPostsCap).toList();
      await prefs.set(
        optionPluginSubstackSavedPosts,
        SubstackPost.listToPrefs(capped),
      );
      return capped;
    });
  }
}

class SubstackFeedStore extends Store<SubstackFeedSnapshot> {
  final SubstackClient client;
  final SubstackPublicationsStore publications;

  var _offset = 0;
  var _allPosts = const <SubstackPost>[];
  var _filter = SubstackFeedFilter.all;
  Set<String> _readIds = const {};
  DateTime? _fetchedAt;

  SubstackFeedStore(this.client, this.publications)
    : super(const SubstackFeedSnapshot());

  SubstackFeedFilter get filter => _filter;

  /// Unfiltered merged posts (Home chips / Inbox read from this).
  List<SubstackPost> get allPosts => _allPosts;

  DateTime? get fetchedAt => _fetchedAt;

  /// When the home strip remounts this tab, skip a full refetch if the last
  /// one is still inside [kAccountPostsCacheTtl]. Pull-to-refresh passes
  /// [force].
  Future<void> refresh({bool force = false}) async {
    if (publications.state.isEmpty) {
      if (_allPosts.isNotEmpty) {
        _allPosts = const [];
        update(const SubstackFeedSnapshot());
      }
      _fetchedAt ??= DateTime.now();
      return;
    }
    if (!force &&
        _allPosts.isNotEmpty &&
        pluginFeedIsFresh(_fetchedAt, ttl: kAccountPostsCacheTtl)) {
      return;
    }
    _offset = 0;
    if (_allPosts.isNotEmpty) {
      try {
        update(await _fetchPage(replace: true));
      } catch (_) {
        update(state);
      }
      return;
    }
    await execute(() => _fetchPage(replace: true));
  }

  Future<void> loadMore() async {
    if (!state.canLoadMore) return;
    await execute(() => _fetchPage(replace: false));
  }

  /// Applies a local inbox filter without refetching.
  void setFilter(SubstackFeedFilter filter, Set<String> readIds) {
    _filter = filter;
    _readIds = readIds;
    update(
      _snapshotFromCache(
        canLoadMore: state.canLoadMore,
        failedCount: state.failedCount,
      ),
    );
  }

  void syncReadIds(Set<String> readIds) {
    _readIds = readIds;
    if (_filter == SubstackFeedFilter.unread) {
      update(
        _snapshotFromCache(
          canLoadMore: state.canLoadMore,
          failedCount: state.failedCount,
        ),
      );
    }
  }

  Future<SubstackFeedSnapshot> _fetchPage({required bool replace}) async {
    final pubs = publications.state;
    if (pubs.isEmpty) {
      _allPosts = const [];
      return const SubstackFeedSnapshot();
    }

    final results = await Future.wait(
      pubs.map((p) async {
        try {
          final posts = await client.fetchPosts(
            p,
            limit: substackFeedPageSize,
            offset: _offset,
          );
          return (posts: posts, failed: false);
        } catch (_) {
          return (posts: const <SubstackPost>[], failed: true);
        }
      }),
    );

    final pagePosts = results.expand((e) => e.posts).toList();
    final failedCount = results.where((e) => e.failed).length;
    final canLoadMore = results.any(
      (e) => e.posts.length >= substackFeedPageSize,
    );

    _offset += substackFeedPageSize;

    final merged = replace ? pagePosts : _mergePosts(_allPosts, pagePosts);
    merged.sort((a, b) => (b.postDate ?? '').compareTo(a.postDate ?? ''));
    _allPosts = merged;
    _fetchedAt = DateTime.now();

    return _snapshotFromCache(
      canLoadMore: canLoadMore,
      failedCount: replace ? failedCount : state.failedCount,
    );
  }

  SubstackFeedSnapshot _snapshotFromCache({
    required bool canLoadMore,
    required int failedCount,
  }) {
    final visible = _allPosts
        .where((p) => postMatchesSubstackFilter(p, _filter, _readIds))
        .toList();
    return SubstackFeedSnapshot(
      posts: visible,
      canLoadMore: canLoadMore,
      failedCount: failedCount,
    );
  }

  List<SubstackPost> _mergePosts(
    List<SubstackPost> existing,
    List<SubstackPost> incoming,
  ) {
    final seen = existing.map((e) => e.id).toSet();
    return [...existing, ...incoming.where((e) => !seen.contains(e.id))];
  }
}

/// Global public Notes discovery (not a personalized Following timeline).
class SubstackNotesStore extends Store<SubstackNotesPage> {
  final SubstackClient client;
  final SubstackPublicationsStore publications;

  var _notes = const <SubstackNote>[];
  String? _cursor;
  var _hostIndex = 0;
  DateTime? _fetchedAt;

  SubstackNotesStore(this.client, this.publications)
    : super(const SubstackNotesPage());

  Future<void> refresh({bool force = false}) async {
    if (!force &&
        _notes.isNotEmpty &&
        pluginFeedIsFresh(_fetchedAt, ttl: kAccountPostsCacheTtl)) {
      return;
    }
    _cursor = null;
    if (_notes.isNotEmpty) {
      try {
        update(await _fetch(replace: true));
      } catch (_) {
        update(state);
      }
      return;
    }
    await execute(() => _fetch(replace: true));
  }

  Future<void> loadMore() async {
    if (state.nextCursor == null || state.nextCursor!.isEmpty) return;
    await execute(() => _fetch(replace: false));
  }

  Future<SubstackNotesPage> _fetch({required bool replace}) async {
    final hostName = _nextNotesHost();
    final page = await client.fetchReaderNotes(
      host: hostName,
      cursor: replace ? null : _cursor,
    );
    _cursor = page.nextCursor;
    final merged = replace ? page.notes : _mergeNotes(_notes, page.notes);
    _notes = merged;
    _fetchedAt = DateTime.now();
    return SubstackNotesPage(notes: merged, nextCursor: page.nextCursor);
  }

  /// Rotate among followed hosts so Notes discovery is not stuck on one pub.
  String? _nextNotesHost() {
    final pubs = publications.state;
    if (pubs.isEmpty) return null;
    final host = Uri.tryParse(pubs[_hostIndex % pubs.length].baseUrl)?.host;
    _hostIndex = (_hostIndex + 1) % pubs.length;
    return host;
  }

  List<SubstackNote> _mergeNotes(
    List<SubstackNote> existing,
    List<SubstackNote> incoming,
  ) {
    final seen = existing.map((e) => e.id).toSet();
    return [...existing, ...incoming.where((e) => !seen.contains(e.id))];
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
