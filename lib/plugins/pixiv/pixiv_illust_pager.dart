import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/ui/empty_pane.dart';
import 'package:xta/ui/motion.dart';

/// How far a work's last (or first) page must be pulled on before the next work turns in.
const pixivEdgeTurnDistance = 56.0;

/// How close to the end of the list the next page starts loading.
const pixivPagerPrefetch = 2;

/// A work's page viewer pushed past its first page ([delta] < 0) or its last (> 0), or
/// let go ([ended]). The pager over a list turns to the neighbouring work from these.
class PixivPageEdgeNotification extends Notification {
  final double delta;
  final bool ended;

  const PixivPageEdgeNotification(this.delta) : ended = false;
  const PixivPageEdgeNotification.ended() : delta = 0, ended = true;
}

/// What a page viewer's own scrolling says about its edges: a drag pushing past the first
/// or last page (clamping and bouncing physics alike), the end of a drag, or nothing.
PixivPageEdgeNotification? pixivPageEdge(ScrollNotification notification) {
  if (notification.metrics.axis != Axis.horizontal) return null;
  return switch (notification) {
    ScrollEndNotification() => const PixivPageEdgeNotification.ended(),
    OverscrollNotification(dragDetails: _?, :final overscroll) => PixivPageEdgeNotification(overscroll),
    ScrollUpdateNotification(dragDetails: _?, :final metrics, scrollDelta: final delta?)
        when (metrics.pixels < metrics.minScrollExtent && delta < 0) ||
            (metrics.pixels > metrics.maxScrollExtent && delta > 0) =>
      PixivPageEdgeNotification(delta),
    _ => null,
  };
}

/// What follows a pager's last work.
enum PixivPagerTail { none, more, loading, failed, end }

class PixivIllustPagerState {
  final List<PixivIllust> illusts;
  final PixivPagerTail tail;

  const PixivIllustPagerState(this.illusts, this.tail);
}

List<PixivIllust> _everyIllust(List<PixivIllust> illusts) => illusts;

/// The works a pager swipes through: the list as it was tapped, growing by its store's next
/// pages. Works only ever join at the end, so the work on screen keeps its place.
class PixivIllustPagerStore extends Store<PixivIllustPagerState> {
  final PixivIllustListStore? source;
  final PixivIllustListFilter visible;
  double _push = 0;
  bool _turned = false;
  bool _closed = false;

  PixivIllustPagerStore(List<PixivIllust> illusts, {this.source, PixivIllustListFilter? visible})
    : visible = visible ?? _everyIllust,
      super(PixivIllustPagerState(illusts, _tailOf(source)));

  static PixivPagerTail _tailOf(PixivIllustListStore? source) => switch (source) {
    null => PixivPagerTail.none,
    final store when store.loadMoreFailed => PixivPagerTail.failed,
    final store when store.hasMore => PixivPagerTail.more,
    _ => PixivPagerTail.end,
  };

  /// Every work, then the page that says what comes after them.
  int get pageCount => state.illusts.length + (state.tail == PixivPagerTail.none ? 0 : 1);

  /// The list's next page, also as Retry after it failed.
  Future<void> loadMore() async {
    final store = source;
    if (store == null || state.tail == PixivPagerTail.loading || state.tail == PixivPagerTail.end) return;
    update(PixivIllustPagerState(state.illusts, PixivPagerTail.loading));
    await store.loadMore();
    if (_closed) return;
    update(PixivIllustPagerState(mergePixivIllusts(state.illusts, visible(store.state)), _tailOf(store)));
  }

  /// Follows a page viewer pushed past its edge: -1 or 1 once, when the push is far enough
  /// to turn to the previous or next work, and 0 otherwise.
  int edgePush(PixivPageEdgeNotification edge) {
    if (edge.ended) {
      _push = 0;
      _turned = false;
      return 0;
    }
    if (_turned) return 0;
    _push = _push.sign == edge.delta.sign ? _push + edge.delta : edge.delta;
    if (_push.abs() < pixivEdgeTurnDistance) return 0;
    _turned = true;
    return _push.sign.toInt();
  }

  @override
  Future<void> destroy() {
    _closed = true;
    return super.destroy();
  }
}

/// A work opened from a list, in a sideways pager over its neighbours. Past the last work
/// the list's next page loads, with Retry when it fails and an end once there is no more.
class PixivIllustPager extends StatefulWidget {
  final List<PixivIllust> illusts;
  final int initialIndex;
  final PixivIllustListStore? source;

  /// What of the store's later pages may show, such as the reader's mutes.
  final PixivIllustListFilter? visible;
  final Widget Function(PixivIllust illust) page;

  const PixivIllustPager({
    super.key,
    required this.illusts,
    required this.initialIndex,
    required this.page,
    this.source,
    this.visible,
  });

  @override
  State<PixivIllustPager> createState() => _PixivIllustPagerState();
}

class _PixivIllustPagerState extends State<PixivIllustPager> {
  late final _store = PixivIllustPagerStore(widget.illusts, source: widget.source, visible: widget.visible);
  late final _pages = PageController(initialPage: widget.initialIndex);

  @override
  void initState() {
    super.initState();
    _changed(widget.initialIndex);
  }

  @override
  void dispose() {
    _pages.dispose();
    _store.destroy();
    super.dispose();
  }

  void _changed(int index) {
    if (index >= _store.state.illusts.length - pixivPagerPrefetch) unawaited(_store.loadMore());
  }

  bool _onEdge(PixivPageEdgeNotification edge) {
    final turn = _store.edgePush(edge);
    if (turn != 0 && _pages.hasClients) _turn(turn);
    return true;
  }

  void _turn(int direction) {
    final target = (_pages.page ?? widget.initialIndex.toDouble()).round() + direction;
    if (target < 0 || target >= _store.pageCount) return;
    final duration = xtaMotionDuration(context, kXtaMotionNavigation);
    if (duration == Duration.zero) {
      _pages.jumpToPage(target);
    } else {
      unawaited(_pages.animateToPage(target, duration: duration, curve: Curves.easeOutCubic));
    }
  }

  @override
  Widget build(BuildContext context) => NotificationListener<PixivPageEdgeNotification>(
    onNotification: _onEdge,
    child: ScopedBuilder<PixivIllustPagerStore, PixivIllustPagerState>(
      store: _store,
      onState: (context, state) => PageView.builder(
        key: const ValueKey('pixiv-illust-pager'),
        controller: _pages,
        itemCount: _store.pageCount,
        onPageChanged: _changed,
        itemBuilder: (context, index) => index < state.illusts.length
            ? KeyedSubtree(key: ValueKey(state.illusts[index].id), child: widget.page(state.illusts[index]))
            : PixivPagerTailPage(tail: state.tail, onRetry: _store.loadMore),
      ),
    ),
  );
}

/// The page after a pager's last work: loading, a failed load with Retry, or the end.
class PixivPagerTailPage extends StatelessWidget {
  final PixivPagerTail tail;
  final VoidCallback onRetry;

  const PixivPagerTailPage({super.key, required this.tail, required this.onRetry});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Scaffold(
      key: const ValueKey('pixiv-pager-tail'),
      appBar: AppBar(),
      body: switch (tail) {
        PixivPagerTail.failed => EmptyPane(
          icon: Icons.cloud_off_outlined,
          message: l10n.plugin_pixiv_pager_load_failed,
          action: FilledButton.icon(onPressed: onRetry, icon: const Icon(Icons.refresh), label: Text(l10n.retry)),
        ),
        PixivPagerTail.end ||
        PixivPagerTail.none => EmptyPane(icon: Icons.done_all, message: l10n.plugin_pixiv_pager_no_more),
        PixivPagerTail.more || PixivPagerTail.loading => const Center(child: CircularProgressIndicator()),
      },
    );
  }
}
