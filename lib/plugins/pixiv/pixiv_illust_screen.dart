import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_author_works.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_detail_menu.dart';
import 'package:xta/plugins/pixiv/pixiv_detail_meta.dart';
import 'package:xta/plugins/pixiv/pixiv_detail_related.dart';
import 'package:xta/plugins/pixiv/pixiv_detail_split.dart';
import 'package:xta/plugins/pixiv/pixiv_detail_store.dart';
import 'package:xta/plugins/pixiv/pixiv_detail_viewer.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_page_surface.dart';
import 'package:xta/plugins/pixiv/pixiv_reader_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/pixiv/pixiv_viewing_prefs.dart';
import 'package:xta/plugins/plugin_view_store.dart';

/// In-app illust viewer — pages, caption, tags, stats, related works (Pixez-like).
///
/// A shell over the detail's parts: the viewer, the meta block, the author's
/// other works, similar works and the AppBar actions each live in their own file.
class PixivIllustScreen extends StatefulWidget {
  final PixivIllust illust;

  /// Told once the whole work has loaded the first time.
  final ValueChanged<PixivIllust>? onLoaded;

  const PixivIllustScreen({super.key, required this.illust, this.onLoaded});

  @override
  State<PixivIllustScreen> createState() => _PixivIllustScreenState();
}

class _PixivIllustScreenState extends State<PixivIllustScreen> with PixivPageSurface {
  late final PixivIllustDetailStore _detail;
  late final PixivIllustListStore _related;
  final _page = PluginViewStore<int>(0);
  final _pager = PageController();
  final _inFlight = <Future<void>>{};

  @override
  void initState() {
    super.initState();
    final client = context.read<PixivClient>();
    _detail = PixivIllustDetailStore(client, widget.illust);
    _related = pixivRelatedStore(client, widget.illust, filter: context.read<PixivMuteStore>().filter);
    unawaited(_firstLoad());
    WidgetsBinding.instance.addPostFrameCallback((_) => _followRestoredPage());
  }

  Future<void> _firstLoad() async {
    if (await _load() && mounted) widget.onLoaded?.call(_detail.state);
  }

  /// A work swiped back into a pager reopens on the page it was left at; the counter follows.
  void _followRestoredPage() {
    if (mounted && _pager.hasClients) _page.select((_pager.page ?? 0).round());
  }

  @override
  void dispose() {
    _pager.dispose();
    // Loads still in flight write to the stores when they land; destroy after.
    unawaited(Future.wait(_inFlight).whenComplete(_destroyStores));
    super.dispose();
  }

  void _destroyStores() {
    for (final store in <Store<Object?>>[_detail, _related, _page]) {
      store.destroy();
    }
  }

  Future<T> _track<T>(Future<T> work) {
    _inFlight.add(work);
    unawaited(work.whenComplete(() => _inFlight.remove(work)));
    return work;
  }

  void _maybeLoadMore(ScrollMetrics metrics) {
    if (metrics.pixels > metrics.maxScrollExtent - 1400 && _related.hasMore && !_related.loadingMore) {
      _track(_related.loadMore());
    }
  }

  /// Detail and similar works in parallel; the seed stays on screen meanwhile.
  /// True once the detail arrived.
  Future<bool> _load() => _track((_detail.load(), _related.refresh()).wait.then((loaded) => loaded.$1));

  @override
  PixivIllust get pageIllust => _detail.state;

  @override
  int get currentPage => _page.state;

  @override
  bool get offersVertical => true;

  @override
  void showPage(int page) {
    if (_pager.hasClients) _pager.jumpToPage(page);
  }

  @override
  void changeDirection() => _openReader(currentPage, vertical: true);

  void _openReader(int page, {required bool vertical}) => Navigator.push(
    context,
    MaterialPageRoute<void>(
      builder: (_) => PixivReaderScreen(illust: pageIllust, initialPage: page, vertical: vertical),
    ),
  );

  @override
  Widget build(BuildContext context) => ScopedBuilder<PixivIllustDetailStore, PixivIllust>(
    store: _detail,
    onState: (context, illust) => Scaffold(
      appBar: AppBar(
        title: Text(illust.title.isEmpty ? L10n.of(context).plugin_pixiv_title : illust.title),
        actions: pixivDetailActions(context, this),
      ),
      body: LayoutBuilder(
        builder: (context, constraints) =>
            pixivDetailSplits(constraints.maxWidth, pixivDetailLayout(pixivPrefsOf(context)))
            ? _split(illust)
            : _scroller([..._pagesSlivers(illust), ..._infoSlivers(illust)]),
      ),
    ),
  );

  /// The pictures on the left, everything else scrolling on the right.
  Widget _split(PixivIllust illust) => PixivDetailSplit(
    images: Column(
      children: [
        Expanded(child: _viewer(illust, expand: true)),
        if (illust.viewerUrls.length > 1) _pageBar(illust),
        const SizedBox(height: 8),
      ],
    ),
    info: _scroller(_infoSlivers(illust)),
  );

  Widget _scroller(List<Widget> slivers) => RefreshIndicator(
    onRefresh: _load,
    child: NotificationListener<ScrollNotification>(
      onNotification: (notification) {
        if (notification.metrics.axis == Axis.vertical) _maybeLoadMore(notification.metrics);
        return false;
      },
      child: CustomScrollView(physics: const AlwaysScrollableScrollPhysics(), slivers: slivers),
    ),
  );

  Widget _viewer(PixivIllust illust, {bool expand = false}) => PixivDetailViewer(
    illust: illust,
    controller: _pager,
    expand: expand,
    onPageChanged: _page.select,
    onOpenPage: (page) => _openReader(page, vertical: false),
    onPageActions: openPageActions,
  );

  Widget _pageBar(PixivIllust illust) => ScopedBuilder<PluginViewStore<int>, int>(
    store: _page,
    onState: (context, page) => PixivDetailPageBar(
      page: page,
      pages: illust.viewerUrls.length,
      onOverview: openPageOverview,
      onReadVertically: changeDirection,
    ),
  );

  List<Widget> _pagesSlivers(PixivIllust illust) => [
    SliverToBoxAdapter(child: _viewer(illust)),
    if (illust.viewerUrls.length > 1) SliverToBoxAdapter(child: _pageBar(illust)),
  ];

  List<Widget> _infoSlivers(PixivIllust illust) => [
    SliverToBoxAdapter(child: PixivDetailMeta(illust: illust)),
    SliverToBoxAdapter(
      child: PixivAuthorWorks(key: ValueKey('pixiv-author-${illust.userId}'), illust: illust),
    ),
    PixivDetailRelated(store: _related, detailError: _detail.triple.error, onRetry: _load),
  ];
}
