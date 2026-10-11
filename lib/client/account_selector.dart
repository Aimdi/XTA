import 'dart:math';

import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';

/// Health-aware account selection policy.
///
/// Pure and clock-injected so it can be unit-tested without a database. An
/// account is "healthy" when it is not flagged not-found (auth broken) and not
/// currently rate-limited on the target endpoint. Rate-limit state is supplied
/// via [isRateLimited] so this class stays free of global/in-memory state.
/// Whether an account is inside its not-found cooldown, i.e. earlier evidence
/// said its authentication had stopped working.
bool isNotFoundFlagged(Account account, DateTime now) =>
    account.lastNotFoundAt?.add(notFoundCooldown).isAfter(now) ?? false;

class AccountSelector {
  final List<Account> accounts;
  final DateTime now;
  final bool Function(Account) isRateLimited;

  /// Requests an account has left on the target endpoint, or null when not
  /// known yet. Supplied, like [isRateLimited], so this stays pure.
  final int? Function(Account) remainingQuota;

  AccountSelector(
    this.accounts,
    this.now, {
    bool Function(Account)? isRateLimited,
    int? Function(Account)? remainingQuota,
  }) : isRateLimited = isRateLimited ?? ((_) => false),
       remainingQuota = remainingQuota ?? ((_) => null);

  bool _healthy(Account a) => !isNotFoundFlagged(a, now) && !isRateLimited(a);

  /// Picks an account not already tried this request, preferring healthy ones
  /// but falling back to flagged accounts so a request is always attempted while
  /// any account remains. Returns null only when every account has been tried.
  Account? pick({required Set<String> exclude}) {
    final remaining = accounts.where((a) => !exclude.contains(a.id)).toList();
    if (remaining.isEmpty) {
      return null;
    }
    final healthy = remaining.where(_healthy).toList();
    final pool = _leastUsed(healthy.isNotEmpty ? healthy : remaining);
    return pool[Random().nextInt(pool.length)];
  }

  /// The accounts with the most requests left, so all of them drain evenly
  /// instead of one running dry while others idle. One not used on this
  /// endpoint in the current window is fresh and comes first; ties stay random.
  List<Account> _leastUsed(List<Account> pool) {
    int score(Account a) => remainingQuota(a) ?? 1 << 30;
    final best = pool.map(score).reduce(max);
    return pool.where((a) => score(a) == best).toList();
  }
}
