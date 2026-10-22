import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:logging/logging.dart';
import 'package:xta/catcher/exceptions.dart';
import 'package:xta/client/client_unauthenticated.dart';
import 'package:xta/client/endpoint_overrides.dart';
import 'package:xta/client/endpoints.dart';
import 'package:xta/client/headers.dart';
import 'package:xta/constants.dart';

/// Live canary over every GraphQL endpoint the app calls. Opt in with:
///   flutter test test/live/endpoint_canary_test.dart --dart-define=RUN_LIVE=true
///
/// This does not need an X account, because it does not check that a request
/// *succeeds* — it checks that the query id still *exists*. X answers an unknown
/// query id with 404, so a 404 is the rotation signal we want to catch before
/// users do; a made-up id is asked for first, to prove X still answers that way.
///
/// A guest can only tell for the operations X lets guests read. The others
/// answer a guest 404 whether their id is live or not (measured 2026-10-22),
/// so they are listed but not judged.
///
/// Requests are signed (`x-client-transaction-id`) as the app signs them, and
/// carry the bearer the guest token was issued for: X refuses a guest token
/// with any other bearer ("Bad guest token", 239), whatever the query id.
void main() {
  const enabled = bool.fromEnvironment('RUN_LIVE', defaultValue: false);

  // Enough shape to get past request validation; wrong values are fine and
  // expected, since a rejected value still proves the operation resolved.
  const probeVariables = {'userId': '783214', 'screenName': 'X', 'count': 1};

  // What X answers a guest without the 404 a closed operation gets.
  const guestReadable = {
    XEndpoints.userByRestId,
    XEndpoints.userByScreenName,
    XEndpoints.userTweetsAndReplies,
    XEndpoints.audioSpaceById,
  };
  const madeUpId = 'AAAAAAAAAAAAAAAAAAAAAA';

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

      Future<int> statusOf(Uri uri) async {
        final headers = await TwitterHeaders.getHeaders(uri, null);
        final response = await client.get(
          uri,
          headers: {...headers, 'authorization': guestBearerToken, 'x-guest-token': guestToken},
        );
        return response.statusCode;
      }

      final params = {'variables': jsonEncode(probeVariables), 'features': jsonEncode(const <String, bool>{})};
      String unreachableFor(Object e) => 'unreachable (${e is TransactionIdUnavailableException ? e.cause : e})';

      final controls = <String, String>{};
      for (final host in XEndpoints.all.map((endpoint) => endpoint.host).toSet()) {
        final uri = Uri.https(host, XEndpoints.endpoint(XEndpoints.tweetDetail).pathFor(madeUpId), params);
        try {
          controls[host] = '${await statusOf(uri)}';
        } catch (e) {
          controls[host] = unreachableFor(e);
        }
        await Future.delayed(const Duration(seconds: 2));
      }

      final rotated = <String>[];
      final unreachable = <String>[];
      final report = [for (final MapEntry(:key, :value) in controls.entries) 'made-up id on $key: $value'];

      for (final endpoint in XEndpoints.all) {
        final uri = XEndpoints.uri(endpoint.name, params);
        final judged = guestReadable.contains(endpoint.name);

        String verdict;
        String status;
        try {
          final code = await statusOf(uri);
          status = '$code';
          verdict = switch (code) {
            404 when judged => 'ROTATED, or closed to guests',
            404 => 'closed to guests (not judged)',
            429 => 'rate limited (inconclusive)',
            200 => 'ok',
            _ => 'alive',
          };
          if (code == 404 && judged) {
            rotated.add(endpoint.name);
          }
        } catch (e) {
          // A blocked or flaky runner must not be reported as X breaking its
          // API, so transport failures are inconclusive rather than fatal.
          status = '---';
          verdict = unreachableFor(e);
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
        controls.values.where((status) => status != '404'),
        isEmpty,
        reason:
            'X did not answer a made-up query id with 404 ($controls), so this run proves nothing about the '
            "query ids. Check the runner's network and the canary's request before reading anything into it.",
      );

      expect(
        rotated,
        isEmpty,
        reason:
            'X no longer knows these query ids, or no longer lets guests read them. Re-capture them from the '
            'web client and publish the corrected ids in endpoints.json — see lib/client/endpoint_overrides.dart.',
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
