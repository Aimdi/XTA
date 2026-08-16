import 'package:crypto/crypto.dart';
import 'package:quiver/iterables.dart';

/// Users per X search query. Search queries are capped at ~512 characters;
/// 16 screen names stay under that, while 32 does not (upstream #165).
const int feedChunkSize = 16;

/// One X or search member as the live group feed hashes them: id plus the
/// subscription `created_at` used to order chunks (oldest first).
class FeedChunkMember {
  final String id;
  final DateTime createdAt;

  const FeedChunkMember({required this.id, required this.createdAt});
}

/// SHA-1 of the same string [SubscriptionGroupFeedChunk.hash] used to write
/// `feed_group_chunk` rows. Id order is the caller's: already sorted and
/// partitioned the way the open feed does.
String feedChunkHash(
  List<String> ids, {
  required bool includeReplies,
  required bool includeRetweets,
}) {
  final toHash = '${ids.join(', ')}$includeReplies$includeRetweets';
  return sha1.convert(toHash.codeUnits).toString();
}

/// Hashes for [members] after the live feed's sort (oldest `createdAt` first)
/// and partition of [feedChunkSize].
List<String> feedChunkHashesFor(
  Iterable<FeedChunkMember> members, {
  required bool includeReplies,
  required bool includeRetweets,
}) {
  final sorted = [...members]
    ..sort((a, b) => a.createdAt.compareTo(b.createdAt));
  return partition(sorted, feedChunkSize)
      .map(
        (chunk) => feedChunkHash(
          chunk.map((m) => m.id).toList(),
          includeReplies: includeReplies,
          includeRetweets: includeRetweets,
        ),
      )
      .toList();
}
