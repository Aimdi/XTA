import 'dart:convert';

import 'package:flutter/foundation.dart' show listEquals;
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:sqflite/sqflite.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/plugins/account_posts.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';

/// The instances the reader configured, home first, in the order given.
///
/// May be empty — the plugin still works, because [mastodonInstanceCandidates]
/// falls through to the built-in defaults. A corrupt stored list reads as
/// having none rather than wedging the plugin shut.
List<String> mastodonConfiguredInstances(BasePrefService prefs) {
  final home = (prefs.get<String>(optionPluginMastodonInstance) ?? '').trim();
  final raw = prefs.get<String>(optionPluginMastodonInstances) ?? '';

  var extras = const <String>[];
  if (raw.isNotEmpty) {
    try {
      final decoded = jsonDecode(raw);
      if (decoded is List) {
        extras = decoded.whereType<String>().toList(growable: false);
      }
    } catch (_) {}
  }

  return [if (home.isNotEmpty) home, ...extras];
}

/// Fediverse accounts the reader follows locally, kept in the database.
class MastodonAccountsStore extends Store<List<MastodonAccount>> {
  MastodonAccountsStore() : super(const []);

  Future<void> load() async {
    await execute(_read);
  }

  Future<List<MastodonAccount>> _read() async {
    final database = await Repository.readOnly();
    final rows = await database.query(tableMastodonSubscription, orderBy: 'name COLLATE NOCASE');

    return rows.map(MastodonSubscription.fromMap).map(accountOf).toList(growable: false);
  }

  Future<void> add(MastodonAccount account) async {
    await execute(() async {
      final database = await Repository.writable();
      await database.insert(
        tableMastodonSubscription,
        subscriptionOf(account).toMap(),
        conflictAlgorithm: ConflictAlgorithm.replace,
      );
      return _read();
    });
  }

  Future<void> remove(String acct) async {
    await execute(() async {
      final database = await Repository.writable();
      await database.delete(tableMastodonSubscription, where: 'id = ?', whereArgs: [acct]);
      await database.delete(tableSubscriptionGroupMember, where: 'profile_id = ?', whereArgs: [acct]);
      return _read();
    });
  }

  bool follows(String acct) => state.any((e) => e.acct == acct);
}

MastodonSubscription subscriptionOf(MastodonAccount account) => MastodonSubscription(
  id: account.acct,
  name: account.name,
  avatarUrl: account.avatarUrl,
  createdAt: DateTime.now(),
  inFeed: true,
);

MastodonAccount accountOf(MastodonSubscription subscription) =>
    MastodonAccount(acct: subscription.id, name: subscription.name, avatarUrl: subscription.avatarUrl);

abstract class _MastodonReadStore<T> extends Store<T> {
  _MastodonReadStore(super.initialState);

  var _generation = 0;
  var _epoch = 0;
  var _disposed = false;
  Object? _refreshError;

  Object? get refreshError => _refreshError;

  bool _current(int generation) => !_disposed && generation == _generation;

  void _invalidateReads() {
    _generation++;
    _epoch++;
    _refreshError = null;
  }

  void _readFailed(Object error, {required bool hasContent}) {
    _refreshError = error;
    if (hasContent) {
      update(state, force: true);
    } else {
      setError(error);
    }
  }

  @override
  Future<void> destroy() async {
    _disposed = true;
    _invalidateReads();
    await super.destroy();
  }
}

/// Merged timeline of every followed acct, newest first.
class MastodonFeedStore extends _MastodonReadStore<List<MastodonPost>> {
  final MastodonClient client;
  final BasePrefService prefs;
  final MastodonAccountsStore accounts;

  MastodonFeedStore(this.client, this.prefs, this.accounts) : super(const []);

  void forget() {
    if (_disposed) return;
    _invalidateReads();
    _posts = _newCache();
    update(const [], force: true);
  }

  Future<void> refresh({bool force = false}) async {
    if (_disposed) return;
    final read = _readContext();
    final generation = ++_generation;
    _refreshError = null;
    update(state, force: true);
    setLoading(state.isEmpty);
    try {
      final posts = await _load(
        accounts.state.map((account) => account.acct).toList(growable: false),
        read,
        forceRefresh: force,
        onPartial: (posts) {
          if (_current(generation)) _paintPartial(posts);
        },
      );
      if (_current(generation)) update(posts, force: true);
    } catch (error) {
      if (_current(generation)) _readFailed(error, hasContent: state.isNotEmpty);
    }
  }

  void _paintPartial(List<MastodonPost> posts) {
    update(_followingPosts([...posts, ...state]));
  }

  List<MastodonPost> _followingPosts(List<MastodonPost> posts) =>
      appendUniqueMastodonPosts(const [], posts)
        ..sort((a, b) => (b.timelineDate ?? DateTime(0)).compareTo(a.timelineDate ?? DateTime(0)));

  /// Posts for [accts], newest first.
  ///
  /// Per account, not one shared home: each acct is asked for at its own
  /// instance first, which is the only place guaranteed to have all of it.
  /// Bounded per call for the same reason as Bluesky — these are somebody's
  /// hobby servers, and a long follow list should not arrive as a burst.
  Future<List<MastodonPost>> postsFor(
    List<String> accts, {
    bool forceRefresh = false,
    void Function(List<MastodonPost>)? onPartial,
  }) async {
    if (_disposed) return const [];
    final read = _readContext();
    final epoch = _epoch;
    final posts = await _load(
      accts,
      read,
      forceRefresh: forceRefresh,
      onPartial: (posts) {
        if (!_disposed && epoch == _epoch) onPartial?.call(posts);
      },
    );
    return !_disposed && epoch == _epoch ? posts : const [];
  }

  ({AccountPostCache<MastodonPost> cache, List<String> configured}) _readContext() {
    final configured = mastodonConfiguredInstances(prefs);
    if (!listEquals(_configured, configured)) {
      _invalidateReads();
      _configured = configured;
      _posts = _newCache();
    } else if (_activeCaches.contains(_posts)) {
      // A superseded merge must not repopulate the newer request's cache.
      _posts = _newCache();
    }
    return (cache: _posts, configured: configured);
  }

  Future<List<MastodonPost>> _load(
    List<String> accts,
    ({AccountPostCache<MastodonPost> cache, List<String> configured}) read, {
    required bool forceRefresh,
    void Function(List<MastodonPost>)? onPartial,
  }) async {
    _activeCaches.add(read.cache);
    try {
      final posts = await read.cache.merge(
        accts,
        (acct) => client.fetchAccountAnywhere(
          mastodonFeedInstanceCandidates(acct, configured: read.configured),
          acct,
          limit: mastodonPostsPerAccount,
        ),
        forceRefresh: forceRefresh,
        maxFetches: mastodonMaxAccountsPerLoad,
        onPartial: (posts) => onPartial?.call(_followingPosts(posts)),
      );
      return _followingPosts(posts);
    } finally {
      _activeCaches.remove(read.cache);
    }
  }

  List<String>? _configured;

  final _activeCaches = <AccountPostCache<MastodonPost>>{};
  var _posts = _newCache();

  static AccountPostCache<MastodonPost> _newCache() => AccountPostCache<MastodonPost>(
    dateOf: (post) => post.timelineDate,
    perAccount: mastodonPostsPerAccount,
    concurrency: 2,
  );
}

/// Home instance first, then extras, then the built-in defaults.
List<String> mastodonDiscoveryInstances(BasePrefService prefs) {
  final ordered = [...mastodonConfiguredInstances(prefs), ...kMastodonDefaultInstances];
  final seen = <String>{};
  return [
    for (final candidate in ordered)
      if (normaliseMastodonInstance(candidate) case final instance? when seen.add(instance)) instance,
  ];
}

const _publicPageSize = 30;

/// Canonical URLs retain their first position, preferring original posts to
/// boosts so hiding boosts cannot also hide a followed author's own post.
List<MastodonPost> appendUniqueMastodonPosts(List<MastodonPost> current, List<MastodonPost> more) {
  final positions = <String, int>{};
  final posts = <MastodonPost>[];
  for (final post in [...current, ...more]) {
    final key = canonicalMastodonPostKey(post);
    final position = positions[key];
    if (position == null) {
      positions[key] = posts.length;
      posts.add(post);
    } else if (posts[position].boosted && !post.boosted) {
      posts[position] = post;
    }
  }
  return posts;
}

/// One public timeline (local or federated) that pages with `max_id`.
class MastodonPublicFeedStore extends _MastodonReadStore<List<MastodonPost>> {
  final MastodonClient client;
  final BasePrefService prefs;
  final bool local;

  MastodonPublicFeedStore(this.client, this.prefs, {required this.local}) : super(const []);

  String? _instance;

  /// The server that supplied this timeline, including a fallback when used.
  String? get instance => _instance;

  void restoreReading(List<MastodonPost> posts, String? instance) {
    if (_disposed || state.isNotEmpty || posts.isEmpty || _refreshing) return;
    _instance = normaliseMastodonInstance(instance ?? '');
    _nextId = posts.last.pagingId;
    _hasMore = true;
    _loadMoreError = null;
    update(appendUniqueMastodonPosts(const [], posts));
  }

  var _hasMore = true;
  var _loadingMore = false;
  var _refreshing = false;
  String? _nextId;
  Object? _loadMoreError;

  Object? get loadMoreError => _loadMoreError;

  bool get canLoadMore =>
      !_disposed && _hasMore && !_loadingMore && !_refreshing && _loadMoreError == null && state.isNotEmpty;

  bool get loadingMore => _loadingMore;

  void forget() {
    if (_disposed) return;
    _invalidateReads();
    _instance = null;
    _nextId = null;
    _hasMore = true;
    _loadingMore = false;
    _refreshing = false;
    _loadMoreError = null;
    update(const [], force: true);
  }

  Future<void> refresh() async {
    if (_disposed) return;
    final generation = ++_generation;
    _refreshError = null;
    _refreshing = true;
    _loadingMore = false;
    update(state, force: true);
    setLoading(state.isEmpty);
    try {
      final page = await _firstPage();
      if (!_current(generation)) return;
      _instance = page.instance;
      _nextId = page.posts.lastOrNull?.pagingId;
      _hasMore = page.posts.length >= _publicPageSize;
      _loadMoreError = null;
      _refreshing = false;
      update(appendUniqueMastodonPosts(const [], page.posts), force: true);
    } catch (error) {
      if (_current(generation)) {
        _refreshing = false;
        _readFailed(error, hasContent: state.isNotEmpty);
      }
    }
  }

  Future<void> loadMore() async {
    final instance = _instance;
    if (!canLoadMore || instance == null) return;
    final generation = _generation;
    final cursor = _nextId ?? state.last.pagingId;
    _loadingMore = true;
    update(state, force: true);
    try {
      final more = await client.getPublicTimeline(instance, local: local, limit: _publicPageSize, maxId: cursor);
      if (!_current(generation)) return;
      _nextId = more.lastOrNull?.pagingId;
      _hasMore = more.length >= _publicPageSize && _nextId != cursor;
      update(appendUniqueMastodonPosts(state, more));
    } catch (error) {
      if (_current(generation)) _loadMoreError = error;
    } finally {
      if (_current(generation)) {
        _loadingMore = false;
        update(state, force: true);
      }
    }
  }

  Future<void> retryLoadMore() async {
    if (_disposed || _refreshing || _loadingMore || _loadMoreError == null) return;
    _loadMoreError = null;
    await loadMore();
  }

  Future<({String instance, List<MastodonPost> posts})> _firstPage() async {
    return client.firstInstanceThat(mastodonDiscoveryInstances(prefs), (instance) async {
      final posts = await client.getPublicTimeline(instance, local: local, limit: _publicPageSize);
      return (instance: instance, posts: posts);
    });
  }
}

class MastodonLocalStore extends MastodonPublicFeedStore {
  MastodonLocalStore(super.client, super.prefs) : super(local: true);
}

class MastodonFederatedStore extends MastodonPublicFeedStore {
  MastodonFederatedStore(super.client, super.prefs) : super(local: false);
}

class MastodonExplorePage {
  final List<MastodonTrendingTag> tags;
  final List<MastodonPost> posts;

  const MastodonExplorePage({this.tags = const [], this.posts = const []});
}

/// Trending tags + trending statuses — Phanpy / Tusky Explore.
class MastodonExploreStore extends _MastodonReadStore<MastodonExplorePage> {
  final MastodonClient client;
  final BasePrefService prefs;

  MastodonExploreStore(this.client, this.prefs) : super(const MastodonExplorePage());

  void forget() {
    if (_disposed) return;
    _invalidateReads();
    update(const MastodonExplorePage(), force: true);
  }

  Future<void> refresh() async {
    if (_disposed) return;
    final generation = ++_generation;
    _refreshError = null;
    update(state, force: true);
    setLoading(state.posts.isEmpty && state.tags.isEmpty);
    try {
      final page = await _load();
      if (_current(generation)) update(page, force: true);
    } catch (error) {
      if (!_current(generation)) return;
      _readFailed(error, hasContent: state.posts.isNotEmpty || state.tags.isNotEmpty);
    }
  }

  Future<MastodonExplorePage> _load() async {
    final instances = mastodonDiscoveryInstances(prefs);
    final tagsFuture = _soft(() => client.getTrendingTagsAnywhere(instances), const <MastodonTrendingTag>[]);
    final posts = await client.getTrendingStatusesAnywhere(instances);
    return MastodonExplorePage(tags: await tagsFuture, posts: appendUniqueMastodonPosts(const [], posts));
  }

  Future<T> _soft<T>(Future<T> Function() read, T fallback) async {
    try {
      return await read();
    } catch (_) {
      return fallback;
    }
  }
}
