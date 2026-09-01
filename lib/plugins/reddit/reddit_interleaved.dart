import 'package:flutter/widgets.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/ui/provenance_accent.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_post_card.dart';
import 'package:xta/plugins/reddit/reddit_post_source.dart';
import 'package:xta/plugins/reddit/reddit_store.dart';
import 'package:xta/tweet/interleaved_items.dart';

/// How many posts each subreddit contributes to a shared timeline.
///
/// Small on purpose: the X side pages on and on, and a subreddit that dropped
/// twenty-five posts in would own the top of the feed rather than joining it.
/// The fetch is the same size either way — this is how much of it is shown.
const int kRedditInterleavedPageSize = 10;

/// Whether followed subreddits belong in the home timeline. Off unless asked
/// for: a reader who turned the plugin on wanted a Reddit tab, not a different
/// Following feed.
bool redditInHomeFeed(BasePrefService prefs) =>
    prefs.get<bool>(optionPluginRedditEnabled) == true && prefs.get<bool>(optionPluginRedditInHomeFeed) == true;

/// The subreddits the home timeline should mix in — none unless the option is
/// on.
List<String> redditHomeSubreddits(BuildContext context) {
  final prefs = PrefService.of(context, listen: false);
  if (!redditInHomeFeed(prefs)) {
    return const [];
  }

  return context.read<RedditSubredditsStore>().state;
}

/// One page of each subreddit, as dated items a tweet list can slot between its
/// chains.
///
/// Read through the shared [RedditPostSource], so a subreddit already fetched
/// for the Reddit tab or the other timeline is not downloaded again here. Per
/// subreddit failure isolation and the newest-first order come from there too.
Future<List<InterleavedItem>> loadRedditInterleaved(
  BuildContext context,
  List<String> subreddits, {
  int limit = kRedditInterleavedPageSize,
  bool forceRefresh = false,
}) async {
  if (subreddits.isEmpty) {
    return const [];
  }

  final source = context.read<RedditFeedStore>().source;
  // A failure returns nothing rather than throwing, as [SubscriptionSource]
  // requires: this was the one source whose errors could escape into the
  // timeline's unawaited plugin load, taking every Reddit post silently with
  // them. Per-subreddit isolation lives in the source; this catches what sits
  // outside it, like the sign-in refresh.
  try {
    final posts = await source.posts(subreddits, limit: limit, forceRefresh: forceRefresh);

    return redditInterleavedItems(posts);
  } catch (_) {
    return const [];
  }
}

/// Posts as dated items, each wearing the Reddit provenance accent so a mixed
/// timeline shows where the card came from. One with no date is dropped rather
/// than guessed at: there is nowhere in a chronological feed to put it.
List<InterleavedItem> redditInterleavedItems(Iterable<RedditPost> posts) => [
  for (final post in posts)
    if (post.createdAt case final date?)
      provenanceInterleavedItem(
        date: date,
        pluginId: pluginIdReddit,
        build: (_) => RedditPostCard(post: post),
      ),
];
