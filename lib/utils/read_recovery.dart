import 'dart:async';
import 'dart:io' show Platform;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:xta/utils/read_visibility.dart';

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
  final Listenable? changes;
  final bool Function()? isLoading;
  final List<Duration> retryDelays;
  const ReadRecovery({
    super.key,
    required this.child,
    required this.recoverableFailure,
    required this.retry,
    this.networkEvents,
    this.changes,
    this.isLoading,
    this.retryDelays = const [Duration(seconds: 2), Duration(seconds: 5), Duration(seconds: 15)],
  });
  static bool online = false;
  static final _signals = StreamController<bool>.broadcast();
  static bool _started = false;
  static Stream<bool> get network {
    if (!_started) {
      _started = true;

      // Publish the initial state too: a cold-start failure may occur before
      // any connectivity transition. Retry budgets live in the reading surface.
      const EventChannel('com.aimdi.xta/network_state').receiveBroadcastStream().listen((value) {
        online = value == true;
        _signals.add(online);
      }, onError: (Object _) {});
    }
    return _signals.stream;
  }

  @override
  State<ReadRecovery> createState() => _ReadRecoveryState();
}

class _ReadRecoveryState extends State<ReadRecovery> {
  StreamSubscription<bool>? _network;
  Timer? _retryTimer;
  bool _visible = false;
  bool _online = false;
  int _attempts = 0;
  Object? _scheduledFailure;
  Object? _attemptedFailure;

  @override
  void initState() {
    super.initState();
    _online = ReadRecovery.online;
    _subscribe();
    widget.changes?.addListener(_consider);
  }

  void _subscribe() {
    final events = widget.networkEvents ?? (Platform.isAndroid ? ReadRecovery.network : null);
    _network = events?.listen((value) {
      final reconnected = value && !_online;
      _online = ReadRecovery.online = value;
      if (reconnected) {
        _attempts = 0;
        _attemptedFailure = null;
      }
      _consider();
    }, onError: (Object _) {});
  }

  void _cancelTimer() {
    _retryTimer?.cancel();
    _retryTimer = null;
    _scheduledFailure = null;
  }

  void _consider() {
    if (!mounted) return;
    if (widget.isLoading?.call() == true) {
      _cancelTimer();
      _attemptedFailure = null;
      return;
    }
    final failure = widget.recoverableFailure();
    if (failure == null) {
      _cancelTimer();
      _attempts = 0;
      _attemptedFailure = null;
      return;
    }
    if (!_visible || !_online) {
      _cancelTimer();
      return;
    }
    if (_attempts >= widget.retryDelays.length || identical(failure, _attemptedFailure)) return;
    if (_retryTimer != null && identical(failure, _scheduledFailure)) return;
    _cancelTimer();
    _scheduledFailure = failure;
    _retryTimer = Timer(widget.retryDelays[_attempts], () {
      _retryTimer = null;
      _scheduledFailure = null;
      if (!mounted || !_visible || !_online || ModalRoute.of(context)?.isCurrent == false) return;
      if (widget.isLoading?.call() == true || !identical(widget.recoverableFailure(), failure)) {
        _consider();
        return;
      }
      _attempts++;
      _attemptedFailure = failure;
      // A callback that does not start work cannot spin a timer indefinitely.
      widget.retry();
    });
  }

  @override
  void didUpdateWidget(ReadRecovery oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.changes != widget.changes) {
      oldWidget.changes?.removeListener(_consider);
      widget.changes?.addListener(_consider);
    }
    if (oldWidget.networkEvents != widget.networkEvents) {
      _network?.cancel();
      _subscribe();
    }
    _consider();
  }

  @override
  void dispose() {
    widget.changes?.removeListener(_consider);
    _network?.cancel();
    _cancelTimer();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ReadVisibility(
    onHidden: () {
      _visible = false;
      _cancelTimer();
    },
    onVisible: () {
      _visible = true;
      if (widget.networkEvents == null) _online = ReadRecovery.online;
      _consider();
    },
    child: widget.child,
  );
}
