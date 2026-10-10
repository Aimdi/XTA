import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_button.dart';
import 'package:xta/plugins/pixiv/pixiv_downloaded_badge.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_link_open.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_post_actions.dart';
import 'package:xta/plugins/pixiv/pixiv_tile_badges.dart';
import 'package:xta/plugins/pixiv/pixiv_tile_caption.dart';

/// Stable Hero tag from a grid tile into the illust viewer.
String pixivIllustHeroTag(int id) => 'pixiv-illust-$id';

/// One masonry cell — image first, title and bookmark count under it.
class PixivIllustTile extends StatelessWidget {
  final PixivIllust illust;

  /// The list the tile sits in and its place there, so opening it knows its
  /// neighbours; a lone tile is a list of one.
  final List<PixivIllust>? siblings;
  final int index;

  const PixivIllustTile({super.key, required this.illust, this.siblings, this.index = 0});

  void _open(BuildContext context) {
    final list = siblings;
    openPixivIllustFromList(context, list ?? [illust], list == null ? 0 : index);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final ratio = illust.aspectRatio.clamp(0.45, 1.6);

    return Material(
      color: theme.scaffoldBackgroundColor,
      borderRadius: BorderRadius.circular(8),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onLongPress: () => showPixivPostActions(context, illust),
        onTap: () => _open(context),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            AspectRatio(
              aspectRatio: ratio,
              child: Stack(
                fit: StackFit.expand,
                children: [
                  Hero(
                    tag: pixivIllustHeroTag(illust.id),
                    child: RepaintBoundary(child: _image(theme)),
                  ),
                  PixivTileBadges(illust: illust),
                  // Above the heart, so the R-18 and AI labels keep the bottom row on a narrow tile.
                  Positioned(right: 9, bottom: 48, child: PixivDownloadedBadge(illust: illust)),
                  Positioned(right: 0, bottom: 0, child: PixivBookmarkButton(illust: illust, compact: true)),
                ],
              ),
            ),
            PixivTileCaption(illust: illust),
          ],
        ),
      ),
    );
  }

  Widget _image(ThemeData theme) => PixivNetworkImage(
    url: illust.thumbnailUrl,
    fit: BoxFit.cover,
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
}
