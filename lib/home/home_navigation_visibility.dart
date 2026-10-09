import 'package:flutter/widgets.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';

/// Whether the Home bar is on screen: it slips away while the reader scrolls
/// down and comes back on the first scroll up, at the top or the end of a
/// list, and on a tab change. Never on a mere stop: a bar that returns every
/// time the finger lifts sits in the way of reading.
class HomeNavigationVisibilityStore extends Store<bool> {
  /// How far down the reader scrolls before the bar goes.
  static const double hideAfter = 24;

  /// How far up brings it back; shorter, so a short flick is enough.
  static const double showAfter = 12;

  final BasePrefService prefs;

  /// Signed distance scrolled since the direction last changed; down is positive.
  double _run = 0;

  HomeNavigationVisibilityStore(this.prefs) : super(true);

  bool get enabled => prefs.get<bool>(optionHideNavigationOnScroll) != false;

  void show() {
    _run = 0;
    if (!state) update(true);
  }

  /// Feeds one scroll notification in. [accessible] is the screen reader's
  /// flag: with one on, a bar that moves is a bar that gets lost.
  void onScroll(ScrollNotification notification, {bool accessible = false}) {
    if (notification.metrics.axis != Axis.vertical) return;
    if (!enabled || accessible) return show();
    final metrics = notification.metrics;
    switch (notification) {
      case ScrollUpdateNotification(:final scrollDelta?):
        _onDelta(scrollDelta, metrics);
      case OverscrollNotification(:final overscroll) when overscroll < 0:
        show();
      case ScrollEndNotification() when _atEdge(metrics):
        show();
      default:
        break;
    }
  }

  void _onDelta(double delta, ScrollMetrics metrics) {
    if (delta == 0) return;
    if (_atEdge(metrics)) return show();
    if ((delta > 0) != (_run > 0)) _run = 0;
    _run += delta;
    if (_run >= hideAfter && state) {
      _run = 0;
      update(false);
    } else if (_run <= -showAfter && !state) {
      _run = 0;
      update(true);
    }
  }

  /// At the top, at the end, or with nothing to scroll: the bar belongs on screen.
  bool _atEdge(ScrollMetrics metrics) =>
      !metrics.hasContentDimensions ||
      metrics.maxScrollExtent <= 0 ||
      metrics.pixels <= metrics.minScrollExtent ||
      metrics.extentAfter <= 0;
}
