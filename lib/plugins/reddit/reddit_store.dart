import 'dart:async';
import 'dart:convert';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:sqflite/sqflite.dart';
import 'package:xta/plugins/plugin_feed_fresh.dart';
import 'package:xta/plugins/reddit/reddit_auth.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_post_source.dart';

/// Subreddits the reader follows, kept in the database.
///
/// They used to be a JSON list in preferences, which is why a subreddit could
/// never be a member of a group. Anything still in that list is imported on
/// first load and the preference cleared.
class RedditSubredditsStore extends Store<List<String>> {
  final BasePrefService prefs;
  bool _hydrated = false;

  RedditSubredditsStore(this.prefs) : super(const []);

  /// Home-strip remounts call this on every swipe. An empty following list is
  /// a real answer — do not hit SQLite again just to paint the same empty pane.
  Future<void> load({bool force = false}) async {
    if (_hydrated && !force) {
      return;
    }
    await execute(() async {
      await _importFromPrefs();
      final names = await _read();
      _hydrated = true;
      return names;
    });
  }

  Future<List<String>> _read() async {
    final database = await Repository.readOnly();
    final rows = await database.query(
      tableRedditSubscription,
      orderBy: 'name COLLATE NOCASE',
    );

    return rows.map((e) => e['name'] as String).toList(growable: false);
  }

  Future<void> _importFromPrefs() async {
    final raw = prefs.get<String>(optionPluginRedditSubreddits) ?? '';
    if (raw.isEmpty) {
      return;
    }

    try {
      final decoded = jsonDecode(raw);
      if (decoded is List) {
        for (final name in decoded.whereType<String>()) {
          await _write(name);
        }
      }
    } catch (_) {
      // A corrupt value should not wedge the plugin shut.
    }
    await prefs.set(optionPluginRedditSubreddits, '');
  }

  Future<void> _write(String name) async {
    final normalised = normaliseSubreddit(name);
    if (normalised == null) {
      return;
    }

    final database = await Repository.writable();
    await database.insert(
      tableRedditSubscription,
      RedditSubscription(
        id: normalised.toLowerCase(),
        name: normalised,
        createdAt: DateTime.now(),
        inFeed: true,
      ).toMap(),
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<void> add(String subreddit) async {
    await execute(() async {
      await _write(subreddit);
      return _read();
    });
  }

  Future<void> remove(String subreddit) async {
    await execute(() async {
      final id = subreddit.toLowerCase();
      final database = await Repository.writable();
      await database.delete(
        tableRedditSubscription,
        where: 'id = ?',
        whereArgs: [id],
      );
      // A subreddit that is gone should not linger as a member of a group.
      await database.delete(
        tableSubscriptionGroupMember,
        where: 'profile_id = ?',
        whereArgs: [id],
      );
      return _read();
    });
  }
}

/// The merged feed: the first page of every followed subreddit, newest first.
///
/// Reddit paginates per listing, so there is no single cursor across
/// subreddits; this loads one page each and interleaves by date, which is what
/// makes a combined feed possible without inventing a cursor.
class RedditFeedStore extends Store<List<RedditPost>> {
  final RedditSubredditsStore subreddits;

  /// Shared with the home timeline and For you, which is what stops the same
  /// subreddit being downloaded once per surface. It is reached through this
  /// store because this is the Reddit object every surface can already see.
  final RedditPostSource source;

  DateTime? _fetchedAt;
  Future<void>? _inFlight;

  RedditFeedStore(
    RedditClient client,
    this.subreddits,
    BasePrefService prefs, {
    RedditAuth? auth,
    RedditPostSource? source,
  }) : source = source ?? RedditPostSource(client, prefs, auth: auth),
       super(const []);

  /// When the last successful following merge finished. Tests assert remounts
  /// keep it.
  DateTime? get fetchedAt => _fetchedAt;

  /// [sort] defaults to the reader's stored choice rather than hot, so the tab
  /// and the timeline agree about what they are showing.
  ///
  /// [force] is the pull-to-refresh: it goes past the shared cache, which is
  /// otherwise how the reader would pull down and be handed the same posts.
  /// Home-strip remounts omit [force] so an empty following list — or a fresh
  /// first page — is not fetched again on every swipe from Für dich.
  Future<void> refresh({RedditSort? sort, bool force = false}) async {
    if (subreddits.state.isEmpty) {
      if (state.isNotEmpty) {
        update(const []);
      }
      _fetchedAt ??= DateTime.now();
      return;
    }

    if (!force && state.isNotEmpty && pluginFeedIsFresh(_fetchedAt)) {
      return;
    }

    final existing = _inFlight;
    if (existing != null && !force) {
      await existing;
      return;
    }

    final done = Completer<void>();
    _inFlight = done.future;
    try {
      await _refreshBody(sort: sort, force: force);
      _fetchedAt = DateTime.now();
    } finally {
      _inFlight = null;
      done.complete();
    }
  }

  Future<void> _refreshBody({RedditSort? sort, required bool force}) async {
    if (state.isNotEmpty) {
      try {
        update(
          await source.posts(subreddits.state, sort: sort, forceRefresh: force),
        );
      } catch (_) {
        update(state);
      }
      return;
    }
    await execute(
      () => source.posts(subreddits.state, sort: sort, forceRefresh: force),
    );
  }
}

const int kRedditSavedPostsCap = 200;

class RedditSavedStore extends Store<List<RedditPost>> {
  final BasePrefService prefs;

  RedditSavedStore(this.prefs) : super(const []);

  Future<void> load() async {
    await execute(
      () async => RedditPost.listFromPrefs(
        prefs.get<String>(optionPluginRedditSavedPosts),
      ),
    );
  }

  bool isSaved(RedditPost post) => state.any((saved) => saved.id == post.id);

  Future<void> toggle(RedditPost post) async {
    await execute(() async {
      final exists = isSaved(post);
      final next = exists
          ? state.where((saved) => saved.id != post.id).toList(growable: false)
          : [
              post,
              ...state.where((saved) => saved.id != post.id),
            ].take(kRedditSavedPostsCap).toList();
      await prefs.set(
        optionPluginRedditSavedPosts,
        jsonEncode(next.map((saved) => saved.toJson()).toList()),
      );
      return next;
    });
  }
}
