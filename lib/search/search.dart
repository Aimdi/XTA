import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/profile/profile_chrome.dart';
import 'package:xta/search/advanced_search.dart';
import 'package:xta/search/advanced_search_model.dart';
import 'package:xta/search/search_chrome.dart';
import 'package:xta/search/search_media_grid.dart';
import 'package:xta/search/search_model.dart';
import 'package:xta/tweet/paginated_tweet_list.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/tweet_context_scope.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/motion.dart';
import 'package:xta/user.dart';

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
  late final SearchViewStore _viewStore;
  late final SearchTweetsPagination _topTweets;
  late final SearchTweetsPagination _latestTweets;
  late final SearchMediaPagination _mediaResults;
  late final SearchUsersModel _searchUsersModel;

  Timer? _debounce;
  String _lastDispatchedQuery = '';
  String? _pendingQuery;
  final _appliedTo = <int>{};

  @override
  void initState() {
    super.initState();
    final initialQuery = (widget.query ?? '').trim();
    _tabController = TabController(
      length: 4,
      vsync: this,
      initialIndex: widget.initialTab,
    )..addListener(_applyPendingQuery);
    _viewStore = SearchViewStore(initialQuery: initialQuery);
    _topTweets = SearchTweetsPagination(product: 'Top');
    _latestTweets = SearchTweetsPagination(product: 'Latest');
    _mediaResults = SearchMediaPagination();
    _searchUsersModel = SearchUsersModel();

    _queryController.text = initialQuery;
    _lastDispatchedQuery = initialQuery;
    _queryController.addListener(_onQueryChanged);
    _pendingQuery = initialQuery;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _applyPendingQuery();
    });

    if (widget.focusInputOnOpen) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        _queryController.selection = TextSelection.collapsed(
          offset: _queryController.text.length,
        );
        _focusNode.requestFocus();
      });
    }
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
    _searchUsersModel.destroy();
    _viewStore.destroy();
    super.dispose();
  }

  void _onQueryChanged() {
    final draft = _queryController.text.trim();
    _viewStore.invalidateAdvancedForDraft(draft);
    if (draft == _lastDispatchedQuery) return;
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 750), _dispatchQuery);
  }

  void _dispatchQuery([String? submitted]) {
    if (!mounted) return;
    _debounce?.cancel();
    final query = (submitted ?? _queryController.text).trim();
    _lastDispatchedQuery = query;
    _viewStore.commitQuery(query);
    _pendingQuery = query;
    _appliedTo.clear();
    _applyPendingQuery();
    if (submitted != null) _focusNode.unfocus();
  }

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
        _searchUsersModel.searchUsers(query);
    }
  }

  void _setQueryText(String query) {
    _queryController.value = TextEditingValue(
      text: query,
      selection: TextSelection.collapsed(offset: query.length),
    );
  }

  void _clearQuery() {
    _viewStore.clear();
    _setQueryText('');
    _dispatchQuery('');
    _focusNode.requestFocus();
  }

  Future<void> _openAdvancedSearch(AdvancedSearchState current) async {
    final initial = current.activeFilters.isEmpty
        ? AdvancedSearchState.fromQuery(_queryController.text)
        : current;
    final result = await Navigator.push<AdvancedSearchState>(
      context,
      MaterialPageRoute(
        fullscreenDialog: true,
        builder: (_) => AdvancedSearchScreen(initialState: initial),
      ),
    );
    if (!mounted || result == null) return;
    _viewStore.applyAdvanced(result);
    _setQueryText(result.query);
    _dispatchQuery(result.query);
  }

  void _clearFilter(AdvancedSearchFilter filter) {
    final advanced = _viewStore.clearFilter(filter);
    _setQueryText(advanced.query);
    _dispatchQuery(advanced.query);
  }

  Widget _searchField(SearchViewState state) {
    return ValueListenableBuilder<TextEditingValue>(
      valueListenable: _queryController,
      builder: (context, value, _) => XtaSearchField(
        controller: _queryController,
        focusNode: _focusNode,
        activeFilterCount: state.advanced.activeFilters.length,
        onSubmitted: _dispatchQuery,
        onClear: _clearQuery,
        onAdvanced: () => _openAdvancedSearch(state.advanced),
      ),
    );
  }

  List<Widget> _queryActions(SearchViewState state) {
    return [
      IconButton(
        icon: const Icon(Icons.sensors),
        tooltip: L10n.of(context).antenna_title,
        onPressed: () => Navigator.pushNamed(context, routeAntennas),
      ),
      if (state.hasQuery)
        FollowButton(
          user: SearchSubscription(id: state.query, createdAt: DateTime.now()),
        ),
      const SizedBox(width: kTweetSpace1),
    ];
  }

  List<Widget> _filterChips(AdvancedSearchState advanced) {
    return advanced.activeFilters.map((filter) {
      final label = advancedFilterLabel(context, filter);
      final value = advanced.valueOf(filter);
      return SearchActiveFilterChip(
        label: value.isEmpty ? label : '$label: $value',
        onDeleted: () => _clearFilter(filter),
      );
    }).toList(growable: false);
  }

  Widget _results(SearchViewState state) {
    if (!state.hasQuery) return const SearchStartState();
    return Column(
      children: [
        XtaAnimatedSwitcher(
          animateSize: true,
          child: state.advanced.activeFilters.isEmpty
              ? const SizedBox.shrink(key: ValueKey('search-filters-empty'))
              : SearchFilterStrip(
                  key: const ValueKey('search-filters-active'),
                  chips: _filterChips(state.advanced),
                ),
        ),
        Expanded(
          child: TabBarView(
            controller: _tabController,
            children: [
              _tweetResults(_topTweets),
              _tweetResults(_latestTweets),
              SearchMediaGrid(model: _mediaResults),
              _UserSearchResultList(
                store: _searchUsersModel,
                onRetry: () => _searchUsersModel.searchUsers(state.query),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _tweetResults(SearchTweetsPagination pagination) {
    final l10n = L10n.of(context);
    return PaginatedTweetList(
      feed: pagination.feed,
      loadPage: pagination.loadPage,
      username: null,
      firstPageErrorPrefix: l10n.unable_to_load_the_search_results,
      newPageErrorPrefix: l10n.unable_to_load_the_next_page_of_tweets,
      emptyMessage: l10n.no_results,
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<SearchViewStore, SearchViewState>(
      store: _viewStore,
      onState: (_, state) => SearchSystemBars(
        child: Scaffold(
          resizeToAvoidBottomInset: false,
          appBar: AppBar(
            toolbarHeight: MediaQuery.textScalerOf(context).scale(1) >= 1.3
                ? 72
                : 64,
            leading: IconButton(
              tooltip: MaterialLocalizations.of(context).backButtonTooltip,
              icon: const Icon(Icons.arrow_back),
              onPressed: () => Navigator.pop(context),
            ),
            titleSpacing: 0,
            title: _searchField(state),
            actions: _queryActions(state),
            bottom: SearchResultsTabBar(
              controller: _tabController,
              tabs: [
                Tab(text: l10n.popular),
                Tab(text: l10n.recent),
                Tab(text: l10n.media),
                Tab(text: l10n.account),
              ],
            ),
          ),
          body: TweetContextScope(child: _results(state)),
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
      onLoading: (_) => const ProfileUserListSkeleton(),
      onError: (_, error) => FullPageErrorWidget(
        error: error,
        stackTrace: null,
        prefix: L10n.of(context).unable_to_load_the_search_results,
        onRetry: onRetry,
      ),
      onState: (_, items) {
        if (items.isEmpty) {
          return ProfileEmptyState(
            icon: Icons.person_search,
            message: L10n.of(context).no_results,
          );
        }
        return ListView.separated(
          keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
          padding: EdgeInsets.only(
            bottom: MediaQuery.paddingOf(context).bottom,
          ),
          itemCount: items.length,
          separatorBuilder: (context, index) => Padding(
            padding: const EdgeInsetsDirectional.only(
              start: kTweetHorizontalPadding + kTweetTouchTarget + kTweetSpace3,
            ),
            child: tweetHairlineDivider(context),
          ),
          itemBuilder: (context, index) =>
              UserTile(user: UserSubscription.fromUser(items[index])),
        );
      },
    );
  }
}
