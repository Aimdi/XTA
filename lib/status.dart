import 'package:flutter/material.dart';
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/database/timeline_cache.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/profile/profile.dart';
import 'package:xta/tweet/conversation.dart';
import 'package:xta/tweet/threaded_conversation.dart';
import 'package:xta/ui/errors.dart';
import 'package:infinite_scroll_pagination/infinite_scroll_pagination.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/utils/paging.dart';
import 'package:xta/utils/translation.dart';
import 'package:logging/logging.dart';
import 'package:scroll_to_index/scroll_to_index.dart';

/// Zen mode hides the replies under an opened post until the reader
/// deliberately reveals them by holding the comment button.
class ZenRepliesState extends ChangeNotifier {
  bool revealed = false;

  void reveal() {
    revealed = true;
    notifyListeners();
  }
}

class StatusScreenArguments {
  final String id;
  final String? username;
  final bool tweetOpened;
  final int initialMediaIndex;
  final TweetWithCard? initialTweet;

  StatusScreenArguments(
      {required this.id,
      required this.username,
      this.tweetOpened = false,
      this.initialMediaIndex = 0,
      this.initialTweet});

  @override
  String toString() {
    return 'StatusScreenArguments{id: $id, username: $username}';
  }
}

class StatusScreen extends StatelessWidget {
  const StatusScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final args = ModalRoute.of(context)!.settings.arguments as StatusScreenArguments;

    return _StatusScreen(
        username: args.username,
        id: args.id,
        tweetOpened: args.tweetOpened,
        initialMediaIndex: args.initialMediaIndex,
        initialTweet: args.initialTweet);
  }
}

class _StatusScreen extends StatefulWidget {
  final String? username;
  final String id;
  final bool tweetOpened;
  final int initialMediaIndex;
  final TweetWithCard? initialTweet;

  const _StatusScreen(
      {required this.username,
      required this.id,
      required this.tweetOpened,
      this.initialMediaIndex = 0,
      this.initialTweet});

  @override
  _StatusScreenState createState() => _StatusScreenState();
}

class _StatusScreenState extends State<_StatusScreen> {
  static final log = Logger('StatusScreen');

  late final CursorPagingController<String, TweetChain> _paging;
  PagingController<int, TweetChain> get _pagingController => _paging.pagingController;
  final _scrollController = AutoScrollController();

  final _seenAlready = <String>{};
  final _seenTweetIds = <String>{};
  bool _firstLoadStarted = false;

  /// Set once paging ends while X is still withholding replies behind its
  /// "Show additional replies" prompt. Held rather than followed: these are the
  /// replies X judged low quality, so asking for them is the reader's call.
  String? _showMoreCursor;

  /// First page came back with a reply count but neither visible replies nor a
  /// show-more cursor — usually a poisoned cache or a transient TweetDetail
  /// omit. Offer retry instead of looking "done".
  bool _repliesMissing = false;

  /// Skip the thread cache on the next first-page fetch (after the reader taps
  /// retry for missing replies).
  bool _bypassThreadCache = false;

  @override
  void initState() {
    super.initState();

    _paging = CursorPagingController<String, TweetChain>(_fetchPage);
    // While the instant preview is shown the PagedListView isn't mounted, so we
    // rebuild to swap it in as soon as the first page (or an error) arrives.
    _pagingController.addListener(_onControllerChanged);
  }

  @override
  void dispose() {
    _pagingController.removeListener(_onControllerChanged);
    _paging.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  void _onControllerChanged() {
    if (mounted) setState(() {});
  }

  bool get _showingPreview {
    final state = _pagingController.value;
    return widget.initialTweet != null && state.items == null && state.error == null;
  }

  // X sometimes returns an empty conversation for a post that plainly exists
  // (e.g. restricted content); with a preview in hand, keep showing the post
  // rather than replacing it with a "not found" message.
  bool get _conversationCameBackEmpty {
    final state = _pagingController.value;
    return widget.initialTweet != null &&
        (state.items?.isEmpty ?? false) &&
        state.error == null &&
        !state.hasNextPage;
  }

  void _maybeStartFirstLoad() {
    if (_firstLoadStarted) return;
    final state = _pagingController.value;
    if (state.items != null || state.error != null) return;
    _firstLoadStarted = true;
    // Deferred: we're called from build() and fetchNextPage() mutates the
    // controller synchronously, which would setState() mid-build via our listener.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _pagingController.fetchNextPage();
    });
  }

  void _scrollToFocalTweet(List<TweetChain> chains) {
    // In threaded mode the opened tweet (with its ancestors) is the first item,
    // so there is nothing to scroll to.
    if (mounted && PrefService.of(context, listen: false).get(optionThreadedReplies) == true) {
      return;
    }
    // Find the chain holding the opened tweet. Ancestors arrive as earlier
    // chains, so index 0 means there's nothing above it (a top-level tweet,
    // already at the top) — leave the view and highlight alone.
    final index = chains.indexWhere((c) => c.tweets.any((t) => t.idStr == widget.id));
    if (index <= 0) return;
    // Defer one frame: the instant preview is still on screen here; the
    // PagedListView (and its scroll controller) only mounts after the rebuild
    // triggered by the new items. scrollToIndex then handles lazy-list scrolling.
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted || !_scrollController.hasClients) return;
      await _scrollController.scrollToIndex(index, preferPosition: AutoScrollPosition.begin);
      await _scrollController.highlight(index);
    });
  }

  /// The first page of a thread, from cache when it is fresh enough.
  ///
  /// Re-opening a post is the commonest thing a reader does after scrolling,
  /// and it cost a TweetDetail request every time. On a failure the cached copy
  /// is used at any age: a thread the reader saw ten minutes ago beats an error
  /// screen when the network is down or every account is rate limited.
  Future<TweetStatus> _fetchFirstPage() async {
    final key = TimelineCache.threadKey(widget.id);
    final cache = TimelineCache(await Repository.writable());

    if (_bypassThreadCache) {
      _bypassThreadCache = false;
      await cache.remove(key);
    } else {
      // The thread screen has no pull-to-refresh, so nothing here has to bypass
      // the cache; re-entering the screen after the window expires re-fetches.
      final cached = await cache.read(key, maxAge: threadCacheMaxAge);
      if (cached != null) {
        return cached;
      }
    }

    try {
      final result = await Twitter.getTweet(widget.id);
      // A focal-only page with a non-zero reply count must not stick for the
      // cache window — re-opening would look like "no replies" again.
      if (_shouldCacheThread(result)) {
        await cache.write(key, result);
      }
      return result;
    } catch (e) {
      final stale = await cache.readStale(key);
      if (stale == null) {
        rethrow;
      }
      log.info('Showing the cached thread for ${widget.id} after $e');
      return stale;
    }
  }

  bool _shouldCacheThread(TweetStatus result) {
    // Cache when the page is usable offline: visible replies, or a show-more
    // cursor we can follow. A bare bottom cursor is not enough — that would
    // stick a focal-only preview for the cache window.
    if (TimelineParser.hasVisibleReplies(result, widget.id) || result.cursorShowMore != null) {
      return true;
    }
    final count = _replyCountOn(result) ?? widget.initialTweet?.replyCount ?? 0;
    return count <= 0;
  }

  int? _replyCountOn(TweetStatus status) {
    for (final chain in status.chains) {
      for (final tweet in chain.tweets) {
        if (tweet.idStr == widget.id) {
          return tweet.replyCount;
        }
      }
    }
    return null;
  }

  Future<CursorPage<String, TweetChain>> _fetchPage(String? cursor) async {
    if (cursor == null) {
      _seenAlready.clear();
      _seenTweetIds.clear();
    }

    var result = cursor == null ? await _fetchFirstPage() : await Twitter.getTweet(widget.id, cursor: cursor);

    // Cursor didn't advance and there are no new tweets -> stop. Still accept
    // the page when TimelineAddToModule appended replies under a repeated
    // bottom cursor (X does that on follow-up TweetDetail pages).
    if (cursor != null && result.cursorBottom == cursor) {
      final hasNew = result.chains.any(
        (c) => c.tweets.any((t) => t.idStr != null && t.idStr!.isNotEmpty && !_seenTweetIds.contains(t.idStr)),
      );
      if (!hasNew) {
        return (items: const <TweetChain>[], nextCursor: null);
      }
    }

    // X often resends earlier replies on later pages — drop tweets we already
    // have. Follow-up `TimelineAddToModule` pages also append *new* tweets under
    // an already-seen conversationthread id; those must still be kept (with a
    // unique chain id so list keys stay unique).
    final chains = <TweetChain>[];
    for (final chain in result.chains) {
      final fresh = chain.tweets.where((t) {
        final id = t.idStr;
        return id != null && id.isNotEmpty && !_seenTweetIds.contains(id);
      }).toList();
      if (fresh.isEmpty) {
        continue;
      }
      final chainId = _seenAlready.contains(chain.id) ? '${chain.id}-${fresh.first.idStr}' : chain.id;
      chains.add(TweetChain(id: chainId, tweets: fresh, isPinned: chain.isPinned));
      _seenAlready.add(chain.id);
      _seenAlready.add(chainId);
      for (final tweet in fresh) {
        _seenTweetIds.add(tweet.idStr!);
      }
    }

    final expected = _replyCountOn(result) ?? widget.initialTweet?.replyCount ?? 0;
    final visibleReplies = TimelineParser.hasVisibleReplies(result, widget.id);

    // On the first page (null cursor), anchor the view on the opened tweet.
    if (cursor == null) {
      _scrollToFocalTweet(chains);
      _repliesMissing = expected > 0 && !visibleReplies && result.cursorShowMore == null && result.cursorBottom == null;
    } else if (visibleReplies) {
      // A later page (or show-more follow-up) delivered replies — drop retry.
      _repliesMissing = false;
    }

    // No new tweets returned, or the cursor doesn't advance -> stop pagination.
    var next = result.cursorBottom;
    var stop = chains.isEmpty || next == null || next == cursor;

    // First page withheld every reply behind show-more: follow that cursor as
    // the next page so the reader is not stuck on a blank thread with a count.
    if (cursor == null && !visibleReplies && expected > 0 && result.cursorShowMore != null) {
      next = result.cursorShowMore;
      stop = false;
      _showMoreCursor = null;
      return (items: chains, nextCursor: next);
    }

    // Only offer the prompt where the thread actually ends, and never offer the
    // cursor we just followed — otherwise the button reloads the same replies.
    _showMoreCursor = stop && result.cursorShowMore != cursor ? result.cursorShowMore : null;

    return (items: chains, nextCursor: stop ? null : next);
  }

  void _loadWithheldReplies() {
    final cursor = _showMoreCursor;
    if (cursor == null) {
      return;
    }

    setState(() => _showMoreCursor = null);
    _paging.resume(cursor);
  }

  Future<void> _retryMissingReplies() async {
    setState(() {
      _repliesMissing = false;
      _showMoreCursor = null;
      _bypassThreadCache = true;
      _seenAlready.clear();
      _seenTweetIds.clear();
      _firstLoadStarted = false;
    });
    _pagingController.refresh();
  }

  /// End-of-thread actions: X's withheld-replies prompt, or a retry when the
  /// footer claimed replies that TweetDetail never returned.
  Widget _threadEndIndicator(BuildContext context) {
    if (_showMoreCursor != null) {
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 16),
        child: Center(
          child: TextButton.icon(
            onPressed: _loadWithheldReplies,
            icon: const Icon(Icons.more_horiz),
            label: Text(L10n.of(context).show_additional_replies),
          ),
        ),
      );
    }

    if (!_repliesMissing) {
      return const SizedBox.shrink();
    }

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 16),
      child: Column(
        children: [
          Text(
            L10n.of(context).unable_to_load_replies,
            textAlign: TextAlign.center,
            style: TextStyle(color: Theme.of(context).hintColor),
          ),
          TextButton.icon(
            onPressed: _retryMissingReplies,
            icon: const Icon(Icons.refresh),
            label: Text(L10n.of(context).retry),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(),
      body: MultiProvider(
        providers: [
          ChangeNotifierProvider<TweetContextState>(
              create: (context) =>
                  TweetContextState.fromPrefs(PrefService.of(context, listen: false))),
          // Long-pressing any translate button translates the whole conversation
          ChangeNotifierProvider<TranslationBroadcast>(create: (_) => TranslationBroadcast()),
          ChangeNotifierProvider<ZenRepliesState>(create: (_) => ZenRepliesState()),
        ],
        // The providers above are looked up from inside these builders, so they
        // need a context below the MultiProvider — not this method's context.
        child: Builder(
          builder: (context) {
            if (_showingPreview) {
              return _buildPreview(context);
            }
            if (_conversationCameBackEmpty) {
              // Still offer show-more / retry under the preview: an empty
              // TweetDetail must not look like a finished thread when the
              // footer already advertised replies.
              return _buildPreview(context, loading: false, showThreadEnd: true);
            }
            return _buildConversation(context);
          },
        ),
      ),
    );
  }

  Widget _buildPreview(BuildContext context, {bool loading = true, bool showThreadEnd = false}) {
    _maybeStartFirstLoad();
    var tweet = widget.initialTweet!;
    return ListView(
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).padding.bottom),
      children: [
        TweetConversation(
          id: tweet.idStr!,
          tweets: [tweet],
          username: null,
          isPinned: false,
          tweetOpened: widget.tweetOpened,
          initialMediaIndex: widget.initialMediaIndex,
        ),
        if (loading)
          const Padding(
            padding: EdgeInsets.all(16),
            child: Center(child: CircularProgressIndicator()),
          ),
        if (showThreadEnd) _threadEndIndicator(context),
      ],
    );
  }

  // Zen mode: only the opened post (and the posts above it in the thread) are
  // shown; the replies below stay hidden until deliberately revealed.
  Widget _buildZenConversation(BuildContext context, List<TweetChain> chains) {
    // Keep paging while replies are collapsed — otherwise a first page that only
    // carries the focal post + a bottom/show-more cursor never loads replies
    // until the reader reveals (and even then only after another scroll).
    final paging = _pagingController.value;
    if (paging.hasNextPage) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _pagingController.fetchNextPage();
      });
    }

    if (chains.isEmpty) {
      final error = pagingErrorOf(paging);
      if (error != null) {
        return FullPageErrorWidget(
          error: error.error,
          stackTrace: error.stackTrace,
          prefix: L10n.of(context).unable_to_load_the_tweet,
          onRetry: _pagingController.fetchNextPage,
        );
      }
      if (paging.status == PagingStatus.noItemsFound) {
        return Center(child: Text(L10n.of(context).could_not_find_any_tweets_by_this_user));
      }
      return const Center(child: CircularProgressIndicator());
    }

    final focal = chains.indexWhere((c) => c.tweets.any((t) => t.idStr == widget.id));
    final visible = chains.take((focal < 0 ? 0 : focal) + 1).toList();

    return ListView(
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).padding.bottom),
      children: [
        for (final chain in visible)
          TweetConversation(
              key: ValueKey(chain.id),
              id: chain.id,
              tweets: chain.tweets,
              username: null,
              isPinned: chain.isPinned,
              tweetOpened: widget.tweetOpened,
              initialMediaIndex: chain.id == widget.id ? widget.initialMediaIndex : 0),
        InkWell(
          onTap: () => context.read<ZenRepliesState>().reveal(),
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Center(
              child: Text(
                L10n.of(context).long_press_to_show_replies,
                textAlign: TextAlign.center,
                style: TextStyle(color: Theme.of(context).hintColor),
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildConversation(BuildContext context) {
    // Without an instant preview (e.g. opened from a media lightbox) nothing
    // else starts the first page load: the zen and threaded views render a
    // plain spinner, not the paged list that normally triggers the fetch.
    _maybeStartFirstLoad();

    final zen = PrefService.of(context, listen: false).get(optionZenMode) == true;
    final zenReplies = context.watch<ZenRepliesState>();

    if (zen && !zenReplies.revealed) {
      return _buildZenConversation(context, _pagingController.value.items ?? const <TweetChain>[]);
    }

    final threaded = PrefService.of(context, listen: false).get(optionThreadedReplies) == true;

    return PagingListener<int, TweetChain>(
      controller: _pagingController,
      builder: (context, state, fetchNextPage) =>
          threaded ? _buildThreadedList(context, state, fetchNextPage) : _buildFlatList(context, state, fetchNextPage),
    );
  }

  Widget _conversationTile(BuildContext context, TweetChain chain, int index) {
    return AutoScrollTag(
      key: ValueKey(chain.id),
      controller: _scrollController,
      index: index,
      highlightColor: Theme.of(context).colorScheme.primary,
      child: TweetConversation(
          id: chain.id,
          tweets: chain.tweets,
          username: null,
          isPinned: chain.isPinned,
          tweetOpened: widget.tweetOpened,
          initialMediaIndex: chain.id == widget.id ? widget.initialMediaIndex : 0),
    );
  }

  Widget _buildFlatList(BuildContext context, PagingState<int, TweetChain> state, NextPageCallback fetchNextPage) {
    if (pagingAwaitingFirstPage(state)) {
      return const Center(child: CircularProgressIndicator());
    }
    if (state.items == null) {
      return FullPageErrorWidget(
        error: pagingErrorOf(state)?.error,
        stackTrace: pagingErrorOf(state)?.stackTrace,
        prefix: L10n.of(context).unable_to_load_the_tweet,
        onRetry: fetchNextPage,
      );
    }
    if (state.items!.isEmpty) {
      return Center(
        child: Text(L10n.of(context).could_not_find_any_tweets_by_this_user),
      );
    }
    return PagedListView<int, TweetChain>(
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).padding.bottom),
      state: state,
      fetchNextPage: fetchNextPage,
      scrollController: _scrollController,
      addAutomaticKeepAlives: false,
      builderDelegate: PagedChildBuilderDelegate(
        itemBuilder: (context, chain, index) => _conversationTile(context, chain, index),
        newPageErrorIndicatorBuilder: (context) => FullPageErrorWidget(
          error: pagingErrorOf(state)?.error,
          stackTrace: pagingErrorOf(state)?.stackTrace,
          prefix: L10n.of(context).unable_to_load_the_next_page_of_replies,
          onRetry: fetchNextPage,
        ),
        noMoreItemsIndicatorBuilder: (_) => _threadEndIndicator(context),
      ),
    );
  }

  // Reddit-style nested replies: the opened tweet on top, replies indented
  // under their parent. Renders the flattened tree in a lazy list, keeping the
  // paging controller for loading more.
  Widget _buildThreadedList(BuildContext context, PagingState<int, TweetChain> state, NextPageCallback fetchNextPage) {
    final items = state.items ?? const <TweetChain>[];
    if (items.isEmpty) {
      final error = pagingErrorOf(state);
      if (error != null) {
        return FullPageErrorWidget(
          error: error.error,
          stackTrace: error.stackTrace,
          prefix: L10n.of(context).unable_to_load_the_tweet,
          onRetry: fetchNextPage,
        );
      }
      if (state.status == PagingStatus.noItemsFound) {
        return Center(child: Text(L10n.of(context).could_not_find_any_tweets_by_this_user));
      }
      return const Center(child: CircularProgressIndicator());
    }

    final display = buildCappedThreadList(buildThreadTree(items, widget.id));
    return ListView.builder(
      controller: _scrollController,
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).padding.bottom),
      itemCount: display.length + 1,
      itemBuilder: (context, index) {
        if (index == display.length) {
          return _buildThreadFooter(context, state, fetchNextPage);
        }
        final item = display[index];
        if (item is ThreadContinueMarker) {
          return ThreadContinueRow(
            indentDepth: item.indentDepth,
            onTap: () => _openContinueThread(context, item.target),
          );
        }
        final node = (item as ThreadDisplayNode).node;
        return ThreadIndent(
          depth: item.visualDepth,
          connectTop: item.connectTop,
          connectBottom: item.connectBottom,
          child: _conversationTile(context, node.chain, index),
        );
      },
    );
  }

  void _openContinueThread(BuildContext context, ThreadNode target) {
    final tweet = target.chain.tweets.isEmpty ? null : target.chain.tweets.first;
    final id = tweet?.idStr;
    if (id == null) {
      return;
    }
    Navigator.pushNamed(
      context,
      routeStatus,
      arguments: StatusScreenArguments(id: id, username: tweet!.user?.screenName),
    );
  }

  Widget _buildThreadFooter(BuildContext context, PagingState<int, TweetChain> state, NextPageCallback fetchNextPage) {
    final error = pagingErrorOf(state);
    if (error != null && state.status == PagingStatus.subsequentPageError) {
      return FullPageErrorWidget(
        error: error.error,
        stackTrace: error.stackTrace,
        prefix: L10n.of(context).unable_to_load_the_next_page_of_replies,
        onRetry: fetchNextPage,
      );
    }
    if (state.hasNextPage) {
      // The controller ignores overlapping calls, so requesting each frame the
      // footer is visible is safe.
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) fetchNextPage();
      });
      return const Padding(padding: EdgeInsets.all(16), child: Center(child: CircularProgressIndicator()));
    }
    return _threadEndIndicator(context);
  }
}
