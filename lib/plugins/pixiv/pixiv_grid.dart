import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show ScrollCacheExtent;
import 'package:flutter_triple/flutter_triple.dart';
import 'package:flutter_staggered_grid_view/flutter_staggered_grid_view.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_button.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_store.dart';
import 'package:xta/plugins/pixiv/pixiv_illust_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

final NumberFormat _pixivCountFormat = NumberFormat.compact(locale: 'en_US');

/// Stable Hero tag from a grid tile into the illust viewer.
String pixivIllustHeroTag(int id) => 'pixiv-illust-$id';

/// Pixez-style staggered gallery of illust thumbnails.
class PixivIllustGrid extends StatelessWidget {
  final List<PixivIllust> illusts;
  final ScrollController? scrollController;
  final Future<void> Function()? onRefresh;
  final bool loadingMore;
  final EdgeInsetsGeometry padding;

  const PixivIllustGrid({
    super.key,
    required this.illusts,
    this.scrollController,
    this.onRefresh,
    this.loadingMore = false,
    this.padding = const EdgeInsets.all(4),
  });

  @override
  Widget build(BuildContext context) {
    // Plain ScopedBuilder (not .transition): mute changes must not animate the
    // whole masonry — that rebuilds every ExtendedImage and thrash-decodes.
    return ScopedBuilder<PixivMuteStore, PixivMuteState>(
      store: context.read<PixivMuteStore>(),
      onState: (context, mute) => _grid(context, mute.filter(illusts)),
    );
  }

  Widget _grid(BuildContext context, List<PixivIllust> visibleIllusts) {
    final grid = CustomScrollView(
      controller: scrollController,
      scrollCacheExtent: const ScrollCacheExtent.pixels(1200),
      physics: const AlwaysScrollableScrollPhysics(),
      slivers: [
        SliverPadding(
          padding: padding,
          sliver: SliverMasonryGrid.count(
            crossAxisCount: 2,
            mainAxisSpacing: 4,
            crossAxisSpacing: 4,
            childCount: visibleIllusts.length,
            itemBuilder: (context, index) =>
                PixivIllustTile(illust: visibleIllusts[index]),
          ),
        ),
        if (loadingMore)
          const SliverToBoxAdapter(
            child: Padding(
              padding: EdgeInsets.all(16),
              child: Center(child: CircularProgressIndicator()),
            ),
          ),
      ],
    );

    if (onRefresh == null) {
      return grid;
    }
    return RefreshIndicator(onRefresh: onRefresh!, child: grid);
  }
}

/// One masonry cell — image first, title and bookmark count under it.
class PixivIllustTile extends StatelessWidget {
  final PixivIllust illust;

  const PixivIllustTile({super.key, required this.illust});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    final ratio = illust.aspectRatio.clamp(0.45, 1.6);

    return Material(
      color: theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.35),
      borderRadius: BorderRadius.circular(8),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () => Navigator.push(
          context,
          MaterialPageRoute(builder: (_) => PixivIllustScreen(illust: illust)),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            AspectRatio(
              aspectRatio: ratio,
              child: Stack(
                fit: StackFit.expand,
                children: [
                  Hero(
                    tag: pixivIllustHeroTag(illust.id),
                    child: RepaintBoundary(
                      child: PixivNetworkImage(
                        url: illust.thumbnailUrl,
                        fit: BoxFit.cover,
                        loadStateChanged: (state) {
                          if (state.extendedImageLoadState ==
                              LoadState.failed) {
                            return ColoredBox(
                              color: theme.colorScheme.surfaceContainerHighest,
                              child: Icon(
                                Icons.broken_image_outlined,
                                color: theme.colorScheme.outline,
                              ),
                            );
                          }
                          return null;
                        },
                      ),
                    ),
                  ),
                  if (illust.pageCount > 1)
                    Positioned(
                      top: 6,
                      right: 6,
                      child: _chip(
                        context,
                        Icons.collections_outlined,
                        '${illust.pageCount}',
                      ),
                    ),
                  if (illust.isUgoira)
                    Positioned(
                      top: 6,
                      left: 6,
                      child: _chip(
                        context,
                        Icons.play_circle_outline,
                        l10n.plugin_pixiv_ugoira,
                      ),
                    ),
                  if (illust.isR18)
                    Positioned(
                      bottom: 6,
                      left: 6,
                      child: _chip(
                        context,
                        Icons.eighteen_up_rating_outlined,
                        l10n.plugin_pixiv_r18,
                      ),
                    ),
                  Positioned(
                    right: 0,
                    bottom: 0,
                    child: PixivBookmarkButton(illust: illust, compact: true),
                  ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(8, 6, 8, 8),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (illust.title.isNotEmpty)
                    Text(
                      illust.title,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.bodySmall!.copyWith(
                        fontWeight: FontWeight.w600,
                        height: 1.2,
                      ),
                    ),
                  const SizedBox(height: 2),
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          illust.userName,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.labelSmall!.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                        ),
                      ),
                      ScopedBuilder<PixivBookmarkStore, Map<int, bool>>(
                        store: context.read<PixivBookmarkStore>(),
                        distinct: (_) => context
                            .read<PixivBookmarkStore>()
                            .isBookmarked(illust),
                        onState: (context, _) {
                          final bookmarks = context.read<PixivBookmarkStore>();
                          final bookmarked = bookmarks.isBookmarked(illust);
                          return Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Icon(
                                bookmarked
                                    ? Icons.favorite
                                    : Icons.favorite_border,
                                size: 12,
                                color: bookmarked
                                    ? theme.colorScheme.primary
                                    : theme.colorScheme.onSurfaceVariant,
                              ),
                              const SizedBox(width: 2),
                              Text(
                                _pixivCountFormat.format(
                                  bookmarks.bookmarkCount(illust),
                                ),
                                style: theme.textTheme.labelSmall!.copyWith(
                                  color: theme.colorScheme.onSurfaceVariant,
                                ),
                              ),
                            ],
                          );
                        },
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _chip(BuildContext context, IconData icon, String label) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: Colors.black.withValues(alpha: 0.55),
        borderRadius: BorderRadius.circular(4),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 12, color: Colors.white),
            const SizedBox(width: 3),
            Text(
              label,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 11,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
