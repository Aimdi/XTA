import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/_saved.dart';
import 'package:xta/profile/archive_filter.dart';
import 'package:xta/saved/liked_tweet_model.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/tweet/sensitive_media_gate.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/user.dart';
import 'package:infinite_scroll_pagination/infinite_scroll_pagination.dart';
import 'package:xta/utils/paging.dart';
import 'package:provider/provider.dart';

class ProfileSaved extends StatefulWidget {
  final UserWithExtra user;
  final ArchiveFilter filter;

  const ProfileSaved({
    super.key,
    required this.user,
    this.filter = ArchiveFilter.all,
  });

  @override
  State<ProfileSaved> createState() => _ProfileSavedState();
}

class _ProfileSavedState extends State<ProfileSaved> {
  late CursorPagingController<int, ArchiveItem> _paging;
  PagingController<int, ArchiveItem> get _pagingController =>
      _paging.pagingController;

  @override
  void initState() {
    super.initState();
    _paging = CursorPagingController<int, ArchiveItem>(_loadTweets);
  }

  @override
  void didUpdateWidget(covariant ProfileSaved oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.filter != oldWidget.filter ||
        widget.user.idStr != oldWidget.user.idStr) {
      _paging.dispose();
      _paging = CursorPagingController<int, ArchiveItem>(_loadTweets);
    }
  }

  @override
  void dispose() {
    _paging.dispose();
    super.dispose();
  }

  String get _emptyMessage {
    final l10n = L10n.of(context);
    return widget.filter == ArchiveFilter.likes
        ? l10n.no_liked_posts_yet
        : l10n.you_have_not_saved_any_tweets_yet;
  }

  // Archive is a single, non-paginated page (nextCursor always null).
  Future<CursorPage<int, ArchiveItem>> _loadTweets(int? cursor) async {
    final savedModel = context.read<SavedTweetModel>();
    final likedModel = context.read<LikedTweetModel>();
    await Future.wait([
      savedModel.listSavedTweets(),
      likedModel.listLikedTweets(),
    ]);

    final userId = widget.user.idStr;
    if (userId == null) {
      return (items: const <ArchiveItem>[], nextCursor: null);
    }

    return (
      items: profileArchiveItems(
        saved: savedModel.state,
        liked: likedModel.state,
        userId: userId,
        filter: widget.filter,
      ),
      nextCursor: null,
    );
  }

  @override
  Widget build(BuildContext context) {
    return SensitiveMediaGate(
      sensitive: widget.user.possiblySensitive ?? false,
      errorMessage: L10n.current.possibly_sensitive_profile,
      wrapInCard: false,
      child: PagingListener<int, ArchiveItem>(
        controller: _pagingController,
        builder: (context, state, fetchNextPage) =>
            PagedListView<int, ArchiveItem>(
          padding: EdgeInsets.zero,
          state: state,
          fetchNextPage: fetchNextPage,
          addAutomaticKeepAlives: false,
          builderDelegate: PagedChildBuilderDelegate(
            itemBuilder: (context, item, index) =>
                SavedTweetTile(id: item.id, content: item.content),
            firstPageErrorIndicatorBuilder: (context) => FullPageErrorWidget(
              error: pagingErrorOf(state)?.error,
              stackTrace: pagingErrorOf(state)?.stackTrace,
              prefix: L10n.of(context).unable_to_load_the_tweets,
              onRetry: fetchNextPage,
            ),
            newPageErrorIndicatorBuilder: (context) => FullPageErrorWidget(
              error: pagingErrorOf(state)?.error,
              stackTrace: pagingErrorOf(state)?.stackTrace,
              prefix: L10n.of(
                context,
              ).unable_to_load_the_next_page_of_tweets,
              onRetry: fetchNextPage,
            ),
            noItemsFoundIndicatorBuilder: (context) {
              return Center(child: Text(_emptyMessage));
            },
          ),
        ),
      ),
    );
  }
}
