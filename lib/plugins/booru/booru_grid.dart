import 'dart:math' show max;
import 'dart:ui' show ImageFilter;

import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show ScrollCacheExtent;
import 'package:flutter_staggered_grid_view/flutter_staggered_grid_view.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_display.dart';
import 'package:xta/plugins/booru/booru_errors.dart';
import 'package:xta/plugins/booru/booru_image.dart';
import 'package:xta/plugins/booru/booru_labels.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_post_pager.dart';
import 'package:xta/plugins/booru/booru_search_store.dart';
import 'package:xta/plugins/booru/booru_store.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/ui/empty_pane.dart';

String booruPostHeroTag(BooruPost post) => 'booru-${post.host}-${post.id}';

class BooruPostGrid extends StatelessWidget {
  final List<BooruPost> posts;
  final ScrollController? scrollController;
  final Future<void> Function()? onRefresh;
  final VoidCallback? onNearEnd;
  final EdgeInsetsGeometry padding;

  /// Where [posts] come from, so the viewer can keep loading past them.
  final BooruFeedStore? feed;

  const BooruPostGrid({
    super.key,
    required this.posts,
    this.scrollController,
    this.onRefresh,
    this.onNearEnd,
    this.padding = const EdgeInsets.all(8),
    this.feed,
  });

  @override
  Widget build(BuildContext context) {
    final display = BooruDisplay.of(context);
    return LayoutBuilder(
      builder: (context, constraints) {
        final grid = NotificationListener<ScrollNotification>(
          onNotification: (notification) {
            if (onNearEnd == null) return false;
            final metrics = notification.metrics;
            if (metrics.pixels >= metrics.maxScrollExtent - 800) {
              onNearEnd!();
            }
            return false;
          },
          child: CustomScrollView(
            controller: pluginInnerScrollController(context, scrollController),
            primary: PluginEmbedded.maybeOf(context) ? false : null,
            scrollCacheExtent: const ScrollCacheExtent.pixels(1200),
            physics: const AlwaysScrollableScrollPhysics(),
            slivers: [
              SliverPadding(
                padding: padding,
                sliver: SliverMasonryGrid.count(
                  crossAxisCount: display.columnsFor(constraints.maxWidth, MediaQuery.textScalerOf(context)),
                  mainAxisSpacing: 8,
                  crossAxisSpacing: 8,
                  childCount: posts.length,
                  itemBuilder: (context, index) => BooruPostTile(
                    post: posts[index],
                    display: display,
                    onOpen: () => openBooruPost(context, posts, index, feed: feed),
                  ),
                ),
              ),
              if (feed case final feed?) SliverToBoxAdapter(child: _PagingFooter(feed: feed)),
            ],
          ),
        );

        if (onRefresh == null) return grid;
        return RefreshIndicator(onRefresh: onRefresh!, child: grid);
      },
    );
  }
}

/// Under the grid: a spinner while the next page loads, a retry button when
/// it failed, and a note once the feed has nothing more.
class _PagingFooter extends StatelessWidget {
  final BooruFeedStore feed;

  const _PagingFooter({required this.feed});

  @override
  Widget build(BuildContext context) => ScopedBuilder<BooruPagingStore, BooruPaging>(
    store: feed.paging,
    onState: (context, paging) => switch (_content(context, paging)) {
      final content? => Padding(
        padding: const EdgeInsets.all(16),
        child: Center(child: content),
      ),
      null => const SizedBox.shrink(),
    },
  );

  Widget? _content(BuildContext context, BooruPaging paging) {
    final l10n = L10n.of(context);
    return switch (paging) {
      BooruPaging.idle => null,
      BooruPaging.loading => const CircularProgressIndicator(),
      BooruPaging.failed => Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(l10n.plugin_booru_load_more_failed, textAlign: TextAlign.center),
          TextButton.icon(onPressed: feed.retryMore, icon: const Icon(Icons.refresh), label: Text(l10n.retry)),
        ],
      ),
      BooruPaging.end => Text(
        l10n.plugin_booru_end_of_results,
        style: Theme.of(context).textTheme.bodySmall?.copyWith(color: Theme.of(context).hintColor),
      ),
    };
  }
}

/// What an empty feed shows. While the host has more pages, the posts so far
/// were all hidden by the rating limit, the blacklist or a missing file, so
/// the reader can go on to the next pages instead of being stuck on the first.
class BooruEmptyFeed extends StatelessWidget {
  final BooruFeedStore feed;

  /// Shown once the host has nothing more.
  final Widget empty;
  final ScrollController? scrollController;

  const BooruEmptyFeed({super.key, required this.feed, required this.empty, this.scrollController});

  @override
  Widget build(BuildContext context) => ScopedBuilder<BooruPagingStore, BooruPaging>(
    store: feed.paging,
    onState: (context, paging) => paging == BooruPaging.end ? empty : _hiddenSoFar(context, paging),
  );

  Widget _hiddenSoFar(BuildContext context, BooruPaging paging) {
    final l10n = L10n.of(context);
    final failed = paging == BooruPaging.failed;
    return EmptyPane(
      icon: Icons.visibility_off_outlined,
      message: failed ? l10n.plugin_booru_load_more_failed : l10n.plugin_booru_hidden_so_far,
      scrollController: scrollController,
      onRefresh: feed.refresh,
      action: paging == BooruPaging.loading
          ? const CircularProgressIndicator()
          : FilledButton.icon(
              key: const ValueKey('booru-load-more'),
              onPressed: failed ? feed.retryMore : feed.loadMore,
              icon: Icon(failed ? Icons.refresh : Icons.expand_more),
              label: Text(failed ? l10n.retry : l10n.plugin_booru_load_more),
            ),
    );
  }
}

/// Above a feed whose refresh failed: why, that the posts from before are
/// still shown, and Retry.
class BooruRefreshNotice extends StatelessWidget {
  final BooruFeedStore feed;

  const BooruRefreshNotice({super.key, required this.feed});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<BooruRefreshFailureStore, ({Object? error})>(
      store: feed.refreshFailure,
      onState: (context, failure) => switch (failure.error) {
        null => const SizedBox.shrink(),
        final error => ListTile(
          leading: const Icon(Icons.cloud_off_outlined),
          title: Text(booruErrorMessage(l10n, error)),
          subtitle: Text(l10n.plugin_booru_showing_earlier),
          trailing: IconButton(icon: const Icon(Icons.refresh), tooltip: l10n.retry, onPressed: feed.refresh),
        ),
      },
    );
  }
}

/// Opens [posts] at [index] in the post viewer, which swipes through the rest
/// and, given the [feed] they came from, on into what it loads next.
void openBooruPost(BuildContext context, List<BooruPost> posts, int index, {BooruFeedStore? feed}) {
  final search = context.read<BooruSearchStore?>();
  Navigator.push(
    context,
    MaterialPageRoute(
      builder: (_) => BooruPostPager(posts: posts, initialIndex: index, search: search, feed: feed),
    ),
  );
}

class BooruPostTile extends StatelessWidget {
  final BooruPost post;
  final BooruDisplay display;
  final VoidCallback onOpen;

  const BooruPostTile({super.key, required this.post, required this.onOpen, this.display = const BooruDisplay()});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    final ratio = post.aspectRatio.clamp(0.45, 1.6);

    return Material(
      color: theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.35),
      borderRadius: BorderRadius.circular(8),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onOpen,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            AspectRatio(
              aspectRatio: ratio,
              child: Stack(
                fit: StackFit.expand,
                children: [
                  Semantics(
                    image: true,
                    label: booruTileLabel(l10n, post),
                    child: Hero(
                      tag: booruPostHeroTag(post),
                      child: RepaintBoundary(child: _image(theme, ratio)),
                    ),
                  ),
                  if (display.blurs(post))
                    Center(child: Icon(Icons.visibility_off_outlined, color: theme.colorScheme.onSurface)),
                  if (post.isVideo)
                    Positioned(
                      top: 6,
                      left: 6,
                      child: _chip(context, Icons.play_circle_outline, l10n.plugin_booru_video),
                    ),
                  if (post.rating == BooruRating.explicit || post.rating == BooruRating.questionable)
                    Positioned(
                      top: 6,
                      right: 6,
                      child: _chip(context, Icons.warning_amber_outlined, post.rating!.code.toUpperCase()),
                    ),
                ],
              ),
            ),
            if (display.tileDetails) ...[
              Padding(
                padding: const EdgeInsetsDirectional.fromSTEB(8, 8, 8, 0),
                child: Text(
                  post.host,
                  style: theme.textTheme.labelMedium,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              if (post.tags.isNotEmpty)
                Padding(
                  padding: const EdgeInsetsDirectional.fromSTEB(8, 4, 8, 0),
                  child: Text(
                    post.tags.take(4).join(' · '),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.bodySmall,
                  ),
                ),
              if (post.score != null)
                Padding(
                  padding: const EdgeInsets.fromLTRB(8, 6, 8, 8),
                  child: Text(
                    l10n.plugin_booru_score(post.score!),
                    style: theme.textTheme.labelSmall,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
            ],
          ],
        ),
      ),
    );
  }

  /// The picture at the size the tile shows it: as wide as the tile, or
  /// wider where the tile crops a picture wider than [ratio].
  Widget _image(ThemeData theme, double ratio) => LayoutBuilder(
    builder: (context, constraints) {
      final width = constraints.maxWidth;
      final pixels = width.isFinite && width > 0
          ? (width * MediaQuery.devicePixelRatioOf(context) * max(1.0, post.aspectRatio / ratio)).ceil()
          : null;
      final image = BooruNetworkImage(
        url: display.tileUrl(post, pixelWidth: pixels),
        fit: BoxFit.cover,
        cacheWidth: pixels,
        loadStateChanged: (state) {
          if (state.extendedImageLoadState == LoadState.failed) {
            return ColoredBox(
              color: theme.colorScheme.surfaceContainerHighest,
              child: Icon(Icons.broken_image_outlined, color: theme.colorScheme.outline),
            );
          }
          return null;
        },
      );
      if (!display.blurs(post)) return image;
      return ImageFiltered(imageFilter: ImageFilter.blur(sigmaX: 20, sigmaY: 20), child: image);
    },
  );

  Widget _chip(BuildContext context, IconData icon, String label) {
    final theme = Theme.of(context);
    return DecoratedBox(
      decoration: BoxDecoration(
        color: theme.colorScheme.surface.withValues(alpha: 0.82),
        borderRadius: BorderRadius.circular(6),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 12, color: theme.colorScheme.onSurface),
            const SizedBox(width: 3),
            Text(label, style: theme.textTheme.labelSmall),
          ],
        ),
      ),
    );
  }
}
