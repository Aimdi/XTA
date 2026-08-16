import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/group/feed_cache.dart';
import 'package:xta/group/feed_chunk_hash.dart';
import 'package:xta/group/feed_read_position.dart';
import 'package:xta/group/group_unread.dart';

/// Last-read mark for the combined Following feed (`following`, else legacy `-1`).
DateTime? lastReadForFollowing(Map<String, DateTime> lastReadByGroup) =>
    lastReadByGroup[feedKeyFollowing] ??
    lastReadByGroup[legacyFeedKeyFollowing];

/// In-feed X accounts, the same set Following hashes (search stays out).
List<FeedChunkMember> followingChunkMembers(
  Iterable<({String id, DateTime createdAt, bool inFeed})> users,
) => [
  for (final user in users)
    if (user.inFeed) FeedChunkMember(id: user.id, createdAt: user.createdAt),
];

/// Whether Following's cached chunks are newer than the last-read mark.
bool followingHasUnread({
  required Iterable<FeedChunkMember> inFeedUsers,
  required bool includeReplies,
  required bool includeRetweets,
  required bool tracksReadPosition,
  required DateTime? lastReadAt,
  required Map<String, DateTime> newestByHash,
}) {
  DateTime? newest;
  for (final hash in feedChunkHashesFor(
    inFeedUsers,
    includeReplies: includeReplies,
    includeRetweets: includeRetweets,
  )) {
    final at = newestByHash[hash];
    if (at != null && (newest == null || at.isAfter(newest))) {
      newest = at;
    }
  }
  return groupHasUnread(
    tracksReadPosition: tracksReadPosition,
    newestCachedAt: newest,
    lastReadAt: lastReadAt,
  );
}

DateTime? forYouNewestCachedAt(BasePrefService prefs) {
  final raw = prefs.get(optionForYouNewestCachedAt);
  return raw is String ? parseChunkTimestamp(raw) : null;
}

Future<void> rememberForYouNewestCached(BasePrefService prefs) async {
  await prefs.set(
    optionForYouNewestCachedAt,
    DateTime.now().toUtc().toIso8601String(),
  );
}
