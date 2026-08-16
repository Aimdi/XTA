import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show ScrollDirection;

import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/author_caps.dart';
import 'package:xta/group/custom_feed_rules.dart';
import 'package:xta/group/feed_cache.dart';
import 'package:xta/group/feed_gap.dart';
import 'package:xta/group/feed_read_position.dart';
import 'package:xta/group/feed_session_cache.dart';
import 'package:xta/group/future_pool.dart';
import 'package:xta/group/group_screen.dart';
import 'package:xta/group/language_filter.dart';
import 'package:xta/profile/media_grid/media_grid.dart';
import 'package:xta/profile/media_grid/media_grid_items/media_grid_item.dart';
import 'package:xta/profile/profile_feed_settings.dart';
import 'package:xta/plugins/subscription_source.dart';
import 'package:xta/tweet/interleaved_items.dart';
import 'package:xta/tweet/paginated_tweet_list.dart';
import 'package:xta/tweet/tweet_context_scope.dart';
import 'package:xta/utils/iterables.dart';
import 'package:xta/utils/paging.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:sqflite/sqflite.dart';
import 'package:xta/utils/urls.dart';
import 'package:xta/group/feed_catch_up.dart';
import 'package:xta/group/feed_first_page_action.dart';
import 'package:xta/group/feed_source_reload.dart';
import 'package:xta/group/held_refresh.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/tweet/catch_up_split.dart';
import 'package:xta/group/feed_rules.dart';

/// One chunk's contribution to a feed page: its chains, whether its gap-fill
/// ran out of allowance, and whether X answered with posts from outside the
/// chunk's own subscriptions.
/// Max in-flight X search requests for feed chunks. Large subscription sets
/// (1000+ → 60+ chunks) used to open every search at once via [Future.wait],
/// which cascaded into 404s and flagged accounts (#165 / #170).
const int feedChunkFetchConcurrency = 3;

/// Wait this long after a membership/filter change before refetching, so a
/// burst of subscribe/unsubscribe actions collapses into one refresh (#170).
const Duration feedChunkRefreshDebounce = Duration(milliseconds: 600);

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
  // When those cached tweets were saved, so a failed load can say how old the
  // posts it falls back on are.
  final DateTime? initialPreviewCachedAt;

  /// This group's members that belong to a plugin, keyed by the source that
  /// fetches them. They are members like any other, but each has its own source
  /// and its own pagination, so they are fetched beside the X search rather
  /// than inside it.
  final Map<SubscriptionSource, List<Subscription>> pluginMembers;

  const SubscriptionGroupFeed({
    super.key,
    required this.group,
    required this.chunks,
    required this.includeReplies,
    required this.includeRetweets,
    required this.mediaOnly,
    this.cacheKey,
    this.initialPreview,
    this.initialPreviewCachedAt,
    this.pluginMembers = const {},
  });

  @override
  State<SubscriptionGroupFeed> createState() => _SubscriptionGroupFeedState();
}

class _SubscriptionGroupFeedState extends State<SubscriptionGroupFeed> {
  Map<String, String> _foldReasons = const {};
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
  // its previously-loaded content instead of a full-screen spinner. They are
  // also the fallback when the first page fails outright.
  List<TweetChain>? _cachedPreview;
  DateTime? _cachedPreviewAt;
  // Set when a first page stopped filling the gap between the newest posts and
  // the stored ones because it ran out of allowance. The catch-up card must not
  // claim the reader is finished when this is true.
  bool _gapCapped = false;

  // Reading position: the boundary is loaded once per mount and stays frozen,
  // so the "You're caught up" divider never moves mid-session.
  FeedReadPosition? _lastSeen;
  bool _readPositionLoadStarted = false;
  bool _readPositionReady = false;
  List<TweetChain>? _pendingFirstPage;
  bool _caughtUpRestoreEvaluated = false;
  bool _userHasScrolled = false;
  String? _lastRecordedChainId;
  final GlobalKey _caughtUpKey = GlobalKey();
  Timer? _chunkRefreshDebounce;

  bool get _usesCache => widget.cacheKey != null;

  /// Posts loaded for each plugin source in this group, newest first.
  ///
  /// A source pages on its own terms — Substack by offset, X by cursor — so
  /// these cannot share one paginator with the X side. They are fetched once per
  /// mount and slotted among the chains by date.
  final Map<SubscriptionSource, List<InterleavedItem>> _pluginItems = {};

  /// The sources merged, rebuilt only when one of them arrives. Built in
  /// `build` it was a fresh list every frame, so nothing downstream could tell
  /// by identity that the interleave had not changed.
  List<InterleavedItem> _interleaved = const [];

  void _mergeInterleaved() =>
      _interleaved = [for (final items in _pluginItems.values) ...items];

  /// Asks every plugin in this group for its posts, at once.
  ///
  /// One loader rather than one per network: they differed only in which store
  /// they read and which ids they passed, and keeping five copies in step is
  /// what made adding a source an eight-line edit in this file alone — and what
  /// left the Fediverse out of two of those five places.
  /// Every registered source, not only the ones this group has members for: a
  /// source whose last member was just removed still has to be asked, or its
  /// posts stay in the feed after the account that brought them is gone.
  Future<void> _loadPluginPosts() async {
    final prefs = PrefService.of(context, listen: false);
    var dirty = false;
    await Future.wait(
      enabledSubscriptionSources(prefs).map((source) async {
        if (await _collectPostsFrom(source)) {
          dirty = true;
        }
      }),
    );
    if (mounted && dirty) {
      setState(_mergeInterleaved);
    }
  }

  Future<bool> _collectPostsFrom(SubscriptionSource source) async {
    if (widget.mediaOnly) {
      return false;
    }

    final isCombined = widget.group.id == legacyFeedKeyFollowing;
    final inHomeFeed = isCombined && source.inHomeFeed(context);
    final ids = sourceIdsFor(
      memberIds:
          widget.pluginMembers[source]
              ?.map((e) => e.id)
              .toList(growable: false) ??
          const [],
      isCombinedFeed: isCombined,
      inHomeFeed: inHomeFeed,
      homeFeedIds: inHomeFeed ? source.homeFeedIds(context) : const [],
    );

    final items = await source.interleavedPosts(context, ids);
    return mounted && replacePluginSlot(_pluginItems, source, items);
  }

  Future<void> _reloadPluginSources(
    Iterable<SubscriptionSource> sources,
  ) async {
    var dirty = false;
    await Future.wait(
      sources.map((source) async {
        if (await _collectPostsFrom(source)) {
          dirty = true;
        }
      }),
    );
    if (mounted && dirty) {
      setState(_mergeInterleaved);
    }
  }

  // Chronological feeds only: in popular order a "seen up to" boundary is
  // meaningless, and the media grid shares this loader but shows no divider.
  bool get _supportsReadPosition => !widget.group.popular && !widget.mediaOnly;

  /// Catch-up mode: this feed shows only what is new since the reader's last
  /// position and stops there. Per feed, off unless turned on for this one.
  bool get _catchUpEnabled =>
      _supportsReadPosition &&
      PrefService.of(
            context,
            listen: false,
          ).get(feedCatchUpModeKey(widget.group.id)) ==
          true;

  bool get _tracksReadPosition =>
      _supportsReadPosition &&
      (PrefService.of(context, listen: false).get(optionFeedReadingPosition) ==
              true ||
          _catchUpEnabled);

  bool _isSeen(TweetChain chain) =>
      _lastSeen != null && isChainSeen(chain, _lastSeen!);

  /// The stop pagination applies in catch-up mode, or null when the mode is off
  /// or there is no recorded position yet — in which case the feed pages as it
  /// always did rather than hiding posts on a guess.
  SeenChainPredicate? _catchUpPredicate() {
    if (!mounted || _lastSeen == null || !_catchUpEnabled) {
      return null;
    }
    return _isSeen;
  }

  /// The reader scrolled to the end of what was new. This is the only place
  /// catch-up mode moves the read position: everything above the card has been
  /// on screen, which a scroll back to the top does not prove.
  void _recordCaughtUp() {
    final items = _feedController.items;
    if (items == null || items.isEmpty) {
      return;
    }
    _recordNewestOf(items);
  }

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
    _feedController.catchUpPredicateProvider = _catchUpPredicate;
    // Cached (pop/push-restored) controllers already hold their tweets; only a
    // fresh controller needs the preview while it loads the first page.
    _cachedPreview = widget.initialPreview;
    _cachedPreviewAt = widget.initialPreviewCachedAt;
    // The screen above may already have read and decoded the cached chunks for
    // us; doing it again here decoded the same rows a second time, on the UI
    // isolate, in the frames the reader is waiting on.
    if (!_feedController.hasItems && (widget.initialPreview?.isEmpty ?? true)) {
      _loadPreview();
    }
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        _loadPluginPosts();
      }
    });
  }

  Future<void> _loadPreview() async {
    var repository = await Repository.readOnly();
    var stored = await readCachedChainsForHashes(
      repository,
      widget.chunks.map((e) => e.hash),
    );
    var cached = filterHiddenRetweets(
      stored.chains,
      await hiddenRetweetScreenNames(),
    );
    cached = filterHiddenReplies(cached, await hiddenReplyScreenNames());
    if (!mounted) return;
    setState(() {
      _cachedPreview = cached;
      _cachedPreviewAt = stored.cachedAt;
    });
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
    readFeedReadPosition(feedReadPositionKey(widget.group.id)).then((position) {
      if (!mounted) {
        return;
      }
      setState(() {
        _lastSeen = position;
        _readPositionReady = true;
      });
      // Only the fresh first page waiting in [_pendingFirstPage] — never the
      // session-cached controller items, which can be yesterday's load and
      // would lock caught-up restore onto the wrong boundary.
      final pending = _pendingFirstPage;
      _pendingFirstPage = null;
      if (pending != null && pending.isNotEmpty) {
        _onFirstPageLoaded(pending);
      }
    });
  }

  bool _onScrollNotification(ScrollNotification notification) {
    // Any user-driven scroll cancels an in-flight caught-up restore, so it
    // never yanks the list out from under the reader.
    if (notification is UserScrollNotification &&
        notification.direction != ScrollDirection.idle) {
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
    // Catch-up mode does not take that bet — being at the top says nothing
    // about what was read, and there the position is written only on reaching
    // the end of the new posts.
    if (metrics.hasPixels &&
        metrics.pixels <= feedReadPositionTopThresholdPx &&
        _heldRefresh.returnedToTop()) {
      _applyChunkRefresh();
    }
    if (_tracksReadPosition &&
        !_catchUpEnabled &&
        metrics.hasPixels &&
        metrics.pixels <= feedReadPositionTopThresholdPx) {
      final items = _feedController.items;
      if (items != null && items.isNotEmpty) {
        _recordNewestOf(items);
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

  /// Last known answer while NestedScrollView briefly has 0/2 positions.
  var _lastKnownAtTop = true;

  bool get _atTop {
    final position = _scrollPosition;
    final atTop = feedRefreshAtTop(
      pixels: position?.pixels,
      lastKnownAtTop: _lastKnownAtTop,
    );
    if (position != null) {
      _lastKnownAtTop = atTop;
    }
    return atTop;
  }

  /// Records the newest recordable chain of [chains], when there is one.
  void _recordNewestOf(List<TweetChain> chains) {
    final newest = newestRecordableChain(chains);
    if (newest != null) {
      _recordReadPosition(newest);
    }
  }

  void _recordReadPosition(TweetChain newest) {
    if (newest.id == _lastRecordedChainId) {
      return;
    }
    _lastRecordedChainId = newest.id;
    // Fire-and-forget: a failed position save must never surface as an
    // unhandled async error.
    writeFeedReadPosition(
      feedReadPositionKey(widget.group.id),
      newest,
    ).catchError((_) {});
  }

  // Called with each finalized first page. The first one decides between
  // restoring the caught-up position (there are unread posts above it) and
  // recording; later ones (soft refreshes) record only while at the top, so
  // an app-bar refresh fired mid-scroll can't mark unseen posts as read.
  void _onFirstPageLoaded(List<TweetChain> threads) {
    final action = firstPageAction(
      chains: threads,
      lastSeen: _lastSeen,
      caughtUpAlreadyEvaluated: _caughtUpRestoreEvaluated,
      sessionOffset: _usesCache ? _cache!.readOffset(widget.cacheKey!) : null,
      atTop: _atTop,
    );
    _caughtUpRestoreEvaluated = true;

    switch (action) {
      case RestoreToBoundary(:final index):
        _scheduleCaughtUpRestore(index, threads.length);
      case RecordPosition(:final chain):
        _recordReadPosition(chain);
      case DoNothing():
        break;
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
      if (!mounted ||
          _userHasScrolled ||
          attempts >= maxCaughtUpRestoreFrames) {
        return;
      }
      final position = _scrollPosition;
      // Wait until the real list (not the preview) is mounted and laid out.
      if (position == null ||
          !position.haveDimensions ||
          !_feedController.hasItems) {
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
      final estimated = (position.maxScrollExtent * index / itemCount).clamp(
        0.0,
        position.maxScrollExtent,
      );
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
    _chunkRefreshDebounce?.cancel();
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

    // A group's members are not known when the feed is first built: loading the
    // group is a round trip to the database, and adding a subreddit re-emits it
    // again afterwards. Fetching them only in initState therefore asked for the
    // posts of an empty list and never asked again — which is why a group with
    // a subreddit in it stayed empty of Reddit posts however long you waited.
    unawaited(
      _reloadPluginSources(
        sourcesNeedingReload(
          before: oldWidget.pluginMembers,
          after: widget.pluginMembers,
        ),
      ),
    );

    if (oldWidget.includeReplies != widget.includeReplies ||
        oldWidget.includeRetweets != widget.includeRetweets ||
        oldWidget.group.popular != widget.group.popular ||
        oldWidget.group.custom != widget.group.custom ||
        feedRulesOf(oldWidget.group).cacheKey !=
            feedRulesOf(widget.group).cacheKey ||
        !_chunksMatch(oldWidget.chunks, widget.chunks)) {
      // Subscribe/unsubscribe (and filter toggles) rebuild chunks and used to
      // refresh immediately — with large sets that re-fired every search at
      // once and rate-limited the feed (#170). Debounce into one refresh.
      _scheduleChunkRefresh();
    }
  }

  void _scheduleChunkRefresh() {
    _chunkRefreshDebounce?.cancel();
    _chunkRefreshDebounce = Timer(feedChunkRefreshDebounce, () {
      if (!mounted) {
        return;
      }
      // Held while the reader is scrolled down: refetching empties the list and
      // returns it to the top, which is not what adding somebody to a group
      // should cost you. It runs when they next come back up.
      if (_heldRefresh.request(atTop: _atTop)) {
        _applyChunkRefresh();
      }
    });
  }

  void _applyChunkRefresh() {
    _feedController.controller.refresh();
    _mediaPaging?.pagingController.refresh();
  }

  final _heldRefresh = HeldRefresh();

  bool _chunksMatch(
    List<SubscriptionGroupFeedChunk> a,
    List<SubscriptionGroupFeedChunk> b,
  ) {
    if (a.length != b.length) return false;
    for (var i = 0; i < a.length; i++) {
      if (a[i].hash != b[i].hash) return false;
    }
    return true;
  }

  Future<String> createCursor(Database repository) async {
    return (await repository.insert(
      tableFeedGroupCursor,
      {},
      nullColumnHack: 'id',
    )).toString();
  }

  bool feedContainsUnrelatedTweets(
    TweetStatus tweets,
    List<Subscription> users,
  ) {
    final screenNames = users.map((e) => e.screenName).toSet();
    return tweets.chains.any(
      (chain) => chain.tweets.any(
        (tweet) =>
            tweet.user != null && !screenNames.contains(tweet.user!.screenName),
      ),
    );
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
              SizedBox(
                height: Theme.of(context).textTheme.bodyMedium!.fontSize! * 2,
              ),
              PrefCheckbox(
                title: Text(
                  L10n.of(context).never_show_again,
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
                pref: optionDisableWarningsForUnrelatedPostsInFeed,
              ),
            ],
          ),
          actions: [
            TextButton(
              child: Text(L10n.of(context).more_info),
              onPressed: () async {
                await openUri(
                  context,
                  "https://github.com/Teskann/XTA/issues/26",
                );
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
      },
    );
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

  /// Where a chunk's page starts: the stored chains to show under it (first
  /// page only) and the cursor the fresh search continues from.
  Future<TweetPageResult> _listTweets(String? cursorKey) async {
    var repository = await Repository.writable();
    var nextCursor = await createCursor(repository);
    bool shouldShowUnrelatedPostsInFeedWarning = false;

    // Cap in-flight chunk searches — unbounded Future.wait was the #165 failure
    // mode for 1000+ subscriptions (60+ concurrent searches → 404 cascade).
    final chunkResults = await mapWithConcurrency(widget.chunks, feedChunkFetchConcurrency, (
      chunk,
    ) async {
      var hash = chunk.hash;
      var tweets = <TweetChain>[];

      String? searchCursor;
      BigInt? storedNewestId;

      if (cursorKey == null) {
        // We're loading the initial content for the feed screen, so load all the chunks we already have
        var storedChunks = await repository.query(
          tableFeedGroupChunk,
          where: 'hash = ?',
          whereArgs: [hash],
          orderBy: 'created_at DESC',
          limit: maxCachedChunkRows,
        );

        // Make sure we load any existing stored tweets from the chunk
        tweets.addAll(await chainsFromStoredChunksAsync(storedChunks));
        storedNewestId = newestTweetIdOf(tweets);

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
        var storedChunks = await repository.query(
          tableFeedGroupChunk,
          where: 'cursor_id = ? AND hash = ?',
          whereArgs: [int.parse(cursorKey), hash],
        );
        if (storedChunks.isNotEmpty) {
          searchCursor = storedChunks.first['cursor_bottom'] as String;
        } else {
          searchCursor = null;
        }
      }

      // Perform our search for the next page of results for this chunk, and add those tweets to our collection
      var query = _buildSearchQuery(chunk.users);
      TweetStatus result = await Twitter.searchTweets(
        query,
        widget.includeReplies,
        cursor: searchCursor,
      );
      shouldShowUnrelatedPostsInFeedWarning |= feedContainsUnrelatedTweets(
        result,
        chunk.users,
      );

      if (result.chains.isNotEmpty) {
        tweets.addAll(result.chains);

        // Make sure we insert the set of cursors for this latest chunk, ready for the next time we paginate
        await repository.insert(tableFeedGroupChunk, {
          'cursor_id': int.parse(nextCursor),
          'hash': hash,
          'cursor_top': result.cursorTop,
          'cursor_bottom': result.cursorBottom,
          'response': await encodeChunkBlob(
            result.chains.map((e) => e.toJson()).toList(),
          ),
        });
      }

      // A single fetch returns only the newest page, so a long absence
      // leaves a hole between it and the stored posts. Keep paging down
      // until the fresh content overlaps what was stored (bounded, so a
      // week away can't trigger dozens of requests).
      var page = result;
      var gapFills = 0;
      while (shouldContinueGapFill(
        storedNewestId: storedNewestId,
        oldestFetchedId: oldestTweetIdOf(page.chains),
        pageNonEmpty: page.chains.isNotEmpty,
        hasCursor: page.cursorBottom != null,
        gapFillsSoFar: gapFills,
      )) {
        page = await Twitter.searchTweets(
          query,
          widget.includeReplies,
          cursor: page.cursorBottom,
        );
        gapFills++;

        if (page.chains.isNotEmpty) {
          tweets.addAll(page.chains);
          await repository.insert(tableFeedGroupChunk, {
            'cursor_id': int.parse(nextCursor),
            'hash': hash,
            'cursor_top': page.cursorTop,
            'cursor_bottom': page.cursorBottom,
            'response': await encodeChunkBlob(
              page.chains.map((e) => e.toJson()).toList(),
            ),
          });
        }
      }

      // Whether the hole between the fresh posts and the stored ones was still
      // open when the allowance ran out. The catch-up card must not say the
      // reader is finished when posts in between were never loaded.
      final gapCapped = shouldContinueGapFill(
        storedNewestId: storedNewestId,
        oldestFetchedId: oldestTweetIdOf(page.chains),
        pageNonEmpty: page.chains.isNotEmpty,
        hasCursor: page.cursorBottom != null,
        gapFillsSoFar: 0,
      );

      return (chains: tweets, gapCapped: gapCapped);
    });

    // The stored chunks and the fresh fetch overlap at their window boundaries,
    // so drop repeated chains before display.
    var threads = _sortChains(
      dedupeChainsById(chunkResults.expand((e) => e.chains).toList()),
    );
    threads = filterHiddenRetweets(threads, await hiddenRetweetScreenNames());
    threads = filterHiddenReplies(threads, await hiddenReplyScreenNames());
    final rulesOutcome = applyCustomFeedRules(
      threads,
      feedRulesOf(widget.group),
    );
    threads = rulesOutcome.chains;

    final caps = <String, int>{};
    for (final sub
        in widget.group.subscriptions.whereType<UserSubscription>()) {
      final max = sub.maxPostsPerLoad;
      if (max != null && max > 0) {
        caps[sub.id] = max;
      }
    }
    threads = capChainsPerAuthor(threads, caps);

    if (!mounted) {
      // Keep what we fetched — an empty page with a null cursor would make
      // pagination think the feed is finished.
      return (chains: threads, nextCursor: nextCursor);
    }

    final prefs = PrefService.of(context, listen: false);
    final languageOutcome = applyLanguageFilter(
      threads,
      allowedLanguages: parseFeedLanguages(
        prefs.get(optionFeedLanguages) as String?,
      ),
      action: parseLanguageFilterAction(
        prefs.get(optionFeedLanguageAction) as String?,
      ),
      priorFolds: rulesOutcome.foldReasons,
    );
    threads = languageOutcome.chains;
    // Paging already rebuilds the list when this page returns; a setState
    // here was a second rebuild of the same frame's work.
    _foldReasons = {..._foldReasons, ...languageOutcome.foldReasons};

    if (prefs.get(optionZenMode) == true) {
      threads = _applyZenMode(threads);
    }

    if (shouldShowUnrelatedPostsInFeedWarning &&
        !PrefService.of(
          context,
          listen: false,
        ).get(optionDisableWarningsForUnrelatedPostsInFeed)) {
      await showUnrelatedPostsInFeedWarning();
    }

    if (cursorKey == null) {
      _gapCapped = chunkResults.any((e) => e.gapCapped);
      // Catch-up mode neither restores to the divider (the page it is about to
      // show *is* the new posts) nor records anything here.
      if (_tracksReadPosition && !_catchUpEnabled) {
        if (_readPositionReady) {
          _onFirstPageLoaded(threads);
        } else {
          _pendingFirstPage = threads;
        }
      }
    }

    return (chains: threads, nextCursor: nextCursor);
  }

  static int _likesOf(TweetChain chain) =>
      chain.tweets.firstOrNull?.favoriteCount ?? 0;

  /// Popular groups order the same recent window by likes; recent ones (the
  /// default) by date.
  List<TweetChain> _sortChains(List<TweetChain> chains) {
    if (!widget.group.popular) {
      return sortChainsNewestFirst(chains);
    }
    return chains.sorted((a, b) => _likesOf(b).compareTo(_likesOf(a))).toList();
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
  Future<CursorPage<String, MediaGridItem>> _loadMediaPage(
    String? cursor,
  ) async {
    if (cursor == null) {
      _seenMediaKeys.clear();
    }

    // A profile's lookahead costs one request per page; here every page is the
    // whole per-chunk fan-out, so the default of four turns one screenful of
    // thumbnails into five fan-outs. A media-sparse group shows an emptier
    // first grid in exchange, and fills as the reader scrolls.
    return mediaPageWithLookahead(
      cursor,
      _listTweets,
      _unseenMediaItems,
      maxLookahead: 1,
    );
  }

  // Successive search windows overlap at their boundaries, so keep only media
  // entries not shown on an earlier page.
  List<MediaGridItem> _unseenMediaItems(List<TweetChain> chains) {
    return mediaItemsFromChains(
      chains,
    ).where((m) => _seenMediaKeys.add('${m.tweetId}/${m.mediaIndex}')).toList();
  }

  Widget _buildMediaGrid(BuildContext context) {
    return Scaffold(
      body: TweetContextScope(
        child: MediaGrid(
          controller: _mediaController.pagingController,
          firstPageErrorPrefix: L10n.of(
            context,
          ).unable_to_load_the_tweets_for_the_feed,
          newPageErrorPrefix: L10n.of(
            context,
          ).unable_to_load_the_next_page_of_tweets,
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
    if (widget.chunks.isEmpty && widget.pluginMembers.isEmpty) {
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
            firstPagePreviewCachedAt: _cachedPreviewAt,
            foldReasons: _foldReasons,
            onCaughtUp: _catchUpEnabled ? _recordCaughtUp : null,
            catchUpMayBeIncomplete: () => _gapCapped,
            onRefresh: () async {
              // Not awaited: the reader is waiting on X's first page, and
              // the plugins are beside it rather than in front of it.
              unawaited(_loadPluginPosts());
              // Only this group's rows. The wipe used to take the whole table
              // with it, so pulling to refresh one feed made every other feed
              // refetch its first page from the network next time it opened.
              final hashes = widget.chunks.map((e) => e.hash).toList();
              if (hashes.isEmpty) {
                return;
              }

              var repository = await Repository.writable();
              await repository.delete(
                tableFeedGroupChunk,
                where:
                    'hash IN (${List.filled(hashes.length, '?').join(', ')})',
                whereArgs: hashes,
              );
            },
            firstPageErrorPrefix: L10n.of(
              context,
            ).unable_to_load_the_tweets_for_the_feed,
            newPageErrorPrefix: L10n.of(
              context,
            ).unable_to_load_the_next_page_of_tweets,
            emptyMessage: L10n.of(
              context,
            ).could_not_find_any_tweets_from_the_last_7_days,
            isSeen: _tracksReadPosition && _lastSeen != null ? _isSeen : null,
            caughtUpDividerKey: _caughtUpKey,
            interleaved: _interleaved,
          ),
        ),
      ),
    );
  }
}
