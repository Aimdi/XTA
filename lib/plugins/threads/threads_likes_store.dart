import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:sqflite/sqflite.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/plugins/threads/threads_models.dart';

/// Likes that live on this device, in the same spirit as the X likes:
/// nothing is sent to Threads/Meta, no account is involved — the heart simply
/// remembers what the reader liked.
class ThreadsLikesStore extends Store<List<ThreadsPost>> {
  final BasePrefService prefs;

  ThreadsLikesStore(this.prefs) : super(const []);

  Set<String> _ids = const {};

  List<ThreadsPost> get likedPosts => state;

  Future<void> load() async {
    final database = await Repository.readOnly();
    final rows = await database.query(tableThreadsLocalLike, columns: ['id']);
    _ids = {for (final row in rows) row['id'] as String};
    final posts = ThreadsPost.listFromPrefs(
      prefs.get<String>(optionPluginThreadsLikedPosts),
    ).where((post) => _ids.contains(post.id)).toList(growable: false);
    update(posts);
  }

  bool isLiked(String id) => _ids.contains(id);

  Future<void> toggle(ThreadsPost post) async {
    final database = await Repository.writable();
    if (_ids.contains(post.id)) {
      await database.delete(
        tableThreadsLocalLike,
        where: 'id = ?',
        whereArgs: [post.id],
      );
      _ids = {..._ids}..remove(post.id);
      await _save(
        state.where((liked) => liked.id != post.id).toList(growable: false),
      );
    } else {
      await database.insert(tableThreadsLocalLike, {
        'id': post.id,
      }, conflictAlgorithm: ConflictAlgorithm.ignore);
      _ids = {..._ids, post.id};
      await _save(
        [
          post,
          ...state.where((liked) => liked.id != post.id),
        ].take(threadsLikedPostsCap).toList(),
      );
    }
  }

  Future<void> _save(List<ThreadsPost> posts) async {
    await prefs.set(
      optionPluginThreadsLikedPosts,
      ThreadsPost.listToPrefs(posts),
    );
    update(posts);
  }
}
