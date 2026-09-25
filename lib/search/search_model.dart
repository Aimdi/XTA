import 'package:xta/catcher/exceptions.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:infinite_scroll_pagination/infinite_scroll_pagination.dart';
import 'package:xta/client/client.dart';
import 'package:xta/profile/media_grid/media_grid_items/media_grid_item.dart';
import 'package:xta/search/advanced_search_model.dart';
import 'package:xta/tweet/paginated_tweet_list.dart';
import 'package:xta/user.dart';
import 'package:xta/utils/paging.dart';
import 'package:xta/utils/read_request_scope.dart';

@immutable
class SearchViewState {
  final String query;
  final AdvancedSearchState advanced;

  const SearchViewState({this.query = '', this.advanced = const AdvancedSearchState()});

  bool get hasQuery => query.isNotEmpty;

  SearchViewState copyWith({String? query, AdvancedSearchState? advanced}) {
    return SearchViewState(query: query ?? this.query, advanced: advanced ?? this.advanced);
  }
}

/// Committed result query plus the optional structured filters that produced
/// it. The text controller remains a draft until debounce or IME submission.
class SearchViewStore extends Store<SearchViewState> {
  SearchViewStore({String initialQuery = ''}) : super(SearchViewState(query: initialQuery.trim()));

  void commitQuery(String query) {
    final trimmed = query.trim();
    final advanced = state.advanced.query == trimmed ? state.advanced : const AdvancedSearchState();
    update(SearchViewState(query: trimmed, advanced: advanced));
  }

  void invalidateAdvancedForDraft(String query) {
    if (state.advanced.activeFilters.isEmpty || state.advanced.query == query.trim()) {
      return;
    }
    update(state.copyWith(advanced: const AdvancedSearchState()));
  }

  void applyAdvanced(AdvancedSearchState advanced) {
    update(SearchViewState(query: advanced.query, advanced: advanced));
  }

  AdvancedSearchState clearFilter(AdvancedSearchFilter filter) {
    final advanced = state.advanced.clear(filter);
    update(SearchViewState(query: advanced.query, advanced: advanced));
    return advanced;
  }

  void clear() => update(const SearchViewState());
}

/// Holds the paging controller and loader for one tab of the tweet search
/// (Top / Latest). The query string is mutable — [updateQuery] swaps it in and
/// refreshes the controller so the next page request hits the new query.
class SearchTweetsPagination {
  final TweetFeedController feed = TweetFeedController();
  final String product;
  String _query;

  SearchTweetsPagination({required this.product, String initialQuery = ''}) : _query = initialQuery;

  Future<TweetPageResult> loadPage(String? cursor) async {
    if (_query.isEmpty) {
      return (chains: <TweetChain>[], nextCursor: null);
    }
    final result = await withRateLimitOperations([
      'SearchTimeline',
    ], () => Twitter.searchTweets(_query, true, product: product, cursor: cursor));
    return (chains: result.chains, nextCursor: result.cursorBottom);
  }

  void updateQuery(String newQuery) {
    if (newQuery == _query) return;
    _query = newQuery;
    feed.controller.refresh();
  }

  void dispose() {
    feed.dispose();
  }
}

class SearchMediaPagination {
  late final CursorPagingController<String, MediaGridItem> _paging = CursorPagingController(_loadPage);
  String _query;

  SearchMediaPagination({String initialQuery = ''}) : _query = initialQuery;

  PagingController<int, MediaGridItem> get pagingController => _paging.pagingController;

  Future<CursorPage<String, MediaGridItem>> _loadPage(String? cursor) async {
    if (_query.isEmpty) {
      return (items: const <MediaGridItem>[], nextCursor: null);
    }
    final result = await withRateLimitOperations([
      'SearchTimeline',
    ], () => Twitter.searchTweets(_query, true, product: 'Media', cursor: cursor));
    return mediaPageFromStatus(result, cursor);
  }

  void updateQuery(String newQuery) {
    if (newQuery == _query) return;
    _query = newQuery;
    _paging.pagingController.refresh();
  }

  void dispose() {
    _paging.dispose();
  }
}

class SearchUsersModel extends Store<List<UserWithExtra>> {
  final Future<List<UserWithExtra>> Function(String) _search;
  final Duration requestTimeout;
  final _reads = ReadRequestScope();
  int _generation = 0;
  bool _closed = false;

  SearchUsersModel({
    Future<List<UserWithExtra>> Function(String)? search,
    this.requestTimeout = const Duration(seconds: 30),
  }) : _search = search ?? Twitter.searchUsers,
       super([]);

  @override
  dynamic get error => triple.error;

  Future<void> searchUsers(String query) async {
    if (_closed) return;
    final generation = ++_generation;
    _reads.cancel();
    bool current() => !_closed && generation == _generation;
    final trimmed = query.trim();
    update([], force: true);
    setLoading(trimmed.isNotEmpty, force: true);
    if (trimmed.isEmpty) return;
    try {
      final users = await _reads.start(
        () => withRateLimitOperations(['SearchTimeline'], () => _search(trimmed)),
        timeout: requestTimeout,
      );
      if (current()) update(users, force: true);
    } catch (error) {
      if (current()) setError(error, force: true);
    } finally {
      if (current()) setLoading(false, force: true);
    }
  }

  @override
  Future<void> destroy() {
    _closed = true;
    _generation++;
    _reads.cancel();
    return super.destroy();
  }
}
