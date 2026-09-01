import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';

/// A network image decoded at the size it is actually painted at.
///
/// X serves media at roughly 1200px wide whatever slot it lands in, so a grid
/// tile or a video poster otherwise decodes megabytes of ARGB it never shows,
/// evicting the rest of the screen from the shared image cache and forcing a
/// re-decode on every scroll back.
class CappedNetworkImage extends StatelessWidget {
  final String url;
  final BoxFit fit;

  const CappedNetworkImage({super.key, required this.url, this.fit = BoxFit.cover});

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final maxW = constraints.maxWidth;
        final cacheWidth = maxW.isFinite && maxW > 0 ? (maxW * MediaQuery.devicePixelRatioOf(context)).ceil() : null;
        return ExtendedImage.network(url, cache: true, fit: fit, cacheWidth: cacheWidth);
      },
    );
  }
}
