/// In-memory, per-endpoint rate-limit memory.
///
/// X rate limits are per-endpoint, not per-account-globally: an account can be
/// `429` on `/SearchTimeline` while still serving `/TweetDetail`. We therefore
/// remember the reset time keyed by (account, endpoint). State is intentionally
/// not persisted — 429 windows are short (~15 min), so a restart simply forgets
/// them.
///
/// Besides 429s, the `x-rate-limit-remaining` quota X sends with every answer
/// is followed (ported from QuaX commit 6dca796), so an account is set aside
/// once its quota is spent, before it is refused. Flags only order accounts:
/// [AccountSelector] still falls back to a flagged one.
class RateLimitTracker {
  static final Map<String, Map<String, DateTime>> _resetByAccountEndpoint = {};

  /// Requests left in the current window, as X reports on every response and
  /// as counted down locally since. Only ever narrows [isLimited]'s answer.
  static final Map<String, Map<String, ({int remaining, DateTime resetAt})>> _quotaByAccountEndpoint = {};

  static bool isLimited(String accountId, String endpoint, DateTime now) {
    final reset = _resetByAccountEndpoint[accountId]?[endpoint];
    return reset != null && reset.isAfter(now);
  }

  static void flag(String accountId, String endpoint, DateTime resetAt) {
    (_resetByAccountEndpoint[accountId] ??= {})[endpoint] = resetAt;
  }

  static void clear(String accountId, String endpoint) {
    _resetByAccountEndpoint[accountId]?.remove(endpoint);
    _quotaByAccountEndpoint[accountId]?.remove(endpoint);
  }

  /// Reads the quota X reports with a response it served. A quota already
  /// spent is flagged until it resets, so the next request on this endpoint
  /// goes to another account first rather than earning a 429. Anything else
  /// lifts the flag, as a success always did.
  static void record(String accountId, String endpoint, Map<String, String> headers, DateTime now) {
    final reported = _quotaFromHeaders(headers);
    if (reported == null) {
      clear(accountId, endpoint);
      return;
    }
    final known = _activeQuota(accountId, endpoint, now);
    // Requests still in flight were already counted down here; an answer X
    // wrote before they arrived must not hand those credits back.
    final remaining = known != null && known.resetAt == reported.resetAt && known.remaining < reported.remaining
        ? known.remaining
        : reported.remaining;
    _setQuota(accountId, endpoint, remaining, reported.resetAt);
  }

  /// Counts one request against the known quota as it is sent, so a parallel
  /// batch spreads over accounts instead of all landing on the one about to
  /// run out. An unknown quota is left unknown.
  static void consume(String accountId, String endpoint, DateTime now) {
    final known = _activeQuota(accountId, endpoint, now);
    if (known != null) {
      _setQuota(accountId, endpoint, known.remaining - 1, known.resetAt);
    }
  }

  static void _setQuota(String accountId, String endpoint, int remaining, DateTime resetAt) {
    (_quotaByAccountEndpoint[accountId] ??= {})[endpoint] = (remaining: remaining, resetAt: resetAt);
    if (remaining > 0) {
      _resetByAccountEndpoint[accountId]?.remove(endpoint);
    } else {
      flag(accountId, endpoint, resetAt);
    }
  }

  static ({int remaining, DateTime resetAt})? _activeQuota(String accountId, String endpoint, DateTime now) {
    final quota = _quotaByAccountEndpoint[accountId]?[endpoint];
    return quota != null && quota.resetAt.isAfter(now) ? quota : null;
  }

  static ({int remaining, DateTime resetAt})? _quotaFromHeaders(Map<String, String> headers) {
    final remaining = int.tryParse(headers['x-rate-limit-remaining'] ?? '');
    final reset = int.tryParse(headers['x-rate-limit-reset'] ?? '');
    if (remaining == null || reset == null || reset <= 0 || reset > 8640000000000) {
      return null;
    }
    return (remaining: remaining, resetAt: DateTime.fromMillisecondsSinceEpoch(reset * 1000));
  }

  /// Endpoints still limited for an account, with the time each frees up.
  /// Windows that have already elapsed are left out, so this reads as the
  /// account's live state rather than its history.
  static Map<String, DateTime> activeFor(String accountId, DateTime now) => {
        for (final entry in (_resetByAccountEndpoint[accountId] ?? const <String, DateTime>{}).entries)
          if (entry.value.isAfter(now)) entry.key: entry.value,
      };
}
