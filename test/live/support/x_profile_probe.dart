import 'dart:convert';
import 'dart:io';

import 'package:xta/client/endpoint_overrides.dart';
import 'package:xta/client/endpoints.dart';

Uri publicXProfileUri() {
  final registry = File('endpoints.json');
  if (registry.existsSync()) XEndpoints.applyOverrides(parseEndpointRegistry(registry.readAsStringSync()));
  return XEndpoints.uri(XEndpoints.userByScreenName, {
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
}
