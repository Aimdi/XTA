import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:logging/logging.dart';
import 'package:xta/client/client_unauthenticated.dart';
import 'package:xta/client/endpoint_overrides.dart';
import 'package:xta/client/endpoints.dart';
import 'package:xta/client/headers.dart';

/// Live canary over every GraphQL endpoint the app calls. Opt in with:
///   flutter test test/live/endpoint_canary_test.dart --dart-define=RUN_LIVE=true
///
/// This does not need an X account, because it does not check that a request
/// *succeeds* — it checks that the query id still *exists*. X answers an unknown
/// query id with 404 and a known one with 400/401/403 when the variables or
/// credentials are wrong, so a 404 here is the rotation signal we want to catch
/// before users do. Anything else means the id is still live.
///
/// The one confound is `x-client-transaction-id`: X 404s every request that
/// lacks it, so the canary computes a real one, exactly as the app does.
void main() {
  const enabled = bool.fromEnvironment('RUN_LIVE', defaultValue: false);

  // Enough shape to get past request validation; wrong values are fine and
  // expected, since a rejected value still proves the operation resolved.
  const probeVariables = {'userId': '783214', 'screenName': 'X', 'count': 1};

  test(
    'no X endpoint has had its query id rotated',
    () async {
      // Check what installs actually use: the published registry on top of the
      // shipped ids. Without this the canary would keep flagging an endpoint
      // that endpoints.json has already repaired.
      final registry = File('endpoints.json');
      if (registry.existsSync()) {
        XEndpoints.applyOverrides(parseEndpointRegistry(registry.readAsStringSync()));
      }

      final log = Logger('endpoint_canary');
      final client = http.Client();

      final String guestToken;
      try {
        guestToken = await getToken(log);
      } catch (e) {
        fail('Could not reach X for a guest token, so this run proves nothing about the query ids: $e');
      }

      final rotated = <String>[];
      final unreachable = <String>[];
      final report = <String>[];

      for (final endpoint in XEndpoints.all) {
        final uri = XEndpoints.uri(endpoint.name, {
          'variables': jsonEncode(probeVariables),
          'features': jsonEncode(const <String, bool>{}),
        });

        String verdict;
        String status;
        try {
          final headers = await TwitterHeaders.getHeaders(uri, null);
          final response = await client.get(uri, headers: {...headers, 'x-guest-token': guestToken});
          status = '${response.statusCode}';
          verdict = switch (response.statusCode) {
            404 => 'ROTATED',
            429 => 'rate limited (inconclusive)',
            200 => 'ok',
            _ => 'alive',
          };
          if (response.statusCode == 404) {
            rotated.add(endpoint.name);
          }
        } catch (e) {
          // A blocked or flaky runner must not be reported as X breaking its
          // API, so transport failures are inconclusive rather than fatal.
          status = '---';
          verdict = 'unreachable ($e)';
          unreachable.add(endpoint.name);
        }

        report.add(
          '${endpoint.name.padRight(22)} ${endpoint.host.padRight(12)} '
          '${status.padLeft(3)}  $verdict  ${XEndpoints.queryId(endpoint.name)}',
        );

        // X rate limits per endpoint, but a burst of 13 requests from one guest
        // token still trips its abuse heuristics.
        await Future.delayed(const Duration(seconds: 2));
      }

      // Printed whatever the outcome: a green run is the record of which ids
      // were verified, and a red one names what to re-capture.
      // ignore: avoid_print
      print('\n${report.join('\n')}\n');

      expect(
        rotated,
        isEmpty,
        reason:
            'X no longer knows these query ids. Re-capture them from the web client and publish the '
            'corrected ids in endpoints.json — see lib/client/endpoint_overrides.dart.',
      );

      expect(
        unreachable.length,
        lessThan(XEndpoints.all.length),
        reason:
            'Every endpoint was unreachable, so this run proves nothing about X. Check the runner\'s '
            'network before reading anything into it.',
      );
    },
    timeout: const Timeout(Duration(minutes: 3)),
    skip: enabled ? false : 'Pass --dart-define=RUN_LIVE=true to hit live x.com',
  );
}
