import 'package:xta/group/feed_chunk_hash.dart';
import 'package:xta/group/group_tree.dart';

/// A membership row before it is scoped to one group's feed (itself plus
/// nested groups). [search] is true for `search_subscription` so users are
/// collected first, matching [GroupModel._readGroup].
class FeedMemberRow {
  final String groupId;
  final String id;
  final DateTime createdAt;
  final bool search;

  const FeedMemberRow({
    required this.groupId,
    required this.id,
    required this.createdAt,
    required this.search,
  });
}

/// Stored per-group override: null follows the global default.
bool? includeOverride(Object? value) => value == null ? null : value == 1;

/// Same gate as [SubscriptionGroupFeed._tracksReadPosition], minus the
/// media-grid case (the board never opens that).
bool tracksGroupReadPosition({
  required bool popular,
  required bool globalReadingPosition,
  required bool catchUp,
}) => !popular && (globalReadingPosition || catchUp);

/// Whether a group should show an unread mark.
///
/// Tracking off → never (positions are not written, so every cached group
/// would light up). No cached chunks → nothing new to point at. Tracking
/// with cache but no position → never marked caught-up. Otherwise unread
/// when the newest chunk write is after the position's `updated_at`.
bool groupHasUnread({
  required bool tracksReadPosition,
  required DateTime? newestCachedAt,
  required DateTime? lastReadAt,
}) {
  if (!tracksReadPosition || newestCachedAt == null) {
    return false;
  }
  if (lastReadAt == null) {
    return true;
  }
  return newestCachedAt.isAfter(lastReadAt);
}

/// X + search members of [groupId]'s feed: this group and its descendants,
/// users then searches (by id), then the live feed's `createdAt` sort.
List<FeedChunkMember> membersForGroupFeed({
  required String groupId,
  required Map<String, String?> parentOf,
  required List<FeedMemberRow> rows,
}) {
  final scope = groupAndDescendants(groupId, parentOf);
  final inScope = rows.where((r) => scope.contains(r.groupId));
  final users = inScope.where((r) => !r.search).toList()
    ..sort((a, b) => a.id.compareTo(b.id));
  final searches = inScope.where((r) => r.search).toList()
    ..sort((a, b) => a.id.compareTo(b.id));
  final seen = <String>{};
  final members = <FeedChunkMember>[];
  for (final row in [...users, ...searches]) {
    if (seen.add(row.id)) {
      members.add(FeedChunkMember(id: row.id, createdAt: row.createdAt));
    }
  }
  return members;
}

/// Group ids whose newest cached chunk is newer than their last-read mark.
Set<String> unreadGroupIds({
  required Iterable<String> groupIds,
  required Map<String, String?> parentOf,
  required List<FeedMemberRow> members,
  required Map<String, bool?> includeRepliesByGroup,
  required Map<String, bool?> includeRetweetsByGroup,
  required bool globalIncludeReplies,
  required bool globalIncludeRetweets,
  required bool globalReadingPosition,
  required Set<String> catchUpGroupIds,
  required Set<String> popularGroupIds,
  required Map<String, DateTime> lastReadByGroup,
  required Map<String, DateTime> newestByHash,
}) {
  return {
    for (final id in groupIds)
      if (id != '-1' &&
          _unreadFor(
            id,
            parentOf: parentOf,
            members: members,
            includeReplies: includeRepliesByGroup[id] ?? globalIncludeReplies,
            includeRetweets:
                includeRetweetsByGroup[id] ?? globalIncludeRetweets,
            tracks: tracksGroupReadPosition(
              popular: popularGroupIds.contains(id),
              globalReadingPosition: globalReadingPosition,
              catchUp: catchUpGroupIds.contains(id),
            ),
            lastReadAt: lastReadByGroup[id],
            newestByHash: newestByHash,
          ))
        id,
  };
}

bool _unreadFor(
  String id, {
  required Map<String, String?> parentOf,
  required List<FeedMemberRow> members,
  required bool includeReplies,
  required bool includeRetweets,
  required bool tracks,
  required DateTime? lastReadAt,
  required Map<String, DateTime> newestByHash,
}) {
  final hashes = feedChunkHashesFor(
    membersForGroupFeed(groupId: id, parentOf: parentOf, rows: members),
    includeReplies: includeReplies,
    includeRetweets: includeRetweets,
  );
  DateTime? newest;
  for (final hash in hashes) {
    final at = newestByHash[hash];
    if (at != null && (newest == null || at.isAfter(newest))) {
      newest = at;
    }
  }
  return groupHasUnread(
    tracksReadPosition: tracks,
    newestCachedAt: newest,
    lastReadAt: lastReadAt,
  );
}
