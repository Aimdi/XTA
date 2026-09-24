import 'package:http/http.dart';
import 'dart:async';

final _rateOperations = Object();
Future<T> withRateLimitOperations<T>(List<String> operations, Future<T> Function() read) =>
    runZoned(read, zoneValues: {_rateOperations: operations});

class HttpException {
  final Response response;

  HttpException(this.response);

  int get statusCode => response.statusCode;
  String? get reasonPhrase => response.reasonPhrase;
  String get body => response.body;
  String? get uri => response.request?.url.toString();

  @override
  String toString() {
    return 'HttpException{statusCode: $statusCode, reasonPhrase: $reasonPhrase, uri: $uri, body: $body';
  }
}

/// Thrown when no account is usable (none added, or all currently flagged).
/// Surfaced to the user with a dedicated, actionable error widget rather than
/// reported to the crash catcher.
class NoAccountAvailableException with SyntheticException implements Exception {
  @override
  String toString() => 'No account available';
}

/// Thrown when every usable account is rate-limited (429) on the requested
/// endpoint. Surfaced to the user with a dedicated, actionable error widget
/// rather than reported to the crash catcher.
class RateLimitedException with SyntheticException implements Exception {
  final List<String> operations;
  RateLimitedException({List<String>? operations})
    : operations = List.unmodifiable(operations ?? Zone.current[_rateOperations] as List<String>? ?? const []);
  @override
  String toString() => 'Rate limited';
}

/// Thrown when an account returned a 404 on an endpoint that another account
/// served successfully. That contrast is the only reliable evidence the account
/// itself is at fault, since X answers with 404 for several unrelated reasons.
/// Surfaced with a dedicated, actionable error widget rather than reported to
/// the crash catcher.
class NoWorkingAccountException with SyntheticException implements Exception {
  @override
  String toString() => 'No working account';
}

/// Thrown when *every* account got a 404 on the same endpoint.
///
/// X answers 404 for a rotated GraphQL query id and for a stale
/// `x-client-transaction-id` just as it does for a broken sign-in. Blaming the
/// accounts in that case tells the reader to re-add accounts that were never
/// the problem, so an endpoint that refuses everyone is reported as its own
/// failure instead.
/// XTA could not derive the x-client-transaction-id that X currently
/// requires on GraphQL reads.
///
/// This is deliberately distinct from an endpoint/query-id refusal: both can
/// produce 404s at X, but repairing them requires different machinery.
class TransactionIdUnavailableException
    with SyntheticException
    implements Exception {
  final Object cause;

  TransactionIdUnavailableException(this.cause);

  @override
  String toString() => 'X transaction id unavailable';
}

class EndpointRefusedException with SyntheticException implements Exception {
  final String endpoint;

  EndpointRefusedException(this.endpoint);

  @override
  String toString() => 'Endpoint refused every account: $endpoint';
}

class ManuallyReportedException {
  final Object? exception;

  ManuallyReportedException(this.exception);
}

mixin SyntheticException {}
