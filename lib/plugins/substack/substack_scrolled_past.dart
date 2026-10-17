import 'dart:async';

import 'package:flutter/rendering.dart';
import 'package:flutter/scheduler.dart';
import 'package:flutter/widgets.dart';
import 'package:xta/plugins/substack/substack_models.dart';

/// Finds the posts whose cards have scrolled fully off the top of their list
/// while the reader scrolls down, and hands them to [onPast] at most once per
/// [every]. Cards are looked at on every downward frame, before a fling can
/// carry them out of the list's cache and unregister them; the list's cards
/// register through [SubstackScrolledPastTag].
class SubstackScrolledPast {
  final void Function(List<SubstackPost> posts) onPast;
  final Duration every;
  final _shown = <String, ({SubstackPost post, BuildContext context})>{};
  final _pending = <String, SubstackPost>{};
  final _reported = <String>{};
  Timer? _timer;
  bool _looking = false;
  bool _closed = false;

  SubstackScrolledPast({required this.onPast, this.every = const Duration(seconds: 1)});

  bool onScroll(ScrollNotification notification) {
    if (notification.depth != 0 || notification is! ScrollUpdateNotification) return false;
    if ((notification.scrollDelta ?? 0) <= 0 || _looking) return false;
    // A scroll is reported before the frame lays the cards out where it moved them.
    _looking = true;
    SchedulerBinding.instance.addPostFrameCallback((_) {
      _looking = false;
      if (_closed) return;
      _collect();
      if (_pending.isNotEmpty) _timer ??= Timer(every, _flush);
    });
    return false;
  }

  /// Hands over the registered posts now above the top of the list.
  void check() {
    _collect();
    _flush();
  }

  void dispose() {
    _closed = true;
    _timer?.cancel();
  }

  void _collect() {
    for (final MapEntry(:key, value: shown) in _shown.entries) {
      if (!_reported.contains(key) && _aboveTop(shown.context)) _pending[key] = shown.post;
    }
  }

  void _flush() {
    _timer?.cancel();
    _timer = null;
    if (_pending.isEmpty) return;
    final past = _pending.values.toList();
    _reported.addAll(_pending.keys);
    _pending.clear();
    onPast(past);
  }

  void _show(SubstackPost post, BuildContext context) => _shown[substackPostKey(post)] = (post: post, context: context);

  void _hide(SubstackPost post, BuildContext context) {
    final key = substackPostKey(post);
    if (identical(_shown[key]?.context, context)) _shown.remove(key);
  }
}

bool _aboveTop(BuildContext context) {
  if (!context.mounted) return false;
  final box = context.findRenderObject();
  if (box is! RenderBox || !box.attached || !box.hasSize) return false;
  final viewport = RenderAbstractViewport.maybeOf(box);
  if (viewport is! RenderBox) return false;
  return box.localToGlobal(Offset(0, box.size.height), ancestor: viewport).dy <= 0;
}

/// Registers its card with a [SubstackScrolledPast] while it is built.
class SubstackScrolledPastTag extends StatefulWidget {
  final SubstackScrolledPast tracker;
  final SubstackPost post;
  final Widget child;

  const SubstackScrolledPastTag({super.key, required this.tracker, required this.post, required this.child});

  @override
  State<SubstackScrolledPastTag> createState() => _SubstackScrolledPastTagState();
}

class _SubstackScrolledPastTagState extends State<SubstackScrolledPastTag> {
  @override
  void initState() {
    super.initState();
    widget.tracker._show(widget.post, context);
  }

  @override
  void didUpdateWidget(SubstackScrolledPastTag oldWidget) {
    super.didUpdateWidget(oldWidget);
    oldWidget.tracker._hide(oldWidget.post, context);
    widget.tracker._show(widget.post, context);
  }

  @override
  void dispose() {
    widget.tracker._hide(widget.post, context);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => widget.child;
}
