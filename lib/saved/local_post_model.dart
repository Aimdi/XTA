import 'package:flutter_triple/flutter_triple.dart';
import 'package:logging/logging.dart';
import 'package:sqflite/sqflite.dart';
import 'package:uuid/uuid.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/saved/local_post_files.dart';
import 'package:xta/saved/local_post_logic.dart';

class LocalPostModel extends Store<List<LocalPost>> {
  static final log = Logger('LocalPostModel');

  LocalPostModel() : super([]);

  Future<void> listLocalPosts() async {
    log.info('Listing local posts');
    await execute(() async {
      final database = await Repository.readOnly();
      return (await database.query(
        tableLocalPost,
        orderBy: 'created_at DESC',
      )).map(LocalPost.fromMap).toList(growable: false);
    });
  }

  Future<void> refreshLocalPosts() async {
    final database = await Repository.readOnly();
    final posts = (await database.query(
      tableLocalPost,
      orderBy: 'created_at DESC',
    )).map(LocalPost.fromMap).toList(growable: false);
    update(posts, force: true);
  }

  /// Returns null when there is nothing to save (blank body and no media).
  Future<LocalPost?> saveLocalPost({
    String? id,
    required String body,
    List<LocalPostMedia> media = const [],
    String? quotedTweetId,
    String? quotedTweetJson,
  }) async {
    final normalized = normalizeLocalPostBody(body) ?? '';
    if (!localPostHasContent(body, media)) {
      return null;
    }

    final database = await Repository.writable();
    LocalPost? existing;
    if (id != null) {
      for (final row in state) {
        if (row.id == id) {
          existing = row;
          break;
        }
      }
    }

    final now = DateTime.now();
    final post = LocalPost(
      id: id ?? const Uuid().v4(),
      body: normalized,
      createdAt: existing?.createdAt ?? now,
      updatedAt: now,
      media: media,
      quotedTweetId: quotedTweetId ?? existing?.quotedTweetId,
      quotedTweetJson: quotedTweetJson ?? existing?.quotedTweetJson,
    );

    await deleteRemovedLocalPostMedia(post.id, media);
    await database.insert(
      tableLocalPost,
      post.toMap(),
      conflictAlgorithm: ConflictAlgorithm.replace,
    );

    final next = existing == null
        ? [post, ...state]
        : [
            for (final row in state)
              if (row.id == id) post else row,
          ];
    update(next, force: true);
    return post;
  }

  Future<void> deleteLocalPost(String id) async {
    final database = await Repository.writable();
    await database.delete(tableLocalPost, where: 'id = ?', whereArgs: [id]);
    await deleteLocalPostMediaDir(id);
    update(state.where((e) => e.id != id).toList(growable: false), force: true);
  }
}
