import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:quax/constants.dart';
import 'package:quax/database/entities.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/profile/profile_chrome.dart';
import 'package:quax/search/advanced_search.dart';
import 'package:quax/search/search_chrome.dart';
import 'package:quax/search/search_media_grid.dart';
import 'package:quax/search/search_model.dart';
import 'package:quax/tweet/paginated_tweet_list.dart';
import 'package:quax/tweet/tweet_chrome.dart';
import 'package:quax/tweet/tweet_context_scope.dart';
import 'package:quax/ui/errors.dart';
import 'package:quax/user.dart';

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
  String? _lastDispatchedQuery;
  bool _updatingController = false;

  @override
  void initState() {
    super.initState();
    final initialQuery = widget.query ?? '';
    final initialTab = widget.initialTab.clamp(0, 3).toInt();
    _viewStore = SearchViewStore(
      initialQuery: initialQuery,
      initialTab: initialTab,
    );
    _tabController = TabController(
      length: 4,
      vsync: this,
      initialIndex: initialTab,
    )..addListener(_onTabChanged);
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

    if (initialQuery.isNotEmpty) _searchUsersModel.searchUsers(initialQuery);
    if (widget.focusInputOnOpen) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _focusNode.requestFocus();
      });
    }
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _queryController.dispose();
    _focusNode.dispose();
    _tabController
      ..removeListener(_onTabChanged)
      ..dispose();
    _viewStore.destroy();
    _topTweets.dispose();
    _latestTweets.dispose();
    _mediaResults.dispose();
    _searchUsersModel.destroy();
    super.dispose();
  }

  void _onTabChanged() => _viewStore.selectTab(_tabController.index);

  void _onQueryChanged() {
    if (_updatingController) return;
    final query = _queryController.text;
    _viewStore.editDraft(query);
    if (query == _lastDispatchedQuery) return;
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 750), _dispatchDraft);
  }

  void _dispatchDraft({bool force = false}) {
    if (!mounted) return;
    final query = _queryController.text;
    _viewStore.submit(query);
    _loadQuery(query, force: force);
  }

  void _loadQuery(String query, {bool force = false}) {
    _lastDispatchedQuery = query;
    _topTweets.updateQuery(query, force: force);
    _latestTweets.updateQuery(query, force: force);
    _mediaResults.updateQuery(query, force: force);
    _searchUsersModel.searchUsers(query);
  }

  void _submit(String _) {
    _debounce?.cancel();
    _dispatchDraft(force: true);
    _focusNode.unfocus();
  }

  void _clearQuery() {
    _debounce?.cancel();
    _setControllerText('');
    _viewStore.clear();
    _loadQuery('', force: true);
  }

  Future<void> _openAdvanced(SearchViewState viewState) async {
    final initial =
        viewState.advancedSearch ??
        AdvancedSearchState.fromQuery(viewState.draftQuery);
    final result = await Navigator.push<AdvancedSearchState>(
      context,
      MaterialPageRoute(
        fullscreenDialog: true,
        builder: (_) => AdvancedSearchScreen(initialState: initial),
      ),
    );
    if (result != null && mounted) _applyAdvanced(result);
  }

  void _applyAdvanced(AdvancedSearchState advanced) {
    _debounce?.cancel();
    _setControllerText(advanced.query);
    _viewStore.applyAdvanced(advanced);
    _loadQuery(advanced.query, force: true);
    _focusNode.unfocus();
  }

  void _removeFilter(
    AdvancedSearchState advanced,
    AdvancedSearchFilter filter,
  ) {
    _applyAdvanced(advanced.clear(filter));
  }

  void _setControllerText(String text) {
    _updatingController = true;
    _queryController.value = TextEditingValue(
      text: text,
      selection: TextSelection.collapsed(offset: text.length),
    );
    _updatingController = false;
  }

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<SearchViewStore, SearchViewState>(
      store: _viewStore,
      onState: (context, viewState) => SearchSystemBars(
        child: Scaffold(
          appBar: _buildAppBar(context, viewState),
          body: _buildBody(context, viewState),
        ),
      ),
    );
  }

  AppBar _buildAppBar(BuildContext context, SearchViewState viewState) {
    final advanced = viewState.advancedSearch;
    final activeCount = advanced?.activeFilters.length ?? 0;
    return AppBar(
      leading: IconButton(
        tooltip: L10n.of(context).back,
        icon: const Icon(Icons.arrow_back),
        onPressed: () => Navigator.pop(context),
      ),
      leadingWidth: kTweetTouchTarget,
      titleSpacing: kTweetSpace2,
      toolbarHeight: 64,
      title: XtaSearchField(
        controller: _queryController,
        focusNode: _focusNode,
        activeFilterCount: activeCount,
        onSubmitted: _submit,
        onClear: _clearQuery,
        onAdvanced: () => _openAdvanced(viewState),
      ),
      actions: [
        if (viewState.submittedQuery.trim().isNotEmpty)
          FollowButton(
            user: SearchSubscription(
              id: viewState.submittedQuery,
              createdAt: DateTime.now(),
            ),
          ),
        const SizedBox(width: kTweetSpace1),
      ],
      bottom: _SearchHeaderBottom(
        controller: _tabController,
        filterChips: advanced == null
            ? const []
            : _filterChips(context, advanced),
      ),
    );
  }

  Widget _buildBody(BuildContext context, SearchViewState viewState) {
    if (viewState.submittedQuery.trim().isEmpty) {
      return const SearchStartState();
    }
    return NotificationListener<ScrollStartNotification>(
      onNotification: (notification) {
        if (notification.dragDetails != null) _focusNode.unfocus();
        return false;
      },
      child: TweetContextScope(
        child: TabBarView(
          controller: _tabController,
          children: [
            _tweetResults(context, _topTweets),
            _tweetResults(context, _latestTweets),
            SearchMediaGrid(model: _mediaResults),
            _UserSearchResultList(
              store: _searchUsersModel,
              onRetry: () => _dispatchDraft(force: true),
            ),
          ],
        ),
      ),
    );
  }

  Widget _tweetResults(
    BuildContext context,
    SearchTweetsPagination pagination,
  ) {
    return PaginatedTweetList(
      feed: pagination.feed,
      loadPage: pagination.loadPage,
      username: null,
      firstPageErrorPrefix: L10n.of(context).unable_to_load_the_search_results,
      newPageErrorPrefix: L10n.of(
        context,
      ).unable_to_load_the_next_page_of_tweets,
      emptyMessage: L10n.of(context).no_results,
    );
  }

  List<Widget> _filterChips(
    BuildContext context,
    AdvancedSearchState advanced,
  ) {
    return advanced.activeFilters
        .map(
          (filter) => SearchActiveFilterChip(
            label: _chipLabel(context, advanced, filter),
            onDeleted: () => _removeFilter(advanced, filter),
          ),
        )
        .toList(growable: false);
  }

  String _chipLabel(
    BuildContext context,
    AdvancedSearchState state,
    AdvancedSearchFilter filter,
  ) {
    final label = advancedFilterLabel(context, filter);
    final value = state.valueOf(filter);
    return value.isEmpty ? label : '$label: $value';
  }
}

class _SearchHeaderBottom extends StatelessWidget
    implements PreferredSizeWidget {
  final TabController controller;
  final List<Widget> filterChips;

  const _SearchHeaderBottom({
    required this.controller,
    required this.filterChips,
  });

  @override
  Size get preferredSize => Size.fromHeight(
    kSearchTabsHeight + (filterChips.isEmpty ? 0 : kSearchFilterStripHeight),
  );

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        SearchResultsTabBar(
          controller: controller,
          tabs: [
            _SearchTab(
              icon: Icons.trending_up,
              label: L10n.of(context).popular,
            ),
            _SearchTab(icon: Icons.schedule, label: L10n.of(context).recent),
            _SearchTab(
              icon: Icons.perm_media_outlined,
              label: L10n.of(context).media,
            ),
            _SearchTab(
              icon: Icons.person_search_outlined,
              label: L10n.of(context).account,
            ),
          ],
        ),
        if (filterChips.isNotEmpty) SearchFilterStrip(chips: filterChips),
      ],
    );
  }
}

class _SearchTab extends StatelessWidget {
  final IconData icon;
  final String label;

  const _SearchTab({required this.icon, required this.label});

  @override
  Widget build(BuildContext context) {
    return Tab(
      height: kSearchTabsHeight,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: kTweetActionIconSize),
          const SizedBox(width: kTweetSpace2),
          Text(label),
        ],
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
    return ScopedBuilder<SearchUsersModel, List<UserWithExtra>>.transition(
      store: store,
      onLoading: (_) => const ProfileUserListSkeleton(),
      onError: (_, error) => FullPageErrorWidget(
        error: error,
        stackTrace: null,
        prefix: L10n.of(context).unable_to_load_the_search_results,
        onRetry: onRetry,
      ),
      onState: (_, items) {
        if (items.isEmpty)
          return TweetEmptyState(message: L10n.of(context).no_results);
        return ListView.builder(
          keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
          padding: EdgeInsets.only(
            bottom: MediaQuery.paddingOf(context).bottom,
          ),
          itemCount: items.length,
          itemBuilder: (context, index) =>
              UserTile(user: UserSubscription.fromUser(items[index])),
        );
      },
    );
  }
}
