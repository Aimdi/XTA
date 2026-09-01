import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';

/// How long a Threads CDN fetch may hang before the tile fails closed.
///
/// Does not change how often we talk to Meta — only how long a stalled
/// `cdninstagram` / `fbcdn` image keeps a spinner on screen.
const threadsImageTimeLimit = Duration(seconds: 12);

/// Network image with a hard timeout and a single retry — no prefetch storms.
class ThreadsNetworkImage extends StatelessWidget {
  final String url;
  final BoxFit fit;
  final double? width;
  final double? height;
  final int? cacheWidth;
  final int? cacheHeight;
  final ExtendedImageMode mode;
  final InitGestureConfigHandler? initGestureConfigHandler;
  final LoadStateChanged? loadStateChanged;

  const ThreadsNetworkImage(
    this.url, {
    super.key,
    this.fit = BoxFit.cover,
    this.width,
    this.height,
    this.cacheWidth,
    this.cacheHeight,
    this.mode = ExtendedImageMode.none,
    this.initGestureConfigHandler,
    this.loadStateChanged,
  });

  @override
  Widget build(BuildContext context) {
    return ExtendedImage.network(
      url,
      fit: fit,
      width: width,
      height: height,
      cache: true,
      cacheWidth: cacheWidth,
      cacheHeight: cacheHeight,
      timeLimit: threadsImageTimeLimit,
      retries: 1,
      mode: mode,
      initGestureConfigHandler: initGestureConfigHandler,
      loadStateChanged: loadStateChanged,
    );
  }
}
