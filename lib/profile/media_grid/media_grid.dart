import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_staggered_grid_view/flutter_staggered_grid_view.dart';
import 'package:infinite_scroll_pagination/infinite_scroll_pagination.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/profile/media_grid/gif_playback_gate.dart';
import 'package:xta/profile/media_grid/media_grid_items/media_grid_item.dart';
import 'package:xta/profile/media_grid/media_grid_lightbox.dart';
import 'package:xta/tweet/media_strip.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/capped_network_image.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/motion.dart';
import 'package:xta/utils/paging.dart';
import 'package:visibility_detector/visibility_detector.dart';

typedef MediaGridConfig = ({
  int columns,
  double spacing,
  double radius,
  EdgeInsetsGeometry padding,
  double minAspectRatio,
  double maxAspectRatio,
});

/// Resolves the media-layout preference: masonry follows the column-count
/// setting; the feed layout is one full-width item per row (a timeline
/// without text); the two-per-row layout is a roomier two-column masonry.
MediaGridConfig mediaGridConfigOf(BuildContext context) {
  final prefs = PrefService.of(context, listen: false);
  final layout =
      prefs.get<String>(optionMediaGridLayout) ?? mediaGridLayoutMasonry;
  final mediaRadius = tweetMediaRadiusOf(context);
  return switch (layout) {
    mediaGridLayoutFeed => (
      columns: 1,
      spacing: kTweetSpace3,
      radius: mediaRadius,
      padding: const EdgeInsetsDirectional.fromSTEB(
        kTweetSpace4,
        kTweetSpace2,
        kTweetSpace4,
        kTweetSpace4,
      ),
      minAspectRatio: kMediaMinAspect,
      maxAspectRatio: kMediaMaxAspect,
    ),
    mediaGridLayoutTwoColumns => (
      columns: 2,
      spacing: kTweetSpace2,
      radius: mediaRadius.clamp(8.0, 12.0).toDouble(),
      padding: const EdgeInsets.all(kTweetSpace2),
      minAspectRatio: kMediaMinAspect,
      maxAspectRatio: 3 / 2,
    ),
    _ => (
      columns: (prefs.get<int>(optionMediaGridColumns) ?? 3)
          .clamp(1, 5)
          .toInt(),
      spacing: kTweetMediaGap,
      radius: mediaRadius.clamp(6.0, 8.0).toDouble(),
      padding: const EdgeInsets.all(kTweetMediaGap),
      minAspectRatio: 3 / 5,
      maxAspectRatio: 2,
    ),
  };
}

/// Thumbnail geometry is bounded for browsing; fullscreen keeps the source
/// ratio. Invalid metadata falls back to square rather than breaking layout.
double mediaGridAspectRatio(double aspectRatio, MediaGridConfig config) {
  if (!aspectRatio.isFinite || aspectRatio <= 0) {
    return 1;
  }
  return aspectRatio
      .clamp(config.minAspectRatio, config.maxAspectRatio)
      .toDouble();
}

class MediaGrid extends StatefulWidget {
  final PagingController<int, MediaGridItem> controller;
  final String firstPageErrorPrefix;
  final String newPageErrorPrefix;
  final String emptyMessage;

  const MediaGrid({
    super.key,
    required this.controller,
    required this.firstPageErrorPrefix,
    required this.newPageErrorPrefix,
    required this.emptyMessage,
  });

  @override
  State<MediaGrid> createState() => _MediaGridState();
}

class _MediaGridState extends State<MediaGrid>
    with AutomaticKeepAliveClientMixin<MediaGrid> {
  @override
  bool get wantKeepAlive => true;

  final GifPlaybackGate _gifGate = GifPlaybackGate();
  bool _firstLoadStarted = false;

  @override
  void didUpdateWidget(covariant MediaGrid oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!identical(oldWidget.controller, widget.controller)) {
      _firstLoadStarted = false;
    }
  }

  @override
  void dispose() {
    _gifGate.dispose();
    super.dispose();
  }

  void _maybeStartFirstLoad() {
    scheduleFirstPageFetch(
      widget.controller,
      alreadyStarted: _firstLoadStarted,
      markStarted: () => _firstLoadStarted = true,
      isMounted: () => mounted,
    );
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    _maybeStartFirstLoad();

    final config = mediaGridConfigOf(context);

    return RefreshIndicator(
      onRefresh: () async => widget.controller.refresh(),
      child: PagingListener<int, MediaGridItem>(
        controller: widget.controller,
        builder: (context, state, fetchNextPage) {
          late final Widget child;
          if (pagingAwaitingFirstPage(state)) {
            child = KeyedSubtree(
              key: const ValueKey('media-grid-loading'),
              child: pagingFill(
                child: MediaGridSkeleton(config: config),
              ),
            );
          } else if (state.items == null) {
            child = KeyedSubtree(
              key: const ValueKey('media-grid-error'),
              child: FullPageErrorWidget(
                error: pagingErrorOf(state)?.error,
                stackTrace: pagingErrorOf(state)?.stackTrace,
                prefix: widget.firstPageErrorPrefix,
                onRetry: fetchNextPage,
              ),
            );
          } else if (state.items!.isEmpty) {
            child = KeyedSubtree(
              key: const ValueKey('media-grid-empty'),
              child: pagingFill(
                child: _MediaGridEmpty(message: widget.emptyMessage),
              ),
            );
          } else {
            child = KeyedSubtree(
              key: const ValueKey('media-grid-content'),
              child: PagedMasonryGridView<int, MediaGridItem>.count(
                state: state,
                fetchNextPage: fetchNextPage,
                padding: config.padding,
                crossAxisCount: config.columns,
                mainAxisSpacing: config.spacing,
                crossAxisSpacing: config.spacing,
                addAutomaticKeepAlives: false,
                builderDelegate: PagedChildBuilderDelegate<MediaGridItem>(
                  itemBuilder: (context, item, index) => _MediaGridTile(
                    item: item,
                    gifGate: _gifGate,
                    radius: config.radius,
                    aspectRatio: mediaGridAspectRatio(
                      item.aspectRatio,
                      config,
                    ),
                    position: index + 1,
                    total: state.items!.length,
                    onTap: () => openMediaGridItem(
                      context,
                      item: item,
                      index: index,
                      controller: widget.controller,
                    ),
                  ),
                  newPageErrorIndicatorBuilder: (context) =>
                      FullPageErrorWidget(
                        error: pagingErrorOf(state)?.error,
                        stackTrace: pagingErrorOf(state)?.stackTrace,
                        prefix: widget.newPageErrorPrefix,
                        onRetry: fetchNextPage,
                      ),
                ),
              ),
            );
          }
          return XtaAnimatedSwitcher(child: child);
        },
      ),
    );
  }
}

/// Media-shaped first-page placeholder shared by Profile, Search and Groups.
/// It stays static so reduced-motion users and fast scrolling pay no ticker
/// cost.
class MediaGridSkeleton extends StatelessWidget {
  final MediaGridConfig config;

  const MediaGridSkeleton({super.key, required this.config});

  static const _aspects = <double>[
    4 / 5,
    1,
    3 / 2,
    2 / 3,
    4 / 3,
    1,
    5 / 4,
    3 / 5,
    16 / 9,
    1,
    4 / 5,
    3 / 2,
  ];

  @override
  Widget build(BuildContext context) {
    final color = Color.alphaBlend(
      tweetSecondaryColor(context).withValues(alpha: 0.08),
      tweetSurfaceColor(context),
    );

    return ExcludeSemantics(
      child: MasonryGridView.count(
        primary: false,
        physics: const NeverScrollableScrollPhysics(),
        padding: config.padding,
        crossAxisCount: config.columns,
        mainAxisSpacing: config.spacing,
        crossAxisSpacing: config.spacing,
        itemCount: _aspects.length,
        itemBuilder: (context, index) => AspectRatio(
          aspectRatio: mediaGridAspectRatio(_aspects[index], config),
          child: DecoratedBox(
            decoration: BoxDecoration(
              color: color,
              borderRadius: BorderRadius.circular(config.radius),
            ),
          ),
        ),
      ),
    );
  }
}

class _MediaGridEmpty extends StatelessWidget {
  final String message;

  const _MediaGridEmpty({required this.message});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(kTweetSpace6),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.photo_library_outlined,
              size: 40,
              color: tweetSecondaryColor(context),
            ),
            const SizedBox(height: kTweetSpace3),
            Text(
              message,
              textAlign: TextAlign.center,
              style: tweetMetadataStyle(context).copyWith(fontSize: 14),
            ),
          ],
        ),
      ),
    );
  }
}

/// Non-paginated media grid for an in-memory item list (e.g. saved posts),
/// sharing the paginated grid's tiles and layout preference.
class StaticMediaGrid extends StatefulWidget {
  final List<MediaGridItem> items;
  final String emptyMessage;
  // When set, long-pressing a tile invokes this with its item (used by the
  // saved gallery to remove a bookmark, e.g. a dead "not available" one).
  final void Function(MediaGridItem item)? onLongPressItem;

  const StaticMediaGrid({
    super.key,
    required this.items,
    required this.emptyMessage,
    this.onLongPressItem,
  });

  @override
  State<StaticMediaGrid> createState() => _StaticMediaGridState();
}

class _StaticMediaGridState extends State<StaticMediaGrid> {
  final GifPlaybackGate _gifGate = GifPlaybackGate();

  @override
  void dispose() {
    _gifGate.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (widget.items.isEmpty) {
      return LayoutBuilder(
        builder: (context, constraints) => SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          child: ConstrainedBox(
            constraints: BoxConstraints(minHeight: constraints.maxHeight),
            child: _MediaGridEmpty(message: widget.emptyMessage),
          ),
        ),
      );
    }

    final config = mediaGridConfigOf(context);

    return MasonryGridView.count(
      padding: config.padding,
      physics: const AlwaysScrollableScrollPhysics(),
      crossAxisCount: config.columns,
      mainAxisSpacing: config.spacing,
      crossAxisSpacing: config.spacing,
      itemCount: widget.items.length,
      itemBuilder: (context, index) => _MediaGridTile(
        item: widget.items[index],
        gifGate: _gifGate,
        radius: config.radius,
        aspectRatio: mediaGridAspectRatio(
          widget.items[index].aspectRatio,
          config,
        ),
        position: index + 1,
        total: widget.items.length,
        onTap: () => openMediaGridItem(
          context,
          item: widget.items[index],
          index: index,
          staticItems: widget.items,
        ),
        onLongPress: widget.onLongPressItem == null
            ? null
            : () => widget.onLongPressItem!(widget.items[index]),
      ),
    );
  }
}

class _MediaGridTile extends StatefulWidget {
  final MediaGridItem item;
  final GifPlaybackGate gifGate;
  final double radius;
  final double aspectRatio;
  final int position;
  final int total;
  final VoidCallback onTap;
  final VoidCallback? onLongPress;

  const _MediaGridTile({
    required this.item,
    required this.gifGate,
    required this.radius,
    required this.aspectRatio,
    required this.position,
    required this.total,
    required this.onTap,
    this.onLongPress,
  });

  @override
  State<_MediaGridTile> createState() => _MediaGridTileState();
}

class _MediaGridTileState extends State<_MediaGridTile> {
  bool _showMedia = false;
  int _autoloadEpoch = 0;

  @override
  void initState() {
    super.initState();
    _resolveAutoload();
  }

  @override
  void didUpdateWidget(covariant _MediaGridTile oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.item.tweetId != widget.item.tweetId ||
        oldWidget.item.mediaIndex != widget.item.mediaIndex ||
        oldWidget.item.thumbnailUrl != widget.item.thumbnailUrl) {
      _resolveAutoload();
    }
  }

  void _resolveAutoload() {
    final epoch = ++_autoloadEpoch;
    final disableAutoload =
        PrefService.of(
          context,
          listen: false,
        ).get<bool>(optionMediaDisableAutoload) ??
        false;
    if (!disableAutoload) {
      _showMedia = true;
      return;
    }

    final thumbnailUrl = widget.item.thumbnailUrl;
    if (thumbnailUrl.isEmpty) {
      _showMedia = true;
      return;
    }

    _showMedia = false;
    cachedImageExists(thumbnailUrl).then((cached) {
      if (!mounted || epoch != _autoloadEpoch) return;
      setState(() => _showMedia = cached);
    });
  }

  void _revealMedia() => setState(() => _showMedia = true);

  String _mediaTypeLabel(BuildContext context, MediaGridItem item) {
    final l10n = L10n.of(context);
    return switch (item) {
      GifGridItem() || VideoGridItem() => l10n.videos,
      PhotoGridItem() => l10n.photos,
      BroadcastGridItem() => item.isSpace ? l10n.spaces : l10n.broadcasts,
    };
  }

  Widget? _mediaIndicator(BuildContext context, MediaGridItem item) {
    return switch (item) {
      GifGridItem() => TweetMediaBadge(
        icon: Icons.gif_box_outlined,
        semanticLabel: L10n.of(context).videos,
      ),
      VideoGridItem() => TweetMediaBadge(
        icon: Icons.videocam_outlined,
        semanticLabel: L10n.of(context).videos,
      ),
      _ => null,
    };
  }

  IconData _manualLoadIcon(MediaGridItem item) => switch (item) {
    PhotoGridItem() => Icons.photo_outlined,
    GifGridItem() => Icons.gif_box_outlined,
    VideoGridItem() => Icons.play_circle_outline,
    BroadcastGridItem() => Icons.live_tv_outlined,
  };

  @override
  Widget build(BuildContext context) {
    final item = widget.item;
    final typeLabel = _mediaTypeLabel(context, item);

    Widget body;
    if (_showMedia) {
      body = GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: widget.onTap,
        onLongPress: widget.onLongPress,
        child: item is GifGridItem
            ? _GifGridCell(item: item, gate: widget.gifGate)
            : item.toWidget(context),
      );
      final indicator = _mediaIndicator(context, item);
      if (indicator != null) {
        body = Stack(
          fit: StackFit.expand,
          children: [
            body,
            PositionedDirectional(
              start: kTweetSpace2,
              bottom: kTweetSpace2,
              child: indicator,
            ),
          ],
        );
      }
    } else {
      body = GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: _revealMedia,
        child: ColoredBox(
          color: Color.alphaBlend(
            tweetSecondaryColor(context).withValues(alpha: 0.08),
            tweetSurfaceColor(context),
          ),
          child: Padding(
            padding: const EdgeInsets.all(kTweetSpace2),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(
                  _manualLoadIcon(item),
                  color: tweetSecondaryColor(context),
                ),
                const SizedBox(height: kTweetSpace1),
                Text(
                  L10n.of(
                    context,
                  ).tap_to_show_getMediaType_item_type(typeLabel),
                  textAlign: TextAlign.center,
                  maxLines: 3,
                  overflow: TextOverflow.ellipsis,
                  style: tweetMetadataStyle(context),
                ),
              ],
            ),
          ),
        ),
      );
    }

    return Semantics(
      container: true,
      image: true,
      button: true,
      label:
          '${L10n.of(context).media}: $typeLabel, '
          '${widget.position}/${widget.total}',
      onTap: _showMedia ? widget.onTap : _revealMedia,
      onLongPress: _showMedia ? widget.onLongPress : null,
      excludeSemantics: true,
      child: AspectRatio(
        aspectRatio: widget.aspectRatio,
        child: Container(
          clipBehavior: Clip.antiAlias,
          decoration: BoxDecoration(
            color: Colors.black,
            borderRadius: BorderRadius.circular(widget.radius),
            border: Border.all(
              color: tweetDividerColor(context),
              width: kTweetDividerThickness,
            ),
          ),
          child: body,
        ),
      ),
    );
  }
}

/// A grid GIF cell that animates only while the shared [GifPlaybackGate] grants
/// it one of the limited playback slots; otherwise it shows a static thumbnail.
class _GifGridCell extends StatefulWidget {
  final GifGridItem item;
  final GifPlaybackGate gate;

  const _GifGridCell({required this.item, required this.gate});

  @override
  State<_GifGridCell> createState() => _GifGridCellState();
}

class _GifGridCellState extends State<_GifGridCell> {
  final Key _visibilityKey = UniqueKey();

  @override
  void initState() {
    super.initState();
    widget.gate.addListener(_onGrantsChanged);
  }

  void _onGrantsChanged() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    widget.gate.removeListener(_onGrantsChanged);
    widget.gate.forget(this);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return VisibilityDetector(
      key: _visibilityKey,
      onVisibilityChanged: (info) =>
          widget.gate.report(this, info.visibleFraction),
      child: widget.gate.isGranted(this)
          ? widget.item.toWidget(context)
          : CappedNetworkImage(url: widget.item.thumbnailUrl),
    );
  }
}
