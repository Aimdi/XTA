import 'package:flutter/widgets.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_post_card.dart';
import 'package:xta/plugins/mastodon/mastodon_store.dart';
import 'package:xta/tweet/interleaved_items.dart';
import 'package:xta/ui/provenance_accent.dart';

/// How many posts each Fediverse account contributes to a shared timeline.
const int kMastodonInterleavedPageSize = 10;

/// Whether followed Fediverse accounts belong in the home timeline. Off unless
/// asked for: a reader who turned the plugin on wanted its tab, not a different
/// Following feed.
bool fediverseInHomeFeed(BasePrefService prefs) =>
    prefs.get<bool>(optionPluginMastodonEnabled) == true && prefs.get<bool>(optionPluginMastodonInHomeFeed) == true;

/// The accounts the home timeline should mix in — none unless the option is on.
List<String> fediverseHomeIds(BuildContext context) {
  if (!fediverseInHomeFeed(PrefService.of(context, listen: false))) {
    return const [];
  }

  return context.read<MastodonAccountsStore>().state.map((e) => e.acct).toList(growable: false);
}

/// One page of each account, as dated items a tweet list can slot between its
/// chains.
///
/// A failure returns nothing rather than throwing: one instance being down must
/// not empty a timeline of everything else in it.
Future<List<InterleavedItem>> loadMastodonInterleaved(
  BuildContext context,
  List<String> accts, {
  int limit = kMastodonInterleavedPageSize,
}) async {
  if (accts.isEmpty) {
    return const [];
  }

  final store = context.read<MastodonFeedStore>();
  try {
    final posts = await store.postsFor(accts);
    return mastodonInterleavedItems(posts, limit: limit);
  } catch (_) {
    return const [];
  }
}

/// Posts as dated items, each keeping its source badge so a mixed group feed
/// says where the card came from.
List<InterleavedItem> mastodonInterleavedItems(
  Iterable<MastodonPost> posts, {
  int limit = kMastodonInterleavedPageSize,
}) => [
  for (final post in posts.take(limit))
    if (post.publishedAt case final date?)
      provenanceInterleavedItem(
        date: date,
        pluginId: pluginIdMastodon,
        build: (_) => MastodonPostCard(post: post, showSourceBadge: true),
      ),
];
