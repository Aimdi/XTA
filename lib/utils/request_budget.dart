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
    return Future.sync(operation).timeout(left);
  }
}
