import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:logging/logging.dart';
import 'package:xta/client/client_unauthenticated.dart';

import 'support/x_profile_probe.dart';

/// Live smoke against x.com guest auth. Opt in with:
///   fvm flutter test test/live/guest_api_smoke_test.dart --dart-define=RUN_LIVE=true
void main() {
  const enabled = bool.fromEnvironment('RUN_LIVE', defaultValue: false);

  test(
    'guest token + UserByScreenName works against live x.com',
    () async {
      final log = Logger('guest_api_smoke');
      final token = await getToken(log);
      expect(token, isNotEmpty);

      final response = await fetchUnauthenticated(publicXProfileUri(), log: log);
      expect(response.statusCode, 200, reason: response.body.substring(0, response.body.length.clamp(0, 400)));
      final decoded = jsonDecode(response.body) as Map<String, dynamic>;
      final screenName =
          decoded['data']?['user']?['result']?['legacy']?['screen_name'] as String? ??
          decoded['data']?['user']?['result']?['core']?['screen_name'] as String?;
      expect(screenName, isNotNull);
    },
    skip: enabled ? false : 'Pass --dart-define=RUN_LIVE=true to hit live x.com',
  );
}
