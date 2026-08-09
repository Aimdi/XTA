/// Reading one page of posts per followed account, for the plugins that have
/// no server-side timeline to ask for.
///
/// Threads, Bluesky and Mastodon all work the same way: there is no "your
/// following feed" to fetch, so the plugin asks each account in turn and merges
/// the answers. Each had written that out for itself — the same concurrency,
/// the same per-account failure isolation, the same newest-first sort, the same
/// rule about when an error is worth surfacing — and the same short cache had
/// to be added to Threads when its tab, the home timeline and every group feed
/// turned out to be asking Meta the same question three times over.
library;

import 'package:xta/group/future_pool.dart';

/// How long an account's posts are reused before its network is asked again.
///
/// The tab, the home timeline and every group feed read the same accounts.
/// Without this, opening a group after the tab asked for all of them a second
/// time — which costs the reader rate limit at best and, on a network that
/// watches for scripted behaviour, the account at worst.
const Duration kAccountPostsCacheTtl = Duration(minutes: 10);

/// Merges per-account pages into one timeline, remembering them briefly.
///
/// [dateOf] is how a post says when it was published; posts without one sort
/// last rather than being dropped, since the caller may still want to show them.
class AccountPostCache<T> {
  final DateTime? Function(T post) dateOf;

  /// How many posts of each account's page reach the merged timeline.
  final int perAccount;

  /// How many accounts are read at once. Low on purpose: these are other
  /// people's servers, and a burst is what rate limits are for.
  final int concurrency;

  final Duration ttl;

  AccountPostCache({
    required this.dateOf,
    required this.perAccount,
    this.concurrency = 2,
    this.ttl = kAccountPostsCacheTtl,
  });

  final Map<String, ({DateTime at, List<T> posts})> _entries = {};

  /// Forgets everything, for when the answers would now come from elsewhere —
  /// a different session, a different instance.
  void clear() => _entries.clear();

  List<T>? _fresh(String key) {
    final entry = _entries[key];
    if (entry == null || DateTime.now().difference(entry.at) > ttl) {
      return null;
    }
    return entry.posts;
  }

  /// The merged timeline for [keys], newest first.
  ///
  /// One account failing does not empty the timeline — a renamed handle, or one
  /// instance being down, would otherwise take every other account's posts with
  /// it. The error surfaces only when nothing at all could be read.
  ///
  /// [maxFetches] bounds how many accounts are actually asked for on this call.
  /// Anything already cached is free and always included, so a reader who
  /// imported eight hundred follows gets a timeline that fills in over the next
  /// few refreshes instead of eight hundred requests at once — and no account
  /// is permanently left out, which a plain "first N accounts" cap would do.
  Future<List<T>> merge(
    List<String> keys,
    Future<List<T>> Function(String key) fetch, {
    bool forceRefresh = false,
    int? maxFetches,
  }) async {
    if (keys.isEmpty) {
      return const [];
    }

    var remaining = maxFetches ?? keys.length;
    Object? lastError;
    final batches = await mapWithConcurrency(keys, concurrency, (key) async {
      if (!forceRefresh) {
        if (_fresh(key) case final cached?) {
          return cached;
        }
      }
      // Decremented before the await, so concurrent workers cannot each see the
      // last slot and all take it. Past the budget, what the cache holds — even
      // stale — beats nothing: a forced refresh with more accounts than the cap
      // must not collapse the timeline to the first batch.
      if (remaining <= 0) {
        return _entries[key]?.posts ?? <T>[];
      }
      remaining--;

      try {
        final posts = await fetch(key);
        _entries[key] = (at: DateTime.now(), posts: posts);
        return posts;
      } catch (e) {
        lastError = e;
        return <T>[];
      }
    });

    final posts = batches.expand((e) => e.take(perAccount)).toList();
    if (posts.isEmpty && lastError != null) {
      throw lastError!;
    }

    posts.sort((a, b) => (dateOf(b) ?? DateTime(0)).compareTo(dateOf(a) ?? DateTime(0)));
    return posts;
  }

  /// How many of [keys] would have to be fetched right now — what the cache
  /// cannot already answer. The tab uses it to say that more is still coming.
  int pendingCount(List<String> keys) => keys.where((key) => _fresh(key) == null).length;
}
