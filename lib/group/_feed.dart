import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show ScrollDirection;

import 'package:quax/client/client.dart';
import 'package:quax/constants.dart';
import 'package:quax/database/entities.dart';
import 'package:quax/database/repository.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/group/feed_cache.dart';
import 'package:quax/group/feed_read_position.dart';
import 'package:quax/group/feed_session_cache.dart';
import 'package:quax/group/group_screen.dart';
import 'package:quax/profile/media_grid/media_grid.dart';
import 'package:quax/profile/media_grid/media_grid_items/media_grid_item.dart';
import 'package:logging/logging.dart';
import 'package:quax/plugins/reddit/reddit_interleaved.dart';
import 'package:quax/plugins/substack/substack_client.dart';
import 'package:quax/plugins/substack/substack_post_card.dart';
import 'package:quax/plugins/substack/substack_store.dart';
import 'package:quax/profile/profile_feed_settings.dart';
import 'package:quax/tweet/interleaved_items.dart';
import 'package:quax/tweet/paginated_tweet_list.dart';
import 'package:quax/tweet/tweet_context_scope.dart';
import 'package:quax/utils/iterables.dart';
import 'package:quax/utils/paging.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:sqflite/sqflite.dart';
import 'package:quax/utils/urls.dart';
import 'package:quax/group/custom_feed_rules.dart';

Iterable<BigInt> _tweetIdsOf(Iterable<TweetChain> chains) =>
    chains.expand((c) => c.tweets).map((t) => t.idStr).whereType<String>().map(BigInt.tryParse).whereType<BigInt>();

BigInt? _newestTweetIdOf(Iterable<TweetChain> chains) =>
    _tweetIdsOf(chains).fold<BigInt?>(null, (max, id) => max == null || id > max ? id : max);

BigInt? _oldestTweetIdOf(Iterable<TweetChain> chains) =>
    _tweetIdsOf(chains).fold<BigInt?>(null, (min, id) => min == null || id < min ? id : min);

class SubscriptionGroupFeed extends StatefulWidget {
  final SubscriptionGroupGet group;
  final List<SubscriptionGroupFeedChunk> chunks;
  final bool includeReplies;
  final bool includeRetweets;
  final bool mediaOnly;
  // When non-null, the PagingController and scroll offset are stored in the
  // app-scoped FeedSessionCache under this key, so pop+push of the same route
  // restores tweets and scroll position. When null, state is local to this
  // State and disposed normally — used by home-tab usages, which are kept
  // alive by AutomaticKeepAliveClientMixin in the shell.
  final String? cacheKey;
  // Cached tweets to show immediately while the first page loads, seeded by the
  // caller (e.g. the All/Following feed reuses the preview it already read while
  // its subscriptions were loading). Refined to this feed's own chunks once read.
  final List<TweetChain>? initialPreview;

  /// Substack publications in this group. They are members like any other, but
  /// they have their own source and their own pagination, so they are fetched
  /// beside the X search rather than inside it.
  final List<SubstackSubscription> publications;

  /// Subreddits in this group, fetched beside the X search for the same reason
  /// as the publications: their own source, their own pagination.
  final List<RedditSubscription> subreddits;

  const SubscriptionGroupFeed(
      {super.key,
      required this.group,
      required this.chunks,
      required this.includeReplies,
      required this.includeRetweets,
      required this.mediaOnly,
      this.cacheKey,
      this.initialPreview,
      this.publications = const [],
      this.subreddits = const []});

  @override
  State<SubscriptionGroupFeed> createState() => _SubscriptionGroupFeedState();
}

class _SubscriptionGroupFeedState extends State<SubscriptionGroupFeed> {
  late final TweetFeedController _feedController;
  // Grid-mode paging, created on first use. Kept separately from the tweet
  // list's controller so toggling the media filter swaps views without
  // refetching either of them.
  CursorPagingController<String, MediaGridItem>? _mediaPaging;
  final Set<String> _seenMediaKeys = <String>{};
  FeedSessionCache? _cache;
  ScrollController? _innerScrollController;
  bool _scrollRestoreScheduled = false;
  // Cached tweets shown while the first page loads, so opening the feed reveals
  // its previously-loaded content instead of a full-screen spinner.
  List<TweetChain>? _cachedPreview;

  // Reading position: the boundary is loaded once per mount and stays frozen,
  // so the "You're caught up" divider never moves mid-session.
  FeedReadPosition? _lastSeen;
  bool _readPositionLoadStarted = false;
  bool _caughtUpRestoreEvaluated = false;
  bool _userHasScrolled = false;
  String? _lastRecordedChainId;
  final GlobalKey _caughtUpKey = GlobalKey();

  static final _log = Logger('SubscriptionGroupFeed');

  bool get _usesCache => widget.cacheKey != null;

  /// Substack posts loaded for this group's publications, newest first.
  ///
  /// Substack pages by offset and X by cursor, so the two cannot share one
  /// paginator. These are fetched once per mount and slotted among the chains
  /// by date; scrolling further into X's history does not need more of them,
  /// because a newsletter publishes a handful of posts a week, not a page.
  List<InterleavedItem> _substackItems = const [];

  /// Reddit posts for this group's subreddits, newest first.
  List<InterleavedItem> _redditItems = const [];

  Future<void> _loadRedditPosts() async {
    if (widget.mediaOnly) {
      return;
    }

    // The group's own subreddits, plus every followed one when this is the
    // combined feed and the reader asked for Reddit in it.
    final names = {
      ...widget.subreddits.map((e) => e.name),
      if (widget.group.id == '-1') ...redditHomeSubreddits(context),
    }.toList(growable: false);

    final items = await loadRedditInterleaved(context, names);
    if (mounted && items.isNotEmpty) {
      setState(() => _redditItems = items);
    }
  }

  Future<void> _loadSubstackPosts() async {
    if (widget.publications.isEmpty || widget.mediaOnly) {
      return;
    }

    final client = context.read<SubstackClient>();
    final items = <InterleavedItem>[];

    for (final publication in widget.publications) {
      try {
        final posts = await client.fetchPosts(publicationOf(publication), limit: substackFeedPageSize);
        for (final post in posts) {
          final date = post.publishedAt;
          if (date == null) {
            continue;
          }
          items.add((
            date: date,
            build: (context) => SubstackPostCard(post: post, logoUrl: publication.logoUrl),
          ));
        }
      } catch (e) {
        // One unreachable publication must not empty the whole feed of the
        // others, nor replace a working timeline with an error screen.
        _log.warning('Unable to load Substack posts for ${publication.id}: $e');
      }
    }

    if (mounted) {
      setState(() => _substackItems = items);
    }
  }

  // Chronological feeds only: in popular order a "seen up to" boundary is
  // meaningless, and the media grid shares this loader but shows no divider.
  bool get _tracksReadPosition =>
      !widget.group.popular &&
      !widget.mediaOnly &&
      PrefService.of(context, listen: false).get(optionFeedReadingPosition) == true;

  bool _isSeen(TweetChain chain) => _lastSeen != null && isChainSeen(chain, _lastSeen!);

  CursorPagingController<String, MediaGridItem> get _mediaController =>
      _mediaPaging ??= CursorPagingController(_loadMediaPage);

  @override
  void initState() {
    super.initState();
    if (_usesCache) {
      _cache = context.read<FeedSessionCache>();
      _feedController = _cache!.getOrCreateController(widget.cacheKey!);
    } else {
      _feedController = TweetFeedController();
    }
    _feedController.pageCapProvider = _zenPageCap;
    // Cached (pop/push-restored) controllers already hold their tweets; only a
    // fresh controller needs the preview while it loads the first page.
    _cachedPreview = widget.initialPreview;
    if (!_feedController.hasItems) {
      _loadPreview();
    }
    _loadSubstackPosts();
    _loadRedditPosts();
  }

  Future<void> _loadPreview() async {
    var repository = await Repository.readOnly();
    var cached = await readCachedChainsForHashes(repository, widget.chunks.map((e) => e.hash));
    cached = filterHiddenRetweets(cached, await hiddenRetweetScreenNames());
    cached = filterHiddenReplies(cached, await hiddenReplyScreenNames());
    if (!mounted) return;
    setState(() => _cachedPreview = cached);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    // Inside NestedScrollView's body, PrimaryScrollController is the inner
    // controller PagedListView attaches to, and the one we need for jumpTo().
    _innerScrollController = PrimaryScrollController.maybeOf(context);
    _maybeLoadReadPosition();
    if (!_usesCache) return;
    _maybeRestoreScrollOffset();
  }

  void _maybeLoadReadPosition() {
    if (_readPositionLoadStarted || !_tracksReadPosition) {
      return;
    }
    _readPositionLoadStarted = true;
    readFeedReadPosition(widget.group.id).then((position) {
      if (mounted && position != null) {
        setState(() => _lastSeen = position);
      }
    });
  }

  bool _onScrollNotification(ScrollNotification notification) {
    // Any user-driven scroll cancels an in-flight caught-up restore, so it
    // never yanks the list out from under the reader.
    if (notification is UserScrollNotification && notification.direction != ScrollDirection.idle) {
      _userHasScrolled = true;
    }
    if (notification is! ScrollEndNotification) {
      return false;
    }
    final metrics = notification.metrics;
    if (_usesCache && metrics.hasPixels) {
      _cache!.saveOffset(widget.cacheKey!, metrics.pixels);
    }
    // Scrolled back up to the top: everything currently loaded counts as read.
    if (_tracksReadPosition && metrics.hasPixels && metrics.pixels <= feedReadPositionTopThresholdPx) {
      final items = _feedController.items;
      if (items != null && items.isNotEmpty) {
        _recordReadPosition(items);
      }
    }
    return false;
  }

  // The single attached scroll position, or null when the controller has none
  // or — inside a NestedScrollView during reload/tab transitions — more than
  // one. Reading `controller.position` with several attached asserts and would
  // crash, so every position access goes through here.
  ScrollPosition? get _scrollPosition {
    final controller = _innerScrollController;
    if (controller == null || controller.positions.length != 1) {
      return null;
    }
    return controller.positions.first;
  }

  bool get _atTop {
    final position = _scrollPosition;
    return position == null || position.pixels <= feedReadPositionTopThresholdPx;
  }

  void _recordReadPosition(List<TweetChain> threads) {
    final newest = threads.where((c) => c.tweets.firstOrNull?.createdAt != null).firstOrNull;
    if (newest == null || newest.id == _lastRecordedChainId) {
      return;
    }
    _lastRecordedChainId = newest.id;
    // Fire-and-forget: a failed position save must never surface as an
    // unhandled async error.
    writeFeedReadPosition(widget.group.id, newest).catchError((_) {});
  }

  // Called with each finalized first page. The first one decides between
  // restoring the caught-up position (there are unread posts above it) and
  // recording; later ones (soft refreshes) record only while at the top, so
  // an app-bar refresh fired mid-scroll can't mark unseen posts as read.
  void _onFirstPageLoaded(List<TweetChain> threads) {
    if (!_caughtUpRestoreEvaluated) {
      _caughtUpRestoreEvaluated = true;
      final sessionOffset = _usesCache ? _cache!.readOffset(widget.cacheKey!) : null;
      final boundary = _lastSeen == null ? null : caughtUpBoundaryIndex(threads, _lastSeen!);
      if (boundary != null && (sessionOffset == null || sessionOffset <= 0)) {
        _scheduleCaughtUpRestore(boundary, threads.length);
        return; // The newer posts haven't been seen yet — don't record.
      }
    }
    if (_atTop) {
      _recordReadPosition(threads);
    }
  }

  // Restore near the last-read chain once its row is laid out. Waits (bounded)
  // for the divider's key to resolve, then brings it just under the app bar in
  // a single scroll. If it never builds within the frame budget it does one
  // proportional jump and stops — deliberately gentle, so it never jump-fights
  // the user's own scrolling and never touches a multi-position controller.
  void _scheduleCaughtUpRestore(int index, int itemCount, [int attempts = 0]) {
    if (_userHasScrolled) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || _userHasScrolled || attempts >= maxCaughtUpRestoreFrames) {
        return;
      }
      final position = _scrollPosition;
      // Wait until the real list (not the preview) is mounted and laid out.
      if (position == null || !position.haveDimensions || !_feedController.hasItems) {
        _scheduleCaughtUpRestore(index, itemCount, attempts + 1);
        return;
      }
      final divider = _caughtUpKey.currentContext;
      if (divider != null) {
        Scrollable.ensureVisible(divider, alignment: 0.02);
        return;
      }
      // Divider not built yet: keep waiting a few frames, then settle for a
      // one-shot proportional estimate rather than jumping every frame.
      if (attempts + 1 < maxCaughtUpRestoreFrames) {
        _scheduleCaughtUpRestore(index, itemCount, attempts + 1);
        return;
      }
      final estimated = (position.maxScrollExtent * index / itemCount).clamp(0.0, position.maxScrollExtent);
      position.jumpTo(estimated);
    });
  }

  void _maybeRestoreScrollOffset() {
    if (_scrollRestoreScheduled) return;
    _scrollRestoreScheduled = true;
    final saved = _cache!.readOffset(widget.cacheKey!);
    if (saved == null || saved <= 0) return;
    _scheduleRestore(saved);
  }

  // The cached items render and lay out across the first few frames, so the
  // ScrollPosition may not be attached yet on the very first post-frame.
  // Keep scheduling post-frame callbacks until the scrollable reports stable
  // dimensions, then jump. Terminates via `mounted` when the widget unmounts.
  void _scheduleRestore(double offset) {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final position = _scrollPosition;
      if (position == null || !position.haveDimensions) {
        _scheduleRestore(offset);
        return;
      }
      position.jumpTo(offset.clamp(0.0, position.maxScrollExtent));
    });
  }

  @override
  void dispose() {
    _mediaPaging?.dispose();
    if (!_usesCache) {
      _feedController.dispose();
    }
    // When cached, the FeedSessionCache owns the controller's lifecycle across
    // pop/push; PaginatedTweetList has already detached its own listener.
    super.dispose();
  }

  @override
  void didUpdateWidget(SubscriptionGroupFeed oldWidget) {
    super.didUpdateWidget(oldWidget);

    if (oldWidget.includeReplies != widget.includeReplies ||
        oldWidget.includeRetweets != widget.includeRetweets ||
        oldWidget.group.popular != widget.group.popular ||
        oldWidget.group.custom != widget.group.custom ||
        oldWidget.group.customRules.cacheKey != widget.group.customRules.cacheKey ||
        !_chunksMatch(oldWidget.chunks, widget.chunks)) {
      _feedController.controller.refresh();
      _mediaPaging?.pagingController.refresh();
    }
  }

  bool _chunksMatch(List<SubscriptionGroupFeedChunk> a, List<SubscriptionGroupFeedChunk> b) {
    if (a.length != b.length) return false;
    for (var i = 0; i < a.length; i++) {
      if (a[i].hash != b[i].hash) return false;
    }
    return true;
  }

  Future<String> createCursor(Database repository) async {
    return (await repository.insert(tableFeedGroupCursor, {}, nullColumnHack: 'id')).toString();
  }

  bool feedContainsUnrelatedTweets(TweetStatus tweets, List<Subscription> users) {
    final screenNames = users.map((e) => e.screenName).toSet();
    return tweets.chains.any(
        (chain) => chain.tweets.any((tweet) => tweet.user != null && !screenNames.contains(tweet.user!.screenName)));
  }

  Future<void> showUnrelatedPostsInFeedWarning() async {
    await showDialog(
        context: context,
        builder: (BuildContext context) {
          return AlertDialog(
            title: Text("⚠️ ${L10n.of(context).feed_issue_detected}"),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(L10n.of(context).feed_contains_unrelated_tweets),
                SizedBox(height: Theme.of(context).textTheme.bodyMedium!.fontSize! * 2),
                PrefCheckbox(
                  title: Text(
                    L10n.of(context).never_show_again,
                    style: Theme.of(context).textTheme.bodyMedium,
                  ),
                  pref: optionDisableWarningsForUnrelatedPostsInFeed,
                )
              ],
            ),
            actions: [
              TextButton(
                child: Text(L10n.of(context).more_info),
                onPressed: () async {
                  await openUri(context, "https://github.com/Teskann/QuaX/issues/26");
                  if (context.mounted) {
                    Navigator.of(context).pop();
                  }
                },
              ),
              TextButton(
                child: Text(L10n.of(context).close),
                onPressed: () {
                  Navigator.of(context).pop();
                },
              ),
            ],
          );
        });
  }

  String _buildSearchQuery(List<Subscription> users) {
    var query = '';

    var remainingLength = 512 - query.length;

    for (var user in users) {
      var queryToAdd = '';
      if (user is UserSubscription) {
        queryToAdd = 'from:${user.screenName}';
      } else if (user is SearchSubscription) {
        queryToAdd = '"${user.id}"';
      }

      // If we can add this user to the query and still be less than ~512 characters, do so
      if (query.length + queryToAdd.length < remainingLength) {
        if (query != '' && query.isNotEmpty) {
          query += ' OR ';
        }

        query += queryToAdd;
      } else {
        // Otherwise, add the search future and start a new one
        assert(false, 'should never reach here');
        query = queryToAdd;
      }
    }

    if (!widget.includeReplies) {
      query += ' -filter:replies ';
    }

    if (!widget.includeRetweets) {
      query += ' -filter:retweets ';
    } else {
      query += ' include:nativeretweets ';
    }

    return query;
  }

  /// Search for our next "page" of tweets.
  ///
  /// Here, each page is actually a set of mappings, where the ID of each set is the hash of all the user IDs in that
  /// set. We store this along with the top and bottom pagination cursors, which we use to perform pagination for all
  /// sets at the same time, allowing us to create a feed made up of individual search queries.
  Future<TweetPageResult> _listTweets(String? cursorKey) async {
    List<Future<List<TweetChain>>> futures = [];

    var repository = await Repository.writable();
    var nextCursor = await createCursor(repository);
    bool shouldShowUnrelatedPostsInFeedWarning = false;

    for (var chunk in widget.chunks) {
      var hash = chunk.hash;

      futures.add(Future(() async {
        var tweets = <TweetChain>[];

        String? searchCursor;
        BigInt? storedNewestId;

        if (cursorKey == null) {
          // We're loading the initial content for the feed screen, so load all the chunks we already have
          var storedChunks = await repository.query(tableFeedGroupChunk,
              where: 'hash = ?', whereArgs: [hash], orderBy: 'created_at DESC');

          // Make sure we load any existing stored tweets from the chunk
          tweets.addAll(chainsFromStoredChunks(storedChunks));
          storedNewestId = _newestTweetIdOf(tweets);

          // Use the latest chunk's top cursor to load any new tweets since the last time we checked
          var latestChunk = storedChunks.firstOrNull;
          if (latestChunk != null) {
            searchCursor = latestChunk['cursor_top'] as String;
          } else {
            // Otherwise we need to perform a fresh load from scratch for this chunk
            searchCursor = null;
          }
        } else {
          // We're currently at the end of our current feed, so load the oldest chunk and use its cursor to load more
          var storedChunks = await repository.query(tableFeedGroupChunk,
              where: 'cursor_id = ? AND hash = ?', whereArgs: [int.parse(cursorKey), hash]);
          if (storedChunks.isNotEmpty) {
            searchCursor = storedChunks.first['cursor_bottom'] as String;
          } else {
            searchCursor = null;
          }
        }

        // Perform our search for the next page of results for this chunk, and add those tweets to our collection
        var query = _buildSearchQuery(chunk.users);
        TweetStatus result =
            await Twitter.searchTweets(query, widget.includeReplies, cursor: searchCursor);
        shouldShowUnrelatedPostsInFeedWarning |= feedContainsUnrelatedTweets(result, chunk.users);

        if (result.chains.isNotEmpty) {
          tweets.addAll(result.chains);

          // Make sure we insert the set of cursors for this latest chunk, ready for the next time we paginate
          await repository.insert(tableFeedGroupChunk, {
            'cursor_id': int.parse(nextCursor),
            'hash': hash,
            'cursor_top': result.cursorTop,
            'cursor_bottom': result.cursorBottom,
            'response': jsonEncode(result.chains.map((e) => e.toJson()).toList())
          });
        }

        // A single fetch returns only the newest page, so a long absence
        // leaves a hole between it and the stored posts. Keep paging down
        // until the fresh content overlaps what was stored (bounded, so a
        // week away can't trigger dozens of requests).
        var page = result;
        var gapFills = 0;
        while (storedNewestId != null &&
            page.chains.isNotEmpty &&
            (_oldestTweetIdOf(page.chains) ?? BigInt.zero) > storedNewestId &&
            page.cursorBottom != null &&
            gapFills < maxFeedGapFillPages) {
          page = await Twitter.searchTweets(query, widget.includeReplies, cursor: page.cursorBottom);
          gapFills++;

          if (page.chains.isNotEmpty) {
            tweets.addAll(page.chains);
            await repository.insert(tableFeedGroupChunk, {
              'cursor_id': int.parse(nextCursor),
              'hash': hash,
              'cursor_top': page.cursorTop,
              'cursor_bottom': page.cursorBottom,
              'response': jsonEncode(page.chains.map((e) => e.toJson()).toList())
            });
          }
        }

        return tweets;
      }));
    }

    // Wait for all our searches to complete, then build our list of tweet conversations.
    // The stored chunks and the fresh fetch overlap at their window boundaries,
    // so drop repeated chains before display.
    var result = (await Future.wait(futures));
    var threads = _sortChains(dedupeChainsById(result.expand((element) => element).toList()));
    threads = filterHiddenRetweets(threads, await hiddenRetweetScreenNames());
    threads = filterHiddenReplies(threads, await hiddenReplyScreenNames());
    threads = applyCustomFeedRules(threads, widget.group.customRules);

    if (!mounted) {
      return (chains: <TweetChain>[], nextCursor: null);
    }

    if (PrefService.of(context).get(optionZenMode) == true) {
      threads = _applyZenMode(threads);
    }

    if (shouldShowUnrelatedPostsInFeedWarning &&
        !PrefService.of(context).get(optionDisableWarningsForUnrelatedPostsInFeed)) {
      await showUnrelatedPostsInFeedWarning();
    }

    if (cursorKey == null && _tracksReadPosition) {
      _onFirstPageLoaded(threads);
    }

    return (chains: threads, nextCursor: nextCursor);
  }

  static int _likesOf(TweetChain chain) => chain.tweets.firstOrNull?.favoriteCount ?? 0;

  /// Popular groups order the same recent window by likes; recent ones (the
  /// default) by date.
  List<TweetChain> _sortChains(List<TweetChain> chains) {
    if (!widget.group.popular) {
      return sortChainsNewestFirst(chains);
    }
    return chains.sorted((a, b) => _likesOf(b).compareTo(_likesOf(a)));
  }

  // In zen mode the feed is finite: pagination pauses after this many pages
  // per session. `null` disables the cap when zen mode is off.
  int? _zenPageCap() {
    if (!mounted) {
      return null;
    }
    final prefs = PrefService.of(context, listen: false);
    if (prefs.get(optionZenMode) != true) {
      return null;
    }
    return prefs.get<int>(optionZenModePageCap);
  }

  /// Zen mode: a calm feed with no engagement-based ranking — strictly
  /// newest-first, keeping only each author's few most recent posts so no
  /// account can flood the page.
  List<TweetChain> _applyZenMode(List<TweetChain> chains) {
    final byAuthorCount = <String, int>{};
    final kept = <TweetChain>[];

    for (final chain in sortChainsNewestFirst(chains)) {
      final author = chain.tweets.firstOrNull?.user?.idStr;
      if (author == null) {
        kept.add(chain);
        continue;
      }
      final count = byAuthorCount[author] ?? 0;
      if (count < zenModeMaxTweetsPerAuthor) {
        byAuthorCount[author] = count + 1;
        kept.add(chain);
      }
    }

    return kept;
  }

  /// Loads a page for the media grid: same pages as the tweet list, mapped to
  /// their media entries.
  Future<CursorPage<String, MediaGridItem>> _loadMediaPage(String? cursor) async {
    if (cursor == null) {
      _seenMediaKeys.clear();
    }

    return mediaPageWithLookahead(cursor, _listTweets, _unseenMediaItems);
  }

  // Successive search windows overlap at their boundaries, so keep only media
  // entries not shown on an earlier page.
  List<MediaGridItem> _unseenMediaItems(List<TweetChain> chains) {
    return mediaItemsFromChains(chains)
        .where((m) => _seenMediaKeys.add('${m.tweetId}/${m.mediaIndex}'))
        .toList();
  }

  Widget _buildMediaGrid(BuildContext context) {
    return Scaffold(
      body: TweetContextScope(
        child: MediaGrid(
          controller: _mediaController.pagingController,
          firstPageErrorPrefix: L10n.of(context).unable_to_load_the_tweets_for_the_feed,
          newPageErrorPrefix: L10n.of(context).unable_to_load_the_next_page_of_tweets,
          emptyMessage: L10n.of(context).could_not_find_any_posts_with_media,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    // A group is empty when it has nothing from *any* source. Testing only the
    // X chunks meant a group of nothing but subreddits reported itself empty
    // before its posts were ever asked for — the list below knows how to show
    // interleaved items with no chains, but never got the chance.
    if (widget.chunks.isEmpty && widget.publications.isEmpty && widget.subreddits.isEmpty) {
      return Scaffold(
        body: Center(
          child: Text(L10n.of(context).this_group_contains_no_subscriptions),
        ),
      );
    }

    if (widget.mediaOnly) {
      return _buildMediaGrid(context);
    }

    return Scaffold(
      body: TweetContextScope(
        child: NotificationListener<ScrollNotification>(
          onNotification: _onScrollNotification,
          child: PaginatedTweetList(
            feed: _feedController,
            loadPage: _listTweets,
            username: null,
            firstPagePreview: _cachedPreview,
            onRefresh: () async {
              var repository = await Repository.writable();
              await repository.delete(tableFeedGroupChunk);
            },
            firstPageErrorPrefix: L10n.of(context).unable_to_load_the_tweets_for_the_feed,
            newPageErrorPrefix: L10n.of(context).unable_to_load_the_next_page_of_tweets,
            emptyMessage: L10n.of(context).could_not_find_any_tweets_from_the_last_7_days,
            isSeen: _tracksReadPosition && _lastSeen != null ? _isSeen : null,
            caughtUpDividerKey: _caughtUpKey,
            interleaved: [..._substackItems, ..._redditItems],
          ),
        ),
      ),
    );
  }
}
