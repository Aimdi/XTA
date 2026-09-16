import 'dart:async';
import 'dart:io' show Platform;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:visibility_detector/visibility_detector.dart';

/// Suppresses duplicate resume/network signals and repeated retries of a failure.
class RecoveryGate {
  final Duration cooldown;
  DateTime? _last;
  int? _lastEvent;
  RecoveryGate({this.cooldown = const Duration(seconds: 10)});
  bool take(Object? failure, DateTime now, {required int event}) {
    if (failure == null || event <= 0 || event == _lastEvent) return false;
    if (_last != null && now.difference(_last!) < cooldown) return false;
    _last = now;
    _lastEvent = event;
    return true;
  }
}

/// Kept-alive tabs and routes behind a profile must not all retry together.
class ReadRecovery extends StatefulWidget {
  final Widget child;
  final Object? Function() recoverableFailure;
  final VoidCallback retry;
  final Stream<bool>? networkEvents;
  const ReadRecovery({
    super.key,
    required this.child,
    required this.recoverableFailure,
    required this.retry,
    this.networkEvents,
  });
  static bool online = false;
  static final _signals = StreamController<bool>.broadcast();
  static bool _started = false;
  static Stream<bool> get network {
    if (!_started) {
      _started = true;
      var initial = true;
      // One process-wide subscription: mounting an error notice is not a
      // connectivity change and must not cause another automatic retry.
      const EventChannel('com.aimdi.xta/network_state').receiveBroadcastStream().listen((value) {
        online = value == true;
        if (!initial) _signals.add(online);
        initial = false;
      }, onError: (Object _) {});
    }
    return _signals.stream;
  }

  @override
  State<ReadRecovery> createState() => _ReadRecoveryState();
}

class _ReadRecoveryState extends State<ReadRecovery> with WidgetsBindingObserver {
  final _gate = RecoveryGate();
  final _visibilityKey = UniqueKey();
  StreamSubscription<dynamic>? _network;
  Timer? _debounce;
  bool _visible = false;
  bool _online = false;
  bool _foreground = true;
  int _event = 0;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _foreground =
        WidgetsBinding.instance.lifecycleState == null ||
        WidgetsBinding.instance.lifecycleState == AppLifecycleState.resumed;
    _online = ReadRecovery.online;
    final events = widget.networkEvents ?? (Platform.isAndroid ? ReadRecovery.network : null);
    _network = events?.listen((value) {
      _online = ReadRecovery.online = value == true;
      if (_online) {
        _event++;
        _schedule();
      }
    }, onError: (Object _) {});
  }

  void _schedule() {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 800), () {
      if (!mounted || !_online || !_foreground || !_visible || ModalRoute.of(context)?.isCurrent == false) return;
      if (_gate.take(widget.recoverableFailure(), DateTime.now(), event: _event)) widget.retry();
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _foreground = state == AppLifecycleState.resumed;
    if (widget.networkEvents == null) _online = ReadRecovery.online;
    if (_foreground && _online) {
      _event++;
      _schedule();
    }
  }

  @override
  void didUpdateWidget(ReadRecovery oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (_online && _event > 0) _schedule();
  }

  @override
  void dispose() {
    _network?.cancel();
    _debounce?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => VisibilityDetector(
    key: _visibilityKey,
    onVisibilityChanged: (info) {
      _visible = info.visibleFraction > 0;
    },
    child: widget.child,
  );
}
