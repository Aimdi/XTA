import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/database/repository.dart';

/// The id a comment's upvote is kept under. Comments and posts are numbered
/// apart on Reddit, so a comment's id could equal a post's; Reddit's own `t1_`
/// prefix keeps them apart. A post's upvote stays under its bare id.
String redditCommentVoteId(String commentId) => 't1_$commentId';

/// Upvotes that live on this device, in the same spirit as the X likes:
/// nothing is sent to Reddit, no account is involved, the arrow simply
/// remembers what the reader thought of the post.
class RedditVotesStore extends Store<Set<String>> {
  RedditVotesStore() : super(const {});

  bool _loaded = false;

  Future<void> load() async {
    if (_loaded) {
      return;
    }
    _loaded = true;
    final database = await Repository.readOnly();
    final rows = await database.query(tableRedditLocalVote, columns: ['id']);
    update({for (final row in rows) row['id'] as String});
  }

  bool isUpvoted(String id) => state.contains(id);

  Future<void> toggle(String id) async {
    final database = await Repository.writable();
    if (state.contains(id)) {
      await database.delete(tableRedditLocalVote, where: 'id = ?', whereArgs: [id]);
      update({...state}..remove(id));
    } else {
      await database.insert(tableRedditLocalVote, {'id': id});
      update({...state, id});
    }
  }
}
