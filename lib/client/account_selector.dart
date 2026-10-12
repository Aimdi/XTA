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

  /// Requests an account has in flight on the target endpoint right now.
  final int Function(Account) inFlight;

  AccountSelector(
    this.accounts,
    this.now, {
    bool Function(Account)? isRateLimited,
    int? Function(Account)? remainingQuota,
    int Function(Account)? inFlight,
  }) : isRateLimited = isRateLimited ?? ((_) => false),
       remainingQuota = remainingQuota ?? ((_) => null),
       inFlight = inFlight ?? ((_) => 0);

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

  /// The accounts least busy right now, then those with the most requests
  /// left, so a burst spreads over all of them and they drain evenly. An
  /// account whose quota is not known ties with the best known one rather than
  /// beating it: one failing with errors that carry no quota would otherwise
  /// take every request. Ties stay random.
  List<Account> _leastUsed(List<Account> pool) {
    final fewest = pool.map(inFlight).reduce(min);
    final idle = pool.where((a) => inFlight(a) == fewest).toList();
    final known = idle.map(remainingQuota).whereType<int>();
    if (known.isEmpty) return idle;
    final best = known.reduce(max);
    return idle.where((a) => (remainingQuota(a) ?? best) == best).toList();
  }
}
