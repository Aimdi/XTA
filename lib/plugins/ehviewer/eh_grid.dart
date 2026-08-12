import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/ehviewer/eh_gallery_screen.dart';
import 'package:xta/plugins/ehviewer/eh_models.dart';

const ehImageTimeLimit = Duration(seconds: 20);

class EhNetworkImage extends StatelessWidget {
  final String url;
  final BoxFit fit;
  final int? cacheWidth;

  const EhNetworkImage({
    super.key,
    required this.url,
    this.fit = BoxFit.cover,
    this.cacheWidth,
  });

  @override
  Widget build(BuildContext context) {
    if (url.isEmpty) {
      return ColoredBox(
        color: Theme.of(context).colorScheme.surfaceContainerHighest,
        child: const Icon(Icons.broken_image_outlined),
      );
    }
    return ExtendedImage.network(
      url,
      fit: fit,
      cache: true,
      cacheWidth: cacheWidth,
      timeLimit: ehImageTimeLimit,
      retries: 1,
      loadStateChanged: (state) {
        if (state.extendedImageLoadState == LoadState.failed) {
          return ColoredBox(
            color: Theme.of(context).colorScheme.surfaceContainerHighest,
            child: const Icon(Icons.broken_image_outlined),
          );
        }
        return null;
      },
    );
  }
}

class EhGalleryGrid extends StatelessWidget {
  final List<EhGallery> galleries;
  final ScrollController? scrollController;
  final Future<void> Function()? onRefresh;
  final VoidCallback? onNearEnd;
  final bool loadingMore;

  const EhGalleryGrid({
    super.key,
    required this.galleries,
    this.scrollController,
    this.onRefresh,
    this.onNearEnd,
    this.loadingMore = false,
  });

  @override
  Widget build(BuildContext context) {
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
        controller: scrollController,
        physics: const AlwaysScrollableScrollPhysics(),
        slivers: [
          SliverPadding(
            padding: const EdgeInsets.all(8),
            sliver: SliverGrid(
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 2,
                mainAxisSpacing: 8,
                crossAxisSpacing: 8,
                childAspectRatio: 0.62,
              ),
              delegate: SliverChildBuilderDelegate(
                (context, index) => EhGalleryTile(gallery: galleries[index]),
                childCount: galleries.length,
              ),
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
      ),
    );
    if (onRefresh == null) return grid;
    return RefreshIndicator(onRefresh: onRefresh!, child: grid);
  }
}

class EhGalleryTile extends StatelessWidget {
  final EhGallery gallery;

  const EhGalleryTile({super.key, required this.gallery});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);

    return Material(
      color: theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.35),
      borderRadius: BorderRadius.circular(8),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () => Navigator.push(
          context,
          MaterialPageRoute(builder: (_) => EhGalleryScreen(gallery: gallery)),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Expanded(
              child: LayoutBuilder(
                builder: (context, constraints) {
                  return Stack(
                    fit: StackFit.expand,
                    children: [
                      RepaintBoundary(
                        child: EhNetworkImage(
                          url: gallery.thumbUrl ?? '',
                          cacheWidth:
                              (constraints.maxWidth *
                                      MediaQuery.devicePixelRatioOf(context))
                                  .ceil(),
                        ),
                      ),
                      if (gallery.category != null)
                        Positioned(
                          left: 6,
                          top: 6,
                          child: DecoratedBox(
                            decoration: BoxDecoration(
                              color: theme.colorScheme.surface.withValues(
                                alpha: 0.85,
                              ),
                              borderRadius: BorderRadius.circular(4),
                            ),
                            child: Padding(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 6,
                                vertical: 2,
                              ),
                              child: Text(
                                gallery.category!.label,
                                style: theme.textTheme.labelSmall,
                              ),
                            ),
                          ),
                        ),
                    ],
                  );
                },
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(8, 6, 8, 8),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    gallery.title,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.bodySmall,
                  ),
                  if (gallery.pageCount != null) ...[
                    const SizedBox(height: 2),
                    Text(
                      l10n.plugin_eh_pages(gallery.pageCount!),
                      style: theme.textTheme.labelSmall,
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Crops one tile out of EH's horizontal preview sprite sheet.
class EhSpriteThumb extends StatelessWidget {
  final String url;
  final double offsetX;
  final double tileWidth;

  const EhSpriteThumb({
    super.key,
    required this.url,
    required this.offsetX,
    this.tileWidth = 200,
  });

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final scale = constraints.maxWidth / tileWidth;
        return ClipRect(
          child: Transform.translate(
            offset: Offset(-offsetX.abs() * scale, 0),
            child: OverflowBox(
              alignment: Alignment.topLeft,
              maxWidth: double.infinity,
              maxHeight: constraints.maxHeight,
              child: ExtendedImage.network(
                url,
                height: constraints.maxHeight,
                fit: BoxFit.fitHeight,
                cache: true,
                cacheWidth:
                    (constraints.maxWidth *
                            MediaQuery.devicePixelRatioOf(context))
                        .ceil(),
                timeLimit: ehImageTimeLimit,
                retries: 1,
                loadStateChanged: (state) {
                  if (state.extendedImageLoadState == LoadState.failed) {
                    return ColoredBox(
                      color: Theme.of(
                        context,
                      ).colorScheme.surfaceContainerHighest,
                    );
                  }
                  return null;
                },
              ),
            ),
          ),
        );
      },
    );
  }
}
