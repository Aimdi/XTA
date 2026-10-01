import 'dart:async';
import 'dart:io' show SocketException;

import 'package:http/http.dart' as http;
import 'package:xta/catcher/exceptions.dart';
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
    'origin': 'https://x.com',
    'pragma': 'no-cache',
    'priority': 'u=1, i',
    'referer': 'https://x.com/',
    'user-agent': userAgentHeader['user-agent']!,
    'x-twitter-active-user': 'yes',
    'x-twitter-client-language': 'en',
  };

  /// Override for tests; production derives the key from X's public page.
  static Future<ClientTransaction> Function()? initializer;
  static DateTime Function() clock = DateTime.now;
  static const initializationTimeout = transactionKeyInitializationTimeout;

  // One key for the whole process: the page it comes from is fetched without any session, so the startup
  // warm-up and every account would derive the same bytes. Deriving once per account ran the whole download
  // several times over, side by side, and a slow link timed every copy out.
  static _TransactionContext _context = _TransactionContext();
  static Object _epoch = Object();
  static Object? _lastInitializationFailure;
  static Object? get lastInitializationFailure => _lastInitializationFailure;

  static void resetForTesting() {
    _context = _TransactionContext();
    _epoch = Object();
    _lastInitializationFailure = null;
    initializer = null;
    clock = DateTime.now;
  }

  static Future<ClientTransaction> _transaction() {
    final context = _context;
    final now = clock();
    final cached = context.future;
    if (cached != null &&
        (context.derivedAt == null ||
            transactionKeyUsable(derivedAt: context.derivedAt, now: now, lifetime: transactionKeyLifetime))) {
      return cached;
    }

    // A broken signer/parser is shared by every feed, so rate-limit its retries.
    // Connection failures must reach the next recovery attempt: the reader's
    // bounded retry schedule can finish before this cooldown expires.
    final failure = context.lastFailure;
    final failedAt = context.failedAt;
    if (failure != null && failedAt != null && now.difference(failedAt) < transactionKeyRetryCooldown) {
      return Future.error(failure);
    }

    final started = _deriveTransaction();
    final epoch = _epoch;
    context.future = started;
    context.derivedAt = null;

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
          if (!identical(_epoch, epoch) || !identical(context.future, started)) return;
          context.derivedAt = clock();
          context.lastFailure = null;
          context.failedAt = null;
          _lastInitializationFailure = null;
        },
        onError: (Object error) {
          if (!identical(_epoch, epoch) || !identical(context.future, started)) return;
          context.future = null;
          context.derivedAt = null;
          context.lastFailure = _isTransientFailure(error) ? null : error;
          context.failedAt = context.lastFailure == null ? null : clock();
          _lastInitializationFailure = error;
        },
      ),
    );

    return started;
  }

  static Future<ClientTransaction> _deriveTransaction() async {
    try {
      return await Future.sync(initializer ?? ClientTransaction.initialize).timeout(initializationTimeout);
    } catch (error, stack) {
      if (_isTransientFailure(error)) rethrow;
      Error.throwWithStackTrace(TransactionIdUnavailableException(error), stack);
    }
  }

  static bool _isTransientFailure(Object error) =>
      error is TimeoutException || error is SocketException || error is http.ClientException;

  static Future<Map<String, String>?> getXClientTransactionIdHeader(Uri? uri) async {
    if (uri == null) {
      return null;
    }

    final ct = await _transaction();
    return {'x-client-transaction-id': ct.generateTransactionId('GET', uri.path)};
  }

  static Future<Map<String, String>> getHeaders(Uri? uri, Map<dynamic, dynamic>? authHeader) async {
    final xClientTransactionIdHeader = await getXClientTransactionIdHeader(uri);
    // The web client marks a signed-in session this way; Squawker sends it on every account request.
    return {
      ..._baseHeaders,
      if (authHeader != null) 'x-twitter-auth-type': 'OAuth2Session',
      if (authHeader != null) ...Map<String, String>.from(authHeader),
      ...?xClientTransactionIdHeader,
    };
  }
}

class _TransactionContext {
  Future<ClientTransaction>? future;
  DateTime? derivedAt;
  Object? lastFailure;
  DateTime? failedAt;
}
