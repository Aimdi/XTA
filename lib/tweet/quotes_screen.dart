import 'package:flutter/material.dart';
import 'package:infinite_scroll_pagination/infinite_scroll_pagination.dart';
import 'package:xta/client/client.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/paginated_tweet_list.dart';
import 'package:xta/tweet/tweet_context_scope.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/user.dart';
import 'package:xta/utils/paging.dart';

class QuotesScreenArguments {
  final String id;
  final int initialTab;

  const QuotesScreenArguments({required this.id, this.initialTab = 0});

  @override
  String toString() {
    return 'QuotesScreenArguments{id: $id, initialTab: $initialTab}';
  }
}

/// Quotes of a tweet, plus the people who retweeted it — the same two tabs X
/// shows when you open the repost count. Read-only: this never creates a
/// quote or a repost.
class QuotesScreen extends StatelessWidget {
  const QuotesScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final args =
        ModalRoute.of(context)!.settings.arguments as QuotesScreenArguments;
    return _QuotesScreen(id: args.id, initialTab: args.initialTab);
  }
}

class _QuotesScreen extends StatelessWidget {
  final String id;
  final int initialTab;

  const _QuotesScreen({required this.id, this.initialTab = 0});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final tabColor =
        Theme.of(context).appBarTheme.foregroundColor ??
        Theme.of(context).colorScheme.onSurface;
    return DefaultTabController(
      length: 2,
      initialIndex: initialTab.clamp(0, 1),
      child: Scaffold(
        appBar: AppBar(
          titleSpacing: 0,
          title: TabBar(
            tabs: [
              Tab(text: l10n.quotes),
              Tab(text: l10n.retweets),
            ],
            labelColor: tabColor,
            unselectedLabelColor: tabColor.withAlpha(153),
            indicatorColor: tabColor,
            indicatorSize: TabBarIndicatorSize.tab,
            dividerColor: Colors.transparent,
          ),
        ),
        body: TabBarView(
          children: [
            _QuotesList(id: id),
            _RetweetersList(tweetId: id),
          ],
        ),
      ),
    );
  }
}

class _QuotesList extends StatefulWidget {
  final String id;

  const _QuotesList({required this.id});

  @override
  State<_QuotesList> createState() => _QuotesListState();
}

class _QuotesListState extends State<_QuotesList>
    with AutomaticKeepAliveClientMixin<_QuotesList> {
  final TweetFeedController _feed = TweetFeedController();

  @override
  bool get wantKeepAlive => true;

  @override
  void dispose() {
    _feed.dispose();
    super.dispose();
  }

  Future<TweetPageResult> _loadPage(String? cursor) async {
    final result = await Twitter.searchTweets(
      'quoted_tweet_id:${widget.id}',
      true,
      cursor: cursor,
      // Each quote is a separate post; folding by conversationId merges them.
      mapToThreads: false,
    );
    return (chains: result.chains, nextCursor: result.cursorBottom);
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return TweetContextScope(
      child: PaginatedTweetList(
        feed: _feed,
        loadPage: _loadPage,
        username: null,
        firstPageErrorPrefix: L10n.of(
          context,
        ).unable_to_load_the_tweets_for_the_feed,
        newPageErrorPrefix: L10n.of(
          context,
        ).unable_to_load_the_next_page_of_tweets,
        emptyMessage: L10n.of(context).could_not_find_any_quotes_of_this_post,
      ),
    );
  }
}

class _RetweetersList extends StatefulWidget {
  final String tweetId;

  const _RetweetersList({required this.tweetId});

  @override
  State<_RetweetersList> createState() => _RetweetersListState();
}

class _RetweetersListState extends State<_RetweetersList>
    with AutomaticKeepAliveClientMixin<_RetweetersList> {
  late final CursorPagingController<String, UserWithExtra> _paging;
  PagingController<int, UserWithExtra> get _pagingController =>
      _paging.pagingController;

  final Set<String> _seenIds = {};

  @override
  bool get wantKeepAlive => true;

  @override
  void initState() {
    super.initState();
    _paging = CursorPagingController<String, UserWithExtra>(_fetchPage);
  }

  @override
  void dispose() {
    _paging.dispose();
    super.dispose();
  }

  Future<CursorPage<String, UserWithExtra>> _fetchPage(String? cursor) async {
    final result = await Twitter.getRetweeters(widget.tweetId, cursor: cursor);
    final next = result.cursorBottom;
    final fresh = result.users
        .where((u) => u.idStr != null && _seenIds.add(u.idStr!))
        .toList();
    final end =
        next == null ||
        next.isEmpty ||
        next == '0' ||
        next == cursor ||
        fresh.isEmpty;
    return (items: fresh, nextCursor: end ? null : next);
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    final l10n = L10n.of(context);
    return PagingListener<int, UserWithExtra>(
      controller: _pagingController,
      builder: (context, state, fetchNextPage) =>
          PagedListView<int, UserWithExtra>(
            padding: EdgeInsets.only(
              bottom: MediaQuery.of(context).padding.bottom,
            ),
            state: state,
            fetchNextPage: fetchNextPage,
            addAutomaticKeepAlives: false,
            builderDelegate: PagedChildBuilderDelegate(
              itemBuilder: (context, user, index) =>
                  UserTile(user: UserSubscription.fromUser(user)),
              firstPageErrorIndicatorBuilder: (context) => FullPageErrorWidget(
                error: pagingErrorOf(state)?.error,
                stackTrace: pagingErrorOf(state)?.stackTrace,
                prefix: l10n.unable_to_load_the_list_of_retweets,
                onRetry: fetchNextPage,
              ),
              newPageErrorIndicatorBuilder: (context) => FullPageErrorWidget(
                error: pagingErrorOf(state)?.error,
                stackTrace: pagingErrorOf(state)?.stackTrace,
                prefix: l10n.unable_to_load_the_next_page_of_retweets,
                onRetry: fetchNextPage,
              ),
              noItemsFoundIndicatorBuilder: (context) => Center(
                child: Text(l10n.could_not_find_any_retweets_of_this_post),
              ),
            ),
          ),
    );
  }
}
