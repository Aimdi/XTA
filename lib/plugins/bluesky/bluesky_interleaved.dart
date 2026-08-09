import 'package:flutter/widgets.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_post_card.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';
import 'package:xta/tweet/interleaved_items.dart';
import 'package:xta/ui/provenance_accent.dart';

/// How many posts each Bluesky account contributes to a shared timeline.
const int kBlueskyInterleavedPageSize = 10;

/// Whether followed Bluesky accounts belong in the home timeline. Off unless
/// asked for: a reader who turned the plugin on wanted its tab, not a different
/// Following feed.
bool blueskyInHomeFeed(BasePrefService prefs) =>
    prefs.get<bool>(optionPluginBlueskyEnabled) == true && prefs.get<bool>(optionPluginBlueskyInHomeFeed) == true;

/// The accounts the home timeline should mix in — none unless the option is on.
List<String> blueskyHomeIds(BuildContext context) {
  if (!blueskyInHomeFeed(PrefService.of(context, listen: false))) {
    return const [];
  }

  return context.read<BlueskyAccountsStore>().state.map((e) => e.actor).toList(growable: false);
}

/// One page of each account, as dated items a tweet list can slot between its
/// chains.
///
/// A failure returns nothing rather than throwing: Bluesky being unreachable
/// must not empty a timeline of its X (or other) posts.
Future<List<InterleavedItem>> loadBlueskyInterleaved(
  BuildContext context,
  List<String> actors, {
  int limit = kBlueskyInterleavedPageSize,
}) async {
  if (actors.isEmpty) {
    return const [];
  }

  final store = context.read<BlueskyFeedStore>();
  try {
    final posts = await store.postsFor(actors);
    return blueskyInterleavedItems(posts, limit: limit);
  } catch (_) {
    return const [];
  }
}

/// Posts as dated items. Each card keeps the Bluesky butterfly badge so a mixed
/// group feed is unmistakable next to X — unlike Threads, which relies only on
/// the provenance strip.
List<InterleavedItem> blueskyInterleavedItems(
  Iterable<BlueskyPost> posts, {
  int limit = kBlueskyInterleavedPageSize,
}) =>
    [
      for (final post in posts.take(limit))
        if (post.publishedAt case final date?)
          provenanceInterleavedItem(
            date: date,
            pluginId: pluginIdBluesky,
            build: (_) => BlueskyPostCard(post: post, showSourceBadge: true),
          ),
    ];
