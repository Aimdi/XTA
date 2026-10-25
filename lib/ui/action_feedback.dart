import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/ui/motion.dart';
import 'package:xta/utils/desktop.dart';

/// How large an action's icon grows as it pops.
const double kActionPopScale = 1.25;
const Duration kActionPop = Duration(milliseconds: 300);

/// Whether calm or zen mode keeps the reader's own actions quiet: no heart
/// burst, no pop, no haptic tap.
bool quietActions(BuildContext context) {
  final prefs = context.findAncestorWidgetOfExactType<PrefService>()?.service;
  return prefs?.get<bool>(optionCalmMode) == true || prefs?.get<bool>(optionZenMode) == true;
}

/// Whether XTA answers touch with haptic taps, as it does unless the reader
/// turned Haptic feedback off in Accessibility. Android's own touch feedback
/// setting can still silence them, and the desktop has nothing to tap with.
bool hapticFeedbackOn(BuildContext context) {
  if (isDesktop) return false;
  final prefs = context.findAncestorWidgetOfExactType<PrefService>()?.service;
  return prefs?.get<bool>(optionHapticFeedback) ?? true;
}

/// A light tap answering a tap or a long press, when [hapticFeedbackOn].
void lightHaptic(BuildContext context) {
  if (hapticFeedbackOn(context)) HapticFeedback.lightImpact();
}

/// The click of a choice taking effect, when [hapticFeedbackOn].
void selectionHaptic(BuildContext context) {
  if (hapticFeedbackOn(context)) HapticFeedback.selectionClick();
}

/// The light tap of liking or saving, which calm and zen mode keep quiet too.
void actionTap(BuildContext context) {
  if (!quietActions(context)) lightHaptic(context);
}

/// Grows to [kActionPopScale], then settles back past its size and home.
final Animatable<double> _pop = TweenSequence([
  TweenSequenceItem(
    tween: Tween(begin: 1.0, end: kActionPopScale).chain(CurveTween(curve: Curves.easeOut)),
    weight: 40,
  ),
  TweenSequenceItem(
    tween: Tween(begin: kActionPopScale, end: 1.0).chain(CurveTween(curve: Curves.easeOutBack)),
    weight: 60,
  ),
]);

/// [child], an action's icon, popping as the action turns [active] while it
/// shows, as the bookmark does on saving. Turning off, and arriving already
/// on, leave it still; so do reduced motion and [quietActions].
class ActionPop extends StatefulWidget {
  final bool active;
  final Widget child;

  const ActionPop({super.key, required this.active, required this.child});

  @override
  State<ActionPop> createState() => _ActionPopState();
}

class _ActionPopState extends State<ActionPop> with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final Animation<double> _scale;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: kActionPop);
    _scale = _controller.drive(_pop);
  }

  @override
  void didUpdateWidget(ActionPop oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.active && !oldWidget.active && !xtaReduceMotion(context) && !quietActions(context)) {
      _controller.forward(from: 0);
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ScaleTransition(scale: _scale, child: widget.child);
}
