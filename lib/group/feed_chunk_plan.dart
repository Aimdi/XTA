/// What a group feed works out from its members before fetching anything.
///
/// The live feed, the unread dots and group Discover all have to agree on the
/// same reply / repost flags, the same member order and the same chunks, or the
/// hashes they look cached rows up by stop matching. The feed and Discover plan
/// here; the unread dots hash the same chunks through [feedChunkHashesFor].
library;

import 'package:pref/pref.dart';
import 'package:quiver/iterables.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/group/feed_chunk_hash.dart';
import 'package:xta/group/group_members.dart';
import 'package:xta/home/home_group_filter.dart';
import 'package:xta/utils/iterables.dart';

class SubscriptionGroupFeedChunk {
  final List<Subscription> users;
  final bool includeReplies;
  final bool includeRetweets;

  SubscriptionGroupFeedChunk(this.users, this.includeReplies, this.includeRetweets);

  String get hash =>
      feedChunkHash(users.map((e) => e.id).toList(), includeReplies: includeReplies, includeRetweets: includeRetweets);
}

typedef GroupFeedPlan = ({
  bool includeReplies,
  bool includeRetweets,
  List<Subscription> members,
  GroupMemberSplit split,
  List<SubscriptionGroupFeedChunk> chunks,
});

/// Resolves [group]'s flags against the global defaults, orders its members
/// oldest first and partitions the X members into search chunks.
///
/// [excludedProfiles] only matters for the combined Following feed (`-1`),
/// which leaves out the members of groups the reader has switched off.
GroupFeedPlan planGroupFeed(
  SubscriptionGroupGet group, {
  required BasePrefService prefs,
  Set<String> excludedProfiles = const {},
}) {
  // A group leaves each filter unset (null) to follow the global default.
  final includeReplies = group.includeReplies ?? prefs.get<bool>(optionGlobalIncludeReplies) ?? true;
  final includeRetweets = group.includeRetweets ?? prefs.get<bool>(optionGlobalIncludeRetweets) ?? true;
  final allowed = group.id == '-1'
      ? group.subscriptions.where((e) => subscriptionAllowedInFollowing(e, excludedProfiles))
      : group.subscriptions;
  // Oldest first, so a new member lands in the last chunk instead of reshuffling every group's chunks.
  final members = allowed.sorted((a, b) => a.createdAt.compareTo(b.createdAt)).toList();
  // Plugin members are fetched by their plugin, never put into an X search query.
  final split = splitGroupMembers(members);
  final chunks = [
    for (final users in partition(split.xMembers, feedChunkSize))
      SubscriptionGroupFeedChunk(users, includeReplies, includeRetweets),
  ];
  return (
    includeReplies: includeReplies,
    includeRetweets: includeRetweets,
    members: members,
    split: split,
    chunks: chunks,
  );
}
