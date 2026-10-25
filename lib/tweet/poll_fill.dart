import 'dart:math' as math;

import 'package:flutter/widgets.dart';
import 'package:xta/ui/motion.dart';

/// How long one bar of an opened post's poll takes to grow to its share.
const Duration kPollBarGrowth = Duration(milliseconds: 600);

/// How long after the bar above each bar starts growing.
const Duration kPollBarStagger = Duration(milliseconds: 80);

/// The whole fill: the last of X's four options starts three staggers in.
const Duration kPollFill = Duration(milliseconds: 840);

/// How far bar [index] has grown towards its share at [t] of [kPollFill].
double pollBarGrowth(double t, int index) {
  final total = kPollFill.inMilliseconds;
  final start = math.min(1.0, index * kPollBarStagger.inMilliseconds / total);
  final end = math.min(1.0, start + kPollBarGrowth.inMilliseconds / total);
  return Interval(start, end, curve: Curves.easeOutCubic).transform(t);
}

/// The opened post's poll filling in. The conversation screen keeps it, so the
/// copy shown while X answers and the post X sends fill as one, and a poll
/// scrolled back into view stays full.
///
/// It runs once the first poll asks for it and the screen has slid in, so the
/// reader sees the bars grow.
class FocalPollFill extends StatefulWidget {
  final Widget child;

  const FocalPollFill({super.key, required this.child});

  /// The fill, set going by this call, or null outside a conversation screen.
  static Animation<double>? of(BuildContext context) {
    final owner = context.getInheritedWidgetOfExactType<_FocalPollFillScope>()?.owner;
    owner?._startSoon();
    return owner?._fill;
  }

  @override
  State<FocalPollFill> createState() => _FocalPollFillState();
}

class _FocalPollFillState extends State<FocalPollFill> with SingleTickerProviderStateMixin {
  late final AnimationController _fill;
  Animation<double>? _entrance;
  bool _asked = false;

  @override
  void initState() {
    super.initState();
    _fill = AnimationController(vsync: this, duration: kPollFill);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _entrance = ModalRoute.of(context)?.animation;
  }

  @override
  void dispose() {
    _entrance?.removeStatusListener(_onEntrance);
    _fill.dispose();
    super.dispose();
  }

  void _startSoon() {
    if (_asked) return;
    _asked = true;
    // After the frame that asked: a build must not start an animation.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final entrance = _entrance;
      if (xtaReduceMotion(context)) {
        _fill.value = 1;
      } else if (entrance == null || entrance.isCompleted) {
        _fill.forward();
      } else {
        entrance.addStatusListener(_onEntrance);
      }
    });
  }

  void _onEntrance(AnimationStatus status) {
    if (!status.isCompleted) return;
    _entrance?.removeStatusListener(_onEntrance);
    _fill.forward();
  }

  @override
  Widget build(BuildContext context) => _FocalPollFillScope(owner: this, child: widget.child);
}

class _FocalPollFillScope extends InheritedWidget {
  final _FocalPollFillState owner;

  const _FocalPollFillScope({required this.owner, required super.child});

  @override
  bool updateShouldNotify(_FocalPollFillScope oldWidget) => false;
}
