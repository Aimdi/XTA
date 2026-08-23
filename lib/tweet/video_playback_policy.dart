/// When a tweet's video is allowed to allocate a player.
///
/// Pure and free of Flutter so it can be unit-tested, in the same spirit as
/// `AccountSelector`. The distinction matters because creating a player is the
/// expensive half: each one spins a libmpv instance and a native texture, while
/// building the widget is nearly free.
library;

/// Whether the widget should show its poster and a tap target instead of a
/// player. Autoplay, looping GIFs (`alwaysPlay`) and an explicit tap all opt in
/// to playback; everything else waits for the reader to ask.
bool showsPlayButton({
  required bool autoPlayPref,
  required bool alwaysPlay,
  required bool userRequestedPlay,
  required bool alreadyCached,
}) => !autoPlayPref && !alwaysPlay && !userRequestedPlay && !alreadyCached;

/// Whether to allocate the player now.
///
/// Even a tile that has opted in waits until it has been on screen at least
/// once. Without that, a looping GIF far below the fold allocates libmpv purely
/// by being built, which is what forced the feed's `cacheExtent` back down: a
/// larger cache window built more tiles, and each one cost a native player.
///
/// [alreadyCached] short-circuits the wait — the pool already holds the player,
/// so attaching to it costs nothing and delaying would only make a scroll-back
/// flash its poster again. It short-circuits only while the tile is [isVisible]:
/// a tile that has scrolled away gives its player back so the pool can evict it,
/// and a cached entry that re-attached from `build` alone would pin it again
/// before it was ever on screen.
bool shouldCreatePlayer({
  required bool autoPlayPref,
  required bool alwaysPlay,
  required bool userRequestedPlay,
  required bool alreadyCached,
  required bool hasBeenVisible,
  required bool isVisible,
}) {
  if (showsPlayButton(
    autoPlayPref: autoPlayPref,
    alwaysPlay: alwaysPlay,
    userRequestedPlay: userRequestedPlay,
    alreadyCached: alreadyCached,
  )) {
    return false;
  }
  return hasBeenVisible || (alreadyCached && isVisible);
}

/// Whether an off-screen tile may hand its pooled player back.
///
/// Fullscreen must never reclaim: the opaque route stops the tile being painted
/// (VisibilityDetector reports 0), and releasing the pool ref would let eviction
/// dispose the player still showing on the fullscreen route.
bool shouldReleaseHiddenPlayer({
  required bool isFullscreen,
  required bool mounted,
  required bool hasPoolKey,
  required bool anyVisible,
  required double visibleFraction,
}) {
  if (!mounted || isFullscreen || !hasPoolKey) return false;
  if (anyVisible || visibleFraction >= 0.5) return false;
  return true;
}

/// Whether the pool may start a *new* libmpv instance.
///
/// Reattaching to an existing key is free. Creating past [maxSize] when nothing
/// is evictable is what exhausted MediaCodec and froze the home timeline.
bool videoPoolCanCreate({
  required bool alreadyCached,
  required int entryCount,
  required int maxSize,
  bool hasEvictable = false,
}) => alreadyCached || entryCount < maxSize || hasEvictable;
