import 'package:flutter/material.dart';

/// The pages a test's taps push, taken before they are built, so a page that needs more than the test provides
/// can still be checked for what it would show.
class OpenedPages extends NavigatorObserver {
  final _routes = <MaterialPageRoute<dynamic>>[];

  @override
  void didPush(Route<dynamic> route, Route<dynamic>? previousRoute) {
    if (previousRoute != null && route is MaterialPageRoute) _routes.add(route);
  }

  /// The page pushed last, taken off the navigator.
  Widget take() {
    final route = _routes.removeLast();
    final page = route.builder(route.navigator!.context);
    route.navigator!.removeRoute(route);
    return page;
  }
}
