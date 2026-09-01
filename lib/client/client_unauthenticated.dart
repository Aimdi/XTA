import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:logging/logging.dart';
import 'package:xta/client/http_client.dart';
import 'package:xta/constants.dart';

String? _guestToken;

/// Rate-limit state X last reported for the cached guest token.
///
/// `resetAt` is the `x-rate-limit-reset` header in milliseconds, i.e. when the
/// current window rolls over and the quota refills — not when the token itself
/// dies. -1 means nothing has been observed yet.
int guestTokenResetAt = -1;
int guestTokenRemaining = -1;
int guestTokenLimit = -1;

/// Whether the cached guest token is still worth sending.
///
/// This used to be dead code: the values it consulted were `const int = -1`, so
/// the first branch was always true and the token was reused forever, expiry
/// and quota alike ignored. The counters `fetchUnauthenticated` actually
/// maintained were never read.
bool guestTokenUsable({required int resetAtMillis, required int remaining, required int nowMillis}) {
  // Nothing observed yet — no request has come back with rate-limit headers, so
  // there is no reason to distrust the token.
  if (resetAtMillis < 0) {
    return true;
  }
  // The window rolled over, so the quota has refilled and the counters we hold
  // describe a window that is over.
  if (nowMillis >= resetAtMillis) {
    return true;
  }
  return remaining > 0;
}

void _forgetGuestToken() {
  _guestToken = null;
  guestTokenResetAt = -1;
  guestTokenRemaining = -1;
  guestTokenLimit = -1;
}

/// A guest token, reusing the cached one while it is usable.
///
/// [forceRefresh] discards the cached token first, which is how a caller
/// recovers from X rejecting it outright.
Future<String> getToken(Logger log, {bool forceRefresh = false}) async {
  if (forceRefresh) {
    _forgetGuestToken();
  }

  final cached = _guestToken;
  if (cached != null &&
      guestTokenUsable(
        resetAtMillis: guestTokenResetAt,
        remaining: guestTokenRemaining,
        nowMillis: DateTime.now().millisecondsSinceEpoch,
      )) {
    return cached;
  }

  log.info('Refreshing the X guest token');

  var response = await xHttpClient.post(
    Uri.parse('https://api.x.com/1.1/guest/activate.json'),
    headers: {'Authorization': guestBearerToken},
  );

  if (response.statusCode == 200) {
    var result = jsonDecode(response.body);
    if (result is Map && result.containsKey('guest_token')) {
      _guestToken = result['guest_token'] as String;
      // The counters describe the token we just replaced.
      guestTokenResetAt = -1;
      guestTokenRemaining = -1;
      guestTokenLimit = -1;
      return _guestToken!;
    }
  }

  _forgetGuestToken();

  throw Exception(
    'Unable to refresh the token. The response (${response.statusCode}) from Twitter was: ${response.body}',
  );
}

Future<http.Response> fetchUnauthenticated(Uri uri, {Map<String, String>? headers, required Logger log}) async {
  log.info('Fetching (unauthenticated) $uri');

  Future<http.Response> send(String token) => xHttpClient.get(
    uri,
    headers: {
      ...?headers,
      'Authorization': guestBearerToken,
      'x-guest-token': token,
      'x-twitter-active-user': 'yes',
      // Not userAgentHeader.toString(): that stringifies the whole map,
      // sending "{user-agent: Mozilla/..., Pragma: no-cache, ...}" as the
      // user agent.
      'user-agent': userAgentHeader['user-agent']!,
    },
  );

  var response = await send(await getToken(log));

  // A guest token also expires on its own schedule, which X never tells us
  // about, so the only way to find out is to be turned away. One retry with a
  // fresh token stops the guest path from rotting for the rest of the session.
  if (response.statusCode == 401 || response.statusCode == 403) {
    log.info('The guest token was rejected (${response.statusCode}); retrying with a fresh one');
    response = await send(await getToken(log, forceRefresh: true));
  }

  var headerRateLimitReset = response.headers['x-rate-limit-reset'];
  var headerRateLimitRemaining = response.headers['x-rate-limit-remaining'];
  var headerRateLimitLimit = response.headers['x-rate-limit-limit'];

  if (headerRateLimitReset == null || headerRateLimitRemaining == null || headerRateLimitLimit == null) {
    // If the rate limit headers are missing, the endpoint probably doesn't send them back
    return response;
  }

  guestTokenResetAt = (int.tryParse(headerRateLimitReset) ?? 0) * 1000;
  guestTokenRemaining = int.tryParse(headerRateLimitRemaining) ?? -1;
  guestTokenLimit = int.tryParse(headerRateLimitLimit) ?? -1;

  return response;
}
