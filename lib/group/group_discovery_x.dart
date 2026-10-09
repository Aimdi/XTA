/// X candidates for group Discover, cheapest signal first.
///
/// The feed's stored chunks already cover every member and cost nothing. A
/// rotating handful of members then get their latest posts read, and another
/// handful their following list, so repeated opens walk through the whole
/// group instead of re-reading the same six accounts.
library;

import 'package:sqflite/sqflite.dart';
import 'package:xta/client/client.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/group/discovery_rotation.dart';
import 'package:xta/group/feed_chunk_plan.dart';
import 'package:xta/group/group_discovery.dart';
import 'package:xta/group/group_discovery_feed.dart';
import 'package:xta/group/group_discovery_follows.dart';
import 'package:xta/plugins/account_posts.dart';

final _xAuthorCache = AccountPostCache<TweetChain>(
  dateOf: (chain) => chain.tweets.firstOrNull?.createdAt,
  perAccount: 20,
);

DiscoveryRead xDiscoveryReader(
  GroupFeedPlan plan,
  List<UserSubscription> members, {
  DiscoveryFollowsCache? follows,
  Future<Database> Function() database = Repository.readOnly,
}) =>
    (scan) => _XScan(plan, members, scan, follows ?? DiscoveryFollowsCache.shared, database).run();

/// Accounts the members follow, each vouched for by the member following it.
List<DiscoveryAccount> xFollowDiscoveryAccounts(
  DiscoveryFollowsByMember byMember,
  Map<String, UserSubscription> members,
) => [
  for (final entry in byMember.entries)
    if (members[entry.key] case final member?)
      for (final follow in entry.value)
        if (!members.containsKey(follow.id))
          DiscoveryAccount(
            source: DiscoverySource.x,
            id: follow.id,
            handle: follow.handle,
            name: follow.name,
            avatarUrl: follow.avatarUrl,
            bio: follow.bio,
            supporters: [
              DiscoverySupporter(
                memberId: member.id,
                handle: member.screenName,
                name: member.name,
                kind: DiscoverySignal.followed,
              ),
            ],
          ),
];

class _XScan {
  final GroupFeedPlan plan;
  final List<UserSubscription> members;
  final DiscoveryScan scan;
  final DiscoveryFollowsCache follows;
  final Future<Database> Function() database;
  late final Map<String, UserSubscription> _byId = {for (final member in members) member.id: member};
  late final List<String> _ids = members.map((member) => member.id).toList();
  List<DiscoveryAccount> _feed = const [];
  List<DiscoveryAccount> _authors = const [];
  List<DiscoveryAccount> _follows = const [];
  final _covered = <String>{};
  Object? _error;

  _XScan(this.plan, this.members, this.scan, this.follows, this.database);

  /// Each stage keeps what the others found; the first failure is reported.
  Future<DiscoveryBatch> run() async {
    await _stage(_mineFeed);
    await Future.wait([_stage(_readAuthors), _stage(_readFollows)]);
    return _batch();
  }

  Future<void> _stage(Future<void> Function() work) async {
    try {
      await work();
    } catch (error) {
      _error ??= error;
    }
  }

  DiscoveryBatch _batch() => DiscoveryBatch([..._feed, ..._authors, ..._follows], read: _covered.length, error: _error);

  void _emit() => scan.onPartial(_batch());

  Future<void> _mineFeed() async {
    final mined = await mineFeedDiscovery(await database(), plan);
    _feed = mined.accounts;
    _covered.addAll(mined.covered);
    _emit();
  }

  Future<void> _readAuthors() => readRotating(
    _xAuthorCache,
    _ids,
    scan,
    perLoad: 6,
    fetch: _loadXAuthor,
    onRows: (soFar) => _absorbAuthors(soFar.rows),
  );

  void _absorbAuthors(List<TweetChain> chains) {
    _authors = feedDiscoveryAccounts(chains, members: _byId.keys.toSet(), includeReplies: false);
    _covered.addAll(_ids.where((id) => _xAuthorCache.pendingCount([id]) == 0));
    _emit();
  }

  Future<void> _readFollows() async {
    final fetches = scan.more ? 8 : 4;
    final result = await follows.read(
      _ids,
      maxFetches: fetches,
      timeout: discoveryMemberBudget(fetches),
      onPartial: _absorbFollows,
    );
    _absorbFollows(result.follows);
    if (result.error case final error?) _error ??= error;
  }

  void _absorbFollows(DiscoveryFollowsByMember byMember) {
    _follows = xFollowDiscoveryAccounts(byMember, _byId);
    _covered.addAll(byMember.keys);
    _emit();
  }
}

Future<List<TweetChain>> _loadXAuthor(String id, Duration budget) async {
  var count = 0;
  final page = await Twitter.getTweets(
    id,
    'tweets',
    const [],
    count: 20,
    includeReplies: false,
    includeRetweets: true,
    getTweetsCounter: () => count,
    incrementTweetsCounter: () => count++,
  ).timeout(budget);
  return page.chains;
}
