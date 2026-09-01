import 'package:xta/client/client.dart';

/// Tells whether a chain sits at or below the reader's last read position.
typedef SeenChainPredicate = bool Function(TweetChain chain);

/// A page cut at the reader's last position: [keep] is what is new, [held] is
/// everything from the first already-read chain down, and [reachedBoundary]
/// says the cut actually happened (as opposed to a page of nothing but new
/// posts, which pages on).
typedef CatchUpSplit = ({List<TweetChain> keep, List<TweetChain> held, bool reachedBoundary});

/// Splits [chains] at the first chain [isSeen] accepts.
///
/// The held-back chains are handed back rather than dropped so "show older
/// posts" can put them straight on screen instead of re-fetching the page —
/// and so the feed never opens a hole where they were.
CatchUpSplit splitAtFirstSeen(List<TweetChain> chains, SeenChainPredicate isSeen) {
  final index = chains.indexWhere(isSeen);
  if (index < 0) {
    return (keep: chains, held: const <TweetChain>[], reachedBoundary: false);
  }
  return (keep: chains.sublist(0, index), held: chains.sublist(index), reachedBoundary: true);
}

/// What the card closing a catch-up feed is allowed to claim.
enum CatchUpMessage {
  /// Everything between the newest post and the last read one was loaded.
  caughtUp,

  /// The boundary was the very first chain: nothing has been posted since.
  nothingNew,

  /// The load stopped filling the gap before it reached the stored posts, so
  /// posts between here and the top were never fetched. The reader must not be
  /// told they are finished.
  mayBeIncomplete,
}

CatchUpMessage catchUpMessageFor({required bool mayBeIncomplete, required bool nothingNew}) {
  if (mayBeIncomplete) {
    return CatchUpMessage.mayBeIncomplete;
  }
  return nothingNew ? CatchUpMessage.nothingNew : CatchUpMessage.caughtUp;
}
