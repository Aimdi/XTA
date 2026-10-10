import 'package:flutter/widgets.dart';
import 'package:scroll_to_index/scroll_to_index.dart';
import 'package:xta/reading/article_reading_store.dart';

/// One laid-out block: its index in the text, and its top and height in the
/// viewport's coordinates.
typedef PixivNovelBlockExtent = ({int index, double top, double height});

/// The first block still showing below the viewport's top, and how far above
/// (negative) or below the top its own top sits; null when no block shows.
({int index, double leading})? pixivFirstVisibleBlock(Iterable<PixivNovelBlockExtent> blocks) {
  final showing = blocks.where((block) => block.top + block.height > 0).toList()
    ..sort((a, b) => a.index.compareTo(b.index));
  final first = showing.firstOrNull;
  return first == null ? null : (index: first.index, leading: first.top);
}

/// The quickest move [AutoScrollController] accepts, for jumps the reader
/// should not watch: it splits a move into steps of this over
/// [defaultDurationUnit], and a step must last.
const _instant = Duration(microseconds: defaultDurationUnit);

/// Where the reader stands in a novel's blocks, read from and put back on
/// [controller]: a block and the offset of its top, as the article readers
/// keep it, so a place survives a change of text size.
class PixivNovelScrollPosition {
  final AutoScrollController controller;

  /// On the scroll view, whose top the offsets are measured from.
  final GlobalKey viewport;

  const PixivNovelScrollPosition(this.controller, this.viewport);

  /// The place now, or null before the view is laid out.
  ArticleReadPoint? read() {
    final view = viewport.currentContext?.findRenderObject();
    if (view is! RenderBox || !view.hasSize || !controller.hasClients) return null;
    final first = pixivFirstVisibleBlock([
      for (final MapEntry(key: index, value: tag) in controller.tagMap.entries)
        if (_extentIn(view, tag.context) case final rect?) (index: index, top: rect.top, height: rect.height),
    ]);
    final position = controller.position;
    final max = position.maxScrollExtent;
    return ArticleReadPoint(
      paragraph: first?.index ?? 0,
      leading: first?.leading ?? 0,
      fraction: max > 0 ? (position.pixels / max).clamp(0.0, 1.0) : 0,
    );
  }

  Rect? _extentIn(RenderBox view, BuildContext context) {
    final box = context.findRenderObject();
    if (box is! RenderBox || !box.attached || !box.hasSize) return null;
    return box.localToGlobal(Offset.zero, ancestor: view) & box.size;
  }

  /// Puts [point] back: its block [point.leading] from the top. Nothing moves
  /// for a point at the very start; [blocks] bounds a point from a longer text.
  ///
  /// The list builds its blocks lazily, so the way to a block far from the
  /// ones built is long; [point.fraction] lands near it first.
  Future<void> restore(ArticleReadPoint point, {required int blocks}) async {
    if (point.fraction <= 0.001 || blocks == 0 || !controller.hasClients) return;
    final index = point.paragraph.clamp(0, blocks - 1);
    if (!controller.isIndexStateInLayoutRange(index)) {
      controller.jumpTo(point.fraction * controller.position.maxScrollExtent);
      await WidgetsBinding.instance.endOfFrame;
    }
    await showBlock(index, duration: _instant);
    if (!controller.hasClients) return;
    final position = controller.position;
    controller.jumpTo((position.pixels - point.leading).clamp(position.minScrollExtent, position.maxScrollExtent));
  }

  /// Scrolls block [index] to the top over [duration].
  Future<void> showBlock(int index, {Duration duration = _instant}) async {
    if (!controller.hasClients) return;
    await controller.scrollToIndex(
      index,
      preferPosition: AutoScrollPosition.begin,
      duration: duration > _instant ? duration : _instant,
    );
  }
}
