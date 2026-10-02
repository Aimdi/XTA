import 'dart:async';

/// One deadline shared by sequential requests, retries and fallback work.
class RequestBudget {
  final Duration timeout;
  final Stopwatch _elapsed = Stopwatch()..start();

  RequestBudget(this.timeout);

  Duration get remaining => timeout - _elapsed.elapsed;
  bool get expired => remaining <= Duration.zero;

  Future<T> run<T>(Future<T> Function() operation) {
    final left = remaining;
    if (left <= Duration.zero) {
      return Future.error(TimeoutException('Request budget exhausted', timeout));
    }
    return Future.sync(operation).timeout(ceilToMilliseconds(left));
  }

  /// Timers tick in whole milliseconds; rounding down would let one fire while the budget still shows a remainder.
  static Duration ceilToMilliseconds(Duration duration) =>
      Duration(milliseconds: (duration.inMicroseconds / Duration.microsecondsPerMillisecond).ceil());
}
