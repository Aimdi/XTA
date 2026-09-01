/// What a feed should do with a first page it has just finished loading.
///
/// This was four fields of widget State read inside a callback, which is why
/// it had never been tested and why two bugs lived here at once: a refresh
/// fired mid-scroll marked unread posts as read, and a membership change reset
/// the reader's position. Deciding it here means each rule can be stated as a
/// test rather than argued about.
library;

import 'package:xta/client/client.dart';
import 'package:xta/group/feed_read_position.dart';

sealed class FeedPageAction {
  const FeedPageAction();
}

/// Bring the last-read chain back under the app bar: there are unread posts
/// above it and the reader has not been moved anywhere else this session.
class RestoreToBoundary extends FeedPageAction {
  final int index;

  const RestoreToBoundary(this.index);
}

/// Remember [chain] as the newest post read in this feed.
class RecordPosition extends FeedPageAction {
  final TweetChain chain;

  const RecordPosition(this.chain);
}

/// Leave the reading position alone.
class DoNothing extends FeedPageAction {
  const DoNothing();
}

/// The decision for one finalized first page.
///
/// [caughtUpAlreadyEvaluated] is false only for the first page of a mount: the
/// caught-up boundary is chosen once and then frozen, so the divider never
/// moves under the reader mid-session.
///
/// [sessionOffset] is where this feed was scrolled to when it was last left. A
/// restored offset outranks the boundary — the reader is already somewhere they
/// chose, and jumping them to the divider would take it away.
///
/// [atTop] gates recording rather than restoring: being at the top is what makes
/// "everything above is read" true. A soft refresh fired while scrolled down
/// says nothing about what was read.
FeedPageAction firstPageAction({
  required List<TweetChain> chains,
  required FeedReadPosition? lastSeen,
  required bool caughtUpAlreadyEvaluated,
  required double? sessionOffset,
  required bool atTop,
}) {
  if (!caughtUpAlreadyEvaluated && lastSeen != null && (sessionOffset == null || sessionOffset <= 0)) {
    final boundary = caughtUpBoundaryIndex(chains, lastSeen);
    if (boundary != null) {
      return RestoreToBoundary(boundary);
    }
  }

  if (!atTop) {
    return const DoNothing();
  }

  final newest = newestRecordableChain(chains);
  return newest == null ? const DoNothing() : RecordPosition(newest);
}
