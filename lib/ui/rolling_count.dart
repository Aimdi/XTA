import 'dart:math' as math;
import 'dart:ui' show lerpDouble;

import 'package:flutter/material.dart';
import 'package:xta/ui/motion.dart';

const Duration kRollingCountDuration = Duration(milliseconds: 300);

/// Whether a count moving from [from] to [to] rolls upwards: a rise, or a
/// change whose numbers are not known.
bool rollingCountRises(int? from, int? to) => from == null || to == null || to >= from;

/// A count that rolls to its new number as X's does: the old text slides out
/// of its line while the new one slides in, upwards for a rise and downwards
/// for a fall.
///
/// Only a change between two shown numbers rolls. A count that appears or
/// goes away changes at once, and so does every change with reduced motion.
class RollingCount extends StatefulWidget {
  final String text;

  /// The number [text] shows, which picks the direction of the roll.
  final int? value;
  final TextStyle? style;

  /// What the count showed before this widget existed, such as the copy an
  /// opened post was shown from: the first build rolls from it.
  final String? from;
  final int? fromValue;

  const RollingCount(this.text, {super.key, this.value, this.style, this.from, this.fromValue});

  @override
  State<RollingCount> createState() => _RollingCountState();
}

class _RollingCountState extends State<RollingCount> with SingleTickerProviderStateMixin {
  /// Made on the first change, so a count that never changes runs no ticker.
  AnimationController? _controller;
  String? _old;
  bool _rises = true;
  bool _startedFrom = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final from = widget.from;
    if (!_startedFrom && from != null) {
      _startedFrom = true;
      _roll(from, widget.fromValue);
    }
  }

  @override
  void didUpdateWidget(RollingCount oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.text != widget.text) {
      _roll(oldWidget.text, oldWidget.value);
    }
  }

  @override
  void dispose() {
    _controller?.dispose();
    super.dispose();
  }

  void _roll(String from, int? fromValue) {
    if (from.isEmpty || widget.text.isEmpty || from == widget.text || xtaReduceMotion(context)) {
      _old = null;
      _controller?.value = 1;
      return;
    }
    _old = from;
    _rises = rollingCountRises(fromValue, widget.value);
    (_controller ??= AnimationController(vsync: this, duration: kRollingCountDuration)).forward(from: 0);
  }

  @override
  Widget build(BuildContext context) {
    final current = Text(widget.text, maxLines: 1, style: widget.style);
    final controller = _controller;
    final old = _old;
    if (controller == null || old == null) {
      return current;
    }
    return AnimatedBuilder(
      animation: controller,
      builder: (context, _) => controller.value >= 1
          ? current
          : _rolling(context, Curves.easeOutCubic.transform(controller.value), old, current),
    );
  }

  /// [old] leaving and [current] arriving at [t], inside a line box whose
  /// width moves between theirs.
  Widget _rolling(BuildContext context, double t, String old, Widget current) {
    final style = DefaultTextStyle.of(context).style.merge(widget.style);
    final from = _measure(context, old, style);
    final to = _measure(context, widget.text, style);
    final height = math.max(from.height, to.height);
    final travel = _rises ? -height : height;
    return SizedBox(
      width: lerpDouble(from.width, to.width, t),
      height: height,
      child: ClipRect(
        child: OverflowBox(
          alignment: AlignmentDirectional.centerStart,
          minWidth: 0,
          maxWidth: double.infinity,
          child: Stack(
            children: [
              Transform.translate(
                offset: Offset(0, travel * t),
                child: Opacity(
                  opacity: 1 - t,
                  child: ExcludeSemantics(child: Text(old, maxLines: 1, style: widget.style)),
                ),
              ),
              Transform.translate(
                offset: Offset(0, -travel * (1 - t)),
                child: Opacity(opacity: t, child: current),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Size _measure(BuildContext context, String text, TextStyle style) {
    final painter = TextPainter(
      text: TextSpan(text: text, style: style),
      textDirection: Directionality.of(context),
      textScaler: MediaQuery.textScalerOf(context),
      maxLines: 1,
    )..layout();
    final size = painter.size;
    painter.dispose();
    return size;
  }
}
