import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:scroll_to_index/scroll_to_index.dart';
import 'package:xta/reading/article_block_position.dart';
import 'package:xta/reading/article_reading_store.dart';

/// Keeps a native reader's place in a text of blocks: puts it back on open
/// and after a change of text size, and reports it to [reading] as the
/// reader scrolls. The reader's screen owns it; the scroll view takes its
/// [controller], [viewport] and [onScroll], and each block an [AutoScrollTag].
class ArticleBlockReading {
  final ArticleReadingStore reading;

  /// How many blocks the text has now.
  final int Function() blocks;

  final controller = AutoScrollController();
  final viewport = GlobalKey();
  late final position = ArticleBlockScrollPosition(controller, viewport);
  Timer? _report;
  bool _closed = false;

  /// Set while the reader's place is put back, so the moves made for it are not reported as reading.
  bool _restoring = true;

  /// The place being put back, and how many restores are queued for it: a
  /// text size changed meanwhile holds this place rather than reading a view
  /// still on its way there.
  ArticleReadPoint? _holding;
  int _queued = 0;
  Future<void> _restores = Future.value();
  bool _interacted = false;
  bool _userScrolled = false;

  /// Where the reader was when leaving before the last scroll was reported.
  /// Read while the view is still laid out, handed over in [close], once
  /// nothing on screen listens any more.
  ArticleReadPoint? _leftAt;

  ArticleBlockReading(this.reading, {required this.blocks});

  /// Puts [point] back once the next frame is laid out, after the restores
  /// queued before it; only the last of a burst moves the list. True when it
  /// was the last and the place is back.
  Future<bool> putBack(ArticleReadPoint point) async {
    _holding = point;
    _restoring = true;
    _queued++;
    final previous = _restores;
    final restore = _restores = () async {
      await previous;
      await WidgetsBinding.instance.endOfFrame;
      if (!_closed && _queued == 1) await position.restore(point, blocks: blocks());
    }();
    await restore;
    if (--_queued > 0 || _closed) return false;
    _holding = null;
    _restoring = false;
    return true;
  }

  /// Puts [point] back and reports where that left the reader.
  Future<void> open(ArticleReadPoint point) async {
    if (await putBack(point)) reportNow();
  }

  bool onScroll(ScrollNotification notification) {
    if (notification.depth != 0 || _restoring) return false;
    if (notification is ScrollStartNotification && notification.dragDetails != null) _interacted = true;
    if (notification is ScrollUpdateNotification) {
      if (_interacted) _userScrolled = true;
      _report?.cancel();
      _report = Timer(const Duration(milliseconds: 200), reportNow);
    }
    return false;
  }

  void reportNow() {
    _report?.cancel();
    final point = _restoring ? null : position.read();
    if (point == null) return;
    final scroll = controller.position;
    reading.receivePoint(
      point,
      interacted: _interacted,
      userScrolled: _userScrolled,
      atEnd: scroll.maxScrollExtent > 24 && scroll.pixels >= scroll.maxScrollExtent - 24,
    );
  }

  /// The place now, or the one on its way back; null before a layout.
  ArticleReadPoint? get place => _holding ?? (_restoring ? null : position.read());

  /// A new text size or spacing keeps the passage being read at the top.
  void keepPlace() {
    final point = _holding ?? position.read();
    if (point != null) unawaited(putBack(point));
  }

  void startOver() {
    _interacted = true;
    _userScrolled = false;
    if (controller.hasClients) controller.jumpTo(0);
    reportNow();
  }

  /// Scrolls block [index] to the top, as the reader asked.
  Future<void> showBlock(int index, {required Duration duration}) {
    _interacted = true;
    return position.showBlock(index, duration: duration);
  }

  /// For the screen's [State.deactivate]: a scroll not yet reported is read
  /// while the view is still laid out.
  void deactivate() {
    if (_report?.isActive ?? false) _leftAt = _restoring ? null : position.read();
  }

  /// For the screen's [State.activate].
  void activate() => _leftAt = null;

  /// For the screen's [State.dispose]. Saving the place writes preferences,
  /// which rebuilds what listens to them, so it waits until this frame has
  /// finished taking the reader down, then runs [then].
  void close({FutureOr<void> Function()? then}) {
    _closed = true;
    _report?.cancel();
    controller.dispose();
    final leftAt = _leftAt;
    final interacted = _interacted;
    scheduleMicrotask(() {
      if (leftAt != null) reading.receivePoint(leftAt, interacted: interacted);
      if (then != null) unawaited(Future.sync(then));
    });
  }
}
