import 'dart:async';

import 'package:xta/plugins/reddit/reddit_client.dart';

/// What one cached listing is: a subreddit read in one sort order.
///
/// The page size is deliberately not part of the key. Every surface asks for
/// the same page ([kRedditListingPageSize]) and shows as much of it as it has
/// room for, which is what lets the home interleave and the Reddit tab share
/// one request rather than two.
typedef RedditListingKey = ({
  String subreddit,
  RedditSort sort,
  RedditTimeFilter? timeFilter,
});

/// How long a listing is handed out again before it is fetched afresh.
///
/// Short on purpose: the point is that swiping Following → For you → Reddit
/// does not download the same subreddit three times, not that the reader spends
/// the afternoon looking at a frozen feed.
const Duration kRedditListingTtl = Duration(minutes: 3);

/// How long a subreddit that just failed is left alone.
///
/// A private, renamed or rate-limited subreddit fails the same way for every
/// surface, and asking it again immediately only spends the reader's remaining
/// requests on the answer they already have.
const Duration kRedditListingFailureTtl = Duration(seconds: 30);

/// How many listings are held at once. A reader with a lot of subreddits in a
/// lot of sorts must not accumulate every one of them for as long as the app
/// runs; the pattern is `FeedSessionCache`'s.
const int kRedditListingCacheSize = 24;

DateTime _wallClock() => DateTime.now();

/// The one place a Reddit listing is fetched, so the three surfaces that show
/// Reddit posts — the home timeline, For you, and the Reddit tab — read the
/// same fetch instead of each paying for their own.
///
/// It holds futures rather than results, so surfaces that mount at the same
/// moment join one request instead of racing to make three identical ones.
class RedditListingCache {
  RedditListingCache({
    this.ttl = kRedditListingTtl,
    this.failureTtl = kRedditListingFailureTtl,
    this.maxEntries = kRedditListingCacheSize,
    DateTime Function() clock = _wallClock,
  }) : _now = clock;

  final Duration ttl;
  final Duration failureTtl;
  final int maxEntries;

  final DateTime Function() _now;

  final Map<RedditListingKey, _Entry> _entries = {};

  /// Keys in least-recently-used order. A Dart `Map` iterates in insertion
  /// order, so touching a key means removing and re-adding it.
  final List<RedditListingKey> _order = [];

  int get length => _entries.length;

  /// The listing for [key], from a recent read when there is one.
  ///
  /// [forceRefresh] is what a pull-to-refresh passes: without it the reader
  /// could never get new posts, because the answer to "give me this subreddit"
  /// would keep being the one already on screen.
  Future<RedditListing> listing(
    RedditListingKey key, {
    required Future<RedditListing> Function() fetch,
    bool forceRefresh = false,
  }) {
    _prune();

    if (!forceRefresh) {
      final cached = _entries[key];
      if (cached != null) {
        _touch(key);

        return cached.future;
      }
    }

    final entry = _Entry(fetch());
    _entries[key] = entry;
    _touch(key);
    _evict();

    // Noted on the entry rather than awaited here: the caller owns the failure
    // and reports it, this only needs to know one happened so the subreddit is
    // left alone for a while instead of being asked again by every surface.
    unawaited(
      entry.future.then(
        (_) => entry.settle(_now(), failed: false),
        onError: (_) => entry.settle(_now(), failed: true),
      ),
    );

    return entry.future;
  }

  /// Everything cached is now an answer to a different question — the reader
  /// signed in, signed out, or changed which route the plugin reads through.
  void clear() {
    _entries.clear();
    _order.clear();
  }

  void _touch(RedditListingKey key) {
    _order.remove(key);
    _order.add(key);
  }

  void _prune() {
    final now = _now();
    _entries.removeWhere((key, entry) {
      final stale = entry.isStaleAt(now, ttl: ttl, failureTtl: failureTtl);
      if (stale) {
        _order.remove(key);
      }

      return stale;
    });
  }

  void _evict() {
    while (_order.length > maxEntries) {
      _entries.remove(_order.removeAt(0));
    }
  }
}

class _Entry {
  _Entry(this.future);

  final Future<RedditListing> future;

  DateTime? _settledAt;
  bool _failed = false;

  void settle(DateTime at, {required bool failed}) {
    _settledAt = at;
    _failed = failed;
  }

  /// A request still in flight is never stale: joining it is the whole point,
  /// so three surfaces mounting at once make one request between them.
  bool isStaleAt(
    DateTime now, {
    required Duration ttl,
    required Duration failureTtl,
  }) {
    final settledAt = _settledAt;
    if (settledAt == null) {
      return false;
    }

    return now.difference(settledAt) >= (_failed ? failureTtl : ttl);
  }
}
