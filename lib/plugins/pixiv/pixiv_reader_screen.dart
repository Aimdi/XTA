import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:scroll_to_index/scroll_to_index.dart';
import 'package:visibility_detector/visibility_detector.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_post_actions.dart';
import 'package:xta/plugins/pixiv/pixiv_reader_store.dart';
import 'package:xta/ui/motion.dart';

class PixivReaderScreen extends StatefulWidget {
  final PixivIllust illust;
  final int initialPage;

  const PixivReaderScreen({super.key, required this.illust, this.initialPage = 0});

  @override
  State<PixivReaderScreen> createState() => _PixivReaderScreenState();
}

class _PixivReaderScreenState extends State<PixivReaderScreen> {
  late final _pages = widget.illust.viewerUrls;
  late final _store = PixivReaderStore(pageCount: _pages.length, initialPage: widget.initialPage);
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

  Future<void> _choosePage() async {
    final selected = await showModalBottomSheet<int>(
      context: context,
      showDragHandle: true,
      isScrollControlled: true,
      builder: (context) => SafeArea(
        child: SizedBox(
          height: MediaQuery.sizeOf(context).height * 0.6,
          child: Column(
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                child: Text(L10n.of(context).choose_pages, style: Theme.of(context).textTheme.titleLarge),
              ),
              Expanded(
                child: GridView.builder(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
                  gridDelegate: SliverGridDelegateWithMaxCrossAxisExtent(
                    maxCrossAxisExtent: 88.0 * (MediaQuery.textScalerOf(context).scale(14) / 14).clamp(1.0, 2.0),
                    mainAxisExtent: 56.0 * (MediaQuery.textScalerOf(context).scale(14) / 14).clamp(1.0, 2.0),
                    mainAxisSpacing: 8,
                    crossAxisSpacing: 8,
                  ),
                  itemCount: _pages.length,
                  itemBuilder: (context, index) => Semantics(
                    label: L10n.of(context).plugin_pixiv_page_of(index + 1, _pages.length),
                    selected: index == _store.state.pageIndex,
                    child: FilledButton.tonal(
                      style: FilledButton.styleFrom(
                        padding: const EdgeInsets.symmetric(horizontal: 8),
                        backgroundColor: index == _store.state.pageIndex ? Theme.of(context).colorScheme.primary : null,
                        foregroundColor: index == _store.state.pageIndex
                            ? Theme.of(context).colorScheme.onPrimary
                            : null,
                      ),
                      onPressed: () => Navigator.pop(context, index),
                      child: Text('${index + 1}'),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
    if (!mounted || selected == null) return;
    _restorePosition(selected);
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
          ],
        ),
        body: SafeArea(top: false, bottom: false, child: state.vertical ? _verticalPages() : _horizontalPages()),
        bottomNavigationBar: SafeArea(
          top: false,
          minimum: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
          child: Row(
            children: [
              Icon(state.vertical ? Icons.arrow_downward : Icons.swipe_outlined),
              const SizedBox(width: 12),
              Expanded(
                child: LinearProgressIndicator(
                  value: _pages.isEmpty ? 0 : (state.pageIndex + 1) / _pages.length,
                  minHeight: 3,
                ),
              ),
              const SizedBox(width: 12),
              Tooltip(
                message: l10n.choose_pages,
                child: TextButton.icon(
                  onPressed: _choosePage,
                  icon: const Icon(Icons.grid_view_outlined, size: 18),
                  label: Text(l10n.plugin_pixiv_page_of(state.pageIndex + 1, _pages.length)),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _verticalPages() => ListView.builder(
    key: const PageStorageKey('pixiv-continuous-reader'),
    controller: _verticalController,
    cacheExtent: 400,
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
        child: _pageImage(index, vertical: true),
      ),
    ),
  );

  Widget _horizontalPages() => PageView.builder(
    controller: _horizontalController,
    itemCount: _pages.length,
    onPageChanged: _store.observedPage,
    itemBuilder: (context, index) =>
        InteractiveViewer(minScale: 1, maxScale: 4, child: Center(child: _pageImage(index, vertical: false))),
  );

  Widget _pageImage(int index, {required bool vertical}) => Semantics(
    image: true,
    label: L10n.of(context).plugin_pixiv_page_of(index + 1, _pages.length),
    child: GestureDetector(
      onLongPress: () => showPixivPostActions(context, widget.illust),
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
    ),
  );
}
