/// The HTTP client the X requests share.
///
/// Every plugin in this app (Substack, Deepmarks, Reddit, Karakeep) already
/// takes an injectable `http.Client` and reuses it. The X path — by far the
/// busiest — did not: it called the top-level `http.get`, which builds a client
/// per call and closes it again, so every single API request paid for a fresh
/// TCP and TLS handshake with no keep-alive. Sharing one client lets the
/// connection pool do its job, and makes the core client as mockable as the
/// plugins already are.
library;

import 'dart:async';
import 'dart:io' show SocketException;

import 'package:http/http.dart' as http;
import 'package:xta/utils/request_budget.dart';

const xRequestTimeout = Duration(seconds: 30);
const _retryDelay = Duration(milliseconds: 200);

http.Client? _shared;

/// The shared client, created on first use.
http.Client get xHttpClient => _shared ??= http.Client();

/// Replaces the shared client, for tests. Passing null restores the default on
/// the next access.
set xHttpClient(http.Client? client) => _shared = client;

/// Retry only transient GET failures, within the original request deadline.
Future<http.Response> getXResponse(Uri uri, {Map<String, String>? headers, Duration timeout = xRequestTimeout}) async {
  final budget = RequestBudget(timeout);
  for (var attempt = 0; ; attempt++) {
    try {
      final response = await budget.run(() => _sendGet(uri, headers, budget.remaining));
      if (attempt > 0 || !const [502, 503, 504].contains(response.statusCode)) return response;
      if (budget.remaining <= _retryDelay) return response;
    } on Exception catch (error) {
      if (attempt > 0 || (error is! http.ClientException && error is! SocketException)) rethrow;
      if (budget.remaining <= _retryDelay) rethrow;
    }
    await budget.run(() => Future<void>.delayed(_retryDelay));
  }
}

Future<http.Response> _sendGet(Uri uri, Map<String, String>? headers, Duration timeout) async {
  final abort = Completer<void>();
  final request = http.AbortableRequest('GET', uri, abortTrigger: abort.future);
  if (headers != null) request.headers.addAll(headers);
  return (() async => http.Response.fromStream(await xHttpClient.send(request)))().timeout(
    timeout,
    onTimeout: () {
      abort.complete();
      throw TimeoutException('X request timed out', timeout);
    },
  );
}
