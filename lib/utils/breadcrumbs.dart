import 'dart:collection';

import 'package:flutter/widgets.dart';

/// The short trail of "where the reader was" that a crash line is useless
/// without.
///
/// A stack trace names the widget that threw; it does not say which tab, which
/// plugin, or which fetch was in flight when it did. This keeps a little of
/// each, in memory only, and the crash log copies the trail into every entry.
///
/// The window is per category rather than one shared queue on purpose: a feed
/// that fires ten requests on refresh would otherwise push out the route that
/// says where the reader actually was.
class Breadcrumbs {
  static const int maxPerCategory = 8;

  static final Breadcrumbs instance = Breadcrumbs();

  final Map<String, Queue<Breadcrumb>> _byCategory = {};

  /// Injectable so a test does not depend on the wall clock.
  DateTime Function() now = DateTime.now;

  /// Everything recorded, oldest first.
  List<Breadcrumb> get trail =>
      [for (final queue in _byCategory.values) ...queue]
        ..sort((a, b) => a.at.compareTo(b.at));

  List<String> get lines => [for (final crumb in trail) crumb.toString()];

  /// Records that something happened, collapsing an immediate repeat so a
  /// paging feed does not spend the whole window on one endpoint.
  void drop(String category, String detail) {
    final queue = _byCategory.putIfAbsent(category, Queue<Breadcrumb>.new);
    final last = queue.isEmpty ? null : queue.last;

    if (last != null && last.detail == detail) {
      queue
        ..removeLast()
        ..add(last.repeated(now()));
      return;
    }

    queue.add(Breadcrumb(at: now(), category: category, detail: detail));
    while (queue.length > maxPerCategory) {
      queue.removeFirst();
    }
  }

  /// The newest crumb in [category], for the session marker's "where did it
  /// die" line.
  Breadcrumb? latest(String category) {
    final queue = _byCategory[category];
    return queue == null || queue.isEmpty ? null : queue.last;
  }

  void clear() => _byCategory.clear();
}

class Breadcrumb {
  final DateTime at;
  final String category;
  final String detail;
  final int count;

  const Breadcrumb({
    required this.at,
    required this.category,
    required this.detail,
    this.count = 1,
  });

  Breadcrumb repeated(DateTime at) =>
      Breadcrumb(at: at, category: category, detail: detail, count: count + 1);

  /// Deliberately not localised — see `CrashLogEntry.format`.
  @override
  String toString() {
    final time = at.toIso8601String().split('T').last;
    final times = count > 1 ? ' (x$count)' : '';
    return '$time $category: $detail$times';
  }
}

/// Categories, so the log reads consistently and one busy source cannot
/// crowd out another.
abstract final class Crumb {
  static const route = 'route';
  static const tab = 'tab';
  static const fetch = 'fetch';
  static const lifecycle = 'lifecycle';
  static const media = 'media';
}

/// Records every route the app pushes or pops.
///
/// An anonymous [MaterialPageRoute] carries no name, so it is reported by its
/// type rather than dropped — "where did it die" is the whole point.
class BreadcrumbNavigatorObserver extends NavigatorObserver {
  final Breadcrumbs breadcrumbs;

  BreadcrumbNavigatorObserver({Breadcrumbs? breadcrumbs})
    : breadcrumbs = breadcrumbs ?? Breadcrumbs.instance;

  @override
  void didPush(Route<dynamic> route, Route<dynamic>? previousRoute) {
    breadcrumbs.drop(Crumb.route, 'push ${routeLabel(route)}');
  }

  @override
  void didPop(Route<dynamic> route, Route<dynamic>? previousRoute) {
    breadcrumbs.drop(Crumb.route, 'pop ${routeLabel(route)}');
  }

  @override
  void didReplace({Route<dynamic>? newRoute, Route<dynamic>? oldRoute}) {
    if (newRoute == null) return;
    breadcrumbs.drop(Crumb.route, 'replace ${routeLabel(newRoute)}');
  }
}

String routeLabel(Route<dynamic> route) {
  final name = route.settings.name;
  if (name != null && name.isNotEmpty) return name;
  return route.runtimeType.toString();
}
