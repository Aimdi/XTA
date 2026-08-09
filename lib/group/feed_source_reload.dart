/// Which plugin sources a feed asks, and for which ids.
///
/// Both rules here were got wrong when the per-network loaders were replaced by
/// one: a group lists only the sources it still has members for, so iterating
/// that map alone meant a source whose last member had just been removed was
/// never asked again — and its posts stayed in the feed for good. The same
/// omission left a source that is switched on for the home timeline out of the
/// combined feed whenever none of its accounts happened to be members.
library;

import 'package:flutter/foundation.dart' show listEquals;

/// The sources whose posts are now wrong, given what the group held before and
/// what it holds now.
///
/// Keyed on the union of both maps, not the new one: a key that disappeared is
/// exactly the case that needs refetching, because the answer changed to
/// "nothing".
Set<S> sourcesNeedingReload<S>({required Map<S, List<Object?>> before, required Map<S, List<Object?>> after}) => {
  for (final source in {...before.keys, ...after.keys})
    if (!listEquals(before[source], after[source])) source,
};

/// The ids one source is asked for on this feed.
///
/// The group's own members, plus every followed one when this is the combined
/// feed and the reader asked for that source in it. Deduplicated, because an
/// account can be both.
List<String> sourceIdsFor({
  required List<String> memberIds,
  required bool isCombinedFeed,
  required bool inHomeFeed,
  required List<String> homeFeedIds,
}) => {...memberIds, if (isCombinedFeed && inHomeFeed) ...homeFeedIds}.toList(growable: false);
