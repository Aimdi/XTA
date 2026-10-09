import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_illust_tile.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_ugoira_view.dart';
import 'package:xta/plugins/pixiv/pixiv_zoomable.dart';

/// A work's pages side by side, framed to the art rather than the screen.
class PixivDetailViewer extends StatelessWidget {
  final PixivIllust illust;
  final PageController controller;
  final ValueChanged<int> onPageChanged;

  /// Tapping a page opens it in the reader.
  final ValueChanged<int> onOpenPage;
  final ValueChanged<int> onPageActions;

  const PixivDetailViewer({
    super.key,
    required this.illust,
    required this.controller,
    required this.onPageChanged,
    required this.onOpenPage,
    required this.onPageActions,
  });

  @override
  Widget build(BuildContext context) {
    final size = MediaQuery.sizeOf(context);
    final pages = illust.viewerUrls;
    final cacheWidth = (size.width * MediaQuery.devicePixelRatioOf(context)).ceil();
    return SizedBox(
      height: pixivDetailViewerHeight(
        screenWidth: size.width,
        screenHeight: size.height,
        width: illust.width,
        height: illust.height,
      ),
      child: PageView.builder(
        controller: controller,
        itemCount: pages.length,
        onPageChanged: (index) {
          onPageChanged(index);
          _prefetch(context, pages, index + 1);
        },
        itemBuilder: (context, index) => _page(pages, index, cacheWidth),
      ),
    );
  }

  Widget _page(List<String> pages, int index, int cacheWidth) {
    final image = Stack(
      fit: StackFit.expand,
      alignment: Alignment.center,
      children: [
        // Instant paint from the grid thumb already on disk.
        if (index == 0) PixivNetworkImage(url: illust.thumbnailUrl, fit: BoxFit.contain, cacheWidth: cacheWidth),
        PixivNetworkImage(url: pages[index], fit: BoxFit.contain, cacheWidth: cacheWidth),
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
    final provider = ExtendedNetworkImageProvider(pages[index], headers: pixivImageHeaders, cache: true);
    precacheImage(provider, context, onError: (_, _) {});
  }
}

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
