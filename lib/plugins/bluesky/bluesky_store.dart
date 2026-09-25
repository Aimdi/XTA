import 'dart:async';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:sqflite/sqflite.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/plugins/account_posts.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_feed.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/plugin_feed_fresh.dart';

/// The Bluesky accounts the reader follows locally, kept in the database.
class BlueskyAccountsStore extends Store<List<BlueskyAccount>> {
  BlueskyAccountsStore() : super(const []);

  Future<void> load() async {
    await execute(_read);
  }

  Future<List<BlueskyAccount>> _read() async {
    final database = await Repository.readOnly();
    final rows = await database.query(tableBlueskySubscription, orderBy: 'name COLLATE NOCASE');

    return rows.map(BlueskySubscription.fromMap).map(accountOf).toList(growable: false);
  }

  Future<void> add(BlueskyAccount account) async {
    await execute(() async {
      final database = await Repository.writable();
      await database.insert(
        tableBlueskySubscription,
        subscriptionOf(account).toMap(),
        conflictAlgorithm: ConflictAlgorithm.replace,
      );
      return _read();
    });
  }

  /// Inserts many local follows in one write pass. Skips handles already known.
  ///
  /// Returns how many rows were newly written — used by the import progress UI.
  Future<int> addMany(Iterable<BlueskyAccount> accounts) async {
    final existing = {for (final account in state) account.handle.toLowerCase()};
    final fresh = <BlueskyAccount>[];
    for (final account in accounts) {
      final handle = account.handle.trim();
      if (handle.isEmpty) {
        continue;
      }
      final key = handle.toLowerCase();
      if (!existing.add(key)) {
        continue;
      }
      fresh.add(account);
    }

    if (fresh.isEmpty) {
      return 0;
    }

    final database = await Repository.writable();
    final batch = database.batch();
    for (final account in fresh) {
      batch.insert(
        tableBlueskySubscription,
        subscriptionOf(account).toMap(),
        conflictAlgorithm: ConflictAlgorithm.replace,
      );
    }
    await batch.commit(noResult: true);
    update(await _read());
    return fresh.length;
  }

  Future<void> remove(String handle) async {
    await execute(() async {
      final database = await Repository.writable();
      await database.delete(tableBlueskySubscription, where: 'id = ?', whereArgs: [handle]);
      await database.delete(tableSubscriptionGroupMember, where: 'profile_id = ?', whereArgs: [handle]);
      return _read();
    });
  }

  bool follows(String handle) {
    final key = handle.trim().toLowerCase();
    return state.any((e) => e.handle.toLowerCase() == key);
  }
}

BlueskySubscription subscriptionOf(BlueskyAccount account) => BlueskySubscription(
  id: account.handle,
  name: account.name,
  avatarUrl: account.avatarUrl,
  createdAt: DateTime.now(),
  inFeed: true,
);

BlueskyAccount accountOf(BlueskySubscription subscription) =>
    BlueskyAccount(handle: subscription.id, name: subscription.name, avatarUrl: subscription.avatarUrl);

/// The merged timeline of every followed account, newest activity first.
class BlueskyFeedStore extends Store<List<BlueskyPost>> {
  final BlueskyClient client;
  final BlueskyAccountsStore accounts;

  BlueskyFeedStore(this.client, this.accounts) : super(const []);

  DateTime? _fetchedAt;
  String? _enteredFor;
  String? _paintedServer;
  _BlueskyReadCache? _cache;
  Future<void>? _inFlight;
  Object? _refreshError;
  var _generation = 0;
  var _closed = false;

  DateTime? get fetchedAt => _fetchedAt;
  Object? get refreshError => _refreshError;

  String _entryIdentity() {
    final actors = accounts.state.map((account) => account.actor).toList()..sort();
    return '${client.baseUrl}\n${actors.join('\n')}';
  }

  Future<void> ensureLoaded({bool force = false}) async {
    if (_closed) return;
    final identity = _entryIdentity();
    if (!force && _enteredFor == identity && _refreshError == null) return;
    if (!force && _enteredFor == null && _cache == null && state.isNotEmpty) {
      _enteredFor = identity;
      _paintedServer = client.baseUrl;
      return;
    }
    await refresh(force: force);
  }

  Future<void> refresh({bool force = false}) async {
    if (_closed) return;
    final identity = _entryIdentity();
    if (!force && _inFlight != null && _activeIdentity == identity) return _inFlight;
    final actors = accounts.state.map((account) => account.actor).toSet().toList();
    if (!force &&
        _enteredFor == identity &&
        _refreshError == null &&
        state.isNotEmpty &&
        pending(actors) == 0 &&
        pluginFeedIsFresh(_fetchedAt)) {
      return;
    }
    final request = ++_generation;
    _activeIdentity = identity;
    final done = Completer<void>();
    _inFlight = done.future;
    try {
      await _refresh(actors, identity, request, force);
    } finally {
      if (request == _generation) _inFlight = null;
      done.complete();
    }
  }

  String? _activeIdentity;

  Future<void> _refresh(List<String> actors, String identity, int request, bool force) async {
    final server = client.baseUrl;
    if (_paintedServer != null && _paintedServer != server) {
      update(const []);
      _fetchedAt = null;
    }
    _paintedServer = server;
    final previousError = _refreshError;
    _refreshError = null;
    if (state.isEmpty) setLoading(true);
    try {
      final result = await _read(
        actors,
        force: force,
        onPartial: (posts) {
          if (_current(identity, request)) _emit(posts);
        },
      );
      if (!_current(identity, request)) return;
      _refreshError = result.error;
      _enteredFor = result.error == null ? identity : null;
      if (result.error == null) _fetchedAt = DateTime.now();
      _emit(result.posts, complete: true, notify: previousError != _refreshError);
      if (result.error != null && state.isEmpty) setError(result.error!);
    } catch (error) {
      if (!_current(identity, request)) return;
      _refreshError = error;
      _enteredFor = null;
      if (state.isEmpty) {
        setError(error);
      } else {
        update(List.of(state));
      }
    } finally {
      if (_current(identity, request)) setLoading(false);
    }
  }

  bool _current(String identity, int request) => !_closed && request == _generation && identity == _entryIdentity();

  /// Group reads share the author cache, but never publish into Following.
  Future<List<BlueskyPost>> postsFor(
    List<String> actors, {
    bool forceRefresh = false,
    void Function(List<BlueskyPost>)? onPartial,
  }) async {
    final result = await _read(actors, force: forceRefresh, onPartial: onPartial);
    if (result.posts.isEmpty && result.error != null) throw result.error!;
    return result.posts;
  }

  Future<({List<BlueskyPost> posts, Object? error})> _read(
    List<String> actors, {
    required bool force,
    void Function(List<BlueskyPost>)? onPartial,
  }) async {
    final server = client.baseUrl;
    if (_cache?.server != server) _cache = _BlueskyReadCache(server);
    final cache = _cache!;
    return cache.read(actors, client, force: force, onPartial: onPartial);
  }

  void _emit(List<BlueskyPost> posts, {bool complete = false, bool notify = false}) {
    final next = stabilizeBlueskyFeed(complete ? posts : [...posts, ...state]);
    final replace = complete ? !sameBlueskyFeedPage(state, next) : blueskyFeedShouldReplace(state, next);
    if (replace || notify) update(next);
  }

  int pending(List<String> actors) =>
      _cache?.server == client.baseUrl ? _cache!.pending(actors) : actors.toSet().length;

  @override
  Future<void> destroy() {
    _closed = true;
    _generation++;
    return super.destroy();
  }
}

/// Keeps failed authors retryable while preserving their last successful page.
class _BlueskyReadCache {
  final String server;
  _BlueskyReadCache(this.server);

  final _pages = <String, List<BlueskyPost>>{};
  final _readAt = <String, DateTime>{};
  final _attemptedAt = <String, DateTime>{};
  final _failures = <String, Object>{};
  final _requests = <String, int>{};
  final _posts = AccountPostCache<BlueskyPost>(
    dateOf: (post) => post.timelineDate,
    perAccount: blueskyPostsPerAccount,
    concurrency: 2,
  );

  int pending(List<String> actors) {
    final distinct = actors.toSet();
    return distinct.where(_failures.containsKey).length +
        _posts.pendingCount(distinct.where((actor) => !_failures.containsKey(actor)).toList());
  }

  List<String> _prioritize(List<String> actors) => actors.toSet().toList()
    ..sort((a, b) {
      final unreadOrder = (_attemptedAt.containsKey(a) ? 1 : 0).compareTo(_attemptedAt.containsKey(b) ? 1 : 0);
      if (unreadOrder != 0) return unreadOrder;
      final failureOrder = (_failures.containsKey(a) ? 0 : 1).compareTo(_failures.containsKey(b) ? 0 : 1);
      if (failureOrder != 0) return failureOrder;
      final times = _failures.containsKey(a) ? _attemptedAt : _readAt;
      return (times[a] ?? DateTime(0)).compareTo(times[b] ?? DateTime(0));
    });

  Future<({List<BlueskyPost> posts, Object? error})> read(
    List<String> actors,
    BlueskyClient client, {
    required bool force,
    void Function(List<BlueskyPost>)? onPartial,
  }) async {
    final ordered = _prioritize(actors);
    final errors = <String, Object>{};
    final retry = ordered.any(_failures.containsKey);
    List<BlueskyPost> result = const [];
    try {
      result = await _posts.merge(
        ordered,
        (actor) => _fetch(actor, client, errors),
        forceRefresh: force || retry,
        maxFetches: blueskyMaxAccountsPerLoad,
        onPartial: onPartial == null ? null : (rows) => onPartial(stabilizeBlueskyFeed(rows)),
      );
    } catch (error) {
      if (errors.isEmpty) rethrow;
    }
    final failed = ordered.where(_failures.containsKey).toList();
    return (
      posts: stabilizeBlueskyFeed([...result, for (final actor in failed) ...?_pages[actor]]),
      error: errors.isEmpty ? (failed.isEmpty ? null : _failures[failed.first]) : errors.values.first,
    );
  }

  Future<List<BlueskyPost>> _fetch(String actor, BlueskyClient client, Map<String, Object> errors) async {
    final request = (_requests[actor] ?? 0) + 1;
    _requests[actor] = request;
    _attemptedAt[actor] = DateTime.now();
    try {
      if (client.baseUrl != server) throw StateError('Bluesky AppView changed');
      final page = await client.getAuthorFeed(actor, limit: blueskyPostsPerAccount);
      if (_requests[actor] != request) return _pages[actor] ?? page.posts;
      _pages[actor] = page.posts;
      _readAt[actor] = DateTime.now();
      _failures.remove(actor);
      return page.posts;
    } catch (error) {
      if (_requests[actor] == request) _failures[actor] = error;
      errors[actor] = error;
      rethrow;
    }
  }
}
