import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:sqflite/sqflite.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/plugins/account_posts.dart';
import 'package:xta/plugins/threads/threads_client.dart';
import 'package:xta/plugins/threads/threads_direct_client.dart';
import 'package:xta/plugins/threads/threads_models.dart';

/// How long a handle's posts are reused before Meta is asked for them again.
///
/// The Threads tab, the home timeline and every group feed read the same
/// accounts. Without this, opening a group after the tab asked Meta for all of
/// them a second time — with the reader's own session, which is the one thing
/// this plugin has to spend sparingly. Meta bans accounts that behave like
/// scripts, and the surest way to look like one is to ask for the same thing
/// over and over.
const Duration kThreadsCacheTtl = Duration(minutes: 10);

/// The Threads accounts the reader follows, kept in the database so they can
/// join a subscription group like every other source.
class ThreadsAccountsStore extends Store<List<ThreadsAccount>> {
  ThreadsAccountsStore() : super(const []);

  Future<void> load() async {
    await execute(_read);
  }

  Future<List<ThreadsAccount>> _read() async {
    final database = await Repository.readOnly();
    final rows = await database.query(
      tableThreadsSubscription,
      orderBy: 'name COLLATE NOCASE',
    );

    return rows
        .map(ThreadsSubscription.fromMap)
        .map(accountOf)
        .toList(growable: false);
  }

  Future<void> add(ThreadsAccount account) async {
    await execute(() async {
      final database = await Repository.writable();
      await database.insert(
        tableThreadsSubscription,
        subscriptionOf(account).toMap(),
        conflictAlgorithm: ConflictAlgorithm.replace,
      );
      return _read();
    });
  }

  bool follows(String handle) {
    final key = handle.trim().toLowerCase();
    return state.any((e) => e.handle.toLowerCase() == key);
  }

  Future<void> remove(String handle) async {
    await execute(() async {
      final database = await Repository.writable();
      await database.delete(
        tableThreadsSubscription,
        where: 'id = ?',
        whereArgs: [handle],
      );
      // An account that is gone should not linger as a member of a group.
      await database.delete(
        tableSubscriptionGroupMember,
        where: 'profile_id = ?',
        whereArgs: [handle],
      );
      return _read();
    });
  }
}

ThreadsSubscription subscriptionOf(ThreadsAccount account) =>
    ThreadsSubscription(
      id: account.handle,
      name: account.name,
      avatarUrl: account.avatarUrl,
      createdAt: DateTime.now(),
      inFeed: true,
    );

ThreadsAccount accountOf(ThreadsSubscription subscription) => ThreadsAccount(
  handle: subscription.id,
  name: subscription.name,
  avatarUrl: subscription.avatarUrl,
);

/// The merged timeline of every followed account, newest first — or the Meta
/// Following feed when a Bearer session is pasted.
class ThreadsFeedStore extends Store<List<ThreadsPost>> {
  final ThreadsClient client;
  final ThreadsDirectClient direct;
  final BasePrefService prefs;
  final ThreadsAccountsStore accounts;

  ThreadsFeedStore(this.client, this.direct, this.prefs, this.accounts)
    : super(const []);

  String get _instance => prefs.get<String>(optionPluginThreadsInstance) ?? '';

  /// Reads the best available source (see docs/specs/threads-direct.md).
  ///
  /// Soft path when the feed already has posts: keep the list on screen and on
  /// failure (same idea as Pixiv). Never blank the tab into a spinner that
  /// looks like Meta is hanging — and never ask harder just to paint loading.
  Future<void> refresh({bool force = false}) async {
    // Each account answered paints immediately — behind the anti-ban pacing,
    // waiting for all of them meant a spinner for the sum of every wait. Soft
    // refresh (list already on screen) must do the same, or pull-to-refresh
    // feels frozen until the slowest account answers.
    if (state.isNotEmpty) {
      try {
        update(await _loadPosts(force: force, onPartial: update));
      } catch (_) {
        update(state);
      }
      return;
    }

    await execute(() => _loadPosts(force: force, onPartial: update));
  }

  /// How many followed handles still need a network read.
  int pending(List<String> handles) => _posts.pendingCount(handles);

  Future<List<ThreadsPost>> _loadPosts({
    required bool force,
    void Function(List<ThreadsPost>)? onPartial,
  }) async {
    final handles = accounts.state.map((e) => e.handle).toList(growable: false);

    // Local Accounts (cookies → guest GraphQL fallback). A pasted Bearer no
    // longer hides this list — that was the empty-feed bug for readers who
    // signed in and also followed people in the plugin.
    if (handles.isNotEmpty) {
      return postsFor(handles, forceRefresh: force, onPartial: onPartial);
    }

    // No local Accounts: Bearer shows the Meta home/For You timeline.
    if (direct.hasBearer) {
      return await direct.fetchFollowingTimeline();
    }

    if (direct.hasCookies || _instance.trim().isNotEmpty) {
      // Session alone does not invent Accounts — add handles in the tab.
      return const <ThreadsPost>[];
    }
    throw ThreadsException(
      ThreadsErrorKind.notConfigured,
      'no accounts or session',
    );
  }

  /// Posts for [handles], newest first, through whichever source is configured.
  ///
  /// Public because the Threads tab is no longer the only place these are
  /// shown: group feeds and the home timeline mix them in too, and all three
  /// have to agree about which source a session, an RSSHub instance or neither
  /// implies. Deciding that in one place is what stops the tab working while a
  /// group shows nothing.
  Future<List<ThreadsPost>> postsFor(
    List<String> handles, {
    bool forceRefresh = false,
    void Function(List<ThreadsPost>)? onPartial,
  }) async {
    if (handles.isEmpty) {
      return const [];
    }

    _forgetOnCredentialChange();
    return _posts.merge(
      handles,
      _fetcher(),
      forceRefresh: forceRefresh,
      onPartial: onPartial,
      maxFetches: direct.useSessionApis
          ? threadsSessionMaxAccountsPerLoad
          : threadsMaxAccountsPerLoad,
    );
  }

  /// Which route answers, given what the reader has configured.
  ///
  /// Public guest GraphQL is the default for followed Accounts. Cookie REST
  /// only when the reader opts in; RSSHub is tried when set, then guest.
  Future<List<ThreadsPost>> Function(String handle) _fetcher() {
    if (direct.useSessionApis && direct.hasCookies) {
      return direct.fetchUserThreads;
    }
    final instance = _instance.trim();
    if (instance.isNotEmpty) {
      return (handle) async {
        try {
          final posts = await client.fetchAccount(instance, handle);
          if (posts.isNotEmpty) {
            return posts;
          }
        } catch (_) {
          // Guest path below.
        }
        return direct.fetchGuestAccount(handle);
      };
    }
    return direct.fetchGuestAccount;
  }

  /// What each handle last returned, and when. Meta bans accounts that behave
  /// like scripts, and the surest way to look like one is to ask for the same
  /// thing over and over — so one handle is read at a time, and not twice.
  final _posts = AccountPostCache<ThreadsPost>(
    dateOf: (post) => post.publishedAt,
    perAccount: threadsPostsPerAccount,
    concurrency: 1,
    ttl: kThreadsCacheTtl,
  );

  String? _credentials;

  /// A change of session, or of RSSHub instance, means a different Threads is
  /// answering — so what was cached under the old one is not an answer to the
  /// new question.
  void _forgetOnCredentialChange() {
    final current = [
      prefs.get<String>(optionPluginThreadsDirectCookies) ?? '',
      prefs.get<String>(optionPluginThreadsDirectBearer) ?? '',
      '${prefs.get<bool>(optionPluginThreadsUseSessionApis) == true}',
      _instance,
    ].join(' ');

    if (_credentials != current) {
      _credentials = current;
      _posts.clear();
    }
  }
}
