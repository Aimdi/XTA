import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:logging/logging.dart';
import 'package:xta/client/client_unauthenticated.dart';
import 'package:xta/client/endpoints.dart';
import 'package:xta/client/headers.dart';
import 'package:xta/utils/json.dart';

import 'support/x_profile_probe.dart';

void main() {
  const enabled = bool.fromEnvironment('RUN_LIVE', defaultValue: false);

  setUp(TwitterHeaders.resetForTesting);
  tearDown(() {
    TwitterHeaders.resetForTesting();
    XEndpoints.clearOverrides();
  });

  test(
    'live X bootstrap signs a public profile request',
    () async {
      final uri = publicXProfileUri();
      // Use real bootstrap derivation: a guest-only request can succeed while
      // every signed-in request fails before reaching its GraphQL endpoint.
      final headers = await TwitterHeaders.getHeaders(uri, null);
      expect(headers['x-client-transaction-id'], isNotEmpty);

      final response = await fetchUnauthenticated(
        uri,
        headers: headers,
        log: Logger('x_bootstrap_smoke'),
      ).timeout(const Duration(seconds: 30));
      expect(response.statusCode, 200, reason: response.body.substring(0, response.body.length.clamp(0, 400)));
      final result = Json(jsonDecode(response.body))['data']['user']['result'];
      expect(result['legacy']['screen_name'].string ?? result['core']['screen_name'].string, 'X');
    },
    timeout: const Timeout(Duration(minutes: 1)),
    skip: enabled ? false : 'Pass --dart-define=RUN_LIVE=true to hit live x.com',
  );
}
