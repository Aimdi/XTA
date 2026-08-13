import 'package:xta/tweet/paginated_tweet_list.dart';

/// Persists per-group feed state across navigator pop/push so that re-opening
/// the same pushed group route restores the loaded tweets and scroll offset.
///
/// Keys are caller-defined (`SubscriptionGroupFeed` passes the groupId for the
/// pushed-route case and skips the cache entirely for home-tab usages, so
/// home-tab and pushed-route feeds for the same group never share state).
///
/// Entries are cleared on subscription/group reload (wired in `main.dart`).
///
/// Bounded, because each entry holds every page of tweets its feed loaded: a
/// session spent moving between groups would otherwise retain all of them for
/// as long as the app runs. The pattern is `VideoControllerPool(maxSize: 5)`'s.
class FeedSessionCache {
  FeedSessionCache({this.maxEntries = 8});

  /// How many feeds keep their loaded tweets and scroll offset.
  final int maxEntries;

  final Map<String, TweetFeedController> _controllers = {};
  final Map<String, double> _offsets = {};
  final Map<String, bool> _mediaOnly = {};

  /// Keys in least-recently-used order. A `Map` in Dart iterates in insertion
  /// order, so touching a key means removing and re-adding it.
  final List<String> _order = [];

  int get length => _controllers.length;

  void _touch(String key) {
    _order.remove(key);
    _order.add(key);
  }

  /// Evicts without disposing, for the reason [invalidateAll] gives: a feed
  /// that is still mounted may hold this controller and will detach from it in
  /// its own dispose. Dropping the reference is what lets it be collected.
  void _evictOldest() {
    while (_order.length > maxEntries) {
      final oldest = _order.removeAt(0);
      _controllers.remove(oldest);
      _offsets.remove(oldest);
      _mediaOnly.remove(oldest);
    }
  }

  TweetFeedController getOrCreateController(String key) {
    _touch(key);
    final controller = _controllers.putIfAbsent(
      key,
      () => TweetFeedController(),
    );
    _evictOldest();

    return controller;
  }

  void saveOffset(String key, double offset) {
    _offsets[key] = offset;
  }

  double? readOffset(String key) => _offsets[key];

  // The media-only filter lives here with the controller because the cached
  // controller holds tweets loaded under that filter — restoring one without
  // the other would show mismatched content.
  void saveMediaOnly(String key, bool value) {
    _mediaOnly[key] = value;
  }

  bool readMediaOnly(String key) => _mediaOnly[key] ?? false;

  // Drop references without disposing: a currently-mounted feed state may
  // still hold a reference and will detach its own listener in its dispose().
  // The old controller becomes garbage once the body remounts via the shell's
  // KeyedSubtree onto the freshly-cached controller.
  void invalidateAll() {
    _controllers.clear();
    _offsets.clear();
    _mediaOnly.clear();
    _order.clear();
  }

  /// Drops one feed so the next [getOrCreateController] starts empty.
  ///
  /// Does not dispose: a still-mounted body may hold the old controller until
  /// it remounts, same as [invalidateAll].
  void evict(String key) {
    _controllers.remove(key);
    _offsets.remove(key);
    _mediaOnly.remove(key);
    _order.remove(key);
  }
}
