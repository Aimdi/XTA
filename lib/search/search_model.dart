import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:infinite_scroll_pagination/infinite_scroll_pagination.dart';
import 'package:quax/client/client.dart';
import 'package:quax/profile/media_grid/media_grid_items/media_grid_item.dart';
import 'package:quax/search/advanced_search_model.dart';
import 'package:quax/tweet/paginated_tweet_list.dart';
import 'package:quax/user.dart';
import 'package:quax/utils/paging.dart';

/// Holds the paging controller and loader for one tab of the tweet search
/// (Top / Latest). The query string is mutable — [updateQuery] swaps it in and
/// refreshes the controller so the next page request hits the new query.
class SearchTweetsPagination {
  final TweetFeedController feed = TweetFeedController();
  final String product;
  String _query;

  SearchTweetsPagination({required this.product, String initialQuery = ''})
    : _query = initialQuery;

  Future<TweetPageResult> loadPage(String? cursor) async {
    if (_query.trim().isEmpty) {
      return (chains: <TweetChain>[], nextCursor: null);
    }
    final result = await Twitter.searchTweets(
      _query,
      true,
      product: product,
      cursor: cursor,
    );
    return (chains: result.chains, nextCursor: result.cursorBottom);
  }

  void updateQuery(String newQuery, {bool force = false}) {
    if (!force && newQuery == _query) return;
    _query = newQuery;
    feed.controller.refresh();
  }

  void dispose() {
    feed.dispose();
  }
}

class SearchMediaPagination {
  late final CursorPagingController<String, MediaGridItem> _paging =
      CursorPagingController(_loadPage);
  String _query;

  SearchMediaPagination({String initialQuery = ''}) : _query = initialQuery;

  PagingController<int, MediaGridItem> get pagingController =>
      _paging.pagingController;

  Future<CursorPage<String, MediaGridItem>> _loadPage(String? cursor) async {
    if (_query.trim().isEmpty) {
      return (items: const <MediaGridItem>[], nextCursor: null);
    }
    final result = await Twitter.searchTweets(
      _query,
      true,
      product: 'Media',
      cursor: cursor,
    );
    return mediaPageFromStatus(result, cursor);
  }

  void updateQuery(String newQuery, {bool force = false}) {
    if (!force && newQuery == _query) return;
    _query = newQuery;
    _paging.pagingController.refresh();
  }

  void dispose() {
    _paging.dispose();
  }
}

class SearchUsersModel extends Store<List<UserWithExtra>> {
  SearchUsersModel() : super([]);

  Future<void> searchUsers(String query) async {
    await execute(() async {
      if (query.trim().isEmpty) return [];
      return Twitter.searchUsers(query);
    });
  }
}

@immutable
class SearchViewState {
  final String draftQuery;
  final String submittedQuery;
  final int tabIndex;
  final AdvancedSearchState? advancedSearch;

  const SearchViewState({
    required this.draftQuery,
    required this.submittedQuery,
    required this.tabIndex,
    this.advancedSearch,
  });

  SearchViewState copyWith({
    String? draftQuery,
    String? submittedQuery,
    int? tabIndex,
    AdvancedSearchState? advancedSearch,
    bool clearAdvancedSearch = false,
  }) {
    return SearchViewState(
      draftQuery: draftQuery ?? this.draftQuery,
      submittedQuery: submittedQuery ?? this.submittedQuery,
      tabIndex: tabIndex ?? this.tabIndex,
      advancedSearch: clearAdvancedSearch
          ? null
          : advancedSearch ?? this.advancedSearch,
    );
  }
}

class SearchViewStore extends Store<SearchViewState> {
  SearchViewStore({required String initialQuery, required int initialTab})
    : super(
        SearchViewState(
          draftQuery: initialQuery,
          submittedQuery: initialQuery,
          tabIndex: initialTab,
        ),
      );

  void editDraft(String query) {
    if (query == state.draftQuery) return;
    update(state.copyWith(draftQuery: query, clearAdvancedSearch: true));
  }

  void submit(String query) {
    update(state.copyWith(draftQuery: query, submittedQuery: query));
  }

  void applyAdvanced(AdvancedSearchState advancedSearch) {
    final query = advancedSearch.query;
    update(
      state.copyWith(
        draftQuery: query,
        submittedQuery: query,
        advancedSearch: advancedSearch,
      ),
    );
  }

  void selectTab(int index) {
    if (index != state.tabIndex) update(state.copyWith(tabIndex: index));
  }

  void clear() {
    update(
      SearchViewState(
        draftQuery: '',
        submittedQuery: '',
        tabIndex: state.tabIndex,
      ),
    );
  }
}
