import 'package:flutter/material.dart';
import 'package:infinite_scroll_pagination/infinite_scroll_pagination.dart';
import 'package:xta/client/client.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/profile/profile_chrome.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/reader_chrome.dart';
import 'package:xta/ui/reader_failure.dart';
import 'package:xta/user.dart';
import 'package:xta/utils/paging.dart';
import 'package:xta/utils/read_recovery.dart';

/// A community's members, paged as X sends them. Each row opens a profile.
class CommunityMembersScreen extends StatefulWidget {
  final Community community;

  /// A stand-in for X, for tests.
  final Future<Follows> Function(String id, String? cursor)? readMembers;

  const CommunityMembersScreen({super.key, required this.community, this.readMembers});

  @override
  State<CommunityMembersScreen> createState() => _CommunityMembersScreenState();
}

class _CommunityMembersScreenState extends State<CommunityMembersScreen> {
  late final CursorPagingController<String, UserWithExtra> _paging = CursorPagingController(_fetchPage);
  final Set<String> _seenIds = {};
  bool _firstLoadStarted = false;

  PagingController<int, UserWithExtra> get _pagingController => _paging.pagingController;

  @override
  void dispose() {
    _paging.dispose();
    super.dispose();
  }

  Future<CursorPage<String, UserWithExtra>> _fetchPage(String? cursor) async {
    final read = widget.readMembers ?? (id, cursor) => Twitter.getCommunityMembers(id, cursor: cursor);
    final page = await read(widget.community.id, cursor);
    final fresh = page.users.where((user) => user.idStr != null && _seenIds.add(user.idStr!)).toList();
    final next = page.cursorBottom;
    final end = next == null || next.isEmpty || next == cursor || fresh.isEmpty;
    return (items: fresh, nextCursor: end ? null : next);
  }

  @override
  Widget build(BuildContext context) {
    scheduleFirstPageFetch(
      _pagingController,
      alreadyStarted: _firstLoadStarted,
      markStarted: () => _firstLoadStarted = true,
      isMounted: () => mounted,
    );
    final l10n = L10n.of(context);
    return XtaSystemBars(
      child: Scaffold(
        appBar: AppBar(
          title: Text(l10n.community_members),
          surfaceTintColor: Colors.transparent,
          scrolledUnderElevation: 0,
        ),
        body: PagingReadRecovery(
          controller: _pagingController,
          child: PagingListener<int, UserWithExtra>(
            controller: _pagingController,
            builder: (context, state, fetchNextPage) {
              if (pagingAwaitingFirstPage(state)) return const ProfileUserListSkeleton();
              if (state.items == null) {
                return FullPageErrorWidget(
                  error: pagingErrorOf(state)?.error,
                  stackTrace: pagingErrorOf(state)?.stackTrace,
                  prefix: l10n.unable_to_load_the_members,
                  onRetry: fetchNextPage,
                );
              }
              if (state.items!.isEmpty) {
                return ProfileEmptyState(icon: Icons.people_outline, message: l10n.no_results);
              }
              return PagedListView<int, UserWithExtra>(
                padding: EdgeInsets.only(bottom: MediaQuery.paddingOf(context).bottom),
                state: state,
                fetchNextPage: fetchNextPage,
                addAutomaticKeepAlives: false,
                builderDelegate: PagedChildBuilderDelegate(
                  itemBuilder: (context, user, index) =>
                      UserTile(user: subscriptionForUser(user), verification: user.badges),
                  newPageErrorIndicatorBuilder: (context) => ReaderFailureNotice(
                    error: pagingErrorOf(state)?.error ?? state.error,
                    contextMessage: l10n.unable_to_load_the_members,
                    onRetry: fetchNextPage,
                  ),
                ),
              );
            },
          ),
        ),
      ),
    );
  }
}
