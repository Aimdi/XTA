import 'package:flutter/widgets.dart';
import 'package:xta/client/client.dart';
import 'package:xta/reading/shared_filter_scope.dart';
import 'package:xta/search/loaded_feed_search.dart';
import 'package:xta/tweet/interleaved_items.dart';

/// Everything a reader could see of a post: its words, author, links, and the posts it carries.
String sharedFilterTweetText(TweetWithCard tweet, [int depth = 0]) => [
  tweet.noteText ?? tweet.fullText ?? tweet.text ?? '',
  tweet.user?.name ?? '',
  tweet.user?.screenName ?? '',
  for (final url in tweet.entities?.urls ?? []) url.expandedUrl ?? '',
  if (depth < 2 && tweet.retweetedStatusWithCard != null)
    sharedFilterTweetText(tweet.retweetedStatusWithCard!, depth + 1),
  if (depth < 2 && tweet.quotedStatusWithCard != null) sharedFilterTweetText(tweet.quotedStatusWithCard!, depth + 1),
].join('\n');

String sharedFilterChainText(TweetChain chain) => chain.tweets.map(sharedFilterTweetText).join('\n');

/// A plugin post placed among X posts; posts without a text snapshot are matched by source and link only.
String sharedFilterInterleavedText(InterleavedItem item) =>
    '${item.source ?? ''}\n${item.linkUrl ?? ''}\n${loadedPluginText(item.snapshot)}';

/// Chain verdicts for a paged list whose indices must not move: hidden chains render at zero size in place.
class TweetChainFilter {
  final Set<String> hidden;
  final Map<String, String> folds;
  const TweetChainFilter._(this.hidden, this.folds);

  static const none = TweetChainFilter._({}, {});

  factory TweetChainFilter.of(BuildContext context, List<TweetChain> chains) {
    final projection = sharedFilterProject(context, chains, sharedFilterChainText);
    if (identical(projection.visible, chains)) return none;
    final shown = Set<TweetChain>.identity()..addAll(projection.visible);
    return TweetChainFilter._(
      {
        for (final chain in chains)
          if (!shown.contains(chain)) chain.id,
      },
      {for (final chain in projection.visible) chain.id: ?projection.foldReason(chain)},
    );
  }

  bool get isEmpty => hidden.isEmpty && folds.isEmpty;

  /// Whether every chain loaded after [from] is hidden, i.e. the last page showed the reader nothing.
  bool hidesAllFrom(List<TweetChain> chains, int from) =>
      from < chains.length && chains.skip(from).every((chain) => hidden.contains(chain.id));
}
