import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show ScrollCacheExtent;
import 'package:flutter_triple/flutter_triple.dart';
import 'package:flutter_staggered_grid_view/flutter_staggered_grid_view.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/pixiv/pixiv_grid_columns.dart';
import 'package:xta/plugins/pixiv/pixiv_illust_tile.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_paged_feed.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/pixiv/pixiv_viewing_prefs.dart';
import 'package:xta/plugins/plugin_feed_skeleton.dart';

export 'package:xta/plugins/pixiv/pixiv_illust_tile.dart';

/// The reader's mutes as one list applies them, such as a profile that shows
/// its own creator's works.
typedef PixivMuteView = PixivMuteState Function(PixivMuteState mute);

/// Builds the cell for the work at [index] of the works a grid shows.
typedef PixivIllustTileBuilder = Widget Function(BuildContext context, List<PixivIllust> illusts, int index);

/// How far past the screen a works grid builds its tiles ahead.
const pixivGridCacheExtent = ScrollCacheExtent.pixels(1200);

/// Works as a staggered sliver: as many columns as the list's width and the
/// reader's settings allow, without the works the reader muted.
class PixivIllustSliverGrid extends StatelessWidget {
  final List<PixivIllust> illusts;

  /// Inside the measured width, so the columns follow the list's whole width.
  final EdgeInsetsGeometry padding;

  /// The reader's mutes as this list applies them; all of them when null.
  final PixivMuteView? mutes;

  /// Off for a list the reader keeps themselves, such as the viewing history;
  /// a muted work there still waits behind its notice when opened.
  final bool hideMuted;

  /// A plain tile opening among its neighbours (and [source]'s next pages) when null.
  final PixivIllustTileBuilder? tileBuilder;

  /// The store [illusts] come from, handed to tiles so a work opens among its neighbours.
  final PixivIllustListStore? source;

  const PixivIllustSliverGrid({
    super.key,
    required this.illusts,
    this.padding = EdgeInsets.zero,
    this.mutes,
    this.hideMuted = true,
    this.tileBuilder,
    this.source,
  });

  @override
  Widget build(BuildContext context) => PixivPrefsBuilder(
    keys: pixivGridPrefKeys,
    builder: (context) {
      if (!hideMuted) return _layout(illusts);
      // Plain ScopedBuilder (not .transition): mute changes must not animate the
      // whole masonry — that rebuilds every ExtendedImage and thrash-decodes.
      return ScopedBuilder<PixivMuteStore, PixivMuteState>(
        store: context.read<PixivMuteStore>(),
        onState: (context, mute) => _layout((mutes?.call(mute) ?? mute).filter(illusts)),
      );
    },
  );

  Widget _layout(List<PixivIllust> shown) => SliverLayoutBuilder(
    builder: (context, constraints) => SliverPadding(
      padding: padding,
      sliver: SliverMasonryGrid.count(
        crossAxisCount: pixivGridColumnsFor(context, constraints.crossAxisExtent),
        mainAxisSpacing: 8,
        crossAxisSpacing: 8,
        childCount: shown.length,
        itemBuilder: (context, index) => _tile(context, shown, index),
      ),
    ),
  );

  Widget _tile(BuildContext context, List<PixivIllust> illusts, int index) =>
      tileBuilder?.call(context, illusts, index) ??
      PixivIllustTile(illust: illusts[index], siblings: illusts, index: index, source: source);
}

/// Pixez-style staggered gallery of a fixed list of works, such as the
/// viewing history; a paged list is a [PixivIllustFeed].
class PixivIllustGrid extends StatelessWidget {
  final List<PixivIllust> illusts;
  final ScrollController? scrollController;
  final EdgeInsetsGeometry padding;

  /// Slivers scrolled above the works, such as a carousel or a header row.
  final List<Widget> leadingSlivers;

  /// The reader's mutes as this list applies them; all of them when null.
  final PixivMuteView? mutes;

  /// Whether muted works are left out; see [PixivIllustSliverGrid.hideMuted].
  final bool hideMuted;

  /// A plain tile opening among its neighbours (and [source]'s next pages) when null.
  final PixivIllustTileBuilder? tileBuilder;

  /// The store [illusts] come from, handed to tiles so a work opens among its neighbours.
  final PixivIllustListStore? source;

  const PixivIllustGrid({
    super.key,
    required this.illusts,
    this.scrollController,
    this.padding = const EdgeInsets.all(4),
    this.leadingSlivers = const [],
    this.mutes,
    this.hideMuted = true,
    this.tileBuilder,
    this.source,
  });

  @override
  Widget build(BuildContext context) => pixivScrollView(
    context,
    controller: scrollController,
    cacheExtent: pixivGridCacheExtent,
    slivers: [
      ...leadingSlivers,
      PixivIllustSliverGrid(
        illusts: illusts,
        padding: padding,
        mutes: mutes,
        hideMuted: hideMuted,
        tileBuilder: tileBuilder,
        source: source,
      ),
    ],
  );
}

/// A paged works list as a section shows it: [PixivPagedFeed] over the
/// masonry grid, with a skeleton first and thumbnails fetched ahead.
class PixivIllustFeed extends StatelessWidget {
  final PixivIllustListStore store;
  final String emptyMessage;
  final ScrollController? scrollController;
  final List<Widget> leadingSlivers;

  /// The reader's mutes as this list applies them; [store] filters its pages the same way.
  final PixivMuteView? mutes;

  const PixivIllustFeed({
    super.key,
    required this.store,
    required this.emptyMessage,
    this.scrollController,
    this.leadingSlivers = const [],
    this.mutes,
  });

  @override
  Widget build(BuildContext context) => PixivPagedFeed<PixivIllust>(
    store: store,
    emptyMessage: emptyMessage,
    emptyIcon: Icons.photo_outlined,
    placeholder: const PluginGridSkeleton(columns: 2),
    scrollController: scrollController,
    leadingSlivers: leadingSlivers,
    // The next API page is asked for well before the footer — Pixez-style.
    loadAhead: 1400,
    cacheExtent: pixivGridCacheExtent,
    sliver: (context, illusts) => _ThumbPrefetch(
      illusts: illusts,
      child: PixivIllustSliverGrid(illusts: illusts, padding: const EdgeInsets.all(4), mutes: mutes, source: store),
    ),
  );
}

/// Prefetches thumbs only when the list grows — not on every mute/loading tick.
class _ThumbPrefetch extends StatefulWidget {
  final List<PixivIllust> illusts;
  final Widget child;

  const _ThumbPrefetch({required this.illusts, required this.child});

  @override
  State<_ThumbPrefetch> createState() => _ThumbPrefetchState();
}

class _ThumbPrefetchState extends State<_ThumbPrefetch> {
  var _lastCount = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _maybePrefetch());
  }

  @override
  void didUpdateWidget(covariant _ThumbPrefetch oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.illusts.length != oldWidget.illusts.length) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _maybePrefetch());
    }
  }

  void _maybePrefetch() {
    if (!mounted || widget.illusts.length <= _lastCount) {
      return;
    }
    final from = _lastCount;
    _lastCount = widget.illusts.length;
    unawaited(prefetchPixivThumbs(context, widget.illusts.skip(from)));
  }

  @override
  Widget build(BuildContext context) => widget.child;
}
