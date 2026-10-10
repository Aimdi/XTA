import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show ScrollCacheExtent;
import 'package:flutter_triple/flutter_triple.dart';
import 'package:scroll_to_index/scroll_to_index.dart';
import 'package:visibility_detector/visibility_detector.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_downloaded_badge.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_page_actions.dart';
import 'package:xta/plugins/pixiv/pixiv_page_surface.dart';
import 'package:xta/plugins/pixiv/pixiv_reader_store.dart';
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
            PixivSavePageButton(
              key: const ValueKey('pixiv-reader-download'),
              illust: widget.illust,
              page: state.pageIndex,
              onPressed: () => runPageAction(PixivPageAction.downloadPage, state.pageIndex),
            ),
            IconButton(
              key: const ValueKey('pixiv-reader-more'),
              tooltip: MaterialLocalizations.of(context).showMenuTooltip,
              onPressed: () => openPageActions(state.pageIndex),
              icon: const Icon(Icons.more_vert),
            ),
          ],
        ),
        body: SafeArea(top: false, bottom: false, child: state.vertical ? _verticalPages() : _horizontalPages()),
        bottomNavigationBar: _pages.length < 2 ? null : _pageBar(state),
      ),
    );
  }

  Widget _pageBar(PixivReaderState state) => SafeArea(
    top: false,
    child: Padding(
      padding: const EdgeInsetsDirectional.fromSTEB(4, 0, 12, 4),
      child: Row(
        children: [
          Expanded(
            child: SizedBox(height: kMinInteractiveDimension, child: _pageSlider(state)),
          ),
          Tooltip(
            message: L10n.of(context).plugin_pixiv_all_pages,
            child: OutlinedButton.icon(
              key: const ValueKey('pixiv-reader-counter'),
              onPressed: openPageOverview,
              icon: const Icon(Icons.grid_view_outlined, size: 18),
              label: Text(L10n.of(context).plugin_pixiv_page_of(state.shownIndex + 1, _pages.length)),
            ),
          ),
        ],
      ),
    ),
  );

  Widget _pageSlider(PixivReaderState state) {
    final l10n = L10n.of(context);
    final last = _pages.length - 1;
    return SliderTheme(
      data: SliderTheme.of(context).copyWith(tickMarkShape: SliderTickMarkShape.noTickMark),
      child: Slider(
        key: const ValueKey('pixiv-reader-slider'),
        value: state.shownIndex.toDouble(),
        max: last.toDouble(),
        divisions: last,
        label: '${state.shownIndex + 1}',
        semanticFormatterCallback: (value) => l10n.plugin_pixiv_current_page(value.round() + 1, _pages.length),
        onChanged: (value) => _store.scrub(value.round()),
        onChangeEnd: _jumpFromSlider,
      ),
    );
  }

  Widget _verticalPages() => ListView.builder(
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
        child: GestureDetector(onLongPress: () => openPageActions(index), child: _pageImage(index, vertical: true)),
      ),
    ),
  );

  Widget _horizontalPages() => PageView.builder(
    controller: _horizontalController,
    itemCount: _pages.length,
    onPageChanged: _store.observedPage,
    itemBuilder: (context, index) => PixivZoomable(
      onLongPress: () => openPageActions(index),
      child: Center(child: _pageImage(index, vertical: false)),
    ),
  );

  Widget _pageImage(int index, {required bool vertical}) => Semantics(
    image: true,
    label: L10n.of(context).plugin_pixiv_page_of(index + 1, _pages.length),
    child: PixivNetworkImage(
      url: _pages[index],
      fit: vertical ? BoxFit.fitWidth : BoxFit.contain,
      cacheWidth: (MediaQuery.sizeOf(context).width * MediaQuery.devicePixelRatioOf(context)).ceil(),
      loadStateChanged: (state) {
        if (state.extendedImageLoadState == LoadState.completed) return null;
        return AspectRatio(
          aspectRatio: widget.illust.aspectRatio,
          child: Center(
            child: state.extendedImageLoadState == LoadState.failed
                ? IconButton(
                    tooltip: L10n.of(context).retry,
                    onPressed: state.reLoadImage,
                    icon: const Icon(Icons.refresh),
                  )
                : const CircularProgressIndicator(),
          ),
        );
      },
    ),
  );
}
