import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:xta/tweet/tweet_chrome.dart';

/// One image (or video thumbnail) on a plugin post.
class PluginMediaItem {
  const PluginMediaItem({
    required this.url,
    this.aspectRatio,
    this.alt,
    this.isVideo = false,
  });

  final String url;
  final double? aspectRatio;
  final String? alt;
  final bool isVideo;
}

List<PluginMediaItem> pluginMediaItemsFrom({
  required List<String> urls,
  List<double?> aspects = const [],
  List<bool> videos = const [],
}) {
  return [
    for (var i = 0; i < urls.length; i++)
      PluginMediaItem(
        url: urls[i],
        aspectRatio: i < aspects.length ? aspects[i] : null,
        isVideo: i < videos.length && videos[i],
      ),
  ];
}

/// Official clients size the box to the image, then fill it.
/// Clamp so a 9:16 phone photo does not eat the feed, and a 4:1 banner
/// does not collapse to a sliver.
double clampPluginMediaAspect(double? ratio) {
  const min = 0.45;
  const max = 2.4;
  if (ratio == null || !ratio.isFinite || ratio <= 0) {
    return 16 / 9;
  }
  if (ratio < min) return min;
  if (ratio > max) return max;
  return ratio;
}

/// Width/height or a precomputed `aspect` field (Mastodon `meta.original`).
double? pluginMediaAspectFrom(Object? raw) {
  if (raw is! Map) {
    return null;
  }
  final aspect = raw['aspect'];
  if (aspect is num && aspect > 0 && aspect.isFinite) {
    return aspect.toDouble();
  }
  final w = raw['width'];
  final h = raw['height'];
  if (w is num && h is num && w > 0 && h > 0) {
    return w.toDouble() / h.toDouble();
  }
  return null;
}

typedef PluginMediaImageBuilder =
    Widget Function(BuildContext context, PluginMediaItem item, BoxFit fit);

/// Feed/profile media: real aspect, tap opens a full-screen pager.
class PluginPostMedia extends StatelessWidget {
  const PluginPostMedia({super.key, required this.items, this.imageBuilder});

  final List<PluginMediaItem> items;
  final PluginMediaImageBuilder? imageBuilder;

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) {
      return const SizedBox.shrink();
    }
    if (items.length == 1) {
      return _PluginMediaTile(
        item: items.first,
        index: 0,
        items: items,
        imageBuilder: imageBuilder,
      );
    }
    return _PluginMediaPager(items: items, imageBuilder: imageBuilder);
  }
}

class _PluginMediaPager extends StatefulWidget {
  const _PluginMediaPager({required this.items, this.imageBuilder});

  final List<PluginMediaItem> items;
  final PluginMediaImageBuilder? imageBuilder;

  @override
  State<_PluginMediaPager> createState() => _PluginMediaPagerState();
}

class _PluginMediaPagerState extends State<_PluginMediaPager> {
  var _index = 0;

  @override
  Widget build(BuildContext context) {
    final items = widget.items;
    final ratio = clampPluginMediaAspect(items[_index].aspectRatio);
    final color = Theme.of(context).colorScheme.onSurfaceVariant;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        AspectRatio(
          aspectRatio: ratio,
          child: PageView.builder(
            itemCount: items.length,
            onPageChanged: (i) => setState(() => _index = i),
            itemBuilder: (context, i) => _PluginMediaTile(
              item: items[i],
              index: i,
              items: items,
              imageBuilder: widget.imageBuilder,
              fill: true,
            ),
          ),
        ),
        const SizedBox(height: 6),
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            for (var i = 0; i < items.length; i++)
              Container(
                width: 5,
                height: 5,
                margin: const EdgeInsets.symmetric(horizontal: 2),
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: color.withValues(alpha: i == _index ? 0.85 : 0.28),
                ),
              ),
          ],
        ),
      ],
    );
  }
}

class _PluginMediaTile extends StatelessWidget {
  const _PluginMediaTile({
    required this.item,
    required this.index,
    required this.items,
    this.imageBuilder,
    this.fill = false,
  });

  final PluginMediaItem item;
  final int index;
  final List<PluginMediaItem> items;
  final PluginMediaImageBuilder? imageBuilder;
  final bool fill;

  @override
  Widget build(BuildContext context) {
    final radius = tweetMediaRadiusOf(context);
    final image =
        imageBuilder?.call(context, item, BoxFit.cover) ??
        ExtendedImage.network(
          item.url,
          fit: BoxFit.cover,
          cache: true,
          timeLimit: const Duration(seconds: 12),
          retries: 1,
          loadStateChanged: (state) {
            if (state.extendedImageLoadState == LoadState.failed) {
              return ColoredBox(
                color: Theme.of(context).colorScheme.surfaceContainerHighest,
              );
            }
            return null;
          },
        );

    final framed = ClipRRect(
      borderRadius: fill ? BorderRadius.zero : BorderRadius.circular(radius),
      child: Stack(
        fit: StackFit.expand,
        children: [
          image,
          if (item.isVideo)
            const Center(
              child: DecoratedBox(
                decoration: BoxDecoration(
                  color: Color(0x99000000),
                  shape: BoxShape.circle,
                ),
                child: Padding(
                  padding: EdgeInsets.all(10),
                  child: Icon(
                    Icons.play_arrow_rounded,
                    color: Colors.white,
                    size: 28,
                  ),
                ),
              ),
            ),
        ],
      ),
    );

    final child = fill
        ? framed
        : AspectRatio(
            aspectRatio: clampPluginMediaAspect(item.aspectRatio),
            child: framed,
          );

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: () => openPluginImageViewer(
          context,
          items: items,
          initialIndex: index,
          imageBuilder: imageBuilder,
        ),
        child: child,
      ),
    );
  }
}

void openPluginImageViewer(
  BuildContext context, {
  required List<PluginMediaItem> items,
  int initialIndex = 0,
  PluginMediaImageBuilder? imageBuilder,
}) {
  final visible = items.where((e) => e.url.isNotEmpty).toList();
  if (visible.isEmpty) {
    return;
  }
  Navigator.of(context).push(
    PageRouteBuilder<void>(
      opaque: false,
      pageBuilder: (context, animation, secondary) => PluginImageViewer(
        items: visible,
        initialIndex: initialIndex.clamp(0, visible.length - 1),
        imageBuilder: imageBuilder,
      ),
      transitionsBuilder: (context, animation, secondary, child) {
        return FadeTransition(opacity: animation, child: child);
      },
    ),
  );
}

/// Pinch-zoom pager so every plugin opens media the same way.
class PluginImageViewer extends StatefulWidget {
  const PluginImageViewer({
    super.key,
    required this.items,
    this.initialIndex = 0,
    this.imageBuilder,
  });

  final List<PluginMediaItem> items;
  final int initialIndex;
  final PluginMediaImageBuilder? imageBuilder;

  @override
  State<PluginImageViewer> createState() => _PluginImageViewerState();
}

class _PluginImageViewerState extends State<PluginImageViewer> {
  late final PageController _pages;
  late int _index;

  @override
  void initState() {
    super.initState();
    _index = widget.initialIndex.clamp(0, widget.items.length - 1);
    _pages = PageController(initialPage: _index);
  }

  @override
  void dispose() {
    _pages.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        children: [
          PageView.builder(
            controller: _pages,
            itemCount: widget.items.length,
            onPageChanged: (i) => setState(() => _index = i),
            itemBuilder: (context, i) {
              final item = widget.items[i];
              return widget.imageBuilder?.call(context, item, BoxFit.contain) ??
                  ExtendedImage.network(
                    item.url,
                    fit: BoxFit.contain,
                    mode: ExtendedImageMode.gesture,
                    timeLimit: const Duration(seconds: 12),
                    initGestureConfigHandler: (state) => GestureConfig(
                      minScale: 1,
                      maxScale: 4,
                      animationMinScale: 0.8,
                      animationMaxScale: 4.5,
                      inPageView: widget.items.length > 1,
                    ),
                  );
            },
          ),
          SafeArea(
            child: Row(
              children: [
                IconButton(
                  icon: const Icon(Icons.close, color: Colors.white),
                  onPressed: () => Navigator.of(context).maybePop(),
                ),
                const Spacer(),
                if (widget.items.length > 1)
                  Padding(
                    padding: const EdgeInsets.only(right: 16),
                    child: Text(
                      '${_index + 1}/${widget.items.length}',
                      style: const TextStyle(color: Colors.white70),
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
