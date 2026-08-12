import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';

const booruImageTimeLimit = Duration(seconds: 15);

/// Booru CDN image decoded near paint size.
class BooruNetworkImage extends StatelessWidget {
  final String url;
  final BoxFit fit;
  final int? cacheWidth;
  final LoadStateChanged? loadStateChanged;

  const BooruNetworkImage({
    super.key,
    required this.url,
    this.fit = BoxFit.cover,
    this.cacheWidth,
    this.loadStateChanged,
  });

  @override
  Widget build(BuildContext context) {
    if (url.isEmpty) {
      return ColoredBox(
        color: Theme.of(context).colorScheme.surfaceContainerHighest,
        child: Icon(
          Icons.broken_image_outlined,
          color: Theme.of(context).colorScheme.outline,
        ),
      );
    }

    if (cacheWidth != null) {
      return ExtendedImage.network(
        url,
        fit: fit,
        cache: true,
        cacheWidth: cacheWidth,
        timeLimit: booruImageTimeLimit,
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
          cacheWidth: width,
          timeLimit: booruImageTimeLimit,
          retries: 1,
          loadStateChanged: loadStateChanged,
        );
      },
    );
  }
}
