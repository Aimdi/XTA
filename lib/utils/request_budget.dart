import 'dart:async';

/// One deadline shared by sequential requests, retries and fallback work.
class RequestBudget {
  /// A timer fires on whole milliseconds, up to one before this stopwatch
  /// reaches the deadline, so a step that timed out can leave this much
  /// behind: too little to start another request.
  static const _timerSlack = Duration(milliseconds: 1);

  final Duration timeout;
  final Stopwatch _elapsed = Stopwatch()..start();

  RequestBudget(this.timeout);

  Duration get remaining => timeout - _elapsed.elapsed;
  bool get expired => remaining < _timerSlack;

  Future<T> run<T>(Future<T> Function() operation) {
    final left = remaining;
    if (left < _timerSlack) {
      return Future.error(TimeoutException('Request budget exhausted', timeout));
    }
    return Future.sync(operation).timeout(left);
  }
}
