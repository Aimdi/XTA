import 'dart:async';
import 'dart:io' show HttpDate, SocketException;

import 'package:http/http.dart' as http;
import 'package:xta/catcher/exceptions.dart' as errors;

const _delays = [Duration(milliseconds: 400), Duration(milliseconds: 1200)];
final _budgetKey = Object();

class _RetryBudget {
  var used = 0;
}

/// Keep fallback URLs and parallel sources within one logical read's budget.
/// This establishes a boundary only; it never repeats [read] itself.
Future<T> withReadRetryBudget<T>(Future<T> Function() read) {
  final budget = Zone.current[_budgetKey] as _RetryBudget? ?? _RetryBudget();
  return runZoned(read, zoneValues: {_budgetKey: budget});
}

bool transientReadStatus(int status) => const [408, 500, 502, 503, 504].contains(status);

bool temporaryReadFailure(Object error) {
  if (error is http.RequestAbortedException) return false;
  if (error is errors.TransactionIdUnavailableException) return temporaryReadFailure(error.cause);
  if (error is errors.HttpException) return transientReadStatus(error.statusCode);
  return error is TimeoutException || error is SocketException || error is http.ClientException;
}

Duration readRetryDelay(Object? error, Duration fallback, {DateTime? now}) {
  if (error is! errors.HttpException) return fallback;
  final raw = error.response.headers['retry-after'];
  if (raw == null) return fallback;
  final seconds = int.tryParse(raw.trim());
  Duration? requested;
  if (seconds != null && seconds >= 0) {
    requested = Duration(seconds: seconds);
  } else if (seconds == null) {
    try {
      requested = HttpDate.parse(raw).difference(now ?? DateTime.now());
    } on FormatException {
      return fallback;
    }
  }
  return requested != null && requested > fallback ? requested : fallback;
}

/// Share two retries across nested reads, so one failed page cannot multiply
/// retries through HTTP, profile and feed layers. Call only around read work.
Future<T> retryRead<T>(
  Future<T> Function() read, {
  void Function()? checkpoint,
  Future<void> Function(Duration)? pause,
  Duration Function()? remaining,
}) {
  return withReadRetryBudget(
    () => _attempt(read, Zone.current[_budgetKey] as _RetryBudget, checkpoint, pause, remaining),
  );
}

Future<T> _attempt<T>(
  Future<T> Function() read,
  _RetryBudget budget,
  void Function()? checkpoint,
  Future<void> Function(Duration)? pause,
  Duration Function()? remaining,
) async {
  while (true) {
    checkpoint?.call();
    try {
      return await read();
    } catch (error) {
      checkpoint?.call();
      if (!temporaryReadFailure(error) || budget.used >= _delays.length) rethrow;
      final delay = readRetryDelay(error, _delays[budget.used]);
      if (remaining != null && delay >= remaining()) rethrow;
      budget.used++;
      await (pause ?? Future<void>.delayed)(delay);
    }
  }
}
