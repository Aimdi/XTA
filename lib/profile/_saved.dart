import 'package:flutter/material.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/_saved.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/tweet/sensitive_media_gate.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/user.dart';
import 'package:infinite_scroll_pagination/infinite_scroll_pagination.dart';
import 'package:xta/utils/paging.dart';
import 'package:provider/provider.dart';

class ProfileSaved extends StatefulWidget {
  final UserWithExtra user;

  const ProfileSaved({super.key, required this.user});

  @override
  State<ProfileSaved> createState() => _ProfileSavedState();
}

class _ProfileSavedState extends State<ProfileSaved> {
  late final CursorPagingController<int, SavedTweet> _paging;
  PagingController<int, SavedTweet> get _pagingController => _paging.pagingController;
  bool _firstLoadStarted = false;

  @override
  void initState() {
    super.initState();
    _paging = CursorPagingController<int, SavedTweet>(_loadTweets);
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

  // Saved tweets are a single, non-paginated page (nextCursor always null).
  Future<CursorPage<int, SavedTweet>> _loadTweets(int? cursor) async {
    var model = context.read<SavedTweetModel>();
    await model.listSavedTweets();

    final saved = model.state.where((tweet) => tweet.user == widget.user.idStr).toList();
    return (items: saved, nextCursor: null);
  }

  @override
  Widget build(BuildContext context) {
    _maybeStartFirstLoad();
    return SensitiveMediaGate(
      sensitive: widget.user.possiblySensitive ?? false,
      errorMessage: L10n.current.possibly_sensitive_profile,
      wrapInCard: false,
      child: PagingListener<int, SavedTweet>(
        controller: _pagingController,
        builder: (context, state, fetchNextPage) {
          if (pagingAwaitingFirstPage(state)) {
            return pagingFill(
              child: const Center(child: CircularProgressIndicator()),
            );
          }
          if (state.items == null) {
            return FullPageErrorWidget(
              error: pagingErrorOf(state)?.error,
              stackTrace: pagingErrorOf(state)?.stackTrace,
              prefix: L10n.of(context).unable_to_load_the_tweets,
              onRetry: fetchNextPage,
            );
          }
          if (state.items!.isEmpty) {
            return pagingFill(
              child: Center(
                child: Text(L10n.of(context).you_have_not_saved_any_tweets_yet),
              ),
            );
          }
          return PagedListView<int, SavedTweet>(
            padding: EdgeInsets.zero,
            state: state,
            fetchNextPage: fetchNextPage,
            addAutomaticKeepAlives: false,
            builderDelegate: PagedChildBuilderDelegate(
              itemBuilder: (context, savedTweet, index) =>
                  SavedTweetTile(id: savedTweet.id, content: savedTweet.content),
              newPageErrorIndicatorBuilder: (context) => FullPageErrorWidget(
                error: pagingErrorOf(state)?.error,
                stackTrace: pagingErrorOf(state)?.stackTrace,
                prefix: L10n.of(context).unable_to_load_the_next_page_of_tweets,
                onRetry: fetchNextPage,
              ),
            ),
          );
        },
      ),
    );
  }
}
