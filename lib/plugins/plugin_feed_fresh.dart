import 'package:xta/plugins/account_posts.dart';

/// Whether a plugin feed fetched at [fetchedAt] can be shown again as-is.
///
/// Home strip pins remount their screen on every swipe. Without this, Substack
/// refetched every followed publication (and Notes) each time the tab was
/// opened — the same work Threads / Bluesky already skip via [AccountPostCache].
bool pluginFeedIsFresh(
  DateTime? fetchedAt, {
  DateTime? now,
  Duration ttl = kAccountPostsCacheTtl,
}) {
  if (fetchedAt == null) {
    return false;
  }
  final age = (now ?? DateTime.now()).difference(fetchedAt);
  return !age.isNegative && age <= ttl;
}
