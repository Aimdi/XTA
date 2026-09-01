import 'dart:convert';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:logging/logging.dart';
import 'package:xta/saved/saved_content_index.dart';
import 'package:sqflite/sqflite.dart';

class LikedTweetModel extends Store<List<LikedTweet>> {
  static final log = Logger('LikedTweetModel');

  final _index = SavedContentIndex();
  List<LikedTweet>? _indexedState;

  LikedTweetModel() : super([]);

  /// Derived from [state] on demand rather than hooked into the store's
  /// setters. State arrives here from `update` *and* from `execute`, and the
  /// list identity is the one thing both have in common -- so every mutator
  /// must emit a new list, which they do.
  ///
  /// Answers membership without decoding anything: every visible footer asks
  /// it on every build, and it used to be a linear scan of the whole table.
  SavedContentIndex get _indexed {
    if (!identical(_indexedState, state)) {
      _index.rebuild<LikedTweet>(state, idOf: (e) => e.id, blobOf: (e) => e.content);
      _indexedState = state;
    }

    return _index;
  }

  bool isLiked(String id) => _indexed.contains(id);

  /// The parsed post behind a liked id, or null if it was never stored.
  SavedContent? contentOf(String id) => _indexed[id];

  Future<void> listLikedTweets() async {
    log.info('Listing liked tweets');

    await execute(() async {
      var database = await Repository.readOnly();

      return (await database.query(tableLikedTweet, orderBy: 'liked_at DESC'))
          .map((e) => LikedTweet.fromMap(e))
          .toList();
    });
  }

  /// Reloads without entering the loading state, so the current list stays visible
  /// until the fresh data is ready (used for pull-to-refresh).
  Future<void> refreshLikedTweets() async {
    log.info('Refreshing liked tweets');

    var database = await Repository.readOnly();

    var tweets =
        (await database.query(tableLikedTweet, orderBy: 'liked_at DESC')).map((e) => LikedTweet.fromMap(e)).toList();

    update(tweets, force: true);
  }

  Future<void> likeTweet(String id, String? user, Map<String, dynamic> content) async {
    log.info('Liking tweet with the ID $id');

    var database = await Repository.writable();
    var encodedContent = jsonEncode(content);

    // Idempotent: the same tweet can surface twice in a feed (e.g. a pinned/retweeted
    // copy and its older chronological one), so a second "like" of an id already present
    // must not throw on the primary key nor duplicate the in-memory entry.
    await database.insert(tableLikedTweet, {'id': id, 'user_id': user, 'content': encodedContent},
        conflictAlgorithm: ConflictAlgorithm.replace);
    update([LikedTweet(id: id, user: user, content: encodedContent), ...state.where((e) => e.id != id)], force: true);
  }

  Future<void> unlikeTweet(String id) async {
    log.info('Unliking tweet with the ID $id');

    var database = await Repository.writable();

    await database.delete(tableLikedTweet, where: 'id = ?', whereArgs: [id]);
    update(state.where((e) => e.id != id).toList(), force: true);
  }
}
