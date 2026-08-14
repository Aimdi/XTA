import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/ehviewer/eh_client.dart';
import 'package:xta/plugins/ehviewer/eh_gallery_screen.dart';
import 'package:xta/plugins/ehviewer/eh_models.dart';
import 'package:xta/plugins/ehviewer/eh_ui.dart';

const ehImageTimeLimit = Duration(seconds: 20);

class EhNetworkImage extends StatelessWidget {
  final String url;
  final String? fallbackUrl;
  final BoxFit fit;
  final int? cacheWidth;
  final FilterQuality filterQuality;

  const EhNetworkImage({
    super.key,
    required this.url,
    this.fallbackUrl,
    this.fit = BoxFit.cover,
    this.cacheWidth,
    this.filterQuality = FilterQuality.medium,
  });

  @override
  Widget build(BuildContext context) {
    if (url.isEmpty) {
      return ColoredBox(
        color: Theme.of(context).colorScheme.surfaceContainerHighest,
        child: const Icon(Icons.broken_image_outlined),
      );
    }
    final headers = _ehImageHeaders(context);
    return ExtendedImage.network(
      url,
      fit: fit,
      cache: true,
      cacheWidth: cacheWidth,
      headers: headers,
      filterQuality: filterQuality,
      timeLimit: ehImageTimeLimit,
      retries: 1,
      loadStateChanged: (state) {
        if (state.extendedImageLoadState != LoadState.failed) return null;
        final fallback = fallbackUrl;
        if (fallback != null && fallback.isNotEmpty && fallback != url) {
          return ExtendedImage.network(
            fallback,
            fit: fit,
            cache: true,
            cacheWidth: cacheWidth,
            headers: headers,
            filterQuality: filterQuality,
            timeLimit: ehImageTimeLimit,
            retries: 1,
            loadStateChanged: (retry) {
              if (retry.extendedImageLoadState == LoadState.failed) {
                return _ehBroken(context);
              }
              return null;
            },
          );
        }
        return _ehBroken(context);
      },
    );
  }
}

Widget _ehBroken(BuildContext context) {
  return ColoredBox(
    color: Theme.of(context).colorScheme.surfaceContainerHighest,
    child: const Icon(Icons.broken_image_outlined),
  );
}

Map<String, String>? _ehImageHeaders(BuildContext context) {
  try {
    return context.read<EhClient>().imageHeaders;
  } catch (_) {
    return null;
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
                              color: ehCategoryColor(gallery.category!),
                              borderRadius: BorderRadius.circular(4),
                            ),
                            child: Padding(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 6,
                                vertical: 2,
                              ),
                              child: Text(
                                gallery.category!.label,
                                style: theme.textTheme.labelSmall?.copyWith(
                                  color: Colors.white,
                                ),
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
                    gallery.titleFor(
                      preferJapanese: ehPreferJapaneseOf(context),
                    ),
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
                headers: _ehImageHeaders(context),
                filterQuality: FilterQuality.medium,
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
