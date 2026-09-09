import 'package:flutter/widgets.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/group/group_discovery.dart';
import 'package:xta/group/future_pool.dart';
import 'package:xta/plugins/account_posts.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/mastodon/mastodon_store.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/subscriptions/users_model.dart';

final _xDiscoveryCache = AccountPostCache<TweetChain>(
  dateOf: (chain) => chain.tweets.firstOrNull?.createdAt, perAccount: 20,
);
final _mastodonDiscoveryCache = AccountPostCache<MastodonPost>(
  dateOf: (post) => post.publishedAt, perAccount: 20,
);

Set<String> discoveryFollowedIds(Iterable<Subscription> subscriptions) => {
  for (final subscription in subscriptions)
    if (_sourceOf(subscription) case final source?) ...[
      discoveryIdentity(source, subscription.id),
      discoveryIdentity(source, subscription.screenName),
    ],
  for (final subscription in subscriptions)
    if (subscription is! SearchSubscription && subscription.id.startsWith('$pluginIdPixiv:'))
      discoveryIdentity(DiscoverySource.pixiv, subscription.id.substring(pluginIdPixiv.length + 1)),
};

Set<String> currentDiscoveryFollowedIds(
  BuildContext context, Iterable<Subscription> groupMembers,
) {
  final prefs = PrefService.of(context, listen: false);
  return {
    ...discoveryFollowedIds(context.read<SubscriptionsModel>().state),
    ...discoveryFollowedIds(groupMembers),
    if (pluginById(pluginIdBluesky)?.isEnabled(prefs) == true)
      for (final account in context.read<BlueskyAccountsStore>().state) ...[
        discoveryIdentity(DiscoverySource.bluesky, account.actor),
        discoveryIdentity(DiscoverySource.bluesky, account.handle),
      ],
    if (pluginById(pluginIdMastodon)?.isEnabled(prefs) == true)
      for (final account in context.read<MastodonAccountsStore>().state)
        discoveryIdentity(DiscoverySource.mastodon, account.acct),
  };
}

DiscoverySource? _sourceOf(Subscription subscription) => switch (subscription) {
  UserSubscription() => DiscoverySource.x,
  BlueskySubscription() => DiscoverySource.bluesky,
  MastodonSubscription() => DiscoverySource.mastodon,
  _ => null,
};

List<DiscoveryLoad> groupDiscoverySources(
  BuildContext context, Iterable<Subscription> members,
) {
  final prefs = PrefService.of(context, listen: false);
  bool enabled(String id) => pluginById(id)?.isEnabled(prefs) == true;
  final x = members.whereType<UserSubscription>().map((e) => e.id).toList();
  final bluesky = enabled(pluginIdBluesky)
    ? members.whereType<BlueskySubscription>().map((e) => e.id).toList() : <String>[];
  final mastodon = enabled(pluginIdMastodon)
    ? members.whereType<MastodonSubscription>().map((e) => e.id).toList() : <String>[];
  final skyStore = bluesky.isEmpty ? null : context.read<BlueskyFeedStore>();
  final mastodonStore = mastodon.isEmpty ? null : context.read<MastodonFeedStore>();
  final pixiv = [for (final member in members)
    if (enabled(pluginIdPixiv) && member is! SearchSubscription && member.id.startsWith('$pluginIdPixiv:'))
      if (int.tryParse(member.id.substring(pluginIdPixiv.length + 1)) case final int id) id,
  ];
  final pixivClient = pixiv.isEmpty ? null : context.read<PixivClient>();
  final pixivMute = pixiv.isEmpty ? null : context.read<PixivMuteStore>().state;
  return [
    if (x.isNotEmpty) () async => xDiscoveryAccounts(await _xDiscoveryCache.merge(
      x, _loadXAuthor, maxFetches: 6,
    )),
    if (skyStore != null) () async => blueskyDiscoveryAccounts(await skyStore.postsFor(bluesky)),
    if (mastodonStore != null) () => _loadMastodonDiscovery(
      mastodonStore.client, mastodon, mastodonConfiguredInstances(prefs)),
    if (pixivClient != null) () => loadPixivDiscovery(pixivClient, pixiv, pixivMute!),
  ];
}

Future<List<DiscoveryAccount>> _loadMastodonDiscovery(
  MastodonClient client, List<String> accounts, List<String> configured,
) async {
  final keys = {for (final account in accounts) '${configured.join(',')}|$account': account};
  final posts = await _mastodonDiscoveryCache.merge(keys.keys.toList(), (key) {
    final account = keys[key]!;
    return client.fetchAccountAnywhere(
      mastodonFeedInstanceCandidates(account, configured: configured), account, limit: 20,
    ).timeout(const Duration(seconds: 25));
  }, maxFetches: 6);
  return mastodonDiscoveryAccounts(posts);
}

Future<List<TweetChain>> _loadXAuthor(String id) async {
  var count = 0;
  final page = await Twitter.getTweets(id, 'tweets', const [],
    count: 20, includeReplies: false, includeRetweets: true,
    getTweetsCounter: () => count, incrementTweetsCounter: () => count++,
  ).timeout(const Duration(seconds: 25));
  return page.chains;
}

List<DiscoveryAccount> xDiscoveryAccounts(Iterable<TweetChain> chains) => [
  for (final chain in chains)
    for (final tweet in chain.tweets)
      for (final shared in [tweet.retweetedStatusWithCard, tweet.quotedStatusWithCard])
        if (shared != null)
        if (shared.user?.idStr case final String id)
          DiscoveryAccount(
            source: DiscoverySource.x, id: id,
            handle: shared.user?.screenName ?? '', name: shared.user?.name ?? '',
            avatarUrl: shared.user?.profileImageUrlHttps,
            text: shared.fullText ?? shared.text ?? '',
            postUrl: 'https://x.com/i/status/${shared.idStr}',
            date: shared.createdAt, supportingPost: shared,
          ),
];

Future<List<DiscoveryAccount>> loadPixivDiscovery(
  PixivClient client, List<int> userIds, PixivMuteState mute,
) async {
  final batches = await mapWithConcurrency(userIds.take(2).toList(), 2, (id) async {
    final works = await client.userIllusts(id);
    if (works.illusts.isEmpty) return <PixivIllust>[];
    final related = await client.related(works.illusts.first.id);
    return mute.filter(related.illusts);
  });
  final authors = <int, PixivIllust>{};
  for (final art in batches.expand((e) => e)) {
    if (!userIds.contains(art.userId)) authors.putIfAbsent(art.userId, () => art);
  }
  final verified = await mapWithConcurrency(authors.values.take(8).toList(), 2, (art) async {
    final author = await client.userDetail(art.userId);
    return author.isFollowed ? null : art;
  });
  return pixivDiscoveryAccounts(verified.whereType<PixivIllust>());
}

List<DiscoveryAccount> pixivDiscoveryAccounts(Iterable<PixivIllust> works) => [
  for (final art in works)
    DiscoveryAccount(source: DiscoverySource.pixiv, id: '${art.userId}',
      handle: art.userAccount.isEmpty ? '${art.userId}' : art.userAccount,
      name: art.userName, avatarUrl: art.userAvatarUrl,
      text: '${art.title} ${art.tags.map((tag) => tag.displayName).join(' ')}',
      postUrl: art.url, date: art.createdAt, supportingPost: art,
    ),
];

List<DiscoveryAccount> blueskyDiscoveryAccounts(Iterable<BlueskyPost> posts) => [
  for (final post in posts)
    for (final shared in [if (post.isRepost) post, ?post.quotedPost])
      DiscoveryAccount(
        source: DiscoverySource.bluesky,
        id: shared.did.isEmpty ? shared.handle : shared.did,
        handle: shared.handle, name: shared.authorName, avatarUrl: shared.avatarUrl,
        text: shared.text, postUrl: shared.url,
        date: shared.publishedAt, supportingPost: shared,
      ),
];

List<DiscoveryAccount> mastodonDiscoveryAccounts(Iterable<MastodonPost> posts) => [
  for (final post in posts)
    for (final shared in [if (post.boosted) post, if (post.quote != null) post.quote!.asPost])
      DiscoveryAccount(
        source: DiscoverySource.mastodon, id: shared.acct,
        handle: shared.acct, name: shared.authorName, avatarUrl: shared.avatarUrl,
        text: shared.text, postUrl: shared.url,
        date: shared.publishedAt, supportingPost: shared,
      ),
];
