import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show ScrollCacheExtent;
import 'package:flutter_triple/flutter_triple.dart';
import 'package:scroll_to_index/scroll_to_index.dart';
import 'package:visibility_detector/visibility_detector.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_page_actions.dart';
import 'package:xta/plugins/pixiv/pixiv_page_surface.dart';
import 'package:xta/plugins/pixiv/pixiv_quality.dart';
import 'package:xta/plugins/pixiv/pixiv_reader_bar.dart';
import 'package:xta/plugins/pixiv/pixiv_reader_store.dart';
import 'package:xta/plugins/pixiv/pixiv_share_image.dart';
import 'package:xta/plugins/pixiv/pixiv_viewing_prefs.dart';
import 'package:xta/plugins/pixiv/pixiv_zoomable.dart';
import 'package:xta/ui/motion.dart';

class PixivReaderScreen extends StatefulWidget {
  final PixivIllust illust;
  final int initialPage;
  final bool vertical;

  const PixivReaderScreen({super.key, required this.illust, this.initialPage = 0, this.vertical = true});

  @override
  State<PixivReaderScreen> createState() => _PixivReaderScreenState();
}

class _PixivReaderScreenState extends State<PixivReaderScreen> with PixivPageSurface {
  late final _pages = widget.illust.viewerUrls;
  late final _store = PixivReaderStore(
    pageCount: _pages.length,
    initialPage: widget.initialPage,
    vertical: widget.vertical,
    hd: pixivQuality(pixivPrefsOf(context), PixivQualitySlot.reader) == PixivImageQuality.original,
  );
  final _verticalController = AutoScrollController();
  Future<void> _restoreQueue = Future.value();
  late final _horizontalController = PageController(initialPage: _store.state.pageIndex);

  @override
  void initState() {
    super.initState();
    _restorePosition(_store.state.pageIndex);
  }

  @override
  void dispose() {
    _verticalController.dispose();
    _horizontalController.dispose();
    _store.destroy();
    super.dispose();
  }

  void _restorePosition(int index) {
    final generation = _store.beginNavigation(index);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || !_store.isCurrentNavigation(generation)) return;
      // The index controller cannot safely run two indexed scrolls at once.
      // Stale queued requests retire before touching either controller.
      _restoreQueue = _restoreQueue.then((_) => _restorePage(index, generation));
    });
  }

  Future<void> _restorePage(int index, int generation) async {
    if (!mounted || !_store.isCurrentNavigation(generation)) return;
    try {
      if (_store.state.vertical && _verticalController.hasClients) {
        await _verticalController.scrollToIndex(
          index,
          preferPosition: AutoScrollPosition.begin,
          duration: xtaReduceMotion(context) ? const Duration(microseconds: 1) : kXtaMotionFast,
        );
      } else if (_horizontalController.hasClients) {
        _horizontalController.jumpToPage(index);
      }
    } catch (error, stackTrace) {
      // Detaching a direction's controller can interrupt an older scroll.
      if (mounted && _store.isCurrentNavigation(generation)) {
        FlutterError.reportError(FlutterErrorDetails(exception: error, stack: stackTrace));
      }
    } finally {
      if (mounted && _store.finishNavigation(generation)) {
        VisibilityDetectorController.instance.notifyNow();
      }
    }
  }

  void _toggleDirection() {
    final index = _store.state.pageIndex;
    _store.toggleDirection();
    _restorePosition(index);
  }

  @override
  PixivIllust get pageIllust => widget.illust;

  @override
  int get currentPage => _store.state.pageIndex;

  @override
  bool get offersVertical => !_store.state.vertical;

  @override
  void showPage(int page) => _restorePosition(page);

  @override
  void changeDirection() => _toggleDirection();

  void _jumpFromSlider(double value) {
    _restorePosition(value.round());
    _store.scrub(null);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<PixivReaderStore, PixivReaderState>(
      store: _store,
      onState: (context, state) => Scaffold(
        appBar: AppBar(
          title: Text(
            widget.illust.title.isEmpty ? l10n.plugin_pixiv_title : widget.illust.title,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          actions: [
            IconButton(
              tooltip: state.vertical ? l10n.plugin_pixiv_read_horizontally : l10n.plugin_pixiv_read_vertically,
              onPressed: _toggleDirection,
              icon: Icon(state.vertical ? Icons.swipe_outlined : Icons.arrow_downward),
            ),
            IconButton(
              key: const ValueKey('pixiv-reader-download'),
              tooltip: l10n.plugin_pixiv_download_page,
              onPressed: () => runPageAction(PixivPageAction.downloadPage, state.pageIndex),
              icon: const Icon(Icons.download_outlined),
            ),
            IconButton(
              key: const ValueKey('pixiv-reader-more'),
              tooltip: MaterialLocalizations.of(context).showMenuTooltip,
              onPressed: () => openPageActions(state.pageIndex),
              icon: const Icon(Icons.more_vert),
            ),
          ],
        ),
        body: SafeArea(
          top: false,
          bottom: false,
          child: state.vertical ? _verticalPages(state) : _horizontalPages(state),
        ),
        bottomNavigationBar: PixivReaderBar(
          state: state,
          pages: _pages.length,
          onShare: state.loaded.contains(_urlAt(state.pageIndex, state)) ? () => _share(state) : null,
          onToggleHd: _store.toggleHd,
          onScrub: _store.scrub,
          onJump: _jumpFromSlider,
          onOverview: openPageOverview,
        ),
      ),
    );
  }

  /// The file page [index] shows: the original with HD on, else the large one.
  String _urlAt(int index, PixivReaderState state) => state.hd ? widget.illust.downloadUrlAt(index) : _pages[index];

  Future<void> _share(PixivReaderState state) async {
    final messenger = ScaffoldMessenger.of(context);
    final failed = L10n.of(context).plugin_pixiv_share_image_failed;
    final sharer = PixivImageSharer.of(context);
    final page = state.pageIndex;
    final url = pixivImageUrlFor(context, _urlAt(page, state));
    if (!await sharer.share(widget.illust, page, url)) messenger.showSnackBar(SnackBar(content: Text(failed)));
  }

  Widget _verticalPages(PixivReaderState state) => PixivZoomable(
    onZoomChanged: _store.setZoomed,
    child: ListView.builder(
      key: const PageStorageKey('pixiv-continuous-reader'),
      controller: _verticalController,
      scrollCacheExtent: const ScrollCacheExtent.pixels(400),
      addAutomaticKeepAlives: false,
      itemCount: _pages.length,
      itemBuilder: (context, index) => AutoScrollTag(
        key: ValueKey('pixiv-reader-page-$index'),
        controller: _verticalController,
        index: index,
        child: VisibilityDetector(
          key: ValueKey('pixiv-reader-visible-$index'),
          onVisibilityChanged: (info) {
            if (!mounted) return;
            _store.pageVisibility(index, info.visibleBounds.width * info.visibleBounds.height);
          },
          child: GestureDetector(
            onLongPress: () => openPageActions(index),
            child: _pageImage(index, state, vertical: true),
          ),
        ),
      ),
    ),
  );

  Widget _horizontalPages(PixivReaderState state) => PageView.builder(
    controller: _horizontalController,
    itemCount: _pages.length,
    onPageChanged: _store.observedPage,
    itemBuilder: (context, index) => PixivZoomable(
      onLongPress: () => openPageActions(index),
      onZoomChanged: _store.setZoomed,
      child: Center(child: _pageImage(index, state, vertical: false)),
    ),
  );

  /// A page decoded at the screen's width, or up to three screens wide once zoomed or with HD
  /// on; the decode switch keeps the picture up, a different file starts over with its own progress.
  Widget _pageImage(int index, PixivReaderState state, {required bool vertical}) {
    final url = _urlAt(index, state);
    return Semantics(
      image: true,
      label: L10n.of(context).plugin_pixiv_page_of(index + 1, _pages.length),
      child: PixivNetworkImage(
        key: ValueKey(url),
        url: url,
        fit: vertical ? BoxFit.fitWidth : BoxFit.contain,
        cacheWidth: (MediaQuery.sizeOf(context).width * MediaQuery.devicePixelRatioOf(context)).ceil(),
        fullResolution: state.fullResolution,
        gaplessPlayback: true,
        handleLoadingProgress: true,
        loadStateChanged: (image) => _pageState(image, url),
      ),
    );
  }

  Widget? _pageState(ExtendedImageState image, String url) {
    if (image.extendedImageLoadState == LoadState.completed) {
      if (!_store.state.loaded.contains(url)) {
        WidgetsBinding.instance.addPostFrameCallback((_) => _store.pageLoaded(url));
      }
      return null;
    }
    return AspectRatio(
      aspectRatio: widget.illust.aspectRatio,
      child: Center(
        child: image.extendedImageLoadState == LoadState.failed
            ? IconButton(
                key: const ValueKey('pixiv-reader-retry'),
                tooltip: L10n.of(context).retry,
                onPressed: image.reLoadImage,
                icon: const Icon(Icons.refresh),
              )
            : CircularProgressIndicator(value: pixivLoadProgress(image.loadingProgress)),
      ),
    );
  }
}
