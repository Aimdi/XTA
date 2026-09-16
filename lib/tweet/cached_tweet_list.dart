import 'package:xta/tweet/feed_link_grouping.dart';
import 'package:flutter/material.dart';
import 'package:xta/client/client.dart';
import 'package:xta/tweet/conversation.dart';
import 'package:xta/tweet/interleaved_items.dart';
import 'package:xta/ui/feed_list.dart';

/// A plain (non-paginated) list of tweet chains, used to show cached tweets
/// while a feed's first page loads. Expects the tweet context providers to be
/// supplied by an ancestor (e.g. TweetContextScope or the feed body).
///
/// [interleaved] are plugin cards mixed in by date. They used to be dropped
/// while this list stood in for the paged feed, so a group with a subreddit
/// in it looked X-only until the first search returned — or forever, when
/// that search failed and this list became the stale fallback.
class CachedTweetList extends StatelessWidget {
  final List<TweetChain> chains;
  final String? username;
  final List<InterleavedItem> interleaved;

  const CachedTweetList(this.chains, {super.key, this.username, this.interleaved = const []});

  @override
  Widget build(BuildContext context) {
    if (chains.isEmpty && interleaved.isEmpty) {
      return FeedListView(
        padding: const EdgeInsets.only(top: 4),
        physics: const AlwaysScrollableScrollPhysics(),
        itemCount: 0,
        itemBuilder: (_, _) => const SizedBox.shrink(),
      );
    }

    final grouped = FeedLinkGrouping.build(chains, interleaved, (_, chain) => _conversation(chain));
    Widget conversation(BuildContext context, TweetChain chain) =>
        grouped.chains[chain.id]?.call(context) ?? _conversation(chain);
    if (interleaved.isEmpty) {
      return FeedListView(
        padding: const EdgeInsets.only(top: 4),
        physics: const AlwaysScrollableScrollPhysics(),
        itemCount: chains.length,
        itemBuilder: (context, index) => conversation(context, chains[index]),
      );
    }

    final buckets = placeInterleaved(chains, grouped.plugins);
    if (chains.isEmpty) {
      return FeedListView(
        padding: const EdgeInsets.only(top: 4),
        physics: const AlwaysScrollableScrollPhysics(),
        itemCount: interleaved.length,
        itemBuilder: (context, index) => grouped.plugins[index].build(context),
      );
    }

    return FeedListView(
      padding: const EdgeInsets.only(top: 4),
      physics: const AlwaysScrollableScrollPhysics(),
      itemCount: chains.length,
      itemBuilder: (context, index) {
        final post = conversation(context, chains[index]);
        final above = buckets[index];
        final below = index == chains.length - 1 ? buckets.last : const <InterleavedItem>[];
        if (above.isEmpty && below.isEmpty) {
          return post;
        }
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            for (final item in above) item.build(context),
            post,
            for (final item in below) item.build(context),
          ],
        );
      },
    );
  }

  Widget _conversation(TweetChain chain) {
    return TweetConversation(
      key: ValueKey(chain.id),
      id: chain.id,
      tweets: chain.tweets,
      username: username,
      isPinned: chain.isPinned,
    );
  }
}
