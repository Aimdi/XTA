import 'package:flutter/widgets.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/group/discovery_rotation.dart';
import 'package:xta/group/feed_chunk_plan.dart';
import 'package:xta/group/group_discovery.dart';
import 'package:xta/group/group_discovery_x.dart';
import 'package:xta/plugins/account_posts.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_store.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/subscriptions/users_model.dart';

/// One profile a network related to one member.
typedef BlueskySuggestion = ({String member, BlueskyProfile profile});
typedef PixivRelation = ({String seed, PixivUserPreview preview});

final _mastodonDiscoveryCache = AccountPostCache<MastodonPost>(dateOf: (post) => post.publishedAt, perAccount: 20);
final _blueskySuggestedCache = AccountPostCache<BlueskySuggestion>(dateOf: (_) => null, perAccount: 50);
final _pixivRelatedCache = AccountPostCache<PixivRelation>(dateOf: (_) => null, perAccount: 30);

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

Set<String> currentDiscoveryFollowedIds(BuildContext context, Iterable<Subscription> groupMembers) {
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

/// One reader per network the group has members on, X first.
List<DiscoveryLoad> groupDiscoverySources(
  BuildContext context,
  SubscriptionGroupGet group, {
  Set<String> excludedProfiles = const {},
}) {
  final prefs = PrefService.of(context, listen: false);
  final plan = planGroupFeed(group, prefs: prefs, excludedProfiles: excludedProfiles);
  final x = plan.members.whereType<UserSubscription>().toList();
  return [
    if (x.isNotEmpty) DiscoveryLoad(DiscoverySource.x, members: x.length, read: xDiscoveryReader(plan, x)),
    ..._pluginDiscoverySources(context, plan.members, prefs),
  ];
}

List<DiscoveryLoad> _pluginDiscoverySources(BuildContext context, List<Subscription> members, BasePrefService prefs) {
  bool enabled(String id) => pluginById(id)?.isEnabled(prefs) == true;
  return [
    if (enabled(pluginIdBluesky)) ?_blueskyLoad(context, members.whereType<BlueskySubscription>().toList()),
    if (enabled(pluginIdMastodon)) ?_mastodonLoad(context, members.whereType<MastodonSubscription>().toList(), prefs),
    if (enabled(pluginIdPixiv)) ?_pixivLoad(context, pixivDiscoveryMembers(members)),
  ];
}

DiscoveryLoad? _blueskyLoad(BuildContext context, List<BlueskySubscription> members) => members.isEmpty
    ? null
    : DiscoveryLoad(
        DiscoverySource.bluesky,
        members: members.length,
        read: blueskyDiscoveryReader(context.read<BlueskyFeedStore>(), members),
      );

DiscoveryLoad? _mastodonLoad(BuildContext context, List<MastodonSubscription> members, BasePrefService prefs) {
  if (members.isEmpty) return null;
  final client = context.read<MastodonFeedStore>().client;
  return DiscoveryLoad(
    DiscoverySource.mastodon,
    members: members.length,
    read: mastodonDiscoveryReader(client, members, mastodonConfiguredInstances(prefs)),
  );
}

DiscoveryLoad? _pixivLoad(BuildContext context, List<Subscription> members) => members.isEmpty
    ? null
    : DiscoveryLoad(
        DiscoverySource.pixiv,
        members: members.length,
        read: pixivDiscoveryReader(context.read<PixivClient>(), members, context.read<PixivMuteStore>().state),
      );

/// Pixiv creators in a group carry a `pixiv:<id>` id, whatever class loaded them.
List<Subscription> pixivDiscoveryMembers(Iterable<Subscription> members) => [
  for (final member in members)
    if (member is! SearchSubscription && pixivDiscoveryId(member) != null) member,
];

int? pixivDiscoveryId(Subscription member) =>
    member.id.startsWith('$pluginIdPixiv:') ? int.tryParse(member.id.substring(pluginIdPixiv.length + 1)) : null;

/// The AppView's own "similar accounts" for a rotating sample of members; when
/// it has none to give, the members' reposts and quotes as before.
DiscoveryRead blueskyDiscoveryReader(BlueskyFeedStore store, List<BlueskySubscription> members) =>
    (scan) => _BlueskyScan(store, members, scan).run();

class _BlueskyScan {
  final BlueskyFeedStore store;
  final DiscoveryScan scan;
  final Map<String, BlueskySubscription> byActor;
  final List<String> actors;

  _BlueskyScan(this.store, List<BlueskySubscription> members, this.scan)
    : byActor = {for (final member in members) member.id.toLowerCase(): member},
      actors = members.map((member) => member.id).toList();

  Future<DiscoveryBatch> run() async {
    Object? error;
    RotatingRead<BlueskySuggestion>? suggested;
    try {
      suggested = await _suggestions();
    } catch (e) {
      error = e;
    }
    if (suggested != null && suggested.rows.isNotEmpty) return _suggestedBatch(suggested);
    final posts = await store.postsFor(actors, onPartial: (posts) => scan.onPartial(_postsBatch(posts)));
    final batch = _postsBatch(posts);
    return DiscoveryBatch(batch.accounts, read: batch.read, error: error);
  }

  Future<RotatingRead<BlueskySuggestion>> _suggestions() => readRotating(
    _blueskySuggestedCache,
    actors,
    scan,
    perLoad: 6,
    fetch: (actor, budget) async => [
      for (final profile in await store.client.getSuggestedFollowsByActor(actor).timeout(budget))
        (member: actor, profile: profile),
    ],
    onRows: (soFar) => scan.onPartial(_suggestedBatch(soFar)),
  );

  DiscoveryBatch _suggestedBatch(RotatingRead<BlueskySuggestion> read) =>
      DiscoveryBatch(blueskySuggestedAccounts(read.rows, byActor), read: read.read);

  DiscoveryBatch _postsBatch(List<BlueskyPost> posts) =>
      DiscoveryBatch(blueskyDiscoveryAccounts(posts), read: actors.length - store.pending(actors));
}

List<DiscoveryAccount> blueskySuggestedAccounts(
  Iterable<BlueskySuggestion> rows,
  Map<String, BlueskySubscription> members,
) => [
  for (final row in rows)
    if (members[row.member.toLowerCase()] case final member?
        when !members.containsKey(row.profile.handle.toLowerCase()) && !members.containsKey(row.profile.did))
      DiscoveryAccount(
        source: DiscoverySource.bluesky,
        id: row.profile.did.isEmpty ? row.profile.handle : row.profile.did,
        handle: row.profile.handle,
        name: row.profile.displayName,
        avatarUrl: row.profile.avatarUrl,
        bio: row.profile.description,
        followersCount: row.profile.followersCount,
        supporters: [
          DiscoverySupporter(
            memberId: member.id,
            handle: member.id,
            name: member.name,
            kind: DiscoverySignal.suggested,
          ),
        ],
      ),
];

DiscoveryRead mastodonDiscoveryReader(
  MastodonClient client,
  List<MastodonSubscription> members,
  List<String> configured,
) {
  final keys = {for (final member in members) '${configured.join(',')}|${member.id}': member.id};
  DiscoveryBatch batch(RotatingRead<MastodonPost> read) =>
      DiscoveryBatch(mastodonDiscoveryAccounts(read.rows), read: read.read);
  Future<List<MastodonPost>> fetch(String key, Duration budget) {
    final account = keys[key]!;
    return client
        .fetchAccountAnywhere(mastodonFeedInstanceCandidates(account, configured: configured), account, limit: 20)
        .timeout(budget);
  }

  return (scan) async => batch(
    await readRotating(
      _mastodonDiscoveryCache,
      keys.keys.toList(),
      scan,
      perLoad: 6,
      fetch: fetch,
      onRows: (soFar) => scan.onPartial(batch(soFar)),
    ),
  );
}

/// Creators Pixiv relates to a rotating sample of the group's creators.
DiscoveryRead pixivDiscoveryReader(PixivClient client, List<Subscription> members, PixivMuteState mute) {
  final seeds = {
    for (final member in members)
      if (pixivDiscoveryId(member) case final id?) '$id': member,
  };
  DiscoveryBatch batch(RotatingRead<PixivRelation> read) =>
      DiscoveryBatch(pixivDiscoveryAccounts(read.rows, seeds: seeds, mute: mute), read: read.read);
  Future<List<PixivRelation>> fetch(String seed, Duration budget) async => [
    for (final preview in await client.relatedUsers(int.parse(seed)).timeout(budget)) (seed: seed, preview: preview),
  ];

  return (scan) async => batch(
    await readRotating(
      _pixivRelatedCache,
      seeds.keys.toList(),
      scan,
      perLoad: 4,
      fetch: fetch,
      onRows: (soFar) => scan.onPartial(batch(soFar)),
    ),
  );
}

/// Related creators who are not followed, not in the group and not muted, each
/// shown through their first preview work the reader has not muted either.
List<DiscoveryAccount> pixivDiscoveryAccounts(
  Iterable<PixivRelation> rows, {
  required Map<String, Subscription> seeds,
  required PixivMuteState mute,
}) => [
  for (final row in rows)
    if (seeds[row.seed] case final member?
        when !row.preview.user.isFollowed &&
            !seeds.containsKey('${row.preview.user.id}') &&
            !mute.authorIds.contains(row.preview.user.id))
      _pixivAccount(row.preview.user, mute.filter(row.preview.illusts).firstOrNull, member),
];

DiscoveryAccount _pixivAccount(PixivUser user, PixivIllust? work, Subscription member) => DiscoveryAccount(
  source: DiscoverySource.pixiv,
  id: '${user.id}',
  handle: user.account.isEmpty ? '${user.id}' : user.account,
  name: user.name,
  avatarUrl: user.avatarUrl,
  bio: user.comment,
  text: work == null ? '' : '${work.title} ${work.tags.map((tag) => tag.displayName).join(' ')}',
  postUrl: work?.url ?? '',
  date: work?.createdAt,
  supportingPost: work,
  supporters: [
    DiscoverySupporter(
      memberId: member.id,
      handle: member.screenName,
      name: member.name,
      kind: DiscoverySignal.related,
    ),
  ],
);

/// Reposts and quotes by the members, the reposting or quoting member vouching.
List<DiscoveryAccount> blueskyDiscoveryAccounts(Iterable<BlueskyPost> posts) => [
  for (final post in posts)
    for (final (shared, kind) in [
      if (post.isRepost) (post, DiscoverySignal.reposted),
      if (post.quotedPost case final quoted?) (quoted, DiscoverySignal.quoted),
    ])
      DiscoveryAccount(
        source: DiscoverySource.bluesky,
        id: shared.did.isEmpty ? shared.handle : shared.did,
        handle: shared.handle,
        name: shared.authorName,
        avatarUrl: shared.avatarUrl,
        text: shared.text,
        postUrl: shared.url,
        date: shared.publishedAt,
        supportingPost: shared,
        supporters: [_blueskySupporter(post, kind)],
      ),
];

/// A reposted post names its reposter; a quote is the member's own post.
DiscoverySupporter _blueskySupporter(BlueskyPost post, DiscoverySignal kind) => post.isRepost
    ? DiscoverySupporter(
        memberId: post.repostedByHandle ?? '',
        handle: post.repostedByHandle ?? '',
        name: post.repostedByName ?? post.repostedByHandle ?? '',
        kind: kind,
        date: post.repostedAt ?? post.publishedAt,
      )
    : DiscoverySupporter(
        memberId: post.handle,
        handle: post.handle,
        name: post.authorName,
        kind: kind,
        date: post.publishedAt,
      );

/// Boosts and quotes by the members, the boosting or quoting member vouching.
List<DiscoveryAccount> mastodonDiscoveryAccounts(Iterable<MastodonPost> posts) => [
  for (final post in posts)
    for (final (shared, kind) in [
      if (post.boosted) (post, DiscoverySignal.reposted),
      if (post.quote case final quote?) (quote.asPost, DiscoverySignal.quoted),
    ])
      DiscoveryAccount(
        source: DiscoverySource.mastodon,
        id: shared.acct,
        handle: shared.acct,
        name: shared.authorName,
        avatarUrl: shared.avatarUrl,
        text: shared.text,
        postUrl: shared.url,
        date: shared.publishedAt,
        supportingPost: shared,
        supporters: [_mastodonSupporter(post, kind)],
      ),
];

DiscoverySupporter _mastodonSupporter(MastodonPost post, DiscoverySignal kind) => post.boosted
    ? DiscoverySupporter(
        memberId: post.boostedByAcct ?? '',
        handle: post.boostedByAcct ?? '',
        name: post.boostedBy ?? post.boostedByAcct ?? '',
        kind: kind,
        date: post.timelineDate,
      )
    : DiscoverySupporter(
        memberId: post.acct,
        handle: post.acct,
        name: post.authorName,
        kind: kind,
        date: post.publishedAt,
      );
