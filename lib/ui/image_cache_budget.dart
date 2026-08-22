import 'dart:math';

import 'package:flutter/material.dart';

/// Flutter's 100 MiB default is a lot of decoded bitmaps to be holding next to
/// a video player. Mixed feeds (Substack covers, Reddit, Bluesky) evicted tiles
/// at 64 MiB and re-decoded them on every scroll-back, so the app-wide budget
/// sits just under the default.
const int kImageCacheBytes = 96 * 1024 * 1024;

/// What a masonry wall of thumbnails is allowed to keep *besides* what it is
/// painting.
///
/// A two-column grid on a 1080p phone paints roughly 1.5 MB per tile, and the
/// tiles on screen plus the scroll cache extent are all live — none of that is
/// counted against the cache below. Adding a further 96 MiB of evictable
/// bitmaps on top is how a phone with several image plugins open ends up being
/// killed by Android with no Dart error anywhere. Grids therefore run on a
/// smaller idle budget; the app-wide one comes back when the grid unmounts.
const int kImageGridCacheBytes = 48 * 1024 * 1024;

/// Caps the shared image cache while an image-heavy screen is mounted.
///
/// Budgets nest: the smallest one wins, and unmounting restores whatever the
/// screens still on the tree asked for.
class ImageCacheBudget extends StatefulWidget {
  final int maxBytes;
  final Widget child;

  const ImageCacheBudget({
    super.key,
    this.maxBytes = kImageGridCacheBytes,
    required this.child,
  });

  @override
  State<ImageCacheBudget> createState() => _ImageCacheBudgetState();
}

class _ImageCacheBudgetState extends State<ImageCacheBudget> {
  @override
  void initState() {
    super.initState();
    imageCacheBudgets.push(widget.maxBytes);
  }

  @override
  void didUpdateWidget(ImageCacheBudget oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.maxBytes == widget.maxBytes) return;
    imageCacheBudgets
      ..pop(oldWidget.maxBytes)
      ..push(widget.maxBytes);
  }

  @override
  void dispose() {
    imageCacheBudgets.pop(widget.maxBytes);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => widget.child;
}

/// The set of budgets currently asked for, and the one in force.
///
/// Separate from the widget so the arithmetic can be tested without a binding.
class ImageCacheBudgets {
  final int defaultBytes;
  final void Function(int bytes) apply;

  final List<int> _requested = [];

  ImageCacheBudgets({
    this.defaultBytes = kImageCacheBytes,
    void Function(int bytes)? apply,
  }) : apply = apply ?? applyImageCacheBytes;

  int get current => _requested.isEmpty ? defaultBytes : _requested.reduce(min);

  void push(int bytes) {
    _requested.add(bytes);
    apply(current);
  }

  void pop(int bytes) {
    _requested.remove(bytes);
    apply(current);
  }
}

final imageCacheBudgets = ImageCacheBudgets();

void applyImageCacheBytes(int bytes) {
  final cache = PaintingBinding.instance.imageCache;
  if (cache.maximumSizeBytes == bytes) return;
  cache.maximumSizeBytes = bytes;
}
