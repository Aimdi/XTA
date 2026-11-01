import 'package:flutter/material.dart';
import 'package:xta/client/client.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_session.dart';
import 'package:xta/plugins/stocks/stocks_followed_section.dart';
import 'package:xta/plugins/stocks/stocks_followed_source.dart';
import 'package:xta/plugins/stocks/stocks_signal_banner.dart';
import 'package:xta/plugins/stocks/stocks_signal_posts.dart';
import 'package:xta/tweet/paginated_tweet_list.dart';
import 'package:xta/tweet/tweet_context_scope.dart';

/// Posts about the watchlist (or one filtered ticker), owned as its own feed
/// so changing the query remounts a fresh [TweetFeedController]. Each page
/// puts signal-group posts last ([signalGroupPostsLast]), under a banner that
/// folds them ([stocksSignalChain]). For one ticker ([followedFirst]), the
/// posts of accounts the reader follows go first ([followedFirstPage]).
class StocksPostsFeed extends StatefulWidget {
  final String query;
  final Future<void> Function() onRefreshQuotes;
  final bool followedFirst;

  const StocksPostsFeed({super.key, required this.query, required this.onRefreshQuotes, this.followedFirst = false});

  @override
  State<StocksPostsFeed> createState() => StocksPostsFeedState();
}

class StocksPostsFeedState extends State<StocksPostsFeed> {
  late final PluginSessionLease _session;
  late final TweetFeedController _feed;
  late final StocksSignalFolds _folds;
  late final FollowedSectionStore _section;

  @override
  void initState() {
    super.initState();
    _session = PluginSessionLease(context, 'stocks-posts');
    _feed = _session.obtain(widget.query, TweetFeedController.new, dispose: (feed) => feed.dispose());
    // One for every query: a post folded under one ticker stays folded under
    // the next that finds it.
    _folds = _session.obtain('signal-folds', StocksSignalFolds.new, dispose: (folds) => folds.destroy());
    // Kept with the feed it describes, which outlives this widget.
    _section = _session.obtain(
      'followed-${widget.query}',
      FollowedSectionStore.new,
      dispose: (section) => section.destroy(),
    );
  }

  @override
  void dispose() {
    _session.dispose();
    super.dispose();
  }

  Widget _wrapChain(TweetChain chain, Widget post) {
    final signal = stocksSignalChain(_folds, chain, post);
    return widget.followedFirst ? followedSectionChain(_section, chain, signal) : signal;
  }

  Future<TweetPageResult> _loadPage(String? cursor) {
    if (!widget.followedFirst) return _page(cursor);
    return followedFirstPage(
      cursor,
      query: widget.query,
      follows: mounted ? followedXAccounts(context) : const [],
      section: _section,
      page: _page,
    );
  }

  Future<TweetPageResult> _page(String? cursor) async {
    final result = await Twitter.searchTweets(widget.query, true, cursor: cursor);
    return (chains: signalGroupPostsLast(result.chains), nextCursor: result.cursorBottom);
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
        wrapChain: _wrapChain,
      ),
    );
  }
}
