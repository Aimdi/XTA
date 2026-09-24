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
  if (error is RateLimitedException ||
      (error is HttpException && error.statusCode == 429)) {
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
  if (error is HttpException &&
      const [500, 502, 503, 504].contains(error.statusCode)) {
    return ReadFailureKind.serviceUnavailable;
  }
  if (error is HttpException &&
      const [403, 404].contains(error.statusCode)) {
    return ReadFailureKind.unavailable;
  }
  return ReadFailureKind.unknown;
}

/// Failures where retrying automatically is reasonable and does not risk
/// hammering a rate-limited endpoint or repeatedly retrying a broken session.
Object? recoverableReadFailure(Object? error) {
  return switch (readFailureKind(error)) {
    ReadFailureKind.connection ||
    ReadFailureKind.timedOut ||
    ReadFailureKind.serviceUnavailable => error,
    _ => null,
  };
}
