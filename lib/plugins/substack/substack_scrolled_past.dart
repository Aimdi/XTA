import 'dart:async';

import 'package:flutter/rendering.dart';
import 'package:flutter/widgets.dart';
import 'package:xta/plugins/substack/substack_models.dart';

/// Finds the posts whose cards have scrolled fully off the top of their list,
/// at most once per [every] while the reader scrolls down, and hands them to
/// [onPast]. The list's cards register through [SubstackScrolledPastTag].
class SubstackScrolledPast {
  final void Function(List<SubstackPost> posts) onPast;
  final Duration every;
  final _shown = <String, ({SubstackPost post, BuildContext context})>{};
  Timer? _timer;

  SubstackScrolledPast({required this.onPast, this.every = const Duration(seconds: 1)});

  bool onScroll(ScrollNotification notification) {
    if (notification.depth != 0 || notification is! ScrollUpdateNotification) return false;
    if ((notification.scrollDelta ?? 0) > 0) _timer ??= Timer(every, check);
    return false;
  }

  /// Hands over the registered posts now above the top of the list.
  void check() {
    _timer = null;
    final past = [
      for (final shown in _shown.values)
        if (_aboveTop(shown.context)) shown.post,
    ];
    if (past.isNotEmpty) onPast(past);
  }

  void dispose() => _timer?.cancel();

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
