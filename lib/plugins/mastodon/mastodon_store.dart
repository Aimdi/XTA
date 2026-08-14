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
    final rows = await database.query(
      tableMastodonSubscription,
      orderBy: 'name COLLATE NOCASE',
    );

    return rows
        .map(MastodonSubscription.fromMap)
        .map(accountOf)
        .toList(growable: false);
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
      await database.delete(
        tableMastodonSubscription,
        where: 'id = ?',
        whereArgs: [acct],
      );
      await database.delete(
        tableSubscriptionGroupMember,
        where: 'profile_id = ?',
        whereArgs: [acct],
      );
      return _read();
    });
  }

  bool follows(String acct) => state.any((e) => e.acct == acct);
}

MastodonSubscription subscriptionOf(MastodonAccount account) =>
    MastodonSubscription(
      id: account.acct,
      name: account.name,
      avatarUrl: account.avatarUrl,
      createdAt: DateTime.now(),
      inFeed: true,
    );

MastodonAccount accountOf(MastodonSubscription subscription) => MastodonAccount(
  acct: subscription.id,
  name: subscription.name,
  avatarUrl: subscription.avatarUrl,
);

/// Merged timeline of every followed acct, newest first.
class MastodonFeedStore extends Store<List<MastodonPost>> {
  final MastodonClient client;
  final BasePrefService prefs;
  final MastodonAccountsStore accounts;

  MastodonFeedStore(this.client, this.prefs, this.accounts) : super(const []);

  Future<void> refresh({bool force = false}) async {
    final accts = accounts.state.map((e) => e.acct).toList(growable: false);
    if (state.isNotEmpty) {
      try {
        update(await postsFor(accts, forceRefresh: force));
      } catch (_) {
        update(state);
      }
      return;
    }
    await execute(() => postsFor(accts, forceRefresh: force));
  }

  /// Posts for [accts], newest first.
  ///
  /// Per account, not one shared home: each acct is asked for at its own
  /// instance first, which is the only place guaranteed to have all of it.
  /// Bounded per call for the same reason as Bluesky — these are somebody's
  /// hobby servers, and a long follow list should not arrive as a burst.
  Future<List<MastodonPost>> postsFor(
    List<String> accts, {
    bool forceRefresh = false,
  }) {
    final configured = mastodonConfiguredInstances(prefs);
    // A different set of instances is a different set of answers, so what was
    // cached under the old one is not an answer to the new question.
    if (!listEquals(_configured, configured)) {
      _configured = configured;
      _posts.clear();
    }

    return _posts.merge(
      accts,
      (acct) => client.fetchAccountAnywhere(
        mastodonInstanceCandidates(acct, configured: configured),
        acct,
        limit: mastodonPostsPerAccount,
      ),
      forceRefresh: forceRefresh,
      maxFetches: mastodonMaxAccountsPerLoad,
    );
  }

  List<String>? _configured;

  final _posts = AccountPostCache<MastodonPost>(
    dateOf: (post) => post.publishedAt,
    perAccount: mastodonPostsPerAccount,
    concurrency: 3,
  );
}
