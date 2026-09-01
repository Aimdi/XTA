import 'dart:convert';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:logging/logging.dart';
import 'package:xta/saved/saved_content_index.dart';

class SavedTweetModel extends Store<List<SavedTweet>> {
  static final log = Logger('SavedTweetModel');

  final _index = SavedContentIndex();
  List<SavedTweet>? _indexedState;

  SavedTweetModel() : super([]);

  /// Derived from [state] on demand rather than hooked into the store's
  /// setters. State arrives here from `update` *and* from `execute`, and the
  /// list identity is the one thing both have in common -- so every mutator
  /// must emit a new list, which they do.
  ///
  /// Answers membership without decoding anything: every visible footer asks
  /// it on every build, and it used to be a linear scan of the whole table.
  SavedContentIndex get _indexed {
    if (!identical(_indexedState, state)) {
      _index.rebuild<SavedTweet>(state, idOf: (e) => e.id, blobOf: (e) => e.content);
      _indexedState = state;
    }

    return _index;
  }

  bool isSaved(String id) => _indexed.contains(id);

  /// The parsed post behind a saved id, or null if it was never stored.
  SavedContent? contentOf(String id) => _indexed[id];

  String? folderOf(String id) {
    var match = state.where((e) => e.id == id);
    return match.isEmpty ? null : match.first.folderId;
  }

  Future<void> setFolder(String id, String? folderId) async {
    var database = await Repository.writable();

    await database.update(tableSavedTweet, {'folder_id': folderId}, where: 'id = ?', whereArgs: [id]);

    update(state.map((e) => e.id == id ? e.copyWith(folderId: folderId) : e).toList(), force: true);
  }

  Future<void> setNote(String id, String? note) async {
    final database = await Repository.writable();
    final trimmed = note?.trim();

    await database.update(
      tableSavedTweet,
      {'note': trimmed == null || trimmed.isEmpty ? null : trimmed},
      where: 'id = ?',
      whereArgs: [id],
    );

    update(
      state
          .map((e) => e.id == id ? e.copyWith(note: trimmed == null || trimmed.isEmpty ? null : trimmed) : e)
          .toList(),
      force: true,
    );
  }

  Future<void> removeSavedTweets(List<String> ids) async {
    var database = await Repository.writable();

    var batch = database.batch();
    for (final id in ids) {
      batch.delete(tableSavedTweet, where: 'id = ?', whereArgs: [id]);
    }
    await batch.commit(noResult: true);

    var removed = ids.toSet();
    update(state.where((e) => !removed.contains(e.id)).toList(), force: true);
  }

  Future<void> deleteSavedTweet(String id) async {
    var database = await Repository.writable();

    await database.delete(tableSavedTweet, where: 'id = ?', whereArgs: [id]);

    update(state.where((e) => e.id != id).toList(), force: true);
  }

  Future<void> listSavedTweets() async {
    log.info('Listing saved tweets');

    await execute(() async {
      var database = await Repository.readOnly();

      return (await database.query(tableSavedTweet, orderBy: 'saved_at DESC'))
          .map((e) => SavedTweet.fromMap(e))
          .toList();
    });
  }

  /// Reloads without entering the loading state, so the current list stays visible
  /// until the fresh data is ready (used for pull-to-refresh).
  Future<void> refreshSavedTweets() async {
    log.info('Refreshing saved tweets');

    var database = await Repository.readOnly();

    var tweets = (await database.query(tableSavedTweet, orderBy: 'saved_at DESC'))
        .map((e) => SavedTweet.fromMap(e))
        .toList();

    update(tweets, force: true);
  }

  Future<void> saveTweet(String id, String? user, Map<String, dynamic> content, {String? folderId}) async {
    log.info('Saving tweet with the ID $id');

    await execute(() async {
      var database = await Repository.writable();

      var encodedContent = jsonEncode(content);

      await database.insert(
          tableSavedTweet, {'id': id, 'user_id': user, 'content': encodedContent, 'folder_id': folderId});

      return [...state, SavedTweet(id: id, user: user, content: encodedContent, folderId: folderId)];
    });
  }
}
