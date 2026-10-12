import 'package:xta/client/account_selector.dart';
import 'package:xta/database/entities.dart';

/// In-flight X searches for a Following feed with one usable account, or none.
/// Every chunk at once cascaded into 404s and flagged accounts (#165 / #170).
const int feedChunkFetchConcurrency = 3;

/// The most searches a feed opens at once, however many accounts there are.
const int feedChunkFetchConcurrencyMax = 6;

/// About two searches per account, from [feedChunkFetchConcurrency] up to
/// [feedChunkFetchConcurrencyMax].
int feedChunkConcurrencyFor(int usableAccounts) =>
    (usableAccounts * 2).clamp(feedChunkFetchConcurrency, feedChunkFetchConcurrencyMax);

/// Accounts a Following search can go to: on for home feeds, with a sign-in
/// not flagged broken and not rate-limited on search ([isSearchLimited]). With
/// every account off for home feeds the transport falls back to all of them,
/// so then all of them count.
int usableSearchAccounts(
  List<Account> accounts,
  Set<String> disabledIds,
  DateTime now, {
  bool Function(Account)? isSearchLimited,
}) {
  final limited = isSearchLimited ?? (_) => false;
  final working = accounts.where((a) => !isNotFoundFlagged(a, now) && !limited(a)).toList();
  final enabled = working.where((a) => !disabledIds.contains(a.id)).length;
  return enabled > 0 ? enabled : working.length;
}
