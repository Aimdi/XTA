import 'package:xta/catcher/exceptions.dart';
import 'package:flutter/material.dart';

import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/database/timeline_cache.dart';
import 'package:xta/profile/media_grid/media_grid_items/media_grid_item.dart';
import 'package:xta/profile/merged_timeline.dart';
import 'package:xta/profile/posts_filter.dart';
import 'package:xta/profile/profile_chrome.dart';
import 'package:xta/tweet/conversation.dart';
import 'package:xta/tweet/tweet_skeleton.dart';
import 'package:xta/tweet/sensitive_media_gate.dart';
import 'package:xta/ui/motion.dart';
import 'package:xta/ui/reader_failure.dart';
import 'package:xta/utils/read_recovery.dart';
import 'package:xta/user.dart';
import 'package:infinite_scroll_pagination/infinite_scroll_pagination.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/utils/paging.dart';
import 'package:xta/utils/cached_page.dart';
import 'package:pref/pref.dart';

class ProfileTweets extends StatefulWidget {
  final UserWithExtra user;
  final String type;
  final bool includeReplies;
  final List<String> pinnedTweets;
  final BasePrefService pref;
  final PostsFilter filter;
  @visibleForTesting
  final Future<TweetStatus> Function(String? cursor)? fetchTweets;

  /// The reposts timeline in tests. When only [fetchTweets] is given, the
  /// profile has no reposts.
  @visibleForTesting
  final Future<TweetStatus> Function(String? cursor)? fetchReposts;

  const ProfileTweets({
    super.key,
    required this.user,
    required this.type,
    required this.includeReplies,
    required this.pinnedTweets,
    required this.pref,
    this.filter = PostsFilter.all,
    this.fetchTweets,
    this.fetchReposts,
  });

  @override
  State<ProfileTweets> createState() => _ProfileTweetsState();
}

class _ProfileTweetsState extends State<ProfileTweets> with AutomaticKeepAliveClientMixin<ProfileTweets> {
  late CursorPagingController<String, TweetChain> _paging;
  PagingController<int, TweetChain> get _pagingController => _paging.pagingController;

  static const int pageSize = 20;
  int loadTweetsCounter = 0;
  bool _bypassCache = false;
  bool _firstLoadStarted = false;
  MergedTimeline? _merged;

  /// The posts fade in over their placeholders.
  final _afterPlaceholders = XtaAfterPlaceholders();

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
      _firstLoadStarted = false;
      _paging = CursorPagingController<String, TweetChain>(_fetchPage);
    }
  }

  @override
  void dispose() {
    _paging.dispose();
    super.dispose();
  }

  void _maybeStartFirstLoad() {
    scheduleFirstPageFetch(
      _pagingController,
      alreadyStarted: _firstLoadStarted,
      markStarted: () => _firstLoadStarted = true,
      isMounted: () => mounted,
    );
  }

  void incrementLoadTweetsCounter() {
    if (widget.filter == PostsFilter.all) {
      ++loadTweetsCounter;
    }
  }

  int getLoadTweetsCounter() {
    return loadTweetsCounter;
  }

  /// X serves a profile's reposts apart from its posts, so the Posts tab's
  /// "All" reads both and merges them.
  bool get _mergesReposts => !widget.includeReplies && widget.filter == PostsFilter.all;

  bool get _readsReposts => !widget.includeReplies && widget.filter == PostsFilter.retweets;

  Future<TweetStatus> _load(String? cursor, {bool reposts = false}) {
    if (reposts) {
      return withRateLimitOperations(['UserRepostsTimeline'], () => _fetchReposts(cursor));
    }
    final operation = widget.includeReplies ? 'UserTweetsAndReplies' : 'UserOriginalsTimeline';
    return withRateLimitOperations([operation], () => widget.fetchTweets?.call(cursor) ?? _getTweets(cursor));
  }

  Future<TweetStatus> _fetchReposts(String? cursor) {
    final seam = widget.fetchReposts;
    if (seam != null) return seam(cursor);
    if (widget.fetchTweets != null) return Future.value(TweetStatus(chains: const [], cursorBottom: null, cursorTop: null));
    return _getTweets(cursor, reposts: true);
  }

  Future<TweetStatus> _getTweets(String? cursor, {bool reposts = false}) => Twitter.getTweets(
    widget.user.idStr!,
    widget.type,
    reposts ? const [] : widget.pinnedTweets,
    cursor: cursor,
    count: pageSize,
    includeReplies: widget.includeReplies,
    reposts: reposts,
    getTweetsCounter: getLoadTweetsCounter,
    incrementTweetsCounter: incrementLoadTweetsCounter,
  );

  /// The first page of a profile, from cache when it is fresh enough, and from
  /// cache at any age when the request fails. Opening the same profile twice in
  /// a session used to cost two requests; now the second one paints instantly
  /// and still shows something while rate limited or offline.
  ///
  /// Only the first page is cached — see [TimelineCache].
  Future<TweetStatus> _loadFirstPage({required bool bypassCache, bool reposts = false}) async {
    final type = reposts ? 'reposts' : widget.type;
    final key = TimelineCache.profileKey(widget.user.idStr!, type, includeReplies: widget.includeReplies);
    Future<TimelineCache> cache() async => TimelineCache(await Repository.writable());
    return loadCachedPage(
      bypassCache: bypassCache,
      readFresh: () async => (await cache()).read(key, maxAge: profileCacheMaxAge),
      readStale: () async => (await cache()).readStale(key),
      fetch: () => _load(null, reposts: reposts),
      write: (page) async => (await cache()).write(key, page),
    );
  }

  /// One timeline as the merge reads it: the first page through the cache.
  TimelineFetch _timeline({required bool reposts, required bool bypassCache}) => (cursor) async {
    final result = cursor == null
        ? await _loadFirstPage(bypassCache: bypassCache, reposts: reposts)
        : await _load(cursor, reposts: reposts);
    return (chains: result.chains, next: result.cursorBottom);
  };

  Future<CursorPage<String, TweetChain>> _fetchMerged(String? cursor, bool bypassCache) async {
    final merged = cursor == null || _merged == null
        ? _merged = MergedTimeline(
            _timeline(reposts: false, bypassCache: bypassCache),
            _timeline(reposts: true, bypassCache: bypassCache),
          )
        : _merged!;
    final items = await merged.next();
    return (items: items, nextCursor: merged.nextCursor);
  }

  Future<CursorPage<String, TweetChain>> _fetchPage(String? cursor) async {
    final bypassCache = cursor == null && _bypassCache;
    if (cursor == null) _bypassCache = false;
    if (_mergesReposts) {
      return _fetchMerged(cursor, bypassCache);
    }
    if (widget.filter == PostsFilter.all || _readsReposts) {
      final reposts = _readsReposts;
      final result = cursor == null
          ? await _loadFirstPage(bypassCache: bypassCache, reposts: reposts)
          : await _load(cursor, reposts: reposts);
      final next = result.cursorBottom;
      return (items: result.chains, nextCursor: next == cursor ? null : next);
    }

    return mediaPageWithLookahead<TweetChain>(cursor, (c) async {
      final result = c == null ? await _loadFirstPage(bypassCache: bypassCache) : await _load(c);
      final next = result.cursorBottom;
      return (chains: result.chains, nextCursor: next == c ? null : next);
    }, (chains) => chains.where(widget.filter.accepts).toList());
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    _maybeStartFirstLoad();

    return SensitiveMediaGate(
      sensitive: widget.user.possiblySensitive ?? false,
      errorMessage: L10n.current.possibly_sensitive_profile,
      wrapInCard: false,
      child: PagingReadRecovery(
        controller: _pagingController,
        child: RefreshIndicator(
          onRefresh: () async {
            _bypassCache = true;
            _pagingController.refresh();
            _pagingController.fetchNextPage();
          },
          child: PagingListener<int, TweetChain>(
            controller: _pagingController,
            builder: (context, state, fetchNextPage) {
              // NestedScrollView + TabBarView freeze when PagedListView's first-page
              // slot (SliverFillRemaining) is the inner scrollable. Show skeleton /
              // error / empty outside it, and only mount the list once posts exist.
              if (pagingAwaitingFirstPage(state)) {
                return _afterPlaceholders.placeholders(const TweetFeedSkeleton());
              }
              if (state.items == null) {
                return ListView(
                  physics: const AlwaysScrollableScrollPhysics(),
                  children: [
                    ReaderFailureNotice(
                      recoverAutomatically: false,
                      error: pagingErrorOf(state)?.error ?? state.error,
                      onRetry: fetchNextPage,
                    ),
                  ],
                );
              }
              if (state.items!.isEmpty) {
                return pagingFill(
                  child: ProfileEmptyState(
                    icon: Icons.article_outlined,
                    message: L10n.of(context).could_not_find_any_tweets_by_this_user,
                  ),
                );
              }
              final posts = PagedListView<int, TweetChain>(
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
                      isPinned: chain.isPinned,
                    );
                  },
                  newPageProgressIndicatorBuilder: (context) => const TweetSkeletonTile(),
                  newPageErrorIndicatorBuilder: (context) => ReaderFailureNotice(
                    recoverAutomatically: false,
                    error: pagingErrorOf(state)?.error ?? state.error,
                    onRetry: fetchNextPage,
                  ),
                ),
              );
              return _afterPlaceholders.content(posts);
            },
          ),
        ),
      ),
    );
  }
}
