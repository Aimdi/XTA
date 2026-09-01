import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:logging/logging.dart';
import 'package:xta/client/client_unauthenticated.dart';

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

      final uri = Uri.https('x.com', '/i/api/graphql/IGgvgiOx4QZndDHuD3x9TQ/UserByScreenName', {
        'variables': jsonEncode({'screen_name': 'X', 'withSafetyModeUserFields': true}),
        'features': jsonEncode({
          'hidden_profile_subscriptions_enabled': true,
          'rweb_tipjar_consumption_enabled': true,
          'responsive_web_graphql_exclude_directive_enabled': true,
          'verified_phone_label_enabled': false,
          'subscriptions_verification_info_is_identity_verified_enabled': true,
          'subscriptions_verification_info_verified_since_enabled': true,
          'highlights_tweets_tab_ui_enabled': true,
          'responsive_web_twitter_article_notes_tab_enabled': true,
          'creator_subscriptions_tweet_preview_api_enabled': true,
          'responsive_web_graphql_skip_user_profile_image_extensions_enabled': false,
          'responsive_web_graphql_timeline_navigation_enabled': true,
        }),
      });

      final response = await fetchUnauthenticated(uri, log: log);
      expect(response.statusCode, 200, reason: response.body.substring(0, response.body.length.clamp(0, 400)));
      final decoded = jsonDecode(response.body) as Map<String, dynamic>;
      final screenName = decoded['data']?['user']?['result']?['legacy']?['screen_name'] as String? ??
          decoded['data']?['user']?['result']?['core']?['screen_name'] as String?;
      expect(screenName, isNotNull);
    },
    skip: enabled ? false : 'Pass --dart-define=RUN_LIVE=true to hit live x.com',
  );
}
