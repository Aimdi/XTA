import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_post_card.dart';
import 'package:xta/plugins/mastodon/mastodon_store.dart';
import 'package:xta/reading/mixed_feed_definition.dart';
import 'package:xta/reading/mixed_feed_merge.dart';
import 'package:xta/reading/mixed_feed_source.dart';
import 'package:xta/reading/reader_source_text.dart';

const _pageSize = 30;
final _hashtag = RegExp(r'^[\p{L}\p{N}_]{1,100}$', unicode: true);

/// The same status reached from two instances or two sources is one post: its canonical address names it.
MixedEntry mastodonMixedEntry(MastodonPost post) =>
    MixedEntry(identity: 'mastodon:${canonicalMastodonPostKey(post)}', item: post, date: post.timelineDate);

typedef _PublicPage = Future<List<MastodonPost>> Function(String instance, String? maxId);

/// Public timelines page on the instance that served the first page, since their ids are that instance's own.
MixedSourceReader _publicReader(MastodonClient client, BasePrefService prefs, _PublicPage page) =>
    MixedFunctionReader((cursor) async {
      final (instance, posts, maxId) = switch (cursor) {
        (final String instance, final String maxId) => (instance, await page(instance, maxId), maxId),
        _ => await client.firstInstanceThat(
          mastodonDiscoveryInstances(prefs),
          (instance) async => (instance, await page(instance, null), null),
        ),
      };
      final next = posts.lastOrNull?.pagingId;
      final more = posts.length >= _pageSize && next != null && next != maxId;
      return MixedPage([for (final post in posts) mastodonMixedEntry(post)], next: more ? (instance, next) : null);
    });

abstract class _MastodonMixedSource extends MixedSourceKind {
  const _MastodonMixedSource();

  @override
  String get pluginId => pluginIdMastodon;

  @override
  String signature(BuildContext context, MixedFeedSource source) =>
      mastodonDiscoveryInstances(PrefService.of(context, listen: false)).join('\n');

  @override
  Widget card(BuildContext context, MixedEntry entry) => MastodonPostCard(post: entry.item as MastodonPost);

  @override
  String filterText(MixedEntry entry) => mastodonFilterText(entry.item as MastodonPost);
}

/// Posts of the accounts the reader follows, read the way the Following tab reads them.
class MastodonFollowingMixedSource extends _MastodonMixedSource {
  const MastodonFollowingMixedSource();

  @override
  String get id => 'mastodon.following';

  @override
  IconData get icon => Icons.people_outline;

  @override
  String title(BuildContext context) => L10n.of(context).following;

  @override
  String signature(BuildContext context, MixedFeedSource source) => [
    ...mastodonConfiguredInstances(PrefService.of(context, listen: false)),
    for (final account in context.read<MastodonAccountsStore>().state) account.acct,
  ].join('\n');

  @override
  MixedSourceReader reader(BuildContext context, MixedFeedSource source) {
    final feed = context.read<MastodonFeedStore>();
    final accounts = context.read<MastodonAccountsStore>();
    return MixedFunctionReader((_) async {
      final posts = await feed.postsFor([for (final account in accounts.state) account.acct]);
      return MixedPage([for (final post in posts) mastodonMixedEntry(post)]);
    });
  }
}

class MastodonPublicMixedSource extends _MastodonMixedSource {
  final bool local;
  const MastodonPublicMixedSource({required this.local});

  @override
  String get id => local ? 'mastodon.local' : 'mastodon.federated';

  @override
  IconData get icon => local ? Icons.location_city_outlined : Icons.public;

  @override
  String title(BuildContext context) =>
      local ? L10n.of(context).plugin_mastodon_tab_local : L10n.of(context).plugin_mastodon_tab_federated;

  @override
  MixedSourceReader reader(BuildContext context, MixedFeedSource source) {
    final client = context.read<MastodonClient>();
    return _publicReader(
      client,
      PrefService.of(context, listen: false),
      (instance, maxId) => client.getPublicTimeline(instance, local: local, limit: _pageSize, maxId: maxId),
    );
  }
}

class MastodonTagMixedSource extends _MastodonMixedSource {
  const MastodonTagMixedSource();

  @override
  String get id => 'mastodon.tag';

  @override
  IconData get icon => Icons.tag;

  @override
  MixedSourceInput get input => MixedSourceInput.text;

  @override
  String title(BuildContext context) => L10n.of(context).mixed_feed_hashtag;

  @override
  String? inputHint(BuildContext context) => L10n.of(context).mixed_feed_hashtag_hint;

  @override
  Future<MixedFeedSource?> fromText(BuildContext context, String text) async {
    final tag = text.trim().replaceFirst(RegExp('^#+'), '').toLowerCase();
    return _hashtag.hasMatch(tag) ? MixedFeedSource(kind: id, value: tag, label: '#$tag') : null;
  }

  @override
  MixedSourceReader reader(BuildContext context, MixedFeedSource source) {
    final client = context.read<MastodonClient>();
    return _publicReader(
      client,
      PrefService.of(context, listen: false),
      (instance, maxId) => client.getTagTimeline(instance, source.value, limit: _pageSize, maxId: maxId),
    );
  }
}

const mastodonMixedSourceKinds = <MixedSourceKind>[
  MastodonFollowingMixedSource(),
  MastodonPublicMixedSource(local: true),
  MastodonPublicMixedSource(local: false),
  MastodonTagMixedSource(),
];
