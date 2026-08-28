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
import 'package:xta/ui/capped_network_image.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/utils/paging.dart';
import 'package:visibility_detector/visibility_detector.dart';

typedef MediaGridConfig = ({int columns, double spacing, double radius});

/// Resolves the media-layout preference: masonry follows the column-count
/// setting; the feed layout is one full-width item per row (a timeline
/// without text); the two-per-row layout is a roomier two-column masonry.
MediaGridConfig mediaGridConfigOf(BuildContext context) {
  var prefs = PrefService.of(context, listen: false);
  var layout = prefs.get<String>(optionMediaGridLayout) ?? mediaGridLayoutMasonry;
  return switch (layout) {
    mediaGridLayoutFeed => (columns: 1, spacing: 8.0, radius: 12.0),
    mediaGridLayoutTwoColumns => (columns: 2, spacing: 8.0, radius: 12.0),
    _ => (columns: prefs.get<int>(optionMediaGridColumns) ?? 3, spacing: 2.0, radius: 8.0),
  };
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

class _MediaGridState extends State<MediaGrid> with AutomaticKeepAliveClientMixin<MediaGrid> {
  @override
  bool get wantKeepAlive => true;

  final GifPlaybackGate _gifGate = GifPlaybackGate();

  @override
  void dispose() {
    _gifGate.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);

    var config = mediaGridConfigOf(context);

    return RefreshIndicator(
      onRefresh: () async => widget.controller.refresh(),
      child: PagingListener<int, MediaGridItem>(
        controller: widget.controller,
        builder: (context, state, fetchNextPage) => PagedMasonryGridView<int, MediaGridItem>.count(
          state: state,
          fetchNextPage: fetchNextPage,
          padding: EdgeInsets.all(config.spacing),
          crossAxisCount: config.columns,
          mainAxisSpacing: config.spacing,
          crossAxisSpacing: config.spacing,
          addAutomaticKeepAlives: false,
          builderDelegate: PagedChildBuilderDelegate<MediaGridItem>(
            itemBuilder: (context, item, index) => _MediaGridTile(
                item: item,
                gifGate: _gifGate,
                radius: config.radius,
                onTap: () => openMediaLightbox(context, controller: widget.controller, initialIndex: index)),
            firstPageErrorIndicatorBuilder: (context) => FullPageErrorWidget(
              error: pagingErrorOf(state)?.error,
              stackTrace: pagingErrorOf(state)?.stackTrace,
              prefix: widget.firstPageErrorPrefix,
              onRetry: fetchNextPage,
            ),
            newPageErrorIndicatorBuilder: (context) => FullPageErrorWidget(
              error: pagingErrorOf(state)?.error,
              stackTrace: pagingErrorOf(state)?.stackTrace,
              prefix: widget.newPageErrorPrefix,
              onRetry: fetchNextPage,
            ),
            noItemsFoundIndicatorBuilder: (context) => Center(child: Text(widget.emptyMessage)),
          ),
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

  const StaticMediaGrid({super.key, required this.items, required this.emptyMessage, this.onLongPressItem});

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
            child: Center(child: Text(widget.emptyMessage)),
          ),
        ),
      );
    }

    var config = mediaGridConfigOf(context);

    return MasonryGridView.count(
      padding: EdgeInsets.all(config.spacing),
      physics: const AlwaysScrollableScrollPhysics(),
      crossAxisCount: config.columns,
      mainAxisSpacing: config.spacing,
      crossAxisSpacing: config.spacing,
      itemCount: widget.items.length,
      itemBuilder: (context, index) => _MediaGridTile(
          item: widget.items[index],
          gifGate: _gifGate,
          radius: config.radius,
          onTap: () => openMediaLightbox(context, staticItems: widget.items, initialIndex: index),
          onLongPress: widget.onLongPressItem == null ? null : () => widget.onLongPressItem!(widget.items[index])),
    );
  }
}

class _MediaGridTile extends StatefulWidget {
  final MediaGridItem item;
  final GifPlaybackGate gifGate;
  final double radius;
  final VoidCallback onTap;
  final VoidCallback? onLongPress;

  const _MediaGridTile(
      {required this.item, required this.gifGate, this.radius = 8, required this.onTap, this.onLongPress});

  @override
  State<_MediaGridTile> createState() => _MediaGridTileState();
}

class _MediaGridTileState extends State<_MediaGridTile> {
  bool _showMedia = false;

  @override
  void initState() {
    super.initState();

    var disableAutoload = PrefService.of(context, listen: false).get<bool>(optionMediaDisableAutoload) ?? false;
    if (disableAutoload) {
      cachedImageExists(widget.item.thumbnailUrl).then((value) {
        if (mounted) {
          setState(() {
            _showMedia = value;
          });
        }
      });
    } else {
      _showMedia = true;
    }
  }

  String _getMediaTypeLabel(MediaGridItem item) {
    return switch (item) {
      GifGridItem() => 'GIF',
      PhotoGridItem() => 'photo',
      VideoGridItem() => 'video',
      BroadcastGridItem() => 'broadcast',
    };
  }

  @override
  Widget build(BuildContext context) {
    final item = widget.item;

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
    } else {
      body = GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: () => setState(() => _showMedia = true),
        child: Container(
          color: Colors.black26,
          alignment: Alignment.center,
          padding: const EdgeInsets.all(8),
          child: Text(
            L10n.of(context).tap_to_show_getMediaType_item_type(_getMediaTypeLabel(item)),
            textAlign: TextAlign.center,
          ),
        ),
      );
    }

    return AspectRatio(
      aspectRatio: item.aspectRatio,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(widget.radius),
        child: body,
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
      onVisibilityChanged: (info) => widget.gate.report(this, info.visibleFraction),
      child: widget.gate.isGranted(this)
          ? widget.item.toWidget(context)
          : CappedNetworkImage(url: widget.item.thumbnailUrl),
    );
  }
}
