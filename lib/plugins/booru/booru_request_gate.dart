import 'dart:collection';
import 'dart:math';

/// Paces requests to one booru host.
///
/// At most [perSecond] requests start in any second, the sliding window
/// Boorusama puts in front of every engine. After a 429 or 5xx the host is
/// left alone for a while that doubles with each such answer in a row, from
/// one second up to [maxPause], or for as long as its `Retry-After` asks.
/// Requests waiting meanwhile go out once it is over instead of piling on.
class BooruRequestGate {
  final int perSecond;
  final Duration maxPause;
  final DateTime Function() _now;
  final Future<void> Function(Duration) _wait;

  BooruRequestGate({
    this.perSecond = 10,
    this.maxPause = const Duration(seconds: 32),
    DateTime Function()? now,
    Future<void> Function(Duration)? wait,
  }) : _now = now ?? DateTime.now,
       _wait = wait ?? ((duration) => Future<void>.delayed(duration));

  static const _window = Duration(seconds: 1);
  static const _firstPause = Duration(seconds: 1);

  final _starts = Queue<DateTime>();
  DateTime? _pausedUntil;
  var _failures = 0;
  Future<void> _turn = Future<void>.value();

  /// Completes when a request to the host may start. Callers are let through
  /// in the order they asked.
  Future<void> admit() {
    final turn = _turn.then((_) => _admitNext());
    _turn = turn;
    return turn;
  }

  /// Records how the host answered: a 429 or 5xx pauses it, any other
  /// answer ends the run of failures.
  void answered(int status, {Duration? retryAfter}) {
    if (status != 429 && status < 500) {
      _failures = 0;
      return;
    }
    _failures++;
    final doubled = _firstPause * pow(2, min(_failures - 1, 16)).toInt();
    final pause = retryAfter ?? (doubled > maxPause ? maxPause : doubled);
    _pausedUntil = _now().add(pause);
  }

  Future<void> _admitNext() async {
    while (true) {
      final now = _now();
      final paused = _pausedUntil;
      if (paused != null && now.isBefore(paused)) {
        await _wait(paused.difference(now));
        continue;
      }
      while (_starts.isNotEmpty && now.difference(_starts.first) >= _window) {
        _starts.removeFirst();
      }
      if (_starts.length < perSecond) {
        _starts.add(now);
        return;
      }
      await _wait(_window - now.difference(_starts.first));
    }
  }
}

/// The delay an HTTP `Retry-After` header asks for, in seconds. Dates are
/// not read; the doubling pause stands in for them.
Duration? booruRetryAfter(String? header) {
  final seconds = int.tryParse(header?.trim() ?? '');
  return seconds == null || seconds < 0 ? null : Duration(seconds: seconds);
}
