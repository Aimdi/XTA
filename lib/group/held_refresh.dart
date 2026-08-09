/// A refresh the reader did not ask for, held until it is not in their way.
///
/// Changing what a group contains means the feed has to be refetched. Doing it
/// there and then empties the list and returns it to the top, which is fine for
/// a reader sitting at the top and rude to one who is fifty posts down and only
/// added somebody to a group. The change is still applied — just at the next
/// moment the reader is back where the jump costs them nothing.
library;

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
