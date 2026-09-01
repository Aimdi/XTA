import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/profile/profile.dart';
import 'package:xta/search/advanced_search.dart';
import 'package:xta/search/search_media_grid.dart';
import 'package:xta/search/search_model.dart';
import 'package:xta/search/search_chrome.dart';
import 'package:xta/tweet/_video.dart';
import 'package:xta/tweet/paginated_tweet_list.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/user.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';

class SearchArguments {
  final int initialTab;
  final String? query;
  final bool focusInputOnOpen;

  SearchArguments(this.initialTab, {this.query, this.focusInputOnOpen = false});
}

class ResultsScreen extends StatelessWidget {
  const ResultsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final arguments =
        ModalRoute.of(context)!.settings.arguments as SearchArguments;

    return _ResultsScreen(
      initialTab: arguments.initialTab,
      query: arguments.query,
      focusInputOnOpen: arguments.focusInputOnOpen,
    );
  }
}

class _ResultsScreen extends StatefulWidget {
  final int initialTab;
  final String? query;
  final bool focusInputOnOpen;

  const _ResultsScreen({
    required this.initialTab,
    this.query,
    this.focusInputOnOpen = false,
  });

  @override
  State<_ResultsScreen> createState() => _ResultsScreenState();
}

class _ResultsScreenState extends State<_ResultsScreen>
    with SingleTickerProviderStateMixin {
  final TextEditingController _queryController = TextEditingController();
  final FocusNode _focusNode = FocusNode();

  late final TabController _tabController;
  late final SearchTweetsPagination _topTweets;
  late final SearchTweetsPagination _latestTweets;
  late final SearchMediaPagination _mediaResults;
  late final SearchUsersModel _searchUsersModel;

  Timer? _debounce;
  String? _lastDispatchedQuery;

  /// The query the tabs should be showing, and which of them already are.
  ///
  /// A query used to be pushed into all four tabs at once, so every search
  /// cost four requests -- including the user search, whose tab may never be
  /// opened. A tab now picks the pending query up when it becomes visible.
  String? _pendingQuery;
  final _appliedTo = <int>{};

  @override
  void initState() {
    super.initState();

    _tabController = TabController(
      length: 4,
      vsync: this,
      initialIndex: widget.initialTab,
    );

    final initialQuery = widget.query ?? '';
    _topTweets = SearchTweetsPagination(
      product: 'Top',
      initialQuery: initialQuery,
    );
    _latestTweets = SearchTweetsPagination(
      product: 'Latest',
      initialQuery: initialQuery,
    );
    _mediaResults = SearchMediaPagination(initialQuery: initialQuery);
    _searchUsersModel = SearchUsersModel();

    _queryController.text = initialQuery;
    _lastDispatchedQuery = initialQuery;
    _queryController.addListener(_onQueryChanged);

    // TODO: Focussing makes the selection go to the start?!

    // The tweet tabs' first-page requests are fired automatically by their
    // PagedListViews using the initial query above, so those three already
    // hold it. The user-search Store needs an explicit kick, and only once its
    // tab is actually looked at.
    _pendingQuery = initialQuery;
    _appliedTo.addAll(const [0, 1, 2]);
    if (initialQuery.isEmpty) {
      _appliedTo.add(3);
    } else if (widget.initialTab == 3) {
      _applyPendingQuery();
    }

    _tabController.addListener(_applyPendingQuery);
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _queryController.dispose();
    _focusNode.dispose();
    _tabController.dispose();
    _topTweets.dispose();
    _latestTweets.dispose();
    _mediaResults.dispose();
    super.dispose();
  }

  void _onQueryChanged() {
    if (_queryController.text == _lastDispatchedQuery) return;
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 750), _dispatchQuery);
  }

  void _dispatchQuery() {
    if (!mounted) return;
    final query = _queryController.text;
    _lastDispatchedQuery = query;
    _pendingQuery = query;
    _appliedTo.clear();
    _applyPendingQuery();
  }

  /// Hands the pending query to the visible tab, once.
  void _applyPendingQuery() {
    final query = _pendingQuery;
    if (!mounted || query == null || !_appliedTo.add(_tabController.index)) {
      return;
    }

    switch (_tabController.index) {
      case 0:
        _topTweets.updateQuery(query);
      case 1:
        _latestTweets.updateQuery(query);
      case 2:
        _mediaResults.updateQuery(query);
      case 3:
        _searchUsersModel.searchUsers(query, context);
    }
  }

  @override
  Widget build(BuildContext context) {
    var prefs = PrefService.of(context, listen: false);

    return SearchSystemBars(
      child: Scaffold(
        // Needed as we're nesting Scaffolds, which causes Flutter to calculate keyboard height incorrectly
        resizeToAvoidBottomInset: false,
        appBar: AppBar(
          surfaceTintColor: Colors.transparent,
          scrolledUnderElevation: 0,
          automaticallyImplyLeading: false,
          toolbarHeight: 72,
          flexibleSpace: Padding(
            padding: EdgeInsets.fromLTRB(
              8,
              MediaQuery.paddingOf(context).top + 8,
              8,
              8,
            ),
            child: SearchBar(
              controller: _queryController,
              focusNode: _focusNode,
              textInputAction: TextInputAction.search,
              leading: IconButton(
                icon: const Icon(Icons.arrow_back),
                onPressed: () => Navigator.pop(context),
              ),
              trailing: [
                IconButton(
                  icon: const Icon(Icons.sensors),
                  tooltip: L10n.of(context).antenna_title,
                  onPressed: () => Navigator.pushNamed(context, routeAntennas),
                ),
                IconButton(
                  icon: const Icon(Icons.tune),
                  tooltip: L10n.of(context).advanced_search,
                  onPressed: () async {
                    final query = await Navigator.push<String>(
                      context,
                      MaterialPageRoute(
                        fullscreenDialog: true,
                        builder: (_) => const AdvancedSearchScreen(),
                      ),
                    );
                    if (query != null && query.trim().isNotEmpty) {
                      _queryController.text = query;
                    }
                  },
                ),
                // Hidden while the field is empty: following a blank query
                // saved a search that could never match anything.
                ValueListenableBuilder<TextEditingValue>(
                  valueListenable: _queryController,
                  builder: (context, value, _) {
                    final query = value.text.trim();

                    return query.isEmpty
                        ? const SizedBox.shrink()
                        : FollowButton(
                            user: SearchSubscription(
                              id: query,
                              createdAt: DateTime.now(),
                            ),
                          );
                  },
                ),
              ],
            ),
          ),
          bottom: SearchResultsTabBar(
            controller: _tabController,
            tabs: [
              Tab(
                icon: Tooltip(
                  message: L10n.of(context).tweets,
                  child: const Icon(Icons.trending_up),
                ),
              ),
              Tab(
                icon: Tooltip(
                  message: L10n.of(context).recent,
                  child: const Icon(Icons.access_time_outlined),
                ),
              ),
              Tab(
                icon: Tooltip(
                  message: L10n.of(context).media,
                  child: const Icon(Icons.image),
                ),
              ),
              Tab(
                icon: Tooltip(
                  message: L10n.of(context).account,
                  child: const Icon(Icons.person_search),
                ),
              ),
            ],
          ),
        ),
        body: MultiProvider(
          providers: [
            ChangeNotifierProvider<TweetContextState>(
              create: (_) => TweetContextState.fromPrefs(prefs),
            ),
            ChangeNotifierProvider<VideoContextState>(
              create: (_) =>
                  VideoContextState(prefs.get(optionMediaDefaultMute)),
            ),
          ],
          child: TabBarView(
            controller: _tabController,
            children: [
              PaginatedTweetList(
                feed: _topTweets.feed,
                loadPage: _topTweets.loadPage,
                username: null,
                firstPageErrorPrefix: L10n.of(
                  context,
                ).unable_to_load_the_search_results,
                newPageErrorPrefix: L10n.of(
                  context,
                ).unable_to_load_the_next_page_of_tweets,
                emptyMessage: L10n.of(context).no_results,
              ),
              PaginatedTweetList(
                feed: _latestTweets.feed,
                loadPage: _latestTweets.loadPage,
                username: null,
                firstPageErrorPrefix: L10n.of(
                  context,
                ).unable_to_load_the_search_results,
                newPageErrorPrefix: L10n.of(
                  context,
                ).unable_to_load_the_next_page_of_tweets,
                emptyMessage: L10n.of(context).no_results,
              ),
              SearchMediaGrid(model: _mediaResults),
              _UserSearchResultList(
                store: _searchUsersModel,
                onRetry: _dispatchQuery,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _UserSearchResultList extends StatelessWidget {
  final SearchUsersModel store;
  final VoidCallback onRetry;

  const _UserSearchResultList({required this.store, required this.onRetry});

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<SearchUsersModel, List<UserWithExtra>>(
      store: store,
      onLoading: (_) => const Center(child: CircularProgressIndicator()),
      onError: (_, error) => FullPageErrorWidget(
        error: error,
        stackTrace: null,
        prefix: L10n.of(context).unable_to_load_the_search_results,
        onRetry: onRetry,
      ),
      onState: (_, items) {
        if (items.isEmpty) {
          return Center(child: Text(L10n.of(context).no_results));
        }
        return ListView.builder(
          padding: EdgeInsets.only(
            bottom: MediaQuery.of(context).padding.bottom,
          ),
          itemCount: items.length,
          itemBuilder: (context, index) {
            return UserTile(user: UserSubscription.fromUser(items[index]));
          },
        );
      },
    );
  }
}
