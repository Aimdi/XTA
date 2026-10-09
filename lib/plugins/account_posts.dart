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

import 'dart:async';

import 'package:xta/group/future_pool.dart';

/// How long [AccountPostCache.merge] waits before painting another partial.
///
/// Each account that answers used to rebuild the whole tab. Two hundred
/// milliseconds is long enough to land several accounts on one frame, and
/// short enough that the first posts still appear while the rest are paced.
const Duration kAccountPostsPartialThrottle = Duration(milliseconds: 200);

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
  final Map<String, DateTime> _attemptedAt = {};

  /// Forgets everything, for when the answers would now come from elsewhere —
  /// a different session, a different instance.
  void clear() {
    _entries.clear();
    _attemptedAt.clear();
  }

  /// When each account last answered, for persisting alongside its posts.
  Map<String, DateTime> get answeredAt => {for (final e in _entries.entries) e.key: e.value.at};

  /// Restores what an account answered at [at] — from a snapshot written
  /// before a restart — unless something newer is already held. Within [ttl]
  /// the account is then not asked again, exactly as if it had been read in
  /// this session.
  void seed(String key, List<T> posts, DateTime at) {
    final held = _entries[key];
    if (held != null && !held.at.isBefore(at)) {
      return;
    }
    _entries[key] = (at: at, posts: posts);
    _attemptedAt[key] ??= at;
  }

  /// [keys] with the ones never asked for first, then the longest unasked.
  ///
  /// A capped [merge] spends its budget on the front of the list, so a caller
  /// that passes the same order every time reads the same accounts every time
  /// and never reaches the rest. Ties keep the caller's order.
  List<String> prioritize(List<String> keys) => prioritizeByAttempt(keys, (key) => _attemptedAt[key]);

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
  ///
  /// [onPartial] hears the merged timeline grow as each account answers, so a
  /// screen can paint the first account's posts while the rest are still
  /// behind the pacing — instead of a spinner for the sum of every wait.
  Future<List<T>> merge(
    List<String> keys,
    Future<List<T>> Function(String key) fetch, {
    bool forceRefresh = false,
    int? maxFetches,
    void Function(List<T> postsSoFar)? onPartial,
  }) async {
    if (keys.isEmpty) {
      return const [];
    }

    var remaining = maxFetches ?? keys.length;
    Object? lastError;
    var readAnything = false;
    final partial = _ThrottledPartial<T>(onPartial);
    final run = _MergeRun<T>(_merged, partial, partial.listening ? _heldFor(keys) : {});
    try {
      final batches = await mapWithConcurrency(keys, concurrency, (key) async {
        if (!forceRefresh) {
          if (_fresh(key) case final cached?) {
            readAnything |= cached.isNotEmpty;
            return run.answer(key, cached);
          }
        }
        // Decremented before the await, so concurrent workers cannot each see the
        // last slot and all take it. Past the budget, what the cache holds — even
        // stale — beats nothing: a forced refresh with more accounts than the cap
        // must not collapse the timeline to the first batch.
        if (remaining <= 0) {
          final held = _entries[key]?.posts ?? <T>[];
          readAnything |= held.isNotEmpty;
          return run.answer(key, held);
        }
        remaining--;
        _attemptedAt[key] = DateTime.now();
        run.showHeld();

        try {
          final posts = await fetch(key);
          _entries[key] = (at: DateTime.now(), posts: posts);
          readAnything |= posts.isNotEmpty;
          return run.answer(key, posts);
        } catch (e) {
          lastError = e;
          // What the account said last beats it vanishing from the timeline
          // because one refresh of it failed.
          return run.answer(key, _entries[key]?.posts ?? <T>[]);
        }
      });

      // Posts kept only because their refresh failed are not an answer: when
      // nothing else was read, the failure is what the reader needs to hear.
      if (!readAnything && lastError != null) {
        throw lastError!;
      }
      final posts = _merged(batches);

      return posts;
    } finally {
      partial.flush();
    }
  }

  /// What each of [keys] last answered, stale or not — painted in its place
  /// until it answers again, so a refresh never collapses the timeline to the
  /// accounts read so far.
  Map<String, List<T>> _heldFor(List<String> keys) => {
    for (final key in keys)
      if (_entries[key]?.posts case final posts? when posts.isNotEmpty) key: posts,
  };

  List<T> _merged(List<List<T>> batches) {
    final posts = batches.expand((e) => e.take(perAccount)).toList();
    posts.sort(
      (a, b) => (dateOf(b) ?? DateTime(0)).compareTo(dateOf(a) ?? DateTime(0)),
    );
    return posts;
  }

  /// How many of [keys] would have to be fetched right now — what the cache
  /// cannot already answer. The tab uses it to say that more is still coming.
  int pendingCount(List<String> keys) =>
      keys.where((key) => _fresh(key) == null).length;
}

/// One [AccountPostCache.merge]: the accounts answered so far, and what the
/// rest last said, painted together.
class _MergeRun<T> {
  _MergeRun(this._merge, this._partial, this._held);

  final List<T> Function(List<List<T>>) _merge;
  final _ThrottledPartial<T> _partial;
  final Map<String, List<T>> _held;
  final _done = <List<T>>[];
  var _shownHeld = false;

  /// Records [key]'s answer and repaints when it changed what is on screen.
  List<T> answer(String key, List<T> posts) {
    final dropped = _held.remove(key);
    if (_partial.listening) {
      _done.add(posts);
      if (posts.isNotEmpty || (dropped?.isNotEmpty ?? false)) {
        _paint();
      }
    }
    return posts;
  }

  /// Stale-while-revalidate: paint what is already held before the first
  /// network wait, so a refresh does not blank the feed into one.
  void showHeld() {
    if (_shownHeld || _held.isEmpty) {
      return;
    }
    _shownHeld = true;
    _paint();
  }

  void _paint() => _partial(_merge([..._done, ..._held.values]));
}

/// First paint is immediate; later ones share a frame window.
class _ThrottledPartial<T> {
  _ThrottledPartial(this._emit);

  final void Function(List<T>)? _emit;
  List<T>? _pending;

  bool get listening => _emit != null;
  DateTime? _last;
  Timer? _timer;
  var _opened = false;

  void call(List<T> posts) {
    if (_emit == null) {
      return;
    }
    if (!_opened) {
      _opened = true;
      _send(posts);
      return;
    }
    _pending = posts;
    final wait = _last == null
        ? Duration.zero
        : kAccountPostsPartialThrottle - DateTime.now().difference(_last!);
    if (wait <= Duration.zero) {
      _send(posts);
      return;
    }
    _timer ??= Timer(wait, flush);
  }

  void flush() {
    _timer?.cancel();
    _timer = null;
    final posts = _pending;
    _pending = null;
    if (posts != null) {
      _send(posts);
    }
  }

  void _send(List<T> posts) {
    _pending = null;
    _timer?.cancel();
    _timer = null;
    _last = DateTime.now();
    _emit!(posts);
  }
}

/// [keys] never attempted first, then the longest ago; ties keep the caller's
/// order. The rotation every per-member cache samples its members with.
List<String> prioritizeByAttempt(List<String> keys, DateTime? Function(String key) attemptedAt) {
  final order = {for (var i = 0; i < keys.length; i++) keys[i]: i};
  return order.keys.toList()
    ..sort((a, b) {
      final age = (attemptedAt(a) ?? DateTime(0)).compareTo(attemptedAt(b) ?? DateTime(0));
      return age != 0 ? age : order[a]!.compareTo(order[b]!);
    });
}
