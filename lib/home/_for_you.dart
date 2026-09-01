import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:quax/client/client.dart';
import 'package:quax/plugins/reddit/reddit_interleaved.dart';
import 'package:quax/tweet/interleaved_items.dart';
import 'package:quax/tweet/paginated_tweet_list.dart';
import 'package:quax/tweet/tweet_context_scope.dart';
import 'package:quax/user.dart';
import 'package:quax/generated/l10n.dart';
import 'package:pref/pref.dart';
import '../constants.dart';

final UserWithExtra user = UserWithExtra.fromArguments(idStr: "1", possiblySensitive: false, screenName: "ForYou");

class ForYouStore extends Store<List<InterleavedItem>> {
  int _loadTweetsCounter = 0;

  ForYouStore() : super(const []);

  int get loadTweetsCounter => _loadTweetsCounter;

  void incrementLoadTweetsCounter() => _loadTweetsCounter++;

  void setRedditPosts(List<InterleavedItem> items) {
    if (items.isNotEmpty) {
      update(items);
    }
  }
}

class ForYouTweets extends StatefulWidget {
  final TweetFeedController feed;
  final String type;
  final bool includeReplies;
  final BasePrefService pref;

  const ForYouTweets(this.feed, {super.key, required this.type, required this.includeReplies, required this.pref});

  @override
  State<ForYouTweets> createState() => _ForYouTweetsState();
}

class _ForYouTweetsState extends State<ForYouTweets> with AutomaticKeepAliveClientMixin<ForYouTweets> {
  static const int pageSize = 20;
  late final ForYouStore _store;

  @override
  bool get wantKeepAlive => true;

  @override
  void initState() {
    super.initState();
    _store = ForYouStore();
    widget.feed.pageCapProvider = _zenPageCap;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        _loadRedditPosts();
      }
    });
  }

  /// Loads Reddit once per mount and lets the Tweet list place those posts by
  /// date without coupling Reddit's independent pagination to X's cursor.
  Future<void> _loadRedditPosts() async {
    final items = await loadRedditInterleaved(context, redditHomeSubreddits(context));
    if (mounted) {
      _store.setRedditPosts(items);
    }
  }

  // In zen mode the feed is finite: pagination pauses after this many pages
  // per session. `null` disables the cap when zen mode is off.
  int? _zenPageCap() {
    if (widget.pref.get(optionZenMode) != true) {
      return null;
    }
    return widget.pref.get<int>(optionZenModePageCap);
  }

  Future<TweetPageResult> _loadTweets(String? cursor) async {
    final result = await Twitter.getTimelineTweets(
      user.idStr!,
      widget.type,
      cursor: cursor,
      count: pageSize,
      includeReplies: widget.includeReplies,
      getTweetsCounter: () => _store.loadTweetsCounter,
      incrementTweetsCounter: _store.incrementLoadTweetsCounter,
    );
    return (chains: result.chains, nextCursor: result.cursorBottom);
  }

  @override
  void dispose() {
    _store.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return TweetContextScope(
      child: ScopedBuilder<ForYouStore, List<InterleavedItem>>(
        store: _store,
        onState: (context, redditItems) => PaginatedTweetList(
          feed: widget.feed,
          loadPage: _loadTweets,
          interleaved: redditItems,
          username: user.screenName,
          onRefresh: () async {},
          firstPageErrorPrefix: L10n.of(context).unable_to_load_the_tweets,
          newPageErrorPrefix: L10n.of(context).unable_to_load_the_next_page_of_tweets,
          emptyMessage: L10n.of(context).unable_to_load_the_tweets_for_the_feed,
        ),
      ),
    );
  }
}
