import 'package:flutter/material.dart';
import 'package:xta/client/client.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_session.dart';
import 'package:xta/tweet/paginated_tweet_list.dart';
import 'package:xta/tweet/tweet_context_scope.dart';

/// Posts about the watchlist (or one filtered ticker), owned as its own feed
/// so changing the query remounts a fresh [TweetFeedController].
class StocksPostsFeed extends StatefulWidget {
  final String query;
  final Future<void> Function() onRefreshQuotes;

  const StocksPostsFeed({super.key, required this.query, required this.onRefreshQuotes});

  @override
  State<StocksPostsFeed> createState() => StocksPostsFeedState();
}

class StocksPostsFeedState extends State<StocksPostsFeed> {
  late final PluginSessionLease _session;
  late final TweetFeedController _feed;

  @override
  void initState() {
    super.initState();
    _session = PluginSessionLease(context, 'stocks-posts');
    _feed = _session.obtain(widget.query, TweetFeedController.new, dispose: (feed) => feed.dispose());
  }

  @override
  void dispose() {
    _session.dispose();
    super.dispose();
  }

  Future<TweetPageResult> _loadPage(String? cursor) async {
    final result = await Twitter.searchTweets(widget.query, true, cursor: cursor);
    return (chains: result.chains, nextCursor: result.cursorBottom);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return TweetContextScope(
      child: PaginatedTweetList(
        feed: _feed,
        loadPage: _loadPage,
        username: null,
        onRefresh: widget.onRefreshQuotes,
        firstPageErrorPrefix: l10n.unable_to_load_the_tweets,
        newPageErrorPrefix: l10n.unable_to_load_the_next_page_of_tweets,
        emptyMessage: l10n.no_posts_match_your_search,
      ),
    );
  }
}
