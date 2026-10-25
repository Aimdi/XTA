import 'package:crypto/crypto.dart';
import 'package:xta/group/feed_search_query.dart';

/// One X or search member as the live group feed hashes them: id plus the
/// subscription `created_at` used to order chunks (oldest first), and the
/// [term] it adds to its chunk's search, whose length decides the chunk.
class FeedChunkMember {
  final String id;
  final DateTime createdAt;
  final String term;

  const FeedChunkMember({required this.id, required this.createdAt, required this.term});
}

/// Oldest first, ties by id: the live feed and the unread dots must put the
/// same members in the same chunks, whatever order they were read in.
int compareFeedMembers(DateTime createdAtA, String idA, DateTime createdAtB, String idB) =>
    switch (createdAtA.compareTo(createdAtB)) {
      0 => idA.compareTo(idB),
      final byAge => byAge,
    };

/// SHA-1 of the same string [SubscriptionGroupFeedChunk.hash] used to write
/// `feed_group_chunk` rows. Id order is the caller's: already sorted and
/// packed the way the open feed does.
String feedChunkHash(List<String> ids, {required bool includeReplies, required bool includeRetweets}) {
  final toHash = '${ids.join(', ')}$includeReplies$includeRetweets';
  return sha1.convert(toHash.codeUnits).toString();
}

/// Hashes for [members] after the live feed's sort ([compareFeedMembers]) and
/// packing ([packFeedChunks]).
List<String> feedChunkHashesFor(
  Iterable<FeedChunkMember> members, {
  required bool includeReplies,
  required bool includeRetweets,
}) {
  final sorted = [...members]..sort((a, b) => compareFeedMembers(a.createdAt, a.id, b.createdAt, b.id));
  return packFeedChunks(sorted, (m) => m.term, includeReplies: includeReplies, includeRetweets: includeRetweets)
      .map(
        (chunk) => feedChunkHash(
          chunk.map((m) => m.id).toList(),
          includeReplies: includeReplies,
          includeRetweets: includeRetweets,
        ),
      )
      .toList();
}
