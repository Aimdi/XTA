import 'dart:convert';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:sqflite/sqflite.dart';
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

  RedditSubredditsStore(this.prefs) : super(const []);

  Future<void> load() async {
    await execute(() async {
      await _importFromPrefs();
      return _read();
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

  RedditFeedStore(
    RedditClient client,
    this.subreddits,
    BasePrefService prefs, {
    RedditAuth? auth,
    RedditPostSource? source,
  }) : source = source ?? RedditPostSource(client, prefs, auth: auth),
       super(const []);

  /// [sort] defaults to the reader's stored choice rather than hot, so the tab
  /// and the timeline agree about what they are showing.
  ///
  /// [force] is the pull-to-refresh: it goes past the shared cache, which is
  /// otherwise how the reader would pull down and be handed the same posts.
  Future<void> refresh({RedditSort? sort, bool force = false}) async {
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
