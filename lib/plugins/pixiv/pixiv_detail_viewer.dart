import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_illust_pager.dart';
import 'package:xta/plugins/pixiv/pixiv_illust_tile.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_quality.dart';
import 'package:xta/plugins/pixiv/pixiv_ugoira_view.dart';
import 'package:xta/plugins/pixiv/pixiv_viewing_prefs.dart';
import 'package:xta/plugins/pixiv/pixiv_zoomable.dart';

/// A work's pages side by side, framed to the art rather than the screen, at the size the
/// reader picked for work pages. Pushing past the first or last page tells a pager of works.
class PixivDetailViewer extends StatelessWidget {
  final PixivIllust illust;
  final PageController controller;
  final ValueChanged<int> onPageChanged;

  /// Tapping a page opens it in the reader.
  final ValueChanged<int> onOpenPage;
  final ValueChanged<int> onPageActions;

  /// Fills the space it is given, as the picture pane of a split detail, instead of
  /// taking the art's height.
  final bool expand;

  const PixivDetailViewer({
    super.key,
    required this.illust,
    required this.controller,
    required this.onPageChanged,
    required this.onOpenPage,
    required this.onPageActions,
    this.expand = false,
  });

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      final width = constraints.maxWidth.isFinite ? constraints.maxWidth : MediaQuery.sizeOf(context).width;
      final prefs = pixivPrefsOf(context);
      final quality = pixivQuality(prefs, PixivQualitySlot.detail);
      final pages = [for (var i = 0; i < illust.viewerUrls.length; i++) pixivPageUrl(illust, i, quality)];
      final poster = pixivTileUrl(illust, pixivQuality(prefs, PixivQualitySlot.feed));
      // The screen, not the pane: a split divider dragged would otherwise decode the art again every frame.
      final cacheWidth = (MediaQuery.sizeOf(context).width * MediaQuery.devicePixelRatioOf(context)).ceil();
      final pager = NotificationListener<ScrollNotification>(
        onNotification: (notification) {
          pixivPageEdge(notification)?.dispatch(context);
          return false;
        },
        child: PageView.builder(
          // Keeps the page when a rotation moves the viewer between the split and stacked layouts.
          key: PageStorageKey('pixiv-detail-pages-${illust.id}'),
          controller: controller,
          itemCount: pages.length,
          onPageChanged: (index) {
            onPageChanged(index);
            _prefetch(context, pages, index + 1);
          },
          itemBuilder: (context, index) => _page(pages, index, cacheWidth, poster),
        ),
      );
      if (expand) return pager;
      return SizedBox(height: _height(context, width), child: pager);
    },
  );

  double _height(BuildContext context, double width) => pixivDetailViewerHeight(
    screenWidth: width,
    screenHeight: MediaQuery.sizeOf(context).height,
    width: illust.width,
    height: illust.height,
  );

  Widget _page(List<String> pages, int index, int cacheWidth, String poster) {
    final image = Stack(
      fit: StackFit.expand,
      alignment: Alignment.center,
      children: [
        // Instant paint from the grid thumb already on disk.
        if (index == 0 && poster != pages[index])
          PixivNetworkImage(
            url: poster,
            fit: BoxFit.contain,
            cacheWidth: cacheWidth,
            gaplessPlayback: true,
            loadStateChanged: _quietUnlessLoaded,
          ),
        PixivNetworkImage(
          url: pages[index],
          fit: BoxFit.contain,
          cacheWidth: cacheWidth,
          gaplessPlayback: true,
          loadStateChanged: (state) => pixivRetryLoadState(state, fill: false),
        ),
      ],
    );
    final ugoira = index == 0 && illust.isUgoira;
    final body = PixivZoomable(
      onTap: ugoira ? null : () => onOpenPage(index),
      doubleTapZoom: !ugoira,
      onLongPress: () => onPageActions(index),
      child: Center(
        child: ugoira ? PixivUgoiraView(illust: illust, poster: image) : image,
      ),
    );
    return index == 0 ? Hero(tag: pixivIllustHeroTag(illust.id), child: body) : body;
  }

  void _prefetch(BuildContext context, List<String> pages, int index) {
    if (index < 0 || index >= pages.length || !context.mounted) return;
    precacheImage(pixivImageProvider(context, pages[index]), context, onError: (_, _) {});
  }
}

/// The poster under a page only shows once it has something to show; the page above it
/// carries the spinner and the retry.
Widget? _quietUnlessLoaded(ExtendedImageState state) =>
    state.extendedImageLoadState == LoadState.completed ? null : const SizedBox.shrink();

/// Page counter that opens every page, beside a quiet way into the reader.
class PixivDetailPageBar extends StatelessWidget {
  final int page;
  final int pages;
  final VoidCallback onOverview;
  final VoidCallback onReadVertically;

  const PixivDetailPageBar({
    super.key,
    required this.page,
    required this.pages,
    required this.onOverview,
    required this.onReadVertically,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final scheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
      child: Wrap(
        alignment: WrapAlignment.center,
        crossAxisAlignment: WrapCrossAlignment.center,
        spacing: 8,
        runSpacing: 4,
        children: [
          Tooltip(
            message: l10n.plugin_pixiv_all_pages,
            child: TextButton.icon(
              key: const ValueKey('pixiv-illust-counter'),
              style: TextButton.styleFrom(foregroundColor: scheme.onSurface, iconColor: scheme.onSurfaceVariant),
              onPressed: onOverview,
              icon: const Icon(Icons.grid_view_outlined, size: 18),
              label: Text(l10n.plugin_pixiv_page_of(page + 1, pages)),
            ),
          ),
          OutlinedButton.icon(
            key: const ValueKey('pixiv-illust-read-vertically'),
            style: OutlinedButton.styleFrom(iconColor: scheme.primary),
            onPressed: onReadVertically,
            icon: const Icon(Icons.arrow_downward, size: 18),
            label: Text(l10n.plugin_pixiv_read_vertically),
          ),
        ],
      ),
    );
  }
}
