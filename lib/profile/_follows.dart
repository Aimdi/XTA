import 'package:flutter/material.dart';

import 'package:xta/client/client.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/user.dart';
import 'package:infinite_scroll_pagination/infinite_scroll_pagination.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/utils/paging.dart';

class ProfileFollows extends StatefulWidget {
  final UserWithExtra user;
  final String type;

  const ProfileFollows({super.key, required this.user, required this.type});

  @override
  State<ProfileFollows> createState() => _ProfileFollowsState();
}

class _ProfileFollowsState extends State<ProfileFollows> with AutomaticKeepAliveClientMixin<ProfileFollows> {
  late final CursorPagingController<String, UserWithExtra> _paging;
  PagingController<int, UserWithExtra> get _pagingController => _paging.pagingController;

  final int _pageSize = 200;
  final Set<String> _seenIds = {};
  bool _firstLoadStarted = false;

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

  void _maybeStartFirstLoad() {
    scheduleFirstPageFetch(
      _pagingController,
      alreadyStarted: _firstLoadStarted,
      markStarted: () => _firstLoadStarted = true,
      isMounted: () => mounted,
    );
  }

  Future<CursorPage<String, UserWithExtra>> _fetchPage(String? cursor) async {
    var result = await Twitter.getProfileFollows(widget.user.screenName!, widget.type,
        cursor: cursor, count: _pageSize, id: widget.user.idStr);

    final next = result.cursorBottom;
    final fresh = result.users.where((u) => u.idStr != null && _seenIds.add(u.idStr!)).toList();
    final end = next == null || next.isEmpty || next == '0' || next == cursor || fresh.isEmpty;
    return (items: fresh, nextCursor: end ? null : next);
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    _maybeStartFirstLoad();
    final l10n = L10n.of(context);
    final emptyText = widget.type == 'following'
        ? l10n.this_user_does_not_follow_anyone
        : l10n.this_user_does_not_have_anyone_following_them;

    return Scaffold(
      appBar: AppBar(
        title: Text(widget.type == 'following' ? l10n.following : l10n.followers),
      ),
      body: PagingListener<int, UserWithExtra>(
        controller: _pagingController,
        builder: (context, state, fetchNextPage) {
          if (pagingAwaitingFirstPage(state)) {
            return const Center(child: CircularProgressIndicator());
          }
          if (state.items == null) {
            return FullPageErrorWidget(
              error: pagingErrorOf(state)?.error,
              stackTrace: pagingErrorOf(state)?.stackTrace,
              prefix: l10n.unable_to_load_the_list_of_follows,
              onRetry: fetchNextPage,
            );
          }
          if (state.items!.isEmpty) {
            return Center(child: Text(emptyText));
          }
          return PagedListView<int, UserWithExtra>(
            padding: EdgeInsets.only(bottom: MediaQuery.of(context).padding.bottom),
            state: state,
            fetchNextPage: fetchNextPage,
            addAutomaticKeepAlives: false,
            builderDelegate: PagedChildBuilderDelegate(
              itemBuilder: (context, user, index) =>
                  UserTile(user: UserSubscription.fromUser(user)),
              newPageErrorIndicatorBuilder: (context) => FullPageErrorWidget(
                error: pagingErrorOf(state)?.error,
                stackTrace: pagingErrorOf(state)?.stackTrace,
                prefix: l10n.unable_to_load_the_next_page_of_follows,
                onRetry: fetchNextPage,
              ),
            ),
          );
        },
      ),
    );
  }
}
