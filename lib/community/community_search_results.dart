import 'package:flutter/material.dart';
import 'package:infinite_scroll_pagination/infinite_scroll_pagination.dart';
import 'package:xta/client/client.dart';
import 'package:xta/community/community_tile.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/profile/profile_chrome.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/reader_failure.dart';
import 'package:xta/utils/paging.dart';

/// The Communities tab of search. [updateQuery] swaps the query in and starts
/// the list over.
class CommunitySearchPagination {
  late final CursorPagingController<String, Community> _paging = CursorPagingController(_loadPage);
  final Future<CommunitySearchPage> Function(String query, String? cursor) _search;
  final Set<String> _seenIds = {};
  String _query;

  CommunitySearchPagination({Future<CommunitySearchPage> Function(String query, String? cursor)? search})
    : _search = search ?? ((query, cursor) => Twitter.searchCommunities(query, cursor: cursor)),
      _query = '';

  PagingController<int, Community> get pagingController => _paging.pagingController;

  Future<CursorPage<String, Community>> _loadPage(String? cursor) async {
    if (_query.isEmpty) return (items: const <Community>[], nextCursor: null);
    if (cursor == null) _seenIds.clear();
    final page = await _search(_query, cursor);
    final fresh = page.communities.where((community) => _seenIds.add(community.id)).toList();
    final next = page.cursor;
    final end = next == null || next.isEmpty || next == cursor || fresh.isEmpty;
    return (items: fresh, nextCursor: end ? null : next);
  }

  void updateQuery(String query) {
    if (query == _query) return;
    _query = query;
    _paging.pagingController.refresh();
  }

  void dispose() => _paging.dispose();
}

class CommunitySearchResults extends StatelessWidget {
  final CommunitySearchPagination model;

  const CommunitySearchResults({super.key, required this.model});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return PagingListener<int, Community>(
      controller: model.pagingController,
      builder: (context, state, fetchNextPage) {
        // Nothing asks for the first page of a new query, so the list does.
        if (state.pages == null && state.error == null && !state.isLoading) {
          WidgetsBinding.instance.addPostFrameCallback((_) {
            final now = model.pagingController.value;
            if (now.pages == null && now.error == null && !now.isLoading) fetchNextPage();
          });
        }
        if (pagingAwaitingFirstPage(state)) return const ProfileUserListSkeleton();
        if (state.items == null) {
          return FullPageErrorWidget(
            error: pagingErrorOf(state)?.error,
            stackTrace: pagingErrorOf(state)?.stackTrace,
            prefix: l10n.unable_to_load_the_search_results,
            onRetry: fetchNextPage,
          );
        }
        if (state.items!.isEmpty) return ProfileEmptyState(icon: Icons.groups_outlined, message: l10n.no_results);
        return PagedListView<int, Community>(
          state: state,
          fetchNextPage: fetchNextPage,
          addAutomaticKeepAlives: false,
          builderDelegate: PagedChildBuilderDelegate(
            itemBuilder: (context, community, index) => CommunityTile(community: community),
            newPageErrorIndicatorBuilder: (context) => ReaderFailureNotice(
              error: pagingErrorOf(state)?.error ?? state.error,
              contextMessage: l10n.unable_to_load_the_search_results,
              onRetry: fetchNextPage,
            ),
          ),
        );
      },
    );
  }
}
