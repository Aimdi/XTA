import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_errors.dart';
import 'package:xta/plugins/booru/booru_grid.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_store.dart';
import 'package:xta/plugins/plugin_feed_skeleton.dart';
import 'package:xta/ui/empty_pane.dart';
import 'package:xta/ui/errors.dart';

/// A feed's posts: the skeleton until its first page, then the grid, which
/// pages on as it scrolls; the error page, or a notice over the posts when a
/// refresh failed; [emptyLabel] when the feed has nothing to show. Given
/// [isNew], the posts that came in since the last visit are counted and
/// marked.
class BooruFeedView extends StatelessWidget {
  final BooruFeedStore store;
  final String emptyLabel;
  final ScrollController? scrollController;
  final bool Function(BooruPost post)? isNew;

  const BooruFeedView({super.key, required this.store, required this.emptyLabel, this.scrollController, this.isNew});

  @override
  Widget build(BuildContext context) => Column(
    children: [
      BooruRefreshNotice(feed: store),
      BooruHiddenNotice(feed: store),
      if (isNew case final isNew?) BooruNewPostsNotice(feed: store, isNew: isNew),
      Expanded(child: _posts(context)),
    ],
  );

  Widget _posts(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<BooruFeedStore, List<BooruPost>>(
      store: store,
      onLoading: (_) => store.state.isNotEmpty
          ? BooruPostGrid(
              posts: store.state,
              feed: store,
              isNew: isNew,
              scrollController: scrollController,
              onRefresh: store.refresh,
              onNearEnd: store.loadMore,
            )
          : const PluginGridSkeleton(columns: 2),
      onError: (_, error) => FullPageErrorWidget(
        error: error,
        stackTrace: null,
        prefix: booruErrorMessage(l10n, error),
        onRetry: () => store.refresh(),
      ),
      onState: (context, posts) {
        if (posts.isEmpty) {
          return BooruEmptyFeed(
            feed: store,
            scrollController: scrollController,
            empty: EmptyPane(
              icon: Icons.photo_outlined,
              message: emptyLabel,
              scrollController: scrollController,
              onRefresh: store.refresh,
              action: FilledButton.icon(
                onPressed: store.refresh,
                icon: const Icon(Icons.refresh),
                label: Text(l10n.retry),
              ),
            ),
          );
        }
        return BooruPostGrid(
          posts: posts,
          feed: store,
          isNew: isNew,
          scrollController: scrollController,
          onRefresh: store.refresh,
          onNearEnd: store.loadMore,
        );
      },
    );
  }
}
