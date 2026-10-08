import 'dart:async';
import 'dart:io' show Platform;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:infinite_scroll_pagination/infinite_scroll_pagination.dart';
import 'package:xta/ui/rate_limit_retry.dart';
import 'package:xta/ui/read_failure_kind.dart';
import 'package:xta/utils/paging.dart';
import 'package:xta/utils/read_visibility.dart';
import 'package:xta/utils/reader_value_store.dart';

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

class ReadNetworkState {
  final bool online;
  final String? networkId;
  const ReadNetworkState({required this.online, this.networkId});

  factory ReadNetworkState.fromPlatform(Object? value) => value is Map
      ? ReadNetworkState(
          online: value['online'] == true,
          networkId: value['networkId'] is String ? value['networkId'] as String : null,
        )
      : ReadNetworkState(online: value == true);
}

/// Growing waits between automatic retries of a transient read failure.
const readRetryDelays = [
  Duration(seconds: 2),
  Duration(seconds: 5),
  Duration(seconds: 15),
  Duration(seconds: 30),
  Duration(seconds: 60),
];

/// What one reading surface may still retry on its own: each backoff step
/// once, and a single retry after a known rate-limit reset.
class ReadRetryBudget {
  final List<Duration> delays;
  int _attempts = 0;
  bool _resetSpent = false;
  ReadRetryBudget(this.delays);

  /// The wait before the next automatic retry, or null when none is left.
  Duration? waitFor(ReadRetry mode, {DateTime? reset, required DateTime now}) => switch (mode) {
    ReadRetry.backoff => _attempts < delays.length ? delays[_attempts] : null,
    // A second after the reset, so the tracker no longer counts it as limited.
    ReadRetry.atReset when !_resetSpent && reset != null => _atLeastOneSecond(reset.difference(now) + _second),
    _ => null,
  };

  void spend(ReadRetry mode) {
    if (mode == ReadRetry.atReset) {
      _resetSpent = true;
    } else {
      _attempts++;
    }
  }

  void renew() {
    _attempts = 0;
    _resetSpent = false;
  }
}

const _second = Duration(seconds: 1);
Duration _atLeastOneSecond(Duration wait) => wait < _second ? _second : wait;

/// Retries a failed read on its own while the reader can see it.
///
/// Kept-alive tabs and routes behind a profile must not all retry together:
/// nothing is scheduled while hidden, offline, backgrounded, covered or
/// loading, and a surface never has more than one retry in flight. Failure
/// notices below it read the wait from [countdownOf].
class ReadRecovery extends StatefulWidget {
  final Widget child;
  final Object? Function() recoverableFailure;
  final FutureOr<void> Function() retry;
  final Stream<bool>? networkEvents;
  final Stream<ReadNetworkState>? networkStates;
  final Listenable? changes;
  final bool Function()? isLoading;
  final List<Duration> retryDelays;
  final Future<DateTime?> Function(Object? failure) rateLimitReset;
  const ReadRecovery({
    super.key,
    required this.child,
    required this.recoverableFailure,
    required this.retry,
    this.networkEvents,
    this.networkStates,
    this.changes,
    this.isLoading,
    this.retryDelays = readRetryDelays,
    this.rateLimitReset = readRateLimitReset,
  });

  /// Null until the platform reports: an unknown network must not block retries.
  static bool? online;
  static String? _networkId;
  static final _signals = StreamController<ReadNetworkState>.broadcast();
  static bool _started = false;
  static Stream<ReadNetworkState> get network {
    if (!_started) {
      _started = true;

      // Publish the initial state too: a cold-start failure may occur before
      // any connectivity transition. Retry budgets live in the reading surface.
      const EventChannel('com.aimdi.xta/network_state').receiveBroadcastStream().listen((value) {
        final state = ReadNetworkState.fromPlatform(value);
        online = state.online;
        _networkId = state.networkId;
        _signals.add(state);
      }, onError: (Object _) {});
    }
    return _signals.stream;
  }

  /// Seconds until the enclosing surface retries on its own; 0 while it will not.
  static ReaderValueStore<int>? countdownOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<_RecoveryScope>()?.countdown;

  @override
  State<ReadRecovery> createState() => _ReadRecoveryState();
}

class _ReadRecoveryState extends State<ReadRecovery> with WidgetsBindingObserver {
  final _countdown = ReaderValueStore<int>(0);
  late final _budget = ReadRetryBudget(widget.retryDelays);
  StreamSubscription<ReadNetworkState>? _network;
  Timer? _ticker;
  Timer? _renewCooldown;
  bool _visible = false;
  bool? _online;
  bool _backgrounded = false;
  String? _networkId;
  ReadRetry? _scheduled;
  int _remaining = 0;
  int _lookup = 0;
  // The retry's Future has not completed: never start a second one.
  bool _inFlight = false;
  // A retry ran and no load was seen since, so a quiet moment is not a success.
  bool _awaitingLoad = false;
  Object? _attemptedFailure;
  Object? _resetFailure;
  DateTime? _reset;

  @override
  void initState() {
    super.initState();
    _online = ReadRecovery.online;
    _networkId = ReadRecovery._networkId;
    WidgetsBinding.instance.addObserver(this);
    _subscribe();
    widget.changes?.addListener(_consider);
  }

  void _subscribe() {
    final events =
        widget.networkStates ??
        widget.networkEvents?.map((value) => ReadNetworkState(online: value)) ??
        (Platform.isAndroid ? ReadRecovery.network : null);
    _network = events?.listen((state) {
      final reconnected =
          state.online && (_online != true || (state.networkId != null && state.networkId != _networkId));
      _online = ReadRecovery.online = state.online;
      _networkId = state.networkId;
      if (reconnected) _renewRecovery();
      _consider();
    }, onError: (Object _) {});
  }

  void _renewRecovery() {
    // Resume and handover often arrive together. Each gets a bounded budget,
    // but duplicate platform signals must not restart that budget repeatedly.
    if (_renewCooldown != null) return;
    _cancel();
    _budget.renew();
    _attemptedFailure = null;
    _renewCooldown = Timer(const Duration(seconds: 10), () => _renewCooldown = null);
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused || state == AppLifecycleState.hidden) {
      _backgrounded = true;
      _cancel();
    } else if (state == AppLifecycleState.resumed && _backgrounded) {
      _backgrounded = false;
      _renewRecovery();
      _consider();
    }
  }

  bool get _canRun => _visible && _online != false && !_backgrounded;

  void _cancel() {
    _ticker?.cancel();
    _ticker = null;
    _scheduled = null;
    _lookup++;
    _countdown.update(0);
  }

  void _consider() {
    if (!mounted) return;
    if (widget.isLoading?.call() == true) return _onLoading();
    final failure = widget.recoverableFailure();
    if (failure == null) return _onSettled();
    final mode = readRetryOf(failure);
    if (mode == ReadRetry.manual || !_canRun) return _cancel();
    // A retry that changed nothing waits for a real change instead of spinning.
    if (_inFlight || identical(failure, _attemptedFailure) || _scheduled == mode) return;
    _cancel();
    _schedule(failure, mode);
  }

  void _onLoading() {
    _awaitingLoad = false;
    _attemptedFailure = null;
    _cancel();
  }

  void _onSettled() {
    _cancel();
    _attemptedFailure = null;
    if (!_awaitingLoad && !_inFlight) _budget.renew();
  }

  void _schedule(Object failure, ReadRetry mode) {
    if (mode == ReadRetry.backoff) return _start(failure, mode, _budget.waitFor(mode, now: DateTime.now()));
    _scheduled = mode;
    final lookup = _lookup;
    _resetOf(failure).then((reset) {
      if (!mounted || lookup != _lookup) return;
      _scheduled = null;
      _start(failure, mode, _budget.waitFor(mode, reset: reset, now: DateTime.now()));
    });
  }

  /// The reset X reported, remembered so a deadline that passed while the
  /// reader was away still earns its retry.
  Future<DateTime?> _resetOf(Object failure) async {
    final known = identical(failure, _resetFailure) ? _reset : null;
    DateTime? reset;
    try {
      reset = await widget.rateLimitReset(failure);
    } catch (_) {
      // An unknown deadline leaves the retry to the reader.
    }
    _resetFailure = failure;
    return _reset = reset ?? known;
  }

  void _start(Object failure, ReadRetry mode, Duration? wait) {
    if (wait == null) {
      _attemptedFailure = failure;
      return;
    }
    _scheduled = mode;
    _remaining = (wait.inMilliseconds / 1000).ceil();
    _countdown.update(_remaining);
    _ticker = Timer.periodic(_second, (_) => _tick());
  }

  void _tick() {
    if (--_remaining > 0) return _countdown.update(_remaining);
    // Covered by a sheet or dialog the visibility signal missed: wait for it.
    if (ModalRoute.of(context)?.isCurrent == false) return _countdown.update(0);
    _fire();
  }

  void _fire() {
    final mode = _scheduled;
    _cancel();
    final failure = widget.recoverableFailure();
    final stale = failure == null || readRetryOf(failure) != mode;
    if (mode == null || stale || widget.isLoading?.call() == true || !_canRun) return _consider();
    _budget.spend(mode);
    _attemptedFailure = failure;
    _awaitingLoad = true;
    _run();
    _consider();
  }

  void _run() {
    try {
      final work = widget.retry();
      if (work is Future) _track(work);
    } catch (_) {
      // Spent all the same; the notice keeps its manual retry.
    }
  }

  void _track(Future<Object?> work) {
    _inFlight = true;
    work.then<void>((_) {}, onError: (Object _) {}).whenComplete(() {
      _inFlight = false;
      _awaitingLoad = false;
      _consider();
    });
  }

  @override
  void didUpdateWidget(ReadRecovery oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.changes != widget.changes) {
      oldWidget.changes?.removeListener(_consider);
      widget.changes?.addListener(_consider);
    }
    if (oldWidget.networkEvents != widget.networkEvents || oldWidget.networkStates != widget.networkStates) {
      _network?.cancel();
      _subscribe();
    }
    _consider();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    widget.changes?.removeListener(_consider);
    _network?.cancel();
    _ticker?.cancel();
    _renewCooldown?.cancel();
    _lookup++;
    _countdown.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => _RecoveryScope(
    countdown: _countdown,
    child: ReadVisibility(
      onHidden: () {
        _visible = false;
        _cancel();
      },
      onVisible: () {
        _visible = true;
        if (widget.networkEvents == null && widget.networkStates == null) _online = ReadRecovery.online;
        _consider();
      },
      child: widget.child,
    ),
  );
}

class _RecoveryScope extends InheritedWidget {
  final ReaderValueStore<int> countdown;
  const _RecoveryScope({required this.countdown, required super.child});

  @override
  bool updateShouldNotify(_RecoveryScope oldWidget) => !identical(countdown, oldWidget.countdown);
}

/// [ReadRecovery] for a surface driven by one paging controller, where fetching
/// the failed page again is the retry unless [retry] says otherwise.
class PagingReadRecovery extends StatelessWidget {
  final PagingController<Object?, Object?> controller;
  final FutureOr<void> Function()? retry;
  final Widget child;
  const PagingReadRecovery({super.key, required this.controller, required this.child, this.retry});

  @override
  Widget build(BuildContext context) => ReadRecovery(
    changes: controller,
    isLoading: () => controller.value.isLoading,
    recoverableFailure: () => recoverableReadFailure(pagingErrorOf(controller.value)?.error ?? controller.value.error),
    retry: retry ?? controller.fetchNextPage,
    child: child,
  );
}
