/// A refresh the reader did not ask for, held until it is not in their way.
///
/// Changing what a group contains means the feed has to be refetched. Doing it
/// there and then empties the list and returns it to the top, which is fine for
/// a reader sitting at the top and rude to one who is fifty posts down and only
/// added somebody to a group. The change is still applied — just at the next
/// moment the reader is back where the jump costs them nothing.
library;

import 'package:xta/constants.dart';

class HeldRefresh {
  bool _pending = false;

  /// Whether there is a refresh waiting for the reader to come back up.
  bool get isPending => _pending;

  /// Asks for a refresh. Returns whether to run it now.
  ///
  /// Several changes while the reader is scrolled down collapse into the one
  /// refresh that eventually runs.
  bool request({required bool atTop}) {
    if (atTop) {
      _pending = false;
      return true;
    }

    _pending = true;
    return false;
  }

  /// Tells it the reader is back at the top. Returns whether a held refresh
  /// should run now — false when there was nothing waiting.
  bool returnedToTop() {
    if (!_pending) {
      return false;
    }

    _pending = false;
    return true;
  }
}

/// Whether a membership-driven refresh should run immediately.
///
/// [pixels] null means the scroll position is temporarily unavailable
/// (NestedScrollView attach churn when a sheet closes). Prefer
/// [lastKnownAtTop] over treating null as the top — that mis-read is why
/// adding a member while scrolled mid-timeline wiped the list as soon as the
/// membership sheet closed.
bool feedRefreshAtTop({
  required double? pixels,
  required bool lastKnownAtTop,
  double threshold = feedReadPositionTopThresholdPx,
}) {
  if (pixels == null) {
    return lastKnownAtTop;
  }
  return pixels <= threshold;
}

/// Whether a NestedScrollView scroll restore should wait another frame.
///
/// Following used to reschedule forever while the inner controller had 0 or 2
/// positions, which froze the home timeline. Caught-up restore was already
/// capped; offset restore was not.
bool shouldRetryScrollRestore({
  required bool mounted,
  required bool positionReady,
  required int attempts,
  int maxAttempts = maxCaughtUpRestoreFrames,
}) {
  if (!mounted || positionReady) {
    return false;
  }
  return attempts + 1 < maxAttempts;
}
