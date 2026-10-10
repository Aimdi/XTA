import 'dart:math';

import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_image_source.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_quality.dart';
import 'package:xta/plugins/pixiv/pixiv_viewing_prefs.dart';

/// How long a Pixiv CDN fetch may hang before the tile fails closed.
///
/// Without a limit, ExtendedImage keeps the default spinner forever on a stalled
/// `i.pximg.net` connection — which reads as "the plugin loads forever".
const pixivImageTimeLimit = Duration(seconds: 12);

/// The widest a whole-page decode goes. Some originals pass 8000px, and a few pages of those
/// decoded in full would exhaust a phone's memory.
const pixivFullResolutionMaxWidth = 4096;

/// Three screens wide is sharp beyond the double-tap zoom and close at the deepest pinch.
int pixivFullResolutionWidth(double screenWidthPx) => min(pixivFullResolutionMaxWidth, (screenWidthPx * 3).ceil());

/// [url] from the image server the reader picked in the settings above [context].
String pixivImageUrlFor(BuildContext context, String url) =>
    pixivImageUrl(url, pixivImageHostSetting(pixivPrefsOf(context)));

/// The provider a Pixiv image loads through, for warming the cache ahead of a widget.
ExtendedNetworkImageProvider pixivImageProvider(BuildContext context, String url) => ExtendedNetworkImageProvider(
  pixivImageUrlFor(context, url),
  headers: pixivImageHeaders,
  cache: true,
  timeLimit: pixivImageTimeLimit,
  retries: 1,
);

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

  /// Decodes at [pixivFullResolutionWidth] of the screen rather than the painted width, for
  /// art the reader zooms into.
  final bool fullResolution;

  /// Keeps the current picture up while a new decode of it loads.
  final bool gaplessPlayback;

  /// Reports download progress to [loadStateChanged].
  final bool handleLoadingProgress;

  const PixivNetworkImage({
    super.key,
    required this.url,
    this.fit = BoxFit.cover,
    this.cacheWidth,
    this.cacheHeight,
    this.loadStateChanged,
    this.fullResolution = false,
    this.gaplessPlayback = false,
    this.handleLoadingProgress = false,
  });

  @override
  Widget build(BuildContext context) {
    final source = pixivImageUrlFor(context, url);
    if (fullResolution) {
      final screen = MediaQuery.sizeOf(context).width * MediaQuery.devicePixelRatioOf(context);
      return _image(context, source, width: pixivFullResolutionWidth(screen));
    }
    if (cacheWidth != null || cacheHeight != null) {
      return _image(context, source, width: cacheWidth, height: cacheHeight);
    }

    return LayoutBuilder(
      builder: (context, constraints) {
        final maxW = constraints.maxWidth;
        final width = maxW.isFinite && maxW > 0 ? (maxW * MediaQuery.devicePixelRatioOf(context)).ceil() : null;
        return _image(context, source, width: width);
      },
    );
  }

  /// [loadStateChanged] first; a failure it leaves to the default gets the broken-image
  /// mark rather than the library's untranslated text drawn over the box.
  Widget? _loadState(BuildContext context, ExtendedImageState state) =>
      loadStateChanged?.call(state) ??
      (state.extendedImageLoadState == LoadState.failed ? pixivTileLoadState(context, state) : null);

  Widget _image(BuildContext context, String source, {int? width, int? height}) => ExtendedImage.network(
    source,
    fit: fit,
    cache: true,
    headers: pixivImageHeaders,
    cacheWidth: width,
    cacheHeight: height,
    timeLimit: pixivImageTimeLimit,
    retries: 1,
    gaplessPlayback: gaplessPlayback,
    handleLoadingProgress: handleLoadingProgress,
    loadStateChanged: (state) => _loadState(context, state),
  );
}

/// Quiet thumbnail states: the tile's surface shows while loading, an icon marks a failure.
Widget? pixivTileLoadState(BuildContext context, ExtendedImageState state) => switch (state.extendedImageLoadState) {
  LoadState.loading => const SizedBox.shrink(),
  LoadState.failed => Icon(Icons.broken_image_outlined, color: Theme.of(context).colorScheme.outline),
  LoadState.completed => null,
};

/// A failed image becomes a button that fetches it again; other states keep their default.
Widget? pixivRetryLoadState(ExtendedImageState state, {bool fill = true}) =>
    state.extendedImageLoadState == LoadState.failed ? PixivImageRetry(onRetry: state.reLoadImage, fill: fill) : null;

/// Where an image failed: a retry button, on the tile's surface when [fill] is set.
class PixivImageRetry extends StatelessWidget {
  final VoidCallback onRetry;
  final bool fill;

  const PixivImageRetry({super.key, required this.onRetry, this.fill = true});

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final button = Center(
      child: IconButton.filledTonal(
        key: const ValueKey('pixiv-image-retry'),
        tooltip: L10n.of(context).retry,
        onPressed: onRetry,
        icon: const Icon(Icons.refresh),
      ),
    );
    return fill ? ColoredBox(color: scheme.surfaceContainerHighest, child: button) : button;
  }
}

/// Warm disk cache for thumbs the masonry is about to show.
///
/// Uses the same half-width [cacheWidth] as tiles so prefetch actually hits the
/// decode cache, runs in parallel, and never waits on a hung CDN forever.
Future<void> prefetchPixivThumbs(BuildContext context, Iterable<PixivIllust> illusts, {int max = 12}) async {
  if (!context.mounted) {
    return;
  }

  // Match masonry half-width decode so memory cache keys line up with tiles.
  final dpr = MediaQuery.devicePixelRatioOf(context);
  final cacheWidth = (MediaQuery.sizeOf(context).width / 2 * dpr).ceil();
  final quality = pixivQuality(pixivPrefsOf(context), PixivQualitySlot.feed);
  final providers = [
    for (final illust in illusts.take(max))
      ResizeImage(pixivImageProvider(context, pixivTileUrl(illust, quality)), width: cacheWidth),
  ];

  await Future.wait(
    providers.map((provider) async {
      if (!context.mounted) {
        return;
      }
      try {
        await precacheImage(provider, context).timeout(pixivImageTimeLimit);
      } catch (_) {}
    }),
  );
}
