import 'package:flutter/material.dart';

import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/database/timeline_cache.dart';
import 'package:xta/profile/media_grid/media_grid_items/media_grid_item.dart';
import 'package:xta/profile/posts_filter.dart';
import 'package:xta/tweet/conversation.dart';
import 'package:xta/tweet/tweet_skeleton.dart';
import 'package:xta/tweet/sensitive_media_gate.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/user.dart';
import 'package:infinite_scroll_pagination/infinite_scroll_pagination.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/utils/paging.dart';
import 'package:pref/pref.dart';
import 'package:logging/logging.dart';

class ProfileTweets extends StatefulWidget {
  final UserWithExtra user;
  final String type;
  final bool includeReplies;
  final List<String> pinnedTweets;
  final BasePrefService pref;
  final PostsFilter filter;

  const ProfileTweets(
      {super.key,
      required this.user,
      required this.type,
      required this.includeReplies,
      required this.pinnedTweets,
      required this.pref,
      this.filter = PostsFilter.all});

  @override
  State<ProfileTweets> createState() => _ProfileTweetsState();
}

class _ProfileTweetsState extends State<ProfileTweets> with AutomaticKeepAliveClientMixin<ProfileTweets> {
  static final log = Logger('ProfileTweets');

  late final CursorPagingController<String, TweetChain> _paging;
  PagingController<int, TweetChain> get _pagingController => _paging.pagingController;

  static const int pageSize = 20;
  int loadTweetsCounter = 0;
  bool _bypassCache = false;

  @override
  bool get wantKeepAlive => true;

  @override
  void initState() {
    super.initState();
    _paging = CursorPagingController<String, TweetChain>(_fetchPage);
  }

  @override
  void didUpdateWidget(covariant ProfileTweets oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.filter != oldWidget.filter) {
      _paging.dispose();
      loadTweetsCounter = 0;
      _paging = CursorPagingController<String, TweetChain>(_fetchPage);
    }
  }

  @override
  void dispose() {
    _paging.dispose();
    super.dispose();
  }

  void incrementLoadTweetsCounter() {
    if (widget.filter == PostsFilter.all) {
      ++loadTweetsCounter;
    }
  }

  int getLoadTweetsCounter() {
    return loadTweetsCounter;
  }

  Future<TweetStatus> _load(String? cursor) => Twitter.getTweets(
        widget.user.idStr!,
        widget.type,
        widget.pinnedTweets,
        cursor: cursor,
        count: pageSize,
        includeReplies: widget.includeReplies,
        getTweetsCounter: getLoadTweetsCounter,
        incrementTweetsCounter: incrementLoadTweetsCounter,
      );

  /// The first page of a profile, from cache when it is fresh enough, and from
  /// cache at any age when the request fails. Opening the same profile twice in
  /// a session used to cost two requests; now the second one paints instantly
  /// and still shows something while rate limited or offline.
  ///
  /// Only the first page is cached — see [TimelineCache].
  Future<TweetStatus> _loadFirstPage() async {
    final key = TimelineCache.profileKey(
      widget.user.idStr!,
      widget.type,
      includeReplies: widget.includeReplies,
    );
    final cache = TimelineCache(await Repository.writable());

    // A pull-to-refresh must reach X; serving the cache would make the gesture
    // do nothing for the length of the window.
    if (!_bypassCache) {
      final cached = await cache.read(key, maxAge: profileCacheMaxAge);
      if (cached != null) {
        return cached;
      }
    }
    _bypassCache = false;

    try {
      final result = await _load(null);
      await cache.write(key, result);
      return result;
    } catch (e) {
      final stale = await cache.readStale(key);
      if (stale == null) {
        rethrow;
      }
      log.info('Showing the cached profile timeline for ${widget.user.idStr} after $e');
      return stale;
    }
  }

  Future<CursorPage<String, TweetChain>> _fetchPage(String? cursor) async {
    if (widget.filter == PostsFilter.all) {
      var result = cursor == null ? await _loadFirstPage() : await _load(cursor);
      final next = result.cursorBottom;
      return (items: result.chains, nextCursor: next == cursor ? null : next);
    }

    return mediaPageWithLookahead<TweetChain>(
      cursor,
      (c) async {
        final result = c == null ? await _loadFirstPage() : await _load(c);
        final next = result.cursorBottom;
        return (chains: result.chains, nextCursor: next == c ? null : next);
      },
      (chains) => chains.where(widget.filter.accepts).toList(),
    );
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);

    return SensitiveMediaGate(
      sensitive: widget.user.possiblySensitive ?? false,
      errorMessage: L10n.current.possibly_sensitive_profile,
      wrapInCard: false,
      child: RefreshIndicator(
        onRefresh: () async {
          _bypassCache = true;
          _pagingController.refresh();
        },
        child: PagingListener<int, TweetChain>(
          controller: _pagingController,
          builder: (context, state, fetchNextPage) => PagedListView<int, TweetChain>(
            padding: EdgeInsets.zero,
            state: state,
            fetchNextPage: fetchNextPage,
            addAutomaticKeepAlives: false,
            builderDelegate: PagedChildBuilderDelegate(
              itemBuilder: (context, chain, index) {
                // Keyed by chain id so a refreshed page gives each changed
                // conversation a fresh state instead of recycling the one that
                // happened to sit at the same index.
                return TweetConversation(
                    key: ValueKey(chain.id),
                    id: chain.id,
                    tweets: chain.tweets,
                    username: widget.user.screenName!,
                    isPinned: chain.isPinned);
              },
              firstPageProgressIndicatorBuilder: (context) =>
                  const TweetFeedSkeleton(primary: false),
              newPageProgressIndicatorBuilder: (context) => const TweetSkeletonTile(),
              firstPageErrorIndicatorBuilder: (context) => FullPageErrorWidget(
                error: pagingErrorOf(state)?.error,
                stackTrace: pagingErrorOf(state)?.stackTrace,
                prefix: L10n.of(context).unable_to_load_the_tweets,
                onRetry: fetchNextPage,
              ),
              newPageErrorIndicatorBuilder: (context) => FullPageErrorWidget(
                error: pagingErrorOf(state)?.error,
                stackTrace: pagingErrorOf(state)?.stackTrace,
                prefix: L10n.of(context).unable_to_load_the_next_page_of_tweets,
                onRetry: fetchNextPage,
              ),
              noItemsFoundIndicatorBuilder: (context) {
                return Center(
                  child: Text(
                    L10n.of(context).could_not_find_any_tweets_by_this_user,
                  ),
                );
              },
            ),
          ),
        ),
      ),
    );
  }
}
