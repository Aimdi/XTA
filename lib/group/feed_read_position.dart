import 'package:xta/client/client.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/utils/iterables.dart';
import 'package:sqflite/sqflite.dart';

/// Stable keys for the home Following / For You feeds. Group feeds use their
/// subscription-group id as the key; the combined Following feed historically
/// used [legacyFeedKeyFollowing] (`-1`) and is remapped via
/// [feedReadPositionKey].
const feedKeyFollowing = 'following';
const feedKeyForYou = 'for_you';

/// Pre-generalization key for the combined Following feed (group id `-1`).
const legacyFeedKeyFollowing = '-1';

/// Maps a subscription-group id onto the `feed_read_position.group_id` column.
/// The column name is historical; values are feed keys, not only group ids.
String feedReadPositionKey(String groupId) => groupId == legacyFeedKeyFollowing ? feedKeyFollowing : groupId;

/// The last chain the user is known to have read in a group feed. Compared by
/// value (timestamp), never by presence: the chain itself may have been purged
/// from the feed cache since (pull-to-refresh wipes all chunks).
class FeedReadPosition {
  final String chainId;
  final DateTime? chainCreatedAt;

  const FeedReadPosition({required this.chainId, required this.chainCreatedAt});
}

Future<FeedReadPosition?> readFeedReadPosition(String feedKey) async {
  var repository = await Repository.readOnly();
  final position = await _queryFeedReadPosition(repository, feedKey);
  if (position != null) {
    return position;
  }
  // Following used to be stored under the combined-group id "-1".
  if (feedKey == feedKeyFollowing) {
    return _queryFeedReadPosition(repository, legacyFeedKeyFollowing);
  }
  return null;
}

Future<FeedReadPosition?> _queryFeedReadPosition(Database repository, String feedKey) async {
  var rows = await repository.query(tableFeedReadPosition, where: 'group_id = ?', whereArgs: [feedKey]);
  var row = rows.firstOrNull;
  if (row == null) {
    return null;
  }
  return FeedReadPosition(
    chainId: row['chain_id'] as String,
    chainCreatedAt: DateTime.tryParse(row['chain_created_at'] as String? ?? ''),
  );
}

Future<void> writeFeedReadPosition(String feedKey, TweetChain chain) async {
  var repository = await Repository.writable();
  await repository.insert(tableFeedReadPosition, {
    'group_id': feedKey,
    'chain_id': chain.id,
    'chain_created_at': chain.tweets.firstOrNull?.createdAt?.toIso8601String(),
  }, conflictAlgorithm: ConflictAlgorithm.replace);
}

/// Newest chain that carries a creation timestamp — the value we persist as
/// the reading cursor when the reader is at the top of a chronological feed.
TweetChain? newestRecordableChain(Iterable<TweetChain> chains) =>
    chains.where((c) => c.tweets.firstOrNull?.createdAt != null).firstOrNull;

/// Chain ids are not chronological (conversation chains carry their root's
/// id), so besides the exact-match shortcut, compare by the first tweet's
/// creation date — the same value the newest-first sort uses.
bool isChainSeen(TweetChain chain, FeedReadPosition position) {
  if (chain.id == position.chainId) {
    return true;
  }
  final createdAt = chain.tweets.firstOrNull?.createdAt;
  final lastSeen = position.chainCreatedAt;
  if (createdAt == null || lastSeen == null) {
    return false;
  }
  return !createdAt.isAfter(lastSeen);
}

/// Index of the first previously-seen chain when at least one new chain sits
/// above it; null when nothing is new, or the boundary isn't loaded yet.
int? caughtUpBoundaryIndex(List<TweetChain> chains, FeedReadPosition position) {
  final index = chains.indexWhere((c) => isChainSeen(c, position));
  return index <= 0 ? null : index;
}
