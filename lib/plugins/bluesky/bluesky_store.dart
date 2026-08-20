import 'package:flutter_triple/flutter_triple.dart';
import 'package:sqflite/sqflite.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/plugins/account_posts.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';

/// The Bluesky accounts the reader follows locally, kept in the database.
class BlueskyAccountsStore extends Store<List<BlueskyAccount>> {
  BlueskyAccountsStore() : super(const []);

  Future<void> load() async {
    await execute(_read);
  }

  Future<List<BlueskyAccount>> _read() async {
    final database = await Repository.readOnly();
    final rows = await database.query(
      tableBlueskySubscription,
      orderBy: 'name COLLATE NOCASE',
    );

    return rows
        .map(BlueskySubscription.fromMap)
        .map(accountOf)
        .toList(growable: false);
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
    final existing = {
      for (final account in state) account.handle.toLowerCase(),
    };
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
      await database.delete(
        tableBlueskySubscription,
        where: 'id = ?',
        whereArgs: [handle],
      );
      await database.delete(
        tableSubscriptionGroupMember,
        where: 'profile_id = ?',
        whereArgs: [handle],
      );
      return _read();
    });
  }

  bool follows(String handle) {
    final key = handle.trim().toLowerCase();
    return state.any((e) => e.handle.toLowerCase() == key);
  }
}

BlueskySubscription subscriptionOf(BlueskyAccount account) =>
    BlueskySubscription(
      id: account.handle,
      name: account.name,
      avatarUrl: account.avatarUrl,
      createdAt: DateTime.now(),
      inFeed: true,
    );

BlueskyAccount accountOf(BlueskySubscription subscription) => BlueskyAccount(
  handle: subscription.id,
  name: subscription.name,
  avatarUrl: subscription.avatarUrl,
);

/// The merged timeline of every followed account, newest first.
class BlueskyFeedStore extends Store<List<BlueskyPost>> {
  final BlueskyClient client;
  final BlueskyAccountsStore accounts;

  BlueskyFeedStore(this.client, this.accounts) : super(const []);

  /// Reads the followed accounts and merges them.
  Future<void> refresh({bool force = false}) async {
    final actors = accounts.state.map((e) => e.actor).toList(growable: false);
    if (state.isNotEmpty) {
      try {
        update(await postsFor(actors, forceRefresh: force, onPartial: update));
      } catch (_) {
        update(state);
      }
      return;
    }
    await execute(
      () => postsFor(actors, forceRefresh: force, onPartial: update),
    );
  }

  /// Posts for [actors], newest first — used by the Bluesky tab and by group
  /// feeds that mix Bluesky members in beside X.
  ///
  /// There is no following feed on the public AppView, so this is one request
  /// per account. Bounded per call: importing somebody's following list used to
  /// mean several hundred requests on every refresh, which the AppView rate
  /// limits into an empty tab — the very thing the import was for.
  Future<List<BlueskyPost>> postsFor(
    List<String> actors, {
    bool forceRefresh = false,
    void Function(List<BlueskyPost>)? onPartial,
  }) {
    // A different AppView is a different Bluesky answering, so what was cached
    // under the old one is not an answer to the new question — Threads and
    // Mastodon already forget on a credential change; this one did not.
    final appView = client.baseUrl;
    if (_cachedFrom != appView) {
      _cachedFrom = appView;
      _posts.clear();
    }

    return _posts.merge(
      actors,
      (actor) async {
        final page = await client.getAuthorFeed(
          actor,
          limit: blueskyPostsPerAccount,
        );
        return page.posts;
      },
      forceRefresh: forceRefresh,
      maxFetches: blueskyMaxAccountsPerLoad,
      onPartial: onPartial,
    );
  }

  String? _cachedFrom;

  /// How many followed accounts have still to be read, so the tab can say that
  /// a big import is filling in rather than looking finished and short.
  int pending(List<String> actors) => _posts.pendingCount(actors);

  final _posts = AccountPostCache<BlueskyPost>(
    dateOf: (post) => post.publishedAt,
    perAccount: blueskyPostsPerAccount,
    concurrency: 2,
  );
}
