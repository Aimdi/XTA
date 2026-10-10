import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show ScrollCacheExtent;
import 'package:flutter_triple/flutter_triple.dart';
import 'package:flutter_staggered_grid_view/flutter_staggered_grid_view.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_grid_columns.dart';
import 'package:xta/plugins/pixiv/pixiv_illust_tile.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/pixiv/pixiv_viewing_prefs.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/plugins/plugin_feed_skeleton.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/ui/empty_pane.dart';
import 'package:xta/ui/errors.dart';

export 'package:xta/plugins/pixiv/pixiv_illust_tile.dart';

/// Pixez-style staggered gallery of illust thumbnails.
class PixivIllustGrid extends StatelessWidget {
  final List<PixivIllust> illusts;
  final ScrollController? scrollController;
  final Future<void> Function()? onRefresh;
  final bool loadingMore;
  final EdgeInsetsGeometry padding;

  /// Slivers scrolled above the works, such as a carousel or a header row.
  final List<Widget> leadingSlivers;

  /// The store [illusts] come from, handed to tiles so a work opens among its neighbours.
  final PixivIllustListStore? source;

  const PixivIllustGrid({
    super.key,
    required this.illusts,
    this.scrollController,
    this.onRefresh,
    this.loadingMore = false,
    this.padding = const EdgeInsets.all(4),
    this.leadingSlivers = const [],
    this.source,
  });

  @override
  Widget build(BuildContext context) {
    // Plain ScopedBuilder (not .transition): mute changes must not animate the
    // whole masonry — that rebuilds every ExtendedImage and thrash-decodes.
    return PixivPrefsBuilder(
      keys: pixivGridPrefKeys,
      builder: (context) => ScopedBuilder<PixivMuteStore, PixivMuteState>(
        store: context.read<PixivMuteStore>(),
        onState: (context, mute) => LayoutBuilder(
          builder: (context, constraints) =>
              _grid(context, mute.filter(illusts), pixivGridColumnsFor(context, constraints.maxWidth)),
        ),
      ),
    );
  }

  Widget _grid(BuildContext context, List<PixivIllust> visibleIllusts, int columns) {
    final grid = CustomScrollView(
      controller: pluginInnerScrollController(context, scrollController),
      primary: PluginEmbedded.maybeOf(context) ? false : null,
      scrollCacheExtent: const ScrollCacheExtent.pixels(1200),
      physics: const AlwaysScrollableScrollPhysics(),
      slivers: [
        ...leadingSlivers,
        SliverPadding(
          padding: padding,
          sliver: SliverMasonryGrid.count(
            crossAxisCount: columns,
            mainAxisSpacing: 8,
            crossAxisSpacing: 8,
            childCount: visibleIllusts.length,
            itemBuilder: (context, index) =>
                PixivIllustTile(illust: visibleIllusts[index], siblings: visibleIllusts, index: index, source: source),
          ),
        ),
        if (loadingMore)
          const SliverToBoxAdapter(
            child: Padding(
              padding: EdgeInsets.all(16),
              child: Center(child: CircularProgressIndicator()),
            ),
          ),
      ],
    );

    if (onRefresh == null) {
      return grid;
    }
    return RefreshIndicator(onRefresh: onRefresh!, child: grid);
  }
}

/// A paged works list as a section shows it: skeleton first, the grid kept
/// through soft refreshes and failed appends, a retry when empty or failed.
class PixivIllustFeed extends StatelessWidget {
  final PixivIllustListStore store;
  final String emptyMessage;
  final ScrollController? scrollController;
  final List<Widget> leadingSlivers;

  const PixivIllustFeed({
    super.key,
    required this.store,
    required this.emptyMessage,
    this.scrollController,
    this.leadingSlivers = const [],
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<PixivIllustListStore, List<PixivIllust>>(
      store: store,
      onLoading: (context) {
        // Soft refresh keeps prior tiles; only the first load blanks the tab.
        if (store.state.isNotEmpty) {
          return _list(context, store.state);
        }
        return const PluginGridSkeleton(columns: 2);
      },
      onError: (context, error) {
        if (store.state.isNotEmpty) {
          return _list(context, store.state);
        }
        return Padding(
          padding: const EdgeInsets.all(24),
          child: FullPageErrorWidget(
            error: error,
            stackTrace: null,
            prefix: pixivErrorMessage(l10n, error ?? Exception()),
            onRetry: store.refresh,
          ),
        );
      },
      onState: (context, illusts) => illusts.isEmpty ? _empty(l10n) : _list(context, illusts),
    );
  }

  /// Refreshable even when empty: re-selecting the tab does not reload, so a
  /// transient empty page used to strand the reader with no gesture that asks again.
  Widget _empty(L10n l10n) => EmptyPane(
    icon: Icons.photo_outlined,
    message: emptyMessage,
    onRefresh: store.refresh,
    action: FilledButton.icon(onPressed: store.refresh, icon: const Icon(Icons.refresh), label: Text(l10n.retry)),
  );

  Widget _list(BuildContext context, List<PixivIllust> illusts) {
    return _ThumbPrefetch(
      illusts: illusts,
      child: NotificationListener<ScrollNotification>(
        onNotification: (n) {
          // Prefetch the next API page well before the footer — Pixez-style.
          if (n.metrics.pixels > n.metrics.maxScrollExtent - 1400) {
            store.loadMore();
          }
          return false;
        },
        child: PixivIllustGrid(
          illusts: illusts,
          scrollController: scrollController,
          padding: pluginFeedPadding(context, extra: const EdgeInsets.all(4)),
          onRefresh: store.refresh,
          loadingMore: store.loadingMore,
          leadingSlivers: leadingSlivers,
          source: store,
        ),
      ),
    );
  }
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
