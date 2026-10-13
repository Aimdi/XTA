import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';

const booruImageTimeLimit = Duration(seconds: 15);

/// What [BooruNetworkImage] shows [url] with at [cacheWidth], so a picture
/// loaded ahead with it is the one the page then finds in the image cache.
ImageProvider booruImageProvider(String url, {int? cacheWidth}) =>
    _network(url, cacheWidth: cacheWidth).image;

ExtendedImage _network(
  String url, {
  BoxFit? fit,
  int? cacheWidth,
  LoadStateChanged? loadStateChanged,
}) => ExtendedImage.network(
  url,
  fit: fit,
  cache: true,
  cacheWidth: cacheWidth,
  timeLimit: booruImageTimeLimit,
  retries: 1,
  loadStateChanged: loadStateChanged,
);

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
      return _network(
        url,
        fit: fit,
        cacheWidth: cacheWidth,
        loadStateChanged: loadStateChanged,
      );
    }

    return LayoutBuilder(
      builder: (context, constraints) {
        final maxW = constraints.maxWidth;
        final width = maxW.isFinite && maxW > 0
            ? (maxW * MediaQuery.devicePixelRatioOf(context)).ceil()
            : null;
        return _network(
          url,
          fit: fit,
          cacheWidth: width,
          loadStateChanged: loadStateChanged,
        );
      },
    );
  }
}
