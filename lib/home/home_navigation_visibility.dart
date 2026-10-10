import 'package:flutter/rendering.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/ui/motion.dart';

/// What the bar on screen lends the store: a motion from 0 (on screen) to 1
/// (gone), how many pixels it travels to leave, and whether motion is reduced.
abstract interface class HomeNavigationMotion {
  AnimationController get controller;
  double get travel;
  bool get reduceMotion;
}

/// Whether the Home bar is on screen. It is tied to the finger, as on X,
/// Threads and Chrome: each pixel scrolled down slides it a pixel further off
/// the bottom edge, each pixel up brings it a pixel back. When the scroll
/// ends, a bar caught halfway settles: past [hideAfter] down it finishes
/// leaving, past [showAfter] up it finishes returning, and less springs back.
/// It always returns at the top or the end of a list and on a tab change.
///
/// The state is false only while the bar is fully away or settling there; a
/// hidden bar takes no taps.
class HomeNavigationVisibilityStore extends Store<bool> {
  /// How far down a stopped scroll must have taken the bar for it to go.
  static const double hideAfter = 24;

  /// How far up brings it back; shorter, so a short flick is enough.
  static const double showAfter = 12;

  static const Curve settleCurve = Curves.easeOutCubic;

  final BasePrefService prefs;

  HomeNavigationMotion? _motion;

  /// Only the reader's own drags and flings move the bar; a programmatic jump
  /// (restored position, inserted posts) would yank it without motion.
  bool _userScrolling = false;
  bool _movingDown = false;

  HomeNavigationVisibilityStore(this.prefs) : super(true);

  bool get enabled => prefs.get<bool>(optionHideNavigationOnScroll) != false;

  /// How far away the bar is: 0 on screen, 1 gone.
  double get hidden => _motion?.controller.value ?? (state ? 0 : 1);

  void attach(HomeNavigationMotion motion) {
    _motion = motion;
    motion.controller.value = state ? 0 : 1;
  }

  void detach(HomeNavigationMotion motion) {
    if (identical(_motion, motion)) _motion = null;
  }

  void show() => _settle(hide: false);

  /// Feeds one scroll notification in. [accessible] is the screen reader's
  /// flag: with one on, a bar that moves is a bar that gets lost.
  void onScroll(ScrollNotification notification, {bool accessible = false}) {
    if (notification.metrics.axis != Axis.vertical) return;
    if (!enabled || accessible) return show();
    final atEdge = _atEdge(notification.metrics);
    switch (notification) {
      case UserScrollNotification(:final direction):
        _userScrolling = direction != ScrollDirection.idle;
      case ScrollUpdateNotification(:final scrollDelta?) when scrollDelta != 0:
        atEdge ? show() : _follow(scrollDelta);
      case OverscrollNotification(:final overscroll) when overscroll < 0:
        show();
      case ScrollEndNotification():
        atEdge ? show() : _settleCaught();
      default:
        break;
    }
  }

  /// Where the bar is after the content moved [delta] pixels, down positive.
  static double followed(double hidden, double delta, double travel) =>
      travel <= 0 ? hidden : (hidden + delta / travel).clamp(0.0, 1.0);

  /// Whether a bar [hiddenBy] pixels out of [travel] comes to rest away.
  static bool restsHidden({
    required double hiddenBy,
    required double travel,
    required bool movingDown,
  }) => movingDown ? hiddenBy >= hideAfter : travel - hiddenBy < showAfter;

  /// The settle's length: the full navigation motion for the whole way,
  /// proportionally less for a bar that is nearly there.
  static Duration settleDuration(double distance) {
    final scaled = kXtaMotionNavigation * distance.clamp(0.0, 1.0);
    return scaled < kXtaMotionFast ? kXtaMotionFast : scaled;
  }

  void _follow(double delta) {
    final motion = _motion;
    if (!_userScrolling || motion == null) return;
    _movingDown = delta > 0;
    final next = followed(motion.controller.value, delta, motion.travel);
    motion.controller.value = next;
    _publish(next < 1);
  }

  void _settleCaught() {
    final motion = _motion;
    final value = motion?.controller.value ?? 0;
    if (motion == null || value <= 0 || value >= 1) return;
    if (motion.controller.isAnimating) return;
    final travel = motion.travel;
    _settle(
      hide: restsHidden(
        hiddenBy: value * travel,
        travel: travel,
        movingDown: _movingDown,
      ),
    );
  }

  void _settle({required bool hide}) {
    _publish(!hide);
    final motion = _motion;
    if (motion == null) return;
    final controller = motion.controller;
    final target = hide ? 1.0 : 0.0;
    if (controller.value == target) return;
    if (motion.reduceMotion) {
      controller.value = target;
      return;
    }
    final heading = hide ? AnimationStatus.forward : AnimationStatus.reverse;
    if (controller.isAnimating && controller.status == heading) return;
    final duration = settleDuration((target - controller.value).abs());
    hide
        ? controller.animateTo(target, duration: duration, curve: settleCurve)
        : controller.animateBack(
            target,
            duration: duration,
            curve: settleCurve,
          );
  }

  void _publish(bool shown) {
    if (state != shown) update(shown);
  }

  /// At the top, at the end, or with nothing to scroll: the bar belongs on screen.
  bool _atEdge(ScrollMetrics metrics) =>
      !metrics.hasContentDimensions ||
      metrics.maxScrollExtent <= 0 ||
      metrics.pixels <= metrics.minScrollExtent ||
      metrics.extentAfter <= 0;
}
