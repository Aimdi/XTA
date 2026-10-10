import 'package:flutter/material.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_button.dart';
import 'package:xta/plugins/pixiv/pixiv_downloaded_badge.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_link_open.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_post_actions.dart';
import 'package:xta/plugins/pixiv/pixiv_quality.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/pixiv/pixiv_tile_badges.dart';
import 'package:xta/plugins/pixiv/pixiv_tile_caption.dart';
import 'package:xta/plugins/pixiv/pixiv_viewing_prefs.dart';

/// Stable Hero tag from a grid tile into the illust viewer.
String pixivIllustHeroTag(int id) => 'pixiv-illust-$id';

/// One masonry cell — image first, title and bookmark count under it.
class PixivIllustTile extends StatelessWidget {
  final PixivIllust illust;

  /// The list the tile sits in and its place there, so opening it knows its
  /// neighbours; a lone tile is a list of one.
  final List<PixivIllust>? siblings;
  final int index;

  /// The store [siblings] came from, so a pager over them can load the next page.
  final PixivIllustListStore? source;

  /// In place of the work's actions sheet.
  final VoidCallback? onLongPress;

  const PixivIllustTile({
    super.key,
    required this.illust,
    this.siblings,
    this.index = 0,
    this.source,
    this.onLongPress,
  });

  void _open(BuildContext context) {
    final list = siblings;
    openPixivIllustFromList(context, list ?? [illust], list == null ? 0 : index, source: list == null ? null : source);
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
        onLongPress: onLongPress ?? () => showPixivPostActions(context, illust),
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
                    child: RepaintBoundary(child: _image(context)),
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

  Widget _image(BuildContext context) => PixivNetworkImage(
    url: pixivTileUrl(illust, pixivQuality(pixivPrefsOf(context), PixivQualitySlot.feed)),
    fit: BoxFit.cover,
    loadStateChanged: pixivRetryLoadState,
  );
}
