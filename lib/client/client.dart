import 'dart:async';
import 'dart:convert';

import 'package:dart_twitter_api/twitter_api.dart';
import 'package:ffcache/ffcache.dart';
import 'package:xta/catcher/exceptions.dart';
import 'package:xta/client/endpoints.dart';
import 'package:xta/client/errors.dart';
import 'package:xta/client/transport.dart';
import 'package:xta/client/timeline_parser.dart';
import 'package:xta/client/tweet_models.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/profile/profile_model.dart';
import 'package:xta/user.dart';
import 'package:xta/utils/cache.dart';
import 'package:xta/utils/iterables.dart';

// Re-exported so the 37 files importing client.dart keep compiling unchanged
// after the transport, the error types and the tweet model moved out.
export 'package:xta/client/errors.dart';
export 'package:xta/client/transport.dart';
export 'package:xta/client/timeline_parser.dart';
export 'package:xta/client/tweet_models.dart';

class Twitter {
  static const Map<String, dynamic> _listByRestIdFeatures = {
    "profile_label_improvements_pcf_label_in_post_enabled": true,
    "responsive_web_profile_redirect_enabled": false,
    "rweb_tipjar_consumption_enabled": false,
    "verified_phone_label_enabled": false,
    "responsive_web_graphql_skip_user_profile_image_extensions_enabled": false,
    "responsive_web_graphql_timeline_navigation_enabled": true,
  };
  static final TwitterApi _twitterApi = TwitterApi(client: QuackerTwitterClient());

  static final FFCache _cache = FFCache();

  static Map<String, String> defaultParams = {
    'include_profile_interstitial_type': '1',
    'include_blocking': '1',
    'include_blocked_by': '1',
    'include_followed_by': '1',
    'include_mute_edge': '1',
    'include_can_dm': '1',
    'include_can_media_tag': '1',
    'include_ext_has_nft_avatar': '1',
    'include_ext_is_blue_verified': '1',
    'skip_status': '1',
    'cards_platform': 'Web-12',
    'include_cards': '1',
    'include_ext_alt_text': 'true',
    'include_ext_limited_action_results': 'false',
    'include_quote_count': 'true',
    'include_reply_count': '1',
    'tweet_mode': 'extended',
    'include_ext_collab_control': 'true',
    'include_entities': 'true',
    'include_user_entities': 'true',
    'include_ext_media_color': 'true',
    'include_ext_media_availability': 'true',
    'include_ext_sensitive_media_warning': 'true',
    'include_ext_trusted_friends_metadata': 'true',
    'send_error_codes': 'true',
    'simple_quoted_tweet': 'true',
    'pc': '1',
    'spelling_corrections': '1',
    'include_ext_edit_control': 'true',
    'ext':
        'mediaStats,highlightedLabel,hasNftAvatar,voiceInfo,enrichments,superFollowMetadata,unmentionInfo,editControl,collab_control,vibe,',
  };

  static Map<String, bool> gqlFeatures = {
    "blue_business_profile_image_shape_enabled": true,
    "freedom_of_speech_not_reach_fetch_enabled": false,
    "graphql_is_translatable_rweb_tweet_is_translatable_enabled": false,
    "interactive_text_enabled": false,
    "longform_notetweets_consumption_enabled": true,
    "longform_notetweets_richtext_consumption_enabled": true,
    "longform_notetweets_rich_text_read_enabled": false,
    "responsive_web_edit_tweet_api_enabled": false,
    "responsive_web_enhance_cards_enabled": false,
    "responsive_web_graphql_exclude_directive_enabled": true,
    "responsive_web_graphql_skip_user_profile_image_extensions_enabled": false,
    "responsive_web_graphql_timeline_navigation_enabled": false,
    "responsive_web_text_conversations_enabled": false,
    "responsive_web_twitter_blue_verified_badge_is_enabled": true,
    "spaces_2022_h2_clipping": true,
    "spaces_2022_h2_spaces_communities": true,
    "standardized_nudges_misinfo": false,
    "tweet_awards_web_tipping_enabled": false,
    "tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled": false,
    "tweetypie_unmention_optimization_enabled": false,
    "verified_phone_label_enabled": false,
    "vibe_api_enabled": false,
    "view_counts_everywhere_api_enabled": true,
  };

  static Future<Profile> getProfileById(String id) async {
    var uri = XEndpoints.uri(XEndpoints.userByRestId, {
      'variables': jsonEncode({
        'userId': id,
        'withHighlightedLabel': true,
        'withSafetyModeUserFields': true,
        'withSuperFollowsUserFields': true,
      }),
      'features': jsonEncode({
        'responsive_web_graphql_timeline_navigation_enabled': true,
        'responsive_web_twitter_blue_verified_badge_is_enabled': true,
        'verified_phone_label_enabled': true,
      }),
    });

    return _getProfile(uri);
  }

  static Future<Profile> getProfileByScreenName(String screenName) async {
    if (screenName.startsWith('@')) {
      screenName = screenName.substring(1);
    }
    var uri = XEndpoints.uri(XEndpoints.userByScreenName, {
      'variables': jsonEncode({'screen_name': screenName, "withSafetyModeUserFields": true}),
      'features': jsonEncode({
        "hidden_profile_likes_enabled": true,
        "hidden_profile_subscriptions_enabled": true,
        "rweb_tipjar_consumption_enabled": true,
        "responsive_web_graphql_exclude_directive_enabled": true,
        "verified_phone_label_enabled": false,
        "subscriptions_verification_info_is_identity_verified_enabled": true,
        "subscriptions_verification_info_verified_since_enabled": true,
        "highlights_tweets_tab_ui_enabled": true,
        "responsive_web_twitter_article_notes_tab_enabled": true,
        "creator_subscriptions_tweet_preview_api_enabled": true,
        "responsive_web_graphql_skip_user_profile_image_extensions_enabled": false,
        "responsive_web_graphql_timeline_navigation_enabled": true,
      }),
    });

    return _getProfile(uri);
  }

  static Future<Profile> _getProfile(Uri uri) async {
    var response = await _twitterApi.client.get(uri);
    var content = jsonDecode(response.body) as Map<String, dynamic>;

    var hasErrors = content.containsKey('errors');
    if (hasErrors && content['errors'] != null) {
      var errors = List.from(content['errors']);
      if (errors.isEmpty) {
        throw TwitterError(code: 0, message: 'Unknown error', uri: uri.toString());
      } else {
        throw TwitterError(code: errors.first['code'], message: errors.first['message'], uri: uri.toString());
      }
    }

    var result = content['data']?['user']?['result'];
    if (result == null) {
      throw TwitterError(uri: uri.toString(), code: 50, message: L10n.current.user_not_found);
    }

    var resultType = result['__typename'];
    if (resultType != null) {
      switch (resultType) {
        case 'UserUnavailable':
          var code = result['reason'];
          if (code == 'Suspended') {
            throw TwitterError(code: 63, message: result['reason'], uri: uri.toString());
          } else {
            throw TwitterError(code: -1, message: result['reason'], uri: uri.toString());
          }
        case 'User':
          // This means everything's fine
          break;
        default:
          break;
      }
    }

    var user = UserWithExtra.fromNonLegacyJson(result);
    var pins = List<String>.from((result['legacy']?['pinned_tweet_ids_str'] as List<dynamic>?) ?? const []);

    return Profile(user, pins);
  }

  // GraphQL "Following"
  static Future<PaginatedUsers> friendsList(String userId, int count, {String? cursor}) =>
      _graphqlFollows(userId, count, cursor: cursor, endpoint: XEndpoints.following, features: _followingFeatures);

  // GraphQL "Followers"
  static Future<PaginatedUsers> followersList(String userId, int count, {String? cursor}) =>
      _graphqlFollows(userId, count, cursor: cursor, endpoint: XEndpoints.followers, features: _followersFeatures);

  // Shared cursor-paginated GraphQL user-list fetch (Following / Followers share
  // the same timeline shape; only the endpoint and feature flags differ).
  static Future<PaginatedUsers> _graphqlFollows(
    String userId,
    int count, {
    String? cursor,
    required String endpoint,
    required Map<String, dynamic> features,
  }) async {
    final uri = XEndpoints.uri(endpoint, {
      "variables": jsonEncode({
        "userId": userId,
        "count": count,
        "cursor": ?cursor,
        "includePromotedContent": false,
        "withGrokTranslatedBio": false,
      }),
      "features": jsonEncode(features),
    });

    return _twitterApi.client
        .get(uri)
        .then(
          (response) => TimelineParser.parseUsersTimeline(
            jsonDecode(response.body)?["data"]?["user"]?["result"]?["timeline"]?["timeline"]?["instructions"],
          ),
        );
  }

  // Shared parser for user-timeline instructions (Following, Followers and
  // ListMembers all use the same TimelineAddEntries shape; only the JSON root
  // differs).
  static Future<TwitterListInfo> getListDetails(String listId) async {
    final uri = XEndpoints.uri(XEndpoints.listByRestId, {
      'variables': jsonEncode({'listId': listId}),
      'features': jsonEncode(_listByRestIdFeatures),
    });
    final response = await _twitterApi.client.get(uri);
    final list = (jsonDecode(response.body) as Map<String, dynamic>?)?['data']?['list'];
    return TwitterListInfo(id: listId, name: list?['name'] as String?, memberCount: list?['member_count'] as int?);
  }

  // GraphQL "ListMembers" — one page of an X list's members. The response
  // nests under data.list.members_timeline, not data.user.result.timeline.
  // The web client pages with count=20; larger values are unverified here.
  static Future<Follows> getListMembers(String listId, {String? cursor, int count = 20}) async {
    final uri = XEndpoints.uri(XEndpoints.listMembers, {
      'variables': jsonEncode({'listId': listId, 'count': count, 'cursor': ?cursor}),
      'features': jsonEncode(_followersFeatures),
    });
    final response = await _twitterApi.client.get(uri);
    final users = TimelineParser.parseUsersTimeline(
      jsonDecode(response.body)?['data']?['list']?['members_timeline']?['timeline']?['instructions'],
    );
    return Follows(
      cursorBottom: users.nextCursorStr,
      cursorTop: users.previousCursorStr,
      users: users.users?.map((e) => UserWithExtra.fromJson(e.toJson())).toList() ?? [],
    );
  }

  // GraphQL "Retweeters" — people who reposted [tweetId]. Read-only; this does
  // not create a repost. The web client pages with count=20.
  static Future<Follows> getRetweeters(String tweetId, {String? cursor, int count = 20}) async {
    final uri = XEndpoints.uri(XEndpoints.retweeters, {
      'variables': jsonEncode({
        'tweetId': tweetId,
        'count': count,
        'includePromotedContent': false,
        'cursor': ?cursor,
      }),
      'features': jsonEncode(_followersFeatures),
    });
    final response = await _twitterApi.client.get(uri);
    final decoded = jsonDecode(response.body);
    final instructions = TimelineParser.retweetersInstructions(decoded);
    if (instructions == null) {
      final errors = decoded is Map ? decoded['errors'] : null;
      if (errors is List && errors.isNotEmpty) {
        final first = errors.first;
        final message = first is Map ? (first['message'] as String? ?? 'Retweeters failed') : 'Retweeters failed';
        throw Exception(message);
      }
    }
    final users = TimelineParser.parseUsersTimeline(instructions);
    return Follows(
      cursorBottom: users.nextCursorStr,
      cursorTop: users.previousCursorStr,
      users: users.users?.map((e) => UserWithExtra.fromJson(e.toJson())).toList() ?? [],
    );
  }

  static const Map<String, dynamic> _followingFeatures = {
    "rweb_video_screen_enabled": false,
    "payments_enabled": false,
    "profile_label_improvements_pcf_label_in_post_enabled": true,
    "responsive_web_profile_redirect_enabled": false,
    "rweb_tipjar_consumption_enabled": true,
    "verified_phone_label_enabled": false,
    "creator_subscriptions_tweet_preview_api_enabled": true,
    "responsive_web_graphql_timeline_navigation_enabled": true,
    "responsive_web_graphql_skip_user_profile_image_extensions_enabled": false,
    "premium_content_api_read_enabled": false,
    "communities_web_enable_tweet_community_results_fetch": true,
    "c9s_tweet_anatomy_moderator_badge_enabled": true,
    "responsive_web_grok_analyze_button_fetch_trends_enabled": false,
    "responsive_web_grok_analyze_post_followups_enabled": true,
    "responsive_web_jetfuel_frame": true,
    "responsive_web_grok_share_attachment_enabled": true,
    "articles_preview_enabled": true,
    "responsive_web_edit_tweet_api_enabled": true,
    "graphql_is_translatable_rweb_tweet_is_translatable_enabled": true,
    "view_counts_everywhere_api_enabled": true,
    "longform_notetweets_consumption_enabled": true,
    "responsive_web_twitter_article_tweet_consumption_enabled": true,
    "tweet_awards_web_tipping_enabled": false,
    "responsive_web_grok_show_grok_translated_post": false,
    "responsive_web_grok_analysis_button_from_backend": true,
    "creator_subscriptions_quote_tweet_preview_enabled": false,
    "freedom_of_speech_not_reach_fetch_enabled": true,
    "standardized_nudges_misinfo": true,
    "tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled": true,
    "longform_notetweets_rich_text_read_enabled": true,
    "longform_notetweets_inline_media_enabled": true,
    "responsive_web_grok_image_annotation_enabled": true,
    "responsive_web_grok_imagine_annotation_enabled": true,
    "responsive_web_grok_community_note_auto_translation_is_enabled": false,
    "responsive_web_enhance_cards_enabled": false,
  };

  static const Map<String, dynamic> _followersFeatures = {
    "rweb_video_screen_enabled": false,
    "rweb_cashtags_enabled": true,
    "profile_label_improvements_pcf_label_in_post_enabled": true,
    "responsive_web_profile_redirect_enabled": false,
    "rweb_tipjar_consumption_enabled": false,
    "verified_phone_label_enabled": false,
    "creator_subscriptions_tweet_preview_api_enabled": true,
    "responsive_web_graphql_timeline_navigation_enabled": true,
    "responsive_web_graphql_skip_user_profile_image_extensions_enabled": false,
    "premium_content_api_read_enabled": false,
    "communities_web_enable_tweet_community_results_fetch": true,
    "c9s_tweet_anatomy_moderator_badge_enabled": true,
    "responsive_web_grok_analyze_button_fetch_trends_enabled": false,
    "responsive_web_grok_analyze_post_followups_enabled": true,
    "rweb_cashtags_composer_attachment_enabled": true,
    "responsive_web_jetfuel_frame": true,
    "responsive_web_grok_share_attachment_enabled": true,
    "responsive_web_grok_annotations_enabled": true,
    "articles_preview_enabled": true,
    "responsive_web_edit_tweet_api_enabled": true,
    "rweb_conversational_replies_downvote_enabled": false,
    "graphql_is_translatable_rweb_tweet_is_translatable_enabled": true,
    "view_counts_everywhere_api_enabled": true,
    "longform_notetweets_consumption_enabled": true,
    "responsive_web_twitter_article_tweet_consumption_enabled": true,
    "content_disclosure_indicator_enabled": true,
    "content_disclosure_ai_generated_indicator_enabled": true,
    "responsive_web_grok_show_grok_translated_post": true,
    "responsive_web_grok_analysis_button_from_backend": true,
    "post_ctas_fetch_enabled": false,
    "freedom_of_speech_not_reach_fetch_enabled": true,
    "standardized_nudges_misinfo": true,
    "tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled": true,
    "longform_notetweets_rich_text_read_enabled": true,
    "longform_notetweets_inline_media_enabled": false,
    "responsive_web_grok_image_annotation_enabled": true,
    "responsive_web_grok_imagine_annotation_enabled": true,
    "responsive_web_grok_community_note_auto_translation_is_enabled": true,
    "responsive_web_enhance_cards_enabled": false,
  };

  static Future<Follows> getProfileFollows(
    String screenName,
    String type, {
    String? cursor,
    int? count = 200,
    String? id,
  }) async {
    id ??= (await getProfileByScreenName(screenName)).user.idStr;
    var response = type == 'following'
        ? await friendsList(id!, count!, cursor: cursor)
        : await followersList(id!, count!, cursor: cursor);

    return Follows(
      cursorBottom: response.nextCursorStr,
      cursorTop: response.previousCursorStr,
      users: response.users?.map((e) => UserWithExtra.fromJson(e.toJson())).toList() ?? [],
    );
  }

  static Future<TweetStatus> getTweet(String id, {String? cursor}) async {
    Map<String, dynamic> defaultParam = {
      "variables": jsonEncode({
        "focalTweetId": "0",
        "with_rux_injections": false,
        "rankingMode": "Relevance",
        "includePromotedContent": true,
        "withCommunity": true,
        "withQuickPromoteEligibilityTweetFields": true,
        "withBirdwatchNotes": true,
        "withVoice": true,
      }),
      "features": jsonEncode({
        "rweb_video_screen_enabled": false,
        "profile_label_improvements_pcf_label_in_post_enabled": true,
        "responsive_web_profile_redirect_enabled": false,
        "rweb_tipjar_consumption_enabled": false,
        "verified_phone_label_enabled": false,
        "creator_subscriptions_tweet_preview_api_enabled": true,
        "responsive_web_graphql_timeline_navigation_enabled": true,
        "responsive_web_graphql_skip_user_profile_image_extensions_enabled": false,
        "premium_content_api_read_enabled": false,
        "communities_web_enable_tweet_community_results_fetch": true,
        "c9s_tweet_anatomy_moderator_badge_enabled": true,
        "responsive_web_grok_analyze_button_fetch_trends_enabled": false,
        "responsive_web_grok_analyze_post_followups_enabled": true,
        "responsive_web_jetfuel_frame": true,
        "responsive_web_grok_share_attachment_enabled": true,
        // false: with this on, TweetDetail has been observed to omit replies.
        "responsive_web_grok_annotations_enabled": false,
        "articles_preview_enabled": true,
        "responsive_web_edit_tweet_api_enabled": true,
        "graphql_is_translatable_rweb_tweet_is_translatable_enabled": true,
        "view_counts_everywhere_api_enabled": true,
        "longform_notetweets_consumption_enabled": true,
        "responsive_web_twitter_article_tweet_consumption_enabled": true,
        "tweet_awards_web_tipping_enabled": false,
        "content_disclosure_indicator_enabled": true,
        "content_disclosure_ai_generated_indicator_enabled": true,
        "responsive_web_grok_show_grok_translated_post": false,
        "responsive_web_grok_analysis_button_from_backend": true,
        "post_ctas_fetch_enabled": true,
        "freedom_of_speech_not_reach_fetch_enabled": true,
        "standardized_nudges_misinfo": true,
        "tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled": true,
        "longform_notetweets_rich_text_read_enabled": true,
        "longform_notetweets_inline_media_enabled": false,
        "responsive_web_grok_image_annotation_enabled": true,
        "responsive_web_grok_imagine_annotation_enabled": true,
        "responsive_web_grok_community_note_auto_translation_is_enabled": false,
        "responsive_web_enhance_cards_enabled": false,
      }),
      "fieldToggles": jsonEncode({
        "withArticleRichContentState": true,
        "withArticlePlainText": false,
        "withArticleSummaryText": false,
        "withArticleVoiceOver": false,
        "withGrokAnalyze": false,
        "withDisallowedReplyControls": false,
      }),
    };

    Map<String, dynamic> variables = json.decode(defaultParam["variables"].toString());
    variables["focalTweetId"] = id;

    if (cursor != null) {
      variables['cursor'] = cursor;
    }

    defaultParam["variables"] = json.encode(variables);

    var response = await _twitterApi.client.get(XEndpoints.uri(XEndpoints.tweetDetail, defaultParam));

    var result = json.decode(response.body);

    var instructions = List.from(result?['data']?['threaded_conversation_with_injections_v2']?['instructions'] ?? []);
    if (instructions.isEmpty) {
      return TweetStatus(chains: [], cursorBottom: null, cursorTop: null);
    }

    var addEntriesInstructions = instructions.firstWhereOrNull((e) => e['type'] == 'TimelineAddEntries');
    var addModInstructions = instructions.firstWhereOrNull((e) => e['type'] == 'TimelineAddToModule');
    var addEntries = List.from(addEntriesInstructions?['entries'] ?? []);
    var moduleItems = List.from(addModInstructions?['moduleItems'] ?? []);
    var repEntries = List.from(instructions.where((e) => e['type'] == 'TimelineReplaceEntry'));

    // Later pages often only carry TimelineAddToModule — treat that as a page.
    if (addEntries.isEmpty && moduleItems.isEmpty) {
      return TweetStatus(chains: [], cursorBottom: null, cursorTop: null);
    }

    var chains = [
      ...TimelineParser.createTweetChains(addEntries),
      ...TimelineParser.chainsFromModuleItems(moduleItems),
    ];

    String? cursorBottom = TimelineParser.getCursor(addEntries, repEntries, 'cursor-bottom', 'Bottom') ??
        TimelineParser.getBottomCursorFromModuleItems(moduleItems);
    String? cursorTop = TimelineParser.getCursor(addEntries, repEntries, 'cursor-top', 'Top');

    return TweetStatus(
      chains: chains,
      cursorBottom: cursorBottom,
      cursorTop: cursorTop,
      cursorShowMore: TimelineParser.getShowMoreCursor(addEntries) ??
          TimelineParser.getShowMoreCursorFromModuleItems(moduleItems),
    );
  }

  static Future<TweetStatus> searchTweets(
    String query,
    bool includeReplies, {
    int limit = 20,
    String? cursor,
    String product = "Latest",
    /// When true, posts that share a conversation id are folded into one chain.
    /// Quotes of a post must keep [false]: each quoting post is its own hit.
    bool mapToThreads = true,
  }) async {
    var variables = {
      "rawQuery": query,
      "count": limit.toString(),
      "querySource": "typed_query",
      "product": product,
      "withGrokTranslatedBio": true,
      "withBirdwatchNotes": true,
      "withQuickPromoteEligibilityTweetFields": false,
    };

    var features = {
      "rweb_video_screen_enabled": false,
      "rweb_cashtags_enabled": true,
      "profile_label_improvements_pcf_label_in_post_enabled": true,
      "responsive_web_profile_redirect_enabled": false,
      "rweb_tipjar_consumption_enabled": false,
      "verified_phone_label_enabled": false,
      "creator_subscriptions_tweet_preview_api_enabled": true,
      "responsive_web_graphql_timeline_navigation_enabled": true,
      "responsive_web_graphql_skip_user_profile_image_extensions_enabled": false,
      "premium_content_api_read_enabled": false,
      "communities_web_enable_tweet_community_results_fetch": true,
      "c9s_tweet_anatomy_moderator_badge_enabled": true,
      "responsive_web_grok_analyze_button_fetch_trends_enabled": false,
      "responsive_web_grok_analyze_post_followups_enabled": true,
      "rweb_cashtags_composer_attachment_enabled": true,
      "responsive_web_jetfuel_frame": true,
      "responsive_web_grok_share_attachment_enabled": true,
      "responsive_web_grok_annotations_enabled": true,
      "articles_preview_enabled": true,
      "responsive_web_edit_tweet_api_enabled": true,
      "rweb_conversational_replies_downvote_enabled": false,
      "graphql_is_translatable_rweb_tweet_is_translatable_enabled": true,
      "view_counts_everywhere_api_enabled": true,
      "longform_notetweets_consumption_enabled": true,
      "responsive_web_twitter_article_tweet_consumption_enabled": true,
      "content_disclosure_indicator_enabled": true,
      "content_disclosure_ai_generated_indicator_enabled": true,
      "responsive_web_grok_show_grok_translated_post": true,
      "responsive_web_grok_analysis_button_from_backend": true,
      "post_ctas_fetch_enabled": true,
      "freedom_of_speech_not_reach_fetch_enabled": true,
      "standardized_nudges_misinfo": true,
      "tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled": true,
      "longform_notetweets_rich_text_read_enabled": true,
      "longform_notetweets_inline_media_enabled": false,
      "responsive_web_grok_image_annotation_enabled": true,
      "responsive_web_grok_imagine_annotation_enabled": true,
      "responsive_web_grok_community_note_auto_translation_is_enabled": true,
      "responsive_web_enhance_cards_enabled": false,
    };

    if (cursor != null) {
      variables['cursor'] = cursor;
    }

    var uri = XEndpoints.uri(XEndpoints.searchTimeline, {
      'variables': jsonEncode(variables),
      'features': jsonEncode(features),
    });

    var response = await _twitterApi.client.get(uri);
    var result = json.decode(response.body);

    var timeline = result?['data']?['search_by_raw_query']?['search_timeline'];
    if (timeline == null) {
      // A GraphQL error used to look like "no quotes", which made the quotes
      // screen look broken whenever SearchTimeline rejected the request.
      final errors = result is Map ? result['errors'] : null;
      if (errors is List && errors.isNotEmpty) {
        final first = errors.first;
        final message = first is Map ? (first['message'] as String? ?? 'Search failed') : 'Search failed';
        throw Exception(message);
      }
      return TweetStatus(chains: [], cursorBottom: null, cursorTop: null);
    }

    if (product == "Media") {
      return TimelineParser.createChainsFromGridModule(timeline);
    }

    return TimelineParser.createUnconversationedChainsGraphql(timeline, 'tweet', [], mapToThreads, includeReplies);
  }

  static Future<List<UserWithExtra>> searchUsers(String query, {int limit = 25, String? cursor}) async {
    var variables = {
      "rawQuery": query,
      "count": limit.toString(),
      "querySource": "typed_query",
      "product": 'People',
      "withDownvotePerspective": false,
      "withReactionsMetadata": false,
      "withReactionsPerspective": false,
    };

    var searchFeatures = {
      "responsive_web_graphql_exclude_directive_enabled": true,
      "verified_phone_label_enabled": true,
      "creator_subscriptions_tweet_preview_api_enabled": true,
      "responsive_web_graphql_timeline_navigation_enabled": true,
      "responsive_web_graphql_skip_user_profile_image_extensions_enabled": false,
      "c9s_tweet_anatomy_moderator_badge_enabled": true,
      "tweetypie_unmention_optimization_enabled": true,
      "responsive_web_edit_tweet_api_enabled": true,
      "graphql_is_translatable_rweb_tweet_is_translatable_enabled": true,
      "view_counts_everywhere_api_enabled": true,
      "longform_notetweets_consumption_enabled": true,
      "responsive_web_twitter_article_tweet_consumption_enabled": true,
      "tweet_awards_web_tipping_enabled": false,
      "freedom_of_speech_not_reach_fetch_enabled": true,
      "standardized_nudges_misinfo": true,
      "tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled": true,
      "rweb_video_timestamps_enabled": true,
      "longform_notetweets_rich_text_read_enabled": true,
      "longform_notetweets_inline_media_enabled": true,
      "responsive_web_enhance_cards_enabled": false,
    };

    if (cursor != null) {
      variables['cursor'] = cursor;
    }

    var uri = XEndpoints.uri(XEndpoints.searchTimelineUsers, {
      'variables': jsonEncode(variables),
      'features': jsonEncode(searchFeatures),
    });

    var response = await _twitterApi.client.get(uri);
    if (response.body.isEmpty) {
      return [];
    }

    var result = json.decode(response.body);
    if (result.isEmpty) {
      return [];
    }

    List instructions = List.from(
      result?['data']?['search_by_raw_query']?['search_timeline']?['timeline']?['instructions'] ?? [],
    );
    if (instructions.isEmpty) {
      return [];
    }
    List addEntries = List.from(
      instructions.firstWhere((e) => e['type'] == 'TimelineAddEntries', orElse: () => null)?['entries'] ?? [],
    );
    if (addEntries.isEmpty) {
      return [];
    }

    return addEntries
        .where((entry) => entry['entryId']?.startsWith('user-'))
        .where((entry) => entry['content']?['itemContent']?['user_results']?['result']?['legacy'] != null)
        .map((entry) {
          var res = entry['content']['itemContent']['user_results']['result'];
          return UserWithExtra.fromJson({
            ...res['legacy'],
            'id_str': res['rest_id'],
            'ext_is_blue_verified': res['is_blue_verified'],
          });
        })
        .toList();
  }

  static Future<List<TrendLocation>> getTrendLocations() async {
    var result = await _cache.getOrCreateAsJSON('trends.locations', const Duration(days: 2), () async {
      var locations = await _twitterApi.trendsService.available();

      return jsonEncode(locations.map((e) => e.toJson()).toList());
    });

    return List.from(jsonDecode(result)).map((e) => TrendLocation.fromJson(e)).toList(growable: false);
  }

  static Future<List<Trends>> getTrends(int location) async {
    var result = await _cache.getOrCreateAsJSON('trends.$location', const Duration(minutes: 2), () async {
      var trends = await _twitterApi.trendsService.place(id: location);

      return jsonEncode(trends.map((e) => e.toJson()).toList());
    });

    return List.from(jsonDecode(result)).map((e) => Trends.fromJson(e)).toList(growable: false);
  }

  static Map<String, Object> _homeTimelineParams({required String userId, required int count, String? cursor}) {
    final params = <String, Object>{
      "variables":
          "{\"userId\":\"160534877\",\"count\":$count,\"includePromotedContent\":false,\"withQuickPromoteEligibilityTweetFields\":true,\"withVoice\":true,\"withV2Timeline\":true}",
      "features":
          "{\"rweb_lists_timeline_redesign_enabled\":true,\"responsive_web_graphql_exclude_directive_enabled\":true,\"verified_phone_label_enabled\":true,\"creator_subscriptions_tweet_preview_api_enabled\":true,\"responsive_web_graphql_timeline_navigation_enabled\":true,\"responsive_web_graphql_skip_user_profile_image_extensions_enabled\":false,\"tweetypie_unmention_optimization_enabled\":true,\"responsive_web_edit_tweet_api_enabled\":true,\"graphql_is_translatable_rweb_tweet_is_translatable_enabled\":true,\"view_counts_everywhere_api_enabled\":true,\"longform_notetweets_consumption_enabled\":true,\"responsive_web_twitter_article_tweet_consumption_enabled\":false,\"tweet_awards_web_tipping_enabled\":false,\"freedom_of_speech_not_reach_fetch_enabled\":true,\"standardized_nudges_misinfo\":true,\"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\":true,\"longform_notetweets_rich_text_read_enabled\":true,\"longform_notetweets_inline_media_enabled\":true,\"responsive_web_media_download_video_enabled\":false,\"responsive_web_enhance_cards_enabled\":false}",
      "fieldToggles": "{\"withAuxiliaryUserLabels\":false,\"withArticleRichContentState\":false}",
    };
    final variables = json.decode(params["variables"].toString()) as Map<String, dynamic>;
    variables["userId"] = userId;
    if (cursor != null) {
      variables['cursor'] = cursor;
    }
    params["variables"] = json.encode(variables);
    return params;
  }

  static TweetStatus _parseHomeTimeline(
    Map<String, dynamic> result, {
    List<String>? pinnedTweets,
    required bool includeReplies,
    required bool showPinnedTweet,
    required int Function() getTweetsCounter,
    required void Function() incrementTweetsCounter,
  }) {
    return TimelineParser.createTimelineChains(
      result,
      'tweet',
      pinnedTweets ?? [],
      includeReplies == false,
      includeReplies,
      showPinnedTweet,
      getTweetsCounter,
      incrementTweetsCounter,
    );
  }

  static Future<TweetStatus> getTimelineTweets(
    String id,
    String type, {
    List<String>? pinnedTweets,
    int count = 10,
    String? cursor,
    bool includeReplies = true,
    bool includeRetweets = true,
    required int Function() getTweetsCounter,
    required void Function() incrementTweetsCounter,
  }) async {
    final params = _homeTimelineParams(userId: id, count: count, cursor: cursor);
    final response = await _twitterApi.client.get(XEndpoints.uri(XEndpoints.homeTimeline, params));
    final result = json.decode(response.body) as Map<String, dynamic>;
    return _parseHomeTimeline(
      result,
      pinnedTweets: pinnedTweets,
      includeReplies: includeReplies,
      showPinnedTweet: cursor == null,
      getTweetsCounter: getTweetsCounter,
      incrementTweetsCounter: incrementTweetsCounter,
    );
  }

  /// HomeTimeline for one pinned login [account] (no account rotation).
  static Future<TweetStatus> getTimelineTweetsForAccount(
    Account account, {
    List<String>? pinnedTweets,
    int count = 10,
    String? cursor,
    bool includeReplies = true,
    required int Function() getTweetsCounter,
    required void Function() incrementTweetsCounter,
  }) async {
    final params = _homeTimelineParams(userId: account.id, count: count, cursor: cursor);
    final response = await QuackerTwitterClient.fetchAs(account, XEndpoints.uri(XEndpoints.homeTimeline, params));
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw HttpException(response);
    }
    final result = json.decode(response.body) as Map<String, dynamic>;
    return _parseHomeTimeline(
      result,
      pinnedTweets: pinnedTweets,
      includeReplies: includeReplies,
      showPinnedTweet: cursor == null,
      getTweetsCounter: getTweetsCounter,
      incrementTweetsCounter: incrementTweetsCounter,
    );
  }

  static Future<TweetStatus> getTweets(
    String id,
    String type,
    List<String> pinnedTweets, {
    int count = 10,
    String? cursor,
    bool includeReplies = true,
    bool includeRetweets = true,
    required int Function() getTweetsCounter,
    required void Function() incrementTweetsCounter,
  }) async {
    bool showPinnedTweet = true;
    var query = {...defaultParams, 'count': count.toString()};

    Map<String, Object> defaultUserTweetsParam = {
      "variables": jsonEncode({
        "userId": "8341362",
        "count": 20,
        "includePromotedContent": true,
        "withQuickPromoteEligibilityTweetFields": true,
        "withVoice": true,
      }),
      "features": jsonEncode({
        "rweb_video_screen_enabled": false,
        "rweb_cashtags_enabled": false,
        "profile_label_improvements_pcf_label_in_post_enabled": true,
        "responsive_web_profile_redirect_enabled": false,
        "rweb_tipjar_consumption_enabled": false,
        "verified_phone_label_enabled": false,
        "creator_subscriptions_tweet_preview_api_enabled": true,
        "responsive_web_graphql_timeline_navigation_enabled": true,
        "responsive_web_graphql_skip_user_profile_image_extensions_enabled": false,
        "premium_content_api_read_enabled": false,
        "communities_web_enable_tweet_community_results_fetch": true,
        "c9s_tweet_anatomy_moderator_badge_enabled": true,
        "responsive_web_grok_analyze_button_fetch_trends_enabled": false,
        "responsive_web_grok_analyze_post_followups_enabled": true,
        "responsive_web_jetfuel_frame": true,
        "responsive_web_grok_share_attachment_enabled": true,
        "responsive_web_grok_annotations_enabled": true,
        "articles_preview_enabled": true,
        "responsive_web_edit_tweet_api_enabled": true,
        "graphql_is_translatable_rweb_tweet_is_translatable_enabled": true,
        "view_counts_everywhere_api_enabled": true,
        "longform_notetweets_consumption_enabled": true,
        "responsive_web_twitter_article_tweet_consumption_enabled": true,
        "content_disclosure_indicator_enabled": true,
        "content_disclosure_ai_generated_indicator_enabled": true,
        "responsive_web_grok_show_grok_translated_post": true,
        "responsive_web_grok_analysis_button_from_backend": true,
        "post_ctas_fetch_enabled": true,
        "freedom_of_speech_not_reach_fetch_enabled": true,
        "standardized_nudges_misinfo": true,
        "tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled": true,
        "longform_notetweets_rich_text_read_enabled": true,
        "longform_notetweets_inline_media_enabled": false,
        "responsive_web_grok_image_annotation_enabled": true,
        "responsive_web_grok_imagine_annotation_enabled": true,
        "responsive_web_grok_community_note_auto_translation_is_enabled": true,
        "responsive_web_enhance_cards_enabled": false,
      }),
      "fieldToggles": jsonEncode({"withArticlePlainText": false}),
    };

    if (includeReplies) {
      defaultUserTweetsParam["features"] = jsonEncode({
        "rweb_video_screen_enabled": false,
        "rweb_cashtags_enabled": false,
        "profile_label_improvements_pcf_label_in_post_enabled": true,
        "responsive_web_profile_redirect_enabled": false,
        "rweb_tipjar_consumption_enabled": false,
        "verified_phone_label_enabled": false,
        "creator_subscriptions_tweet_preview_api_enabled": true,
        "responsive_web_graphql_timeline_navigation_enabled": true,
        "responsive_web_graphql_skip_user_profile_image_extensions_enabled": false,
        "premium_content_api_read_enabled": false,
        "communities_web_enable_tweet_community_results_fetch": true,
        "c9s_tweet_anatomy_moderator_badge_enabled": true,
        "responsive_web_grok_analyze_button_fetch_trends_enabled": false,
        "responsive_web_grok_analyze_post_followups_enabled": true,
        "responsive_web_jetfuel_frame": true,
        "responsive_web_grok_share_attachment_enabled": true,
        "responsive_web_grok_annotations_enabled": true,
        "articles_preview_enabled": true,
        "responsive_web_edit_tweet_api_enabled": true,
        "graphql_is_translatable_rweb_tweet_is_translatable_enabled": true,
        "view_counts_everywhere_api_enabled": true,
        "longform_notetweets_consumption_enabled": true,
        "responsive_web_twitter_article_tweet_consumption_enabled": true,
        "content_disclosure_indicator_enabled": true,
        "content_disclosure_ai_generated_indicator_enabled": true,
        "responsive_web_grok_show_grok_translated_post": true,
        "responsive_web_grok_analysis_button_from_backend": true,
        "post_ctas_fetch_enabled": true,
        "freedom_of_speech_not_reach_fetch_enabled": true,
        "standardized_nudges_misinfo": true,
        "tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled": true,
        "longform_notetweets_rich_text_read_enabled": true,
        "longform_notetweets_inline_media_enabled": false,
        "responsive_web_grok_image_annotation_enabled": true,
        "responsive_web_grok_imagine_annotation_enabled": true,
        "responsive_web_grok_community_note_auto_translation_is_enabled": true,
        "responsive_web_enhance_cards_enabled": false,
      });

      defaultUserTweetsParam["filedToggles"] = jsonEncode({"withArticlePlainText": false});
    } else if (type == "media") {
      defaultUserTweetsParam["features"] = jsonEncode({
        "rweb_video_screen_enabled": false,
        "payments_enabled": false,
        "profile_label_improvements_pcf_label_in_post_enabled": true,
        "responsive_web_profile_redirect_enabled": false,
        "rweb_tipjar_consumption_enabled": true,
        "verified_phone_label_enabled": false,
        "creator_subscriptions_tweet_preview_api_enabled": true,
        "responsive_web_graphql_timeline_navigation_enabled": true,
        "responsive_web_graphql_skip_user_profile_image_extensions_enabled": false,
        "premium_content_api_read_enabled": false,
        "communities_web_enable_tweet_community_results_fetch": true,
        "c9s_tweet_anatomy_moderator_badge_enabled": true,
        "responsive_web_grok_analyze_button_fetch_trends_enabled": false,
        "responsive_web_grok_analyze_post_followups_enabled": true,
        "responsive_web_jetfuel_frame": true,
        "responsive_web_grok_share_attachment_enabled": true,
        "articles_preview_enabled": true,
        "responsive_web_edit_tweet_api_enabled": true,
        "graphql_is_translatable_rweb_tweet_is_translatable_enabled": true,
        "view_counts_everywhere_api_enabled": true,
        "longform_notetweets_consumption_enabled": true,
        "responsive_web_twitter_article_tweet_consumption_enabled": true,
        "tweet_awards_web_tipping_enabled": false,
        "responsive_web_grok_show_grok_translated_post": false,
        "responsive_web_grok_analysis_button_from_backend": true,
        "creator_subscriptions_quote_tweet_preview_enabled": false,
        "freedom_of_speech_not_reach_fetch_enabled": true,
        "standardized_nudges_misinfo": true,
        "tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled": true,
        "longform_notetweets_rich_text_read_enabled": true,
        "longform_notetweets_inline_media_enabled": true,
        "responsive_web_grok_image_annotation_enabled": true,
        "responsive_web_grok_imagine_annotation_enabled": true,
        "responsive_web_grok_community_note_auto_translation_is_enabled": false,
        "responsive_web_enhance_cards_enabled": false,
      });
      defaultUserTweetsParam["filedToggles"] = jsonEncode({"withArticlePlainText": false});
    }

    Map<String, dynamic> variables = json.decode(defaultUserTweetsParam["variables"].toString());
    variables["userId"] = id;
    if (cursor != null) {
      variables['cursor'] = cursor;
    }
    variables['count'] = count;
    defaultUserTweetsParam["variables"] = json.encode(variables);

    late String endpoint;
    if (type == "media") {
      endpoint = XEndpoints.userMedia;
    } else {
      endpoint = includeReplies ? XEndpoints.userTweetsAndReplies : XEndpoints.userTweets;
    }

    var response = await _twitterApi.client.get(XEndpoints.uri(endpoint, defaultUserTweetsParam));

    if (cursor != null) {
      query['cursor'] = cursor;
    }

    var result = json.decode(response.body);

    //if this page is not first one on the profile page, dont add pinned tweet
    if (variables['cursor'] != null) showPinnedTweet = false;
    return TimelineParser.createUnconversationedChains(
      result,
      'tweet',
      pinnedTweets,
      includeReplies == false,
      includeReplies,
      showPinnedTweet,
      getTweetsCounter,
      incrementTweetsCounter,
    );
  }

  static Future<Map<String, dynamic>> getBroadcastDetails(String key) async {
    var response = await _twitterApi.client.get(Uri.https('twitter.com', '/i/api/1.1/live_video_stream/status/$key'));

    return json.decode(response.body);
  }

  /// `broadcasts/show.json` for a Periscope/X broadcast id. Yields `media_key`.
  static Future<Object?> fetchBroadcastsShow(String broadcastId) async {
    final response = await _twitterApi.client.get(
      Uri.https('twitter.com', '/i/api/1.1/broadcasts/show.json', {'ids': broadcastId}),
    );
    return json.decode(response.body);
  }

  /// GraphQL AudioSpaceById — metadata (including `media_key`) for a Space.
  static Future<Object?> fetchAudioSpace(String spaceId) async {
    final uri = XEndpoints.uri(XEndpoints.audioSpaceById, {
      'variables': jsonEncode({
        'id': spaceId,
        'isMetatagsQuery': true,
        'withReplays': true,
        'withListeners': true,
      }),
      'features': jsonEncode({
        'spaces_2022_h2_clipping': true,
        'spaces_2022_h2_spaces_communities': true,
      }),
    });
    final response = await _twitterApi.client.get(uri);
    return json.decode(response.body);
  }
}
