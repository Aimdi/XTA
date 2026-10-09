/// One rotating read over a per-member cache, the way every Discover source
/// samples its members: a capped batch of the least recently asked, each
/// request bounded so the batch fits the source's time, and the rows so far
/// handed back as they arrive.
library;

import 'package:xta/group/group_discovery.dart';
import 'package:xta/plugins/account_posts.dart';

typedef RotatingRead<T> = ({List<T> rows, int read});

/// Reads up to [perLoad] members of [keys] (twice that on a "scan more") that
/// [cache] has not answered for, newest-needed first, and merges the result
/// with everything it already holds. [read] counts the members answered for.
Future<RotatingRead<T>> readRotating<T>(
  AccountPostCache<T> cache,
  List<String> keys,
  DiscoveryScan scan, {
  required int perLoad,
  required Future<List<T>> Function(String key, Duration budget) fetch,
  required void Function(RotatingRead<T> soFar) onRows,
}) async {
  final fetches = scan.more ? perLoad * 2 : perLoad;
  final budget = discoveryMemberBudget(fetches);
  final ordered = cache.prioritize(keys);
  RotatingRead<T> result(List<T> rows) => (rows: rows, read: ordered.length - cache.pendingCount(ordered));
  final rows = await cache.merge(
    ordered,
    (key) => fetch(key, budget),
    maxFetches: fetches,
    onPartial: (rows) => onRows(result(rows)),
  );
  final whole = result(rows);
  onRows(whole);
  return whole;
}
