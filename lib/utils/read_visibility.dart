import 'package:flutter/material.dart';
import 'package:visibility_detector/visibility_detector.dart';

final readRouteObserver = RouteObserver<ModalRoute<dynamic>>();

/// A kept-alive tab can be mounted while another tab, route or app is visible.
class ReadVisibility extends StatefulWidget {
  final Widget child;
  final VoidCallback onHidden;
  final VoidCallback onVisible;
  const ReadVisibility({super.key, required this.child, required this.onHidden, required this.onVisible});
  @override
  State<ReadVisibility> createState() => _ReadVisibilityState();
}

class _ReadVisibilityState extends State<ReadVisibility> with RouteAware, WidgetsBindingObserver {
  final _key = UniqueKey();
  ModalRoute<dynamic>? _route;
  bool _inView = false;
  bool _routeVisible = true;
  bool _foreground = true;
  bool? _visible;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _foreground =
        WidgetsBinding.instance.lifecycleState == null ||
        WidgetsBinding.instance.lifecycleState == AppLifecycleState.resumed;
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final route = ModalRoute.of(context);
    if (identical(route, _route)) return;
    readRouteObserver.unsubscribe(this);
    _route = route;
    _routeVisible = route?.isCurrent ?? true;
    if (route != null) readRouteObserver.subscribe(this, route);
    _notify();
  }

  void _notify() {
    final next = _inView && _routeVisible && _foreground;
    if (_visible == next) return;
    _visible = next;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || _visible != next) return;
      next ? widget.onVisible() : widget.onHidden();
    });
  }

  @override
  void didPushNext() {
    _routeVisible = false;
    _notify();
  }

  @override
  void didPopNext() {
    _routeVisible = true;
    _notify();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _foreground = state == AppLifecycleState.resumed;
    _notify();
  }

  @override
  void dispose() {
    VisibilityDetectorController.instance.forget(_key);
    readRouteObserver.unsubscribe(this);
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => VisibilityDetector(
    key: _key,
    onVisibilityChanged: (info) {
      _inView = info.visibleFraction > 0;
      _notify();
    },
    child: widget.child,
  );
}
