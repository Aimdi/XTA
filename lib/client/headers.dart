import 'dart:async';
import 'dart:convert';
import 'dart:io' show SocketException;

import 'package:crypto/crypto.dart';
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
    'pragma': 'no-cache',
    'priority': 'u=1, i',
    'referer': 'https://x.com/',
    'user-agent': userAgentHeader['user-agent']!,
    'x-twitter-active-user': 'yes',
    'x-twitter-client-language': 'en',
  };

  /// Override for tests; production derives the key with the selected session.
  static Future<ClientTransaction> Function()? initializer;
  static DateTime Function() clock = DateTime.now;
  static const initializationTimeout = Duration(seconds: 12);

  static final _contexts = <String, _TransactionContext>{};
  static Object _epoch = Object();
  static Object? _lastInitializationFailure;
  static Object? get lastInitializationFailure => _lastInitializationFailure;

  static void resetForTesting() {
    _contexts.clear();
    _epoch = Object();
    _lastInitializationFailure = null;
    initializer = null;
    clock = DateTime.now;
  }

  static _TransactionContext _contextFor(String? cookie) {
    // Retain only a digest as the cache key, never the session credentials.
    final key = cookie == null ? 'anonymous' : sha256.convert(utf8.encode(cookie)).toString();
    final context = _contexts.remove(key) ?? _TransactionContext();
    _contexts[key] = context;
    while (_contexts.length > 16) {
      _contexts.remove(_contexts.keys.first);
    }
    return context;
  }

  static Future<ClientTransaction> _transaction(String? cookie) {
    final context = _contextFor(cookie);
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

    final started = _deriveTransaction(cookie);
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

  static Future<ClientTransaction> _deriveTransaction(String? cookie) async {
    try {
      return await Future.sync(
        initializer ?? () => ClientTransaction.initialize(cookie: cookie),
      ).timeout(initializationTimeout);
    } catch (error, stack) {
      if (_isTransientFailure(error)) rethrow;
      Error.throwWithStackTrace(TransactionIdUnavailableException(error), stack);
    }
  }

  static bool _isTransientFailure(Object error) =>
      error is TimeoutException || error is SocketException || error is http.ClientException;

  static Future<Map<String, String>?> getXClientTransactionIdHeader(Uri? uri, {String? cookie}) async {
    if (uri == null) {
      return null;
    }

    final ct = await _transaction(cookie);
    return {'x-client-transaction-id': ct.generateTransactionId('GET', uri.path)};
  }

  static Future<Map<String, String>> getHeaders(Uri? uri, Map<dynamic, dynamic>? authHeader) async {
    final xClientTransactionIdHeader = await getXClientTransactionIdHeader(uri, cookie: _cookieOf(authHeader));
    return {
      ..._baseHeaders,
      if (authHeader != null) ...Map<String, String>.from(authHeader),
      ...?xClientTransactionIdHeader,
    };
  }

  static String? _cookieOf(Map<dynamic, dynamic>? headers) {
    if (headers == null) return null;
    for (final entry in headers.entries) {
      if (entry.key is String && (entry.key as String).toLowerCase() == 'cookie' && entry.value is String) {
        final cookie = (entry.value as String).trim();
        return cookie.isEmpty ? null : cookie;
      }
    }
    return null;
  }
}

class _TransactionContext {
  Future<ClientTransaction>? future;
  DateTime? derivedAt;
  Object? lastFailure;
  DateTime? failedAt;
}
