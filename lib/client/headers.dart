import 'dart:async';

import 'package:xta/client/x_client_transaction_id/client_transaction.dart';
import 'package:xta/constants.dart';

/// Whether a derived transaction key may still be used.
///
/// X builds the key from its home page and an on-demand bundle, and rotates
/// both. A process left open for days would otherwise keep signing requests
/// with a key X no longer recognises — and since X answers an unsigned request
/// with 404, that failure looks exactly like a rotated query id.
bool transactionKeyUsable({required DateTime? derivedAt, required DateTime now, required Duration lifetime}) =>
    derivedAt != null && now.difference(derivedAt) < lifetime;

class TwitterHeaders {
  static final Map<String, String> _baseHeaders = {
    'accept': '*/*',
    'accept-language': 'en-US,en;q=0.9',
    'authorization': bearerToken,
    'cache-control': 'no-cache',
    'content-type': 'application/json',
    'pragma': 'no-cache',
    'priority': 'u=1, i',
    'referer': 'https://x.com/',
    'user-agent': userAgentHeader['user-agent']!,
    'x-twitter-active-user': 'yes',
    'x-twitter-client-language': 'en',
  };

  /// Seams for tests: deriving the key needs two live requests to x.com.
  static Future<ClientTransaction> Function() initializer = ClientTransaction.initialize;
  static DateTime Function() clock = DateTime.now;

  static Future<ClientTransaction>? _initFuture;
  static DateTime? _derivedAt;
  static Object? _lastFailure;
  static DateTime? _failedAt;

  static void resetForTesting() {
    _initFuture = null;
    _derivedAt = null;
    _lastFailure = null;
    _failedAt = null;
    initializer = ClientTransaction.initialize;
    clock = DateTime.now;
  }

  static Future<ClientTransaction> _transaction() {
    final now = clock();
    final cached = _initFuture;
    if (cached != null && transactionKeyUsable(derivedAt: _derivedAt, now: now, lifetime: transactionKeyLifetime)) {
      return cached;
    }

    // Forgetting a failure (below) means the next request tries again, which is
    // the point — but deriving costs two requests to x.com, so an outright
    // broken derivation would turn one feed load into twenty extra hits on X.
    // Failures are therefore rate-limited rather than retried on every request.
    final failure = _lastFailure;
    final failedAt = _failedAt;
    if (failure != null && failedAt != null && now.difference(failedAt) < transactionKeyRetryCooldown) {
      return Future.error(failure);
    }

    final started = initializer();
    _initFuture = started;
    _derivedAt = now;

    // Deriving the key does two network requests and parses X's HTML, so it can
    // fail for entirely transient reasons. Leaving a rejected future cached
    // would fail *every* later request with that same error for the life of the
    // process — a blip at startup could only be cleared by force-stopping the
    // app. Forgetting it lets the next call try again.
    //
    // This listener also marks `started` handled, so clearing the cache never
    // surfaces as an unhandled async error; whoever awaits it still sees the
    // failure and reports it normally.
    unawaited(
      started.then(
        (_) {
          _lastFailure = null;
          _failedAt = null;
        },
        onError: (Object error) {
          if (identical(_initFuture, started)) {
            _initFuture = null;
            _derivedAt = null;
          }
          _lastFailure = error;
          _failedAt = clock();
        },
      ),
    );

    return started;
  }

  static Future<Map<String, String>?> getXClientTransactionIdHeader(Uri? uri) async {
    if (uri == null) {
      return null;
    }

    final ct = await _transaction();
    return {'x-client-transaction-id': ct.generateTransactionId('GET', uri.path)};
  }

  static Future<Map<String, String>> getHeaders(Uri? uri, Map<dynamic, dynamic>? authHeader) async {
    final xClientTransactionIdHeader = await getXClientTransactionIdHeader(uri);
    return {
      ..._baseHeaders,
      if (authHeader != null) ...Map<String, String>.from(authHeader),
      ...?xClientTransactionIdHeader,
    };
  }
}
