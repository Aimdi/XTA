import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_post_card.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';
import 'package:xta/reading/mixed_feed_definition.dart';
import 'package:xta/reading/mixed_feed_merge.dart';
import 'package:xta/reading/mixed_feed_source.dart';
import 'package:xta/reading/reader_source_text.dart';

/// A post reposted by two followed accounts is one post: its record URI names it.
MixedEntry blueskyMixedEntry(BlueskyPost post) =>
    MixedEntry(identity: 'bluesky:${post.uri}', item: post, date: post.timelineDate);

typedef _BlueskyPage = Future<BlueskyFeedPage> Function(String? cursor);

/// Pages until the cursor runs out or comes back unchanged.
MixedSourceReader _cursorReader(_BlueskyPage page) => MixedFunctionReader((cursor) async {
  final result = await page(cursor as String?);
  final next = result.cursor;
  return MixedPage([
    for (final post in result.posts) blueskyMixedEntry(post),
  ], next: next == null || next.isEmpty || next == cursor ? null : next);
});

abstract class _BlueskyMixedSource extends MixedSourceKind {
  const _BlueskyMixedSource();

  @override
  String get pluginId => pluginIdBluesky;

  @override
  String signature(BuildContext context, MixedFeedSource source) => context.read<BlueskyClient>().baseUrl;

  @override
  Widget card(BuildContext context, MixedEntry entry) => BlueskyPostCard(post: entry.item as BlueskyPost);

  @override
  String filterText(MixedEntry entry) => blueskyFilterText(entry.item as BlueskyPost);
}

/// Posts of the accounts the reader follows, read the way the Following tab reads them.
class BlueskyFollowingMixedSource extends _BlueskyMixedSource {
  const BlueskyFollowingMixedSource();

  @override
  String get id => 'bluesky.following';

  @override
  IconData get icon => Icons.people_outline;

  @override
  String title(BuildContext context) => L10n.of(context).following;

  @override
  String signature(BuildContext context, MixedFeedSource source) => [
    context.read<BlueskyClient>().baseUrl,
    for (final account in context.read<BlueskyAccountsStore>().state) account.actor,
  ].join('\n');

  @override
  MixedSourceReader reader(BuildContext context, MixedFeedSource source) {
    final feed = context.read<BlueskyFeedStore>();
    final accounts = context.read<BlueskyAccountsStore>();
    return MixedFunctionReader((_) async {
      final posts = await feed.postsFor([for (final account in accounts.state) account.actor]);
      return MixedPage([for (final post in posts) blueskyMixedEntry(post)]);
    });
  }
}

/// A custom feed the reader pinned in Bluesky, or Discover.
class BlueskyFeedMixedSource extends _BlueskyMixedSource {
  const BlueskyFeedMixedSource();

  @override
  String get id => 'bluesky.feed';

  @override
  IconData get icon => Icons.dynamic_feed_outlined;

  @override
  MixedSourceInput get input => MixedSourceInput.choice;

  @override
  String title(BuildContext context) => L10n.of(context).mixed_feed_custom_feed;

  @override
  Future<List<MixedFeedSource>> choices(BuildContext context) async {
    final pinned = blueskyGeneratorsFromPrefs(
      PrefService.of(context, listen: false).get<String>(optionPluginBlueskyPinnedFeeds),
    );
    return [
      MixedFeedSource(kind: id, value: kBlueskyDiscoverFeedUri, label: L10n.of(context).plugin_bluesky_discover_feed),
      for (final feed in pinned)
        if (feed.uri != kBlueskyDiscoverFeedUri) MixedFeedSource(kind: id, value: feed.uri, label: feed.displayName),
    ];
  }

  @override
  MixedSourceReader reader(BuildContext context, MixedFeedSource source) {
    final client = context.read<BlueskyClient>();
    return _cursorReader((cursor) => client.getFeed(source.value, cursor: cursor));
  }
}

/// A list the reader pinned in Bluesky.
class BlueskyListMixedSource extends _BlueskyMixedSource {
  const BlueskyListMixedSource();

  @override
  String get id => 'bluesky.list';

  @override
  IconData get icon => Icons.list_alt;

  @override
  MixedSourceInput get input => MixedSourceInput.choice;

  @override
  String title(BuildContext context) => L10n.of(context).mixed_feed_list;

  @override
  Future<List<MixedFeedSource>> choices(BuildContext context) async => [
    for (final list in blueskyListsFromPrefs(
      PrefService.of(context, listen: false).get<String>(optionPluginBlueskyPinnedLists),
    ))
      MixedFeedSource(kind: id, value: list.uri, label: list.name),
  ];

  @override
  MixedSourceReader reader(BuildContext context, MixedFeedSource source) {
    final client = context.read<BlueskyClient>();
    return _cursorReader((cursor) => client.getListFeed(source.value, cursor: cursor));
  }
}

/// One followed account's posts, paged further than Following reads them.
class BlueskyAuthorMixedSource extends _BlueskyMixedSource {
  const BlueskyAuthorMixedSource();

  @override
  String get id => 'bluesky.author';

  @override
  IconData get icon => Icons.person_outline;

  @override
  MixedSourceInput get input => MixedSourceInput.choice;

  @override
  String title(BuildContext context) => L10n.of(context).mixed_feed_account;

  @override
  Future<List<MixedFeedSource>> choices(BuildContext context) async => [
    for (final account in context.read<BlueskyAccountsStore>().state)
      MixedFeedSource(
        kind: id,
        value: account.actor,
        label: account.name.isEmpty ? '@${account.handle}' : '${account.name} (@${account.handle})',
      ),
  ];

  @override
  MixedSourceReader reader(BuildContext context, MixedFeedSource source) {
    final client = context.read<BlueskyClient>();
    return _cursorReader((cursor) => client.getAuthorFeed(source.value, cursor: cursor));
  }
}

const blueskyMixedSourceKinds = <MixedSourceKind>[
  BlueskyFollowingMixedSource(),
  BlueskyFeedMixedSource(),
  BlueskyListMixedSource(),
  BlueskyAuthorMixedSource(),
];
