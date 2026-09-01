import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

/// How long a Pixiv CDN fetch may hang before the tile fails closed.
///
/// Without a limit, ExtendedImage keeps the default spinner forever on a stalled
/// `i.pximg.net` connection — which reads as "the plugin loads forever".
const pixivImageTimeLimit = Duration(seconds: 12);

/// Pixiv CDN image decoded at the size it is painted — Referer + cacheWidth.
///
/// Without [cacheWidth], every masonry cell decodes a full `medium`/`large`
/// bitmap into the shared image cache and scroll jank follows. Pixez-style
/// clients always resize at decode time for waterfall tiles.
class PixivNetworkImage extends StatelessWidget {
  final String url;
  final BoxFit fit;
  final int? cacheWidth;
  final int? cacheHeight;
  final LoadStateChanged? loadStateChanged;

  const PixivNetworkImage({
    super.key,
    required this.url,
    this.fit = BoxFit.cover,
    this.cacheWidth,
    this.cacheHeight,
    this.loadStateChanged,
  });

  @override
  Widget build(BuildContext context) {
    if (cacheWidth != null || cacheHeight != null) {
      return ExtendedImage.network(
        url,
        fit: fit,
        cache: true,
        headers: pixivImageHeaders,
        cacheWidth: cacheWidth,
        cacheHeight: cacheHeight,
        timeLimit: pixivImageTimeLimit,
        retries: 1,
        loadStateChanged: loadStateChanged,
      );
    }

    return LayoutBuilder(
      builder: (context, constraints) {
        final maxW = constraints.maxWidth;
        final width = maxW.isFinite && maxW > 0
            ? (maxW * MediaQuery.devicePixelRatioOf(context)).ceil()
            : null;
        return ExtendedImage.network(
          url,
          fit: fit,
          cache: true,
          headers: pixivImageHeaders,
          cacheWidth: width,
          timeLimit: pixivImageTimeLimit,
          retries: 1,
          loadStateChanged: loadStateChanged,
        );
      },
    );
  }
}

/// Warm disk cache for thumbs the masonry is about to show.
///
/// Uses the same half-width [cacheWidth] as tiles so prefetch actually hits the
/// decode cache, runs in parallel, and never waits on a hung CDN forever.
Future<void> prefetchPixivThumbs(
  BuildContext context,
  Iterable<PixivIllust> illusts, {
  int max = 12,
}) async {
  if (!context.mounted) {
    return;
  }

  // Match masonry half-width decode so memory cache keys line up with tiles.
  final dpr = MediaQuery.devicePixelRatioOf(context);
  final cacheWidth = (MediaQuery.sizeOf(context).width / 2 * dpr).ceil();
  final targets = illusts.take(max).toList(growable: false);

  await Future.wait(
    targets.map((illust) async {
      if (!context.mounted) {
        return;
      }
      final provider = ResizeImage(
        ExtendedNetworkImageProvider(
          illust.thumbnailUrl,
          headers: pixivImageHeaders,
          cache: true,
          timeLimit: pixivImageTimeLimit,
          retries: 1,
        ),
        width: cacheWidth,
      );
      try {
        await precacheImage(provider, context).timeout(pixivImageTimeLimit);
      } catch (_) {}
    }),
  );
}
