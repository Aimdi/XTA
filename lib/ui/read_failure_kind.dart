import 'dart:async';
import 'dart:io' show SocketException;

import 'package:http/http.dart' as http;
import 'package:xta/catcher/exceptions.dart';
import 'package:xta/client/errors.dart';

/// One shared taxonomy for read failures.
///
/// Full-page errors, inline reader notices and stale-cache banners should all
/// describe the same failure the same way. Keep this file UI-free so it can be
/// used from feed/cache code without importing widgets.
enum ReadFailureKind {
  connection,
  timedOut,
  session,
  rateLimited,
  endpointRefused,
  transactionUnavailable,
  unavailable,
  serviceUnavailable,
  unknown,
}

ReadFailureKind readFailureKind(Object? error) {
  if (error is TimeoutException) {
    return ReadFailureKind.timedOut;
  }
  if (error is SocketException || error is http.ClientException) {
    return ReadFailureKind.connection;
  }
  if (error is RateLimitedException || (error is HttpException && error.statusCode == 429)) {
    return ReadFailureKind.rateLimited;
  }
  if (error is NoAccountAvailableException ||
      error is NoWorkingAccountException ||
      (error is HttpException && error.statusCode == 401) ||
      (error is TwitterError && const [32, 89, 215].contains(error.code))) {
    return ReadFailureKind.session;
  }
  if (error is EndpointRefusedException) {
    return ReadFailureKind.endpointRefused;
  }
  if (error is TransactionIdUnavailableException) {
    return ReadFailureKind.transactionUnavailable;
  }
  if (error is HttpException && const [500, 502, 503, 504].contains(error.statusCode)) {
    return ReadFailureKind.serviceUnavailable;
  }
  if (error is HttpException && const [403, 404].contains(error.statusCode)) {
    return ReadFailureKind.unavailable;
  }
  return ReadFailureKind.unknown;
}

/// How a reading surface may retry a failure without being asked.
enum ReadRetry {
  /// Transient: retry on a growing, bounded backoff.
  backoff,

  /// Rate limited: retry once, after the known reset has passed.
  atReset,

  /// Needs the reader (sign in, or X refused the request shape): never automatic.
  manual,
}

/// X's own answers that a retry cannot change: private, missing, suspended, forbidden.
const _definitiveTwitterErrors = [-1, 22, 34, 50, 63, 200];

ReadRetry readRetryOf(Object? error) => switch (readFailureKind(error)) {
  ReadFailureKind.session || ReadFailureKind.endpointRefused => ReadRetry.manual,
  ReadFailureKind.rateLimited => ReadRetry.atReset,
  _ when error is TwitterError && _definitiveTwitterErrors.contains(error.code) => ReadRetry.manual,
  _ => ReadRetry.backoff,
};

/// The failure when it may be retried automatically, else null.
Object? recoverableReadFailure(Object? error) => error == null || readRetryOf(error) == ReadRetry.manual ? null : error;
