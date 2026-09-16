import 'package:xta/utils/read_visibility.dart';
import 'package:xta/utils/read_recovery.dart';
import 'package:xta/ui/reader_failure.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/group/batch_read_store.dart';
import 'package:xta/utils/read_request_scope.dart';
import 'package:xta/utils/read_activity.dart';
import 'package:xta/tweet/progressive_feed_store.dart';
import 'package:xta/tweet/progressive_feed_view.dart';
import 'package:xta/plugins/plugin.dart';
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
import 'package:xta/group/group_screen.dart';
import 'package:xta/group/language_filter.dart';
import 'package:xta/profile/media_grid/media_grid.dart';
import 'package:xta/profile/media_grid/media_grid_items/media_grid_item.dart';
import 'package:xta/profile/profile_feed_settings.dart';
import 'package:xta/plugins/subscription_source.dart';
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
import 'package:xta/group/feed_search_fallback.dart';
import 'package:xta/group/group_media_page.dart';

/// One chunk's contribution to a feed page: its chains, whether its gap-fill
/// ran out of allowance, and whether X answered with posts from outside the
/// chunk's own subscriptions.
/// Max in-flight X search requests for feed chunks. Large subscription sets
/// (1000+ → 60+ chunks) used to open every search at once via [Future.wait],
/// which cascaded into 404s and flagged accounts (#165 / #170).
typedef GroupBatchResult = ({List<TweetChain> chains, bool gapCapped, Object? error});

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
  final _firstPage = SharedAsyncLoad<TweetPageResult>();

  final _batches = BatchReadStore<GroupBatchResult>();
  final _networkReads = ReadRequestScope();
  List<TweetChain> _progressivePreview = [];
  bool _retryFailedBatches = false;
  bool _retryingBatches = false;
  String? _batchCursor;
  bool _resumeWhenVisible = false;
  bool _screenVisible = true;

  void _suspendReads() {
    _screenVisible = false;
    _resumeWhenVisible |=
        !_feedController.hasItems ||
        _batches.state.loading ||
        _feedController.controller.value.isLoading ||
        (_mediaPaging?.pagingController.value.isLoading ?? false);
    _cancelBatches();
    _feedController.controller.cancel();
    _mediaPaging?.cancel();
  }

  void _resumeReads() {
    _screenVisible = true;
    if (!_resumeWhenVisible) return;
    _resumeWhenVisible = false;
    if (widget.mediaOnly) {
      _mediaController.pagingController.fetchNextPage();
    } else {
      unawaited(_retryBatches());
    }
  }

  Future<void> _retryBatches() async {
    if (_batches.state.loading || _retryingBatches) return;
    _retryingBatches = true;
    _retryFailedBatches = true;
    try {
      await _feedController.repairFirstPage();
    } finally {
      _retryingBatches = false;
    }
  }

  void _cancelBatches() {
    _batches.cancel();
    _networkReads.cancel();
    _firstPage.cancel();
  }

  bool get _usesCache => widget.cacheKey != null;

  /// Posts loaded for each plugin source in this group, newest first.
  ///
  /// A source pages on its own terms — Substack by offset, X by cursor — so
  /// these cannot share one paginator with the X side. They are fetched once per
  /// mount and slotted among the chains by date.
  final _pluginFeed = ProgressiveFeedStore();
  Future<void> _loadPluginPosts() async {
    if (!mounted) return;
    final loaders = <String, SourceLoader>{};
    final keys = <String, String>{};
    final prefs = PrefService.of(context, listen: false);
    for (final source in enabledSubscriptionSources(prefs)) {
      final id = (source as XtaPlugin).id;
      final combined = widget.group.id == legacyFeedKeyFollowing;
      final includeHome = combined && source.inHomeFeed(context);
      final ids = sourceIdsFor(
        memberIds: widget.pluginMembers[source]?.map((entry) => entry.id).toList() ?? const [],
        isCombinedFeed: combined,
        inHomeFeed: includeHome,
        homeFeedIds: includeHome ? source.homeFeedIds(context) : const [],
      );
      if (ids.isEmpty) continue;
      keys[id] = _pluginFeed.cache.key(id, ids);
      loaders[id] = () => source.interleavedPosts(context, ids);
    }
    await _pluginFeed.load(loaders, keys);
  }

  Future<void> _reloadPluginSources(Iterable<SubscriptionSource> sources) => _loadPluginPosts();

  // Chronological feeds only: in popular order a "seen up to" boundary is
  // meaningless, and the media grid shares this loader but shows no divider.
  bool get _supportsReadPosition => !widget.group.popular && !widget.mediaOnly;

  /// Catch-up mode: this feed shows only what is new since the reader's last
  /// position and stops there. Per feed, off unless turned on for this one.
  bool get _catchUpEnabled =>
      _supportsReadPosition && PrefService.of(context, listen: false).get(feedCatchUpModeKey(widget.group.id)) == true;

  bool get _tracksReadPosition =>
      _supportsReadPosition &&
      (PrefService.of(context, listen: false).get(optionFeedReadingPosition) == true || _catchUpEnabled);

  bool _isSeen(TweetChain chain) => _lastSeen != null && isChainSeen(chain, _lastSeen!);

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
    try {
      var repository = await Repository.readOnly();
      var stored = await readCachedChainsForHashes(repository, widget.chunks.map((e) => e.hash));
      var cached = filterHiddenRetweets(stored.chains, await hiddenRetweetScreenNames());
      cached = filterHiddenReplies(cached, await hiddenReplyScreenNames());
      if (!mounted) return;
      setState(() {
        _cachedPreview = cached;
        _cachedPreviewAt = stored.cachedAt;
      });
    } catch (_) {
      // Corrupt or isolate-failed cache must not abort the first feed frame.
    }
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
    if (notification.depth == 0 && notification.metrics.axis == Axis.vertical) {
      _pluginFeed.setReadingAway(notification.metrics.pixels > feedReadPositionTopThresholdPx);
    }
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
    // Catch-up mode does not take that bet — being at the top says nothing
    // about what was read, and there the position is written only on reaching
    // the end of the new posts.
    if (metrics.hasPixels && metrics.pixels <= feedReadPositionTopThresholdPx && _heldRefresh.returnedToTop()) {
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
    final atTop = feedRefreshAtTop(pixels: position?.pixels, lastKnownAtTop: _lastKnownAtTop);
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
    writeFeedReadPosition(feedReadPositionKey(widget.group.id), newest).catchError((_) {});
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
  void _scheduleRestore(double offset, [int attempts = 0]) {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final position = _scrollPosition;
      final ready = position != null && position.haveDimensions;
      if (!ready) {
        if (shouldRetryScrollRestore(mounted: mounted, positionReady: ready, attempts: attempts)) {
          _scheduleRestore(offset, attempts + 1);
        }
        return;
      }
      position.jumpTo(offset.clamp(0.0, position.maxScrollExtent));
    });
  }

  @override
  void dispose() {
    _cancelBatches();
    _batches.destroy();
    if (_usesCache) _feedController.controller.cancel();
    _pluginFeed.destroy();
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
    unawaited(_reloadPluginSources(sourcesNeedingReload(before: oldWidget.pluginMembers, after: widget.pluginMembers)));

    if (oldWidget.includeReplies != widget.includeReplies ||
        oldWidget.includeRetweets != widget.includeRetweets ||
        oldWidget.group.popular != widget.group.popular ||
        oldWidget.group.custom != widget.group.custom ||
        feedRulesOf(oldWidget.group).cacheKey != feedRulesOf(widget.group).cacheKey ||
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
    _cancelBatches();
    _batches.reset();
    _batchCursor = null;
    if (!_screenVisible) _resumeWhenVisible = true;
    _progressivePreview = [];
    _feedController.controller.refresh();
    _mediaPaging?.pagingController.refresh();
  }

  /// The tweet list and the image tab share one first-page Search. Opening
  /// the grid used to start a second per-chunk fan-out and 429 the endpoint.
  Future<TweetPageResult> _listTweetsShared(String? cursor) {
    if (cursor != null) {
      return _listTweets(cursor);
    }
    return _firstPage.load(() => _listTweets(null));
  }

  final _heldRefresh = HeldRefresh();

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
      (chain) => chain.tweets.any((tweet) => tweet.user != null && !screenNames.contains(tweet.user!.screenName)),
    );
  }

  Future<void> showUnrelatedPostsInFeedWarning() async {
    await showDialog(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: Row(
            children: [
              Icon(Icons.warning_amber_rounded, color: Theme.of(context).colorScheme.error),
              const SizedBox(width: 12),
              Expanded(child: Text(L10n.of(context).feed_issue_detected)),
            ],
          ),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(L10n.of(context).feed_contains_unrelated_tweets),
              SizedBox(height: Theme.of(context).textTheme.bodyMedium!.fontSize! * 2),
              PrefCheckbox(
                title: Text(L10n.of(context).never_show_again, style: Theme.of(context).textTheme.bodyMedium),
                pref: optionDisableWarningsForUnrelatedPostsInFeed,
              ),
            ],
          ),
          actions: [
            TextButton(
              child: Text(L10n.of(context).more_info),
              onPressed: () async {
                await openUri(context, "https://github.com/Teskann/XTA/issues/26");
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

  /// Profiles still load while SearchTimeline is exhausted. One page per
  /// member, capped, so a 39-abo group does not open 39 UserTweets at once.
  Future<List<TweetChain>> _fallbackUserTimelines(List<Subscription> users) {
    return fetchUserTimelines(
      users: users,
      getTweets: (user) async {
        ReadWork.checkpoint();
        final status = await Twitter.getTweets(
          user.id,
          'profile',
          const <String>[],
          count: 20,
          includeReplies: widget.includeReplies,
          includeRetweets: widget.includeRetweets,
          getTweetsCounter: () => 0,
          incrementTweetsCounter: () {},
        );
        return dropRetweetsIfNeeded(status.chains, widget.includeRetweets);
      },
    );
  }

  /// Where a chunk's page starts: the stored chains to show under it (first
  /// page only) and the cursor the fresh search continues from.
  Future<TweetPageResult> _listTweets(String? cursorKey) async {
    if (!_screenVisible) throw const ReadCancelled();
    var repository = await Repository.writable();
    final retry = cursorKey == null && _retryFailedBatches;
    _retryFailedBatches = false;
    var nextCursor = retry && _batchCursor != null ? _batchCursor! : await createCursor(repository);
    if (cursorKey == null) _batchCursor = nextCursor;
    final hiddenRetweets = await hiddenRetweetScreenNames();
    final hiddenReplies = await hiddenReplyScreenNames();
    ReadWork.checkpoint();
    if (!mounted) throw const ReadCancelled();
    if (!retry && cursorKey == null) _progressivePreview = [];
    List<TweetChain> prepare(List<GroupBatchResult> results) {
      var threads = _sortChains(dedupeChainsById(results.expand((e) => e.chains).toList()));
      threads = filterHiddenRetweets(threads, hiddenRetweets);
      threads = filterHiddenReplies(threads, hiddenReplies);
      return _filterBatchChains(threads);
    }

    bool shouldShowUnrelatedPostsInFeedWarning = false;

    // Cap in-flight chunk searches — unbounded Future.wait was the #165 failure
    // mode for 1000+ subscriptions (60+ concurrent searches → 404 cascade).
    final chunks = {for (final chunk in widget.chunks) chunk.hash: chunk};
    final pageBatches = cursorKey == null ? _batches : BatchReadStore<GroupBatchResult>();
    final chunkResults = await pageBatches.load(
      chunks.keys,
      (key) async {
        final chunk = chunks[key]!;
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
            searchCursor = searchCursorFromStored(latestChunk['cursor_top']);
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
            searchCursor = searchCursorFromStored(storedChunks.first['cursor_bottom']);
          } else {
            searchCursor = null;
          }
        }

        // SearchTimeline is a different rate-limit bucket from UserTweets. A
        // throw here used to abort every other chunk and replace the feed with
        // the hourglass, even when profiles still loaded.
        var query = _buildSearchQuery(chunk.users);
        ReadWork.checkpoint();
        final network = await fetchChunkWithFallback(
          search: () => _networkReads.start(
            () => Twitter.searchTweets(query, widget.includeReplies, cursor: searchCursor),
            timeout: const Duration(seconds: 20),
            operation: ReadOperation.groupSearch,
          ),
          userTimelines: () => _networkReads.start(
            () => _fallbackUserTimelines(chunk.users),
            timeout: const Duration(seconds: 12),
            operation: ReadOperation.groupFallback,
          ),
        );

        ReadWork.checkpoint();
        var searchPage = network.search;
        if (searchPage != null) {
          shouldShowUnrelatedPostsInFeedWarning |= feedContainsUnrelatedTweets(searchPage, chunk.users);
        }

        final fresh = searchPage?.chains ?? network.fallbackChains;
        if (fresh.isNotEmpty) {
          tweets.addAll(fresh);

          // Fallback rows store null cursors: UserTweets tokens are not
          // SearchTimeline tokens, and mixing them poisons the next search.
          await repository.insert(tableFeedGroupChunk, {
            'cursor_id': int.parse(nextCursor),
            'hash': hash,
            'cursor_top': searchPage?.cursorTop,
            'cursor_bottom': searchPage?.cursorBottom,
            'response': await encodeChunkBlob(fresh.map((e) => e.toJson()).toList()),
          });
        }

        // A single fetch returns only the newest page, so a long absence
        // leaves a hole between it and the stored posts. Keep paging down
        // until the fresh content overlaps what was stored (bounded, so a
        // week away can't trigger dozens of requests). Skip after fallback:
        // those cursors belong to a different endpoint.
        var gapFills = 0;
        if (searchPage != null &&
            !shouldSkipGapFill(usedFallback: network.usedFallback, searchFailed: network.searchFailed)) {
          var page = searchPage;
          try {
            while (shouldContinueGapFill(
              storedNewestId: storedNewestId,
              oldestFetchedId: oldestTweetIdOf(page.chains),
              pageNonEmpty: page.chains.isNotEmpty,
              hasCursor: page.cursorBottom != null,
              gapFillsSoFar: gapFills,
            )) {
              ReadWork.checkpoint();
              final bottom = page.cursorBottom;
              page = await _networkReads.start(
                () => Twitter.searchTweets(query, widget.includeReplies, cursor: bottom),
                timeout: const Duration(seconds: 15),
                operation: ReadOperation.groupGap,
              );
              ReadWork.checkpoint();
              gapFills++;

              if (page.chains.isNotEmpty) {
                tweets.addAll(page.chains);
                await repository.insert(tableFeedGroupChunk, {
                  'cursor_id': int.parse(nextCursor),
                  'hash': hash,
                  'cursor_top': page.cursorTop,
                  'cursor_bottom': page.cursorBottom,
                  'response': await encodeChunkBlob(page.chains.map((e) => e.toJson()).toList()),
                });
              }
            }
          } catch (_) {
            // A later gap-fill 429 must not discard the page we already have.
          }
          searchPage = page;
        }

        // Whether the hole between the fresh posts and the stored ones was still
        // open when the allowance ran out. The catch-up card must not say the
        // reader is finished when posts in between were never loaded.
        final gapCapped =
            searchPage != null &&
            shouldContinueGapFill(
              storedNewestId: storedNewestId,
              oldestFetchedId: oldestTweetIdOf(searchPage.chains),
              pageNonEmpty: searchPage.chains.isNotEmpty,
              hasCursor: searchPage.cursorBottom != null,
              gapFillsSoFar: 0,
            );

        ReadWork.checkpoint();
        return (chains: tweets, gapCapped: gapCapped, error: network.error);
      },
      retryFailed: retry,
      concurrency: feedChunkFetchConcurrency,
      onError: (error) => (chains: <TweetChain>[], gapCapped: true, error: error),
      failed: (result) => result.error != null,
      onProgress: (results) {
        if (!mounted || cursorKey != null || _feedController.hasItems || _catchUpEnabled) return;
        final next = prepare(results);
        if (_userHasScrolled && _progressivePreview.isNotEmpty) {
          final known = _progressivePreview.map((e) => e.id).toSet();
          _progressivePreview = [..._progressivePreview, ...next.where((e) => known.add(e.id))];
        } else {
          _progressivePreview = next;
        }
      },
    );
    if (cursorKey != null) await pageBatches.destroy();
    ReadWork.checkpoint();

    if (!chunkResults.any((e) => e.chains.isNotEmpty)) {
      final error = feedErrorToRethrow([
        for (final e in chunkResults)
          if (e.error != null) e.error!,
      ]);
      if (error != null) {
        throw error;
      }
    }

    var threads = prepare(chunkResults);
    if (cursorKey == null && !_catchUpEnabled && _userHasScrolled && _progressivePreview.isNotEmpty) {
      final byId = {for (final chain in threads) chain.id: chain};
      threads = [
        for (final chain in _progressivePreview)
          if (byId.containsKey(chain.id)) byId.remove(chain.id)!,
        ...byId.values,
      ];
    }

    if (!mounted) throw const ReadCancelled();
    if (shouldShowUnrelatedPostsInFeedWarning &&
        !PrefService.of(context, listen: false).get(optionDisableWarningsForUnrelatedPostsInFeed)) {
      await showUnrelatedPostsInFeedWarning();
    }
    ReadWork.checkpoint();
    if (!mounted) throw const ReadCancelled();

    if (cursorKey == null) {
      _gapCapped = chunkResults.any((e) => e.gapCapped || e.error != null);
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

  List<TweetChain> _filterBatchChains(List<TweetChain> threads) {
    final rulesOutcome = applyCustomFeedRules(threads, feedRulesOf(widget.group));
    threads = rulesOutcome.chains;

    final caps = <String, int>{};
    for (final sub in widget.group.subscriptions.whereType<UserSubscription>()) {
      final max = sub.maxPostsPerLoad;
      if (max != null && max > 0) {
        caps[sub.id] = max;
      }
    }
    threads = capChainsPerAuthor(threads, caps);

    final prefs = PrefService.of(context, listen: false);
    final languageOutcome = applyLanguageFilter(
      threads,
      allowedLanguages: parseFeedLanguages(prefs.get(optionFeedLanguages) as String?),
      action: parseLanguageFilterAction(prefs.get(optionFeedLanguageAction) as String?),
      priorFolds: rulesOutcome.foldReasons,
    );
    threads = languageOutcome.chains;
    // Paging already rebuilds the list when this page returns; a setState
    // here was a second rebuild of the same frame's work.
    _foldReasons = {..._foldReasons, ...languageOutcome.foldReasons};

    if (prefs.get(optionZenMode) == true) {
      threads = _applyZenMode(threads);
    }

    return threads;
  }

  static int _likesOf(TweetChain chain) => chain.tweets.firstOrNull?.favoriteCount ?? 0;

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

  /// Loads a page for the media grid: tweets already on the list first, then
  /// the same pages as the tweet list, mapped to their media entries.
  Future<CursorPage<String, MediaGridItem>> _loadMediaPage(String? cursor) async {
    if (cursor == null) {
      _seenMediaKeys.clear();
    }

    return groupMediaPage(
      cursor: cursor,
      loadedChains: _feedController.items,
      previewChains: _cachedPreview,
      feedNextCursor: _feedController.nextCursor,
      fetch: _listTweetsShared,
      itemsOf: _unseenMediaItems,
    );
  }

  // Successive search windows overlap at their boundaries, so keep only media
  // entries not shown on an earlier page.
  List<MediaGridItem> _unseenMediaItems(List<TweetChain> chains) {
    return mediaItemsFromChains(chains).where((m) => _seenMediaKeys.add('${m.tweetId}/${m.mediaIndex}')).toList();
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
    if (widget.chunks.isEmpty && widget.pluginMembers.isEmpty) {
      return ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        children: [
          SizedBox(height: 240, child: Center(child: Text(L10n.of(context).this_group_contains_no_subscriptions))),
        ],
      );
    }

    if (widget.mediaOnly) {
      return ReadVisibility(onHidden: _suspendReads, onVisible: _resumeReads, child: _buildMediaGrid(context));
    }

    return Scaffold(
      body: ReadVisibility(
        onHidden: _suspendReads,
        onVisible: _resumeReads,
        child: TweetContextScope(
          child: NotificationListener<ScrollNotification>(
            onNotification: _onScrollNotification,
            child: ProgressiveFeedView(
              store: _pluginFeed,
              builder: (items) => ScopedBuilder<BatchReadStore<GroupBatchResult>, BatchReadState<GroupBatchResult>>(
                store: _batches,
                onState: (_, batches) => Column(
                  children: [
                    if (widget.chunks.isNotEmpty)
                      SizedBox(
                        height: 4,
                        child: batches.loading
                            ? LinearProgressIndicator(
                                value: batches.total == 0 ? null : batches.results.length / batches.total,
                              )
                            : null,
                      ),
                    if (widget.chunks.isNotEmpty)
                      ReadRecovery(
                        recoverableFailure: () => batches.loading
                            ? null
                            : batches.results.values
                                  .map((result) => recoverableReadFailure(result.error))
                                  .whereType<Object>()
                                  .firstOrNull,
                        retry: _retryBatches,
                        child: SizedBox(
                          height: 48,
                          child: Row(
                            children: [
                              Expanded(
                                child: Padding(
                                  padding: const EdgeInsets.all(8),
                                  child: Text(
                                    L10n.of(context).reader_batch_progress(batches.results.length, batches.total),
                                  ),
                                ),
                              ),
                              if (!batches.loading && batches.failed.isNotEmpty)
                                TextButton(onPressed: _retryBatches, child: Text(L10n.of(context).retry)),
                            ],
                          ),
                        ),
                      ),
                    Expanded(
                      child: PaginatedTweetList(
                        feed: _feedController,
                        loadPage: _listTweetsShared,
                        username: null,
                        firstPagePreview: _progressivePreview.isNotEmpty ? _progressivePreview : _cachedPreview,
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
                            where: 'hash IN (${List.filled(hashes.length, '?').join(', ')})',
                            whereArgs: hashes,
                          );
                        },
                        firstPageErrorPrefix: L10n.of(context).unable_to_load_the_tweets_for_the_feed,
                        newPageErrorPrefix: L10n.of(context).unable_to_load_the_next_page_of_tweets,
                        emptyMessage: L10n.of(context).could_not_find_any_tweets_from_the_last_7_days,
                        isSeen: _tracksReadPosition && _lastSeen != null ? _isSeen : null,
                        caughtUpDividerKey: _caughtUpKey,
                        interleaved: items,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
