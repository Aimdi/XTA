/// Candidates mined from what the group feed already stored — no request.
///
/// The feed has searched every member and kept the chains in SQLite. Every
/// repost, quote, reply and mention in them points at an account some member
/// cares about, which is exactly what Discover is after.
library;

import 'package:dart_twitter_api/twitter_api.dart' show User, UserMention;
import 'package:sqflite/sqflite.dart';
import 'package:xta/client/client.dart';
import 'package:xta/group/feed_cache.dart';
import 'package:xta/group/feed_chunk_plan.dart';
import 'package:xta/group/group_discovery.dart';

typedef FeedDiscovery = ({List<DiscoveryAccount> accounts, Set<String> covered});

/// Reads the plan's cached chunks. [covered] are the members of every chunk
/// that had rows: their recent activity has been looked at.
Future<FeedDiscovery> mineFeedDiscovery(Database database, GroupFeedPlan plan) async {
  final members = {for (final member in plan.split.xMembers) member.id};
  final accounts = <DiscoveryAccount>[];
  final covered = <String>{};
  for (final chunk in plan.chunks) {
    final cached = await readCachedChainsForHashes(database, [chunk.hash]);
    if (cached.chains.isEmpty) continue;
    covered.addAll(chunk.users.map((user) => user.id));
    accounts.addAll(feedDiscoveryAccounts(cached.chains, members: members, includeReplies: plan.includeReplies));
  }
  return (accounts: accounts, covered: covered);
}

/// Accounts the members' own posts in [chains] point at, each with the member
/// as its supporter. Members themselves are never candidates.
List<DiscoveryAccount> feedDiscoveryAccounts(
  Iterable<TweetChain> chains, {
  required Set<String> members,
  required bool includeReplies,
}) => [
  for (final chain in chains)
    for (final tweet in chain.tweets)
      if (tweet.user?.idStr case final String memberId when members.contains(memberId))
        ..._TweetMiner(tweet, memberId, members).mine(includeReplies: includeReplies),
];

class _TweetMiner {
  final TweetWithCard tweet;
  final String memberId;
  final Set<String> members;
  final _seen = <String>{};

  _TweetMiner(this.tweet, this.memberId, this.members);

  List<DiscoveryAccount> mine({required bool includeReplies}) {
    final retweet = tweet.retweetedStatusWithCard;
    final quote = tweet.quotedStatusWithCard ?? retweet?.quotedStatusWithCard;
    return [
      if (retweet != null) ?_shared(retweet, DiscoverySignal.reposted),
      if (quote != null) ?_shared(quote, DiscoverySignal.quoted),
      if (tweet.inReplyToUserIdStr case final String id when includeReplies)
        ?_named(id, tweet.inReplyToScreenName ?? '', '', DiscoverySignal.replied),
      for (final mention in [...?tweet.entities?.userMentions, ...?tweet.noteEntities?.userMentions])
        ?_mentioned(mention),
    ];
  }

  DiscoverySupporter _supporter(DiscoverySignal kind) => DiscoverySupporter(
    memberId: memberId,
    handle: tweet.user?.screenName ?? '',
    name: tweet.user?.name ?? '',
    kind: kind,
    date: tweet.createdAt,
  );

  bool _candidate(String? id) => id != null && id.isNotEmpty && !members.contains(id) && _seen.add(id);

  /// A reposted or quoted post: the candidate wrote it, so it is the sample.
  DiscoveryAccount? _shared(TweetWithCard post, DiscoverySignal kind) {
    final User? author = post.user;
    if (author == null || !_candidate(author.idStr)) return null;
    return DiscoveryAccount(
      source: DiscoverySource.x,
      id: author.idStr!,
      handle: author.screenName ?? '',
      name: author.name ?? '',
      avatarUrl: author.profileImageUrlHttps,
      bio: author.description,
      followersCount: author.followersCount,
      text: post.fullText ?? post.text ?? '',
      postUrl: 'https://x.com/i/status/${post.idStr}',
      date: post.createdAt,
      supportingPost: post,
      supporters: [_supporter(kind)],
    );
  }

  DiscoveryAccount? _mentioned(UserMention mention) =>
      _named(mention.idStr ?? '', mention.screenName ?? '', mention.name ?? '', DiscoverySignal.mentioned);

  /// A reply target or a mention: only a name is known, and the member's own
  /// post is what shows why.
  DiscoveryAccount? _named(String id, String handle, String name, DiscoverySignal kind) {
    if (!_candidate(id)) return null;
    return DiscoveryAccount(
      source: DiscoverySource.x,
      id: id,
      handle: handle,
      name: name.isEmpty ? handle : name,
      text: tweet.fullText ?? tweet.text ?? '',
      postUrl: 'https://x.com/i/status/${tweet.idStr}',
      date: tweet.createdAt,
      supportingPost: tweet,
      supporters: [_supporter(kind)],
    );
  }
}
