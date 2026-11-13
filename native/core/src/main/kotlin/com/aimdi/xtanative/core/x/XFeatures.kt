package com.aimdi.xtanative.core.x

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The feature switches each read sends, as XTA's client.dart records them
 * from x.com. A query id expects the switches of the web build it came from,
 * so these move together with the ids in [XEndpoints].
 */
object XFeatures {
    private fun flags(vararg pairs: Pair<String, Boolean>) = JsonObject(pairs.associate { (key, on) -> key to JsonPrimitive(on) })

    /** The switches x.com sends with every timeline-shaped read (client.dart's _timelineFeatures). */
    val TIMELINE = flags(
        "articles_preview_enabled" to true,
        "c9s_tweet_anatomy_moderator_badge_enabled" to true,
        "communities_web_enable_tweet_community_results_fetch" to true,
        "content_disclosure_ai_generated_indicator_enabled" to true,
        "content_disclosure_indicator_enabled" to true,
        "creator_subscriptions_tweet_preview_api_enabled" to true,
        "freedom_of_speech_not_reach_fetch_enabled" to true,
        "graphql_is_translatable_rweb_tweet_is_translatable_enabled" to true,
        "longform_notetweets_consumption_enabled" to true,
        "longform_notetweets_inline_media_enabled" to false,
        "longform_notetweets_rich_text_read_enabled" to true,
        "post_ctas_fetch_enabled" to false,
        "premium_content_api_read_enabled" to false,
        "profile_label_improvements_pcf_label_in_post_enabled" to true,
        "responsive_web_edit_tweet_api_enabled" to true,
        "responsive_web_enhance_cards_enabled" to false,
        "responsive_web_graphql_timeline_navigation_enabled" to true,
        "responsive_web_grok_analysis_button_from_backend" to true,
        "responsive_web_grok_analyze_button_fetch_trends_enabled" to false,
        "responsive_web_grok_analyze_post_followups_enabled" to true,
        "responsive_web_grok_annotations_enabled" to true,
        "responsive_web_grok_community_note_auto_translation_is_enabled" to true,
        "responsive_web_grok_image_annotation_enabled" to true,
        "responsive_web_grok_imagine_annotation_enabled" to true,
        "responsive_web_grok_share_attachment_enabled" to true,
        "responsive_web_grok_show_grok_translated_post" to true,
        "responsive_web_jetfuel_frame" to true,
        "responsive_web_nested_quote_preview_enabled" to true,
        "responsive_web_profile_redirect_enabled" to true,
        "responsive_web_twitter_article_tweet_consumption_enabled" to true,
        "rweb_cashtags_composer_attachment_enabled" to true,
        "rweb_cashtags_enabled" to true,
        "rweb_conversational_replies_downvote_enabled" to false,
        "rweb_sports_post_context_enabled" to true,
        "rweb_tipjar_consumption_enabled" to false,
        "rweb_video_screen_enabled" to false,
        "standardized_nudges_misinfo" to true,
        "tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled" to true,
        "verified_phone_label_enabled" to false,
        "view_counts_everywhere_api_enabled" to true,
    )

    /** UserMedia's switches (client.dart's _userMediaFeatures). */
    val MEDIA = flags(
        "articles_preview_enabled" to true,
        "c9s_tweet_anatomy_moderator_badge_enabled" to true,
        "communities_web_enable_tweet_community_results_fetch" to true,
        "creator_subscriptions_quote_tweet_preview_enabled" to false,
        "creator_subscriptions_tweet_preview_api_enabled" to true,
        "freedom_of_speech_not_reach_fetch_enabled" to true,
        "graphql_is_translatable_rweb_tweet_is_translatable_enabled" to true,
        "longform_notetweets_consumption_enabled" to true,
        "longform_notetweets_inline_media_enabled" to true,
        "longform_notetweets_rich_text_read_enabled" to true,
        "payments_enabled" to false,
        "premium_content_api_read_enabled" to false,
        "profile_label_improvements_pcf_label_in_post_enabled" to true,
        "responsive_web_edit_tweet_api_enabled" to true,
        "responsive_web_enhance_cards_enabled" to false,
        "responsive_web_graphql_skip_user_profile_image_extensions_enabled" to false,
        "responsive_web_graphql_timeline_navigation_enabled" to true,
        "responsive_web_grok_analysis_button_from_backend" to true,
        "responsive_web_grok_analyze_button_fetch_trends_enabled" to false,
        "responsive_web_grok_analyze_post_followups_enabled" to true,
        "responsive_web_grok_community_note_auto_translation_is_enabled" to false,
        "responsive_web_grok_image_annotation_enabled" to true,
        "responsive_web_grok_imagine_annotation_enabled" to true,
        "responsive_web_grok_share_attachment_enabled" to true,
        "responsive_web_grok_show_grok_translated_post" to false,
        "responsive_web_jetfuel_frame" to true,
        "responsive_web_profile_redirect_enabled" to false,
        "responsive_web_twitter_article_tweet_consumption_enabled" to true,
        "rweb_tipjar_consumption_enabled" to true,
        "rweb_video_screen_enabled" to false,
        "standardized_nudges_misinfo" to true,
        "tweet_awards_web_tipping_enabled" to false,
        "tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled" to true,
        "verified_phone_label_enabled" to false,
        "view_counts_everywhere_api_enabled" to true,
    )

    /** UserByScreenName's switches (client.dart's _profileFeatures). */
    val PROFILE = flags(
        "creator_subscriptions_tweet_preview_api_enabled" to true,
        "hidden_profile_subscriptions_enabled" to true,
        "highlights_tweets_tab_ui_enabled" to true,
        "profile_label_improvements_pcf_label_in_post_enabled" to true,
        "responsive_web_graphql_timeline_navigation_enabled" to true,
        "responsive_web_profile_redirect_enabled" to true,
        "responsive_web_twitter_article_notes_tab_enabled" to true,
        "rweb_tipjar_consumption_enabled" to false,
        "subscriptions_feature_can_gift_premium" to false,
        "subscriptions_verification_info_is_identity_verified_enabled" to true,
        "subscriptions_verification_info_verified_since_enabled" to true,
        "verified_phone_label_enabled" to false,
    )

    /** UserByRestId's switches, as getProfileById sends them. */
    val PROFILE_BY_ID = flags(
        "responsive_web_graphql_timeline_navigation_enabled" to true,
        "responsive_web_twitter_blue_verified_badge_is_enabled" to true,
        "verified_phone_label_enabled" to true,
    )

    /** ListByRestId's switches (client.dart's _listByRestIdFeatures). */
    val LIST_BY_REST_ID = flags(
        "profile_label_improvements_pcf_label_in_post_enabled" to true,
        "responsive_web_profile_redirect_enabled" to false,
        "rweb_tipjar_consumption_enabled" to false,
        "verified_phone_label_enabled" to false,
        "responsive_web_graphql_skip_user_profile_image_extensions_enabled" to false,
        "responsive_web_graphql_timeline_navigation_enabled" to true,
    )

    /** CommunityQuery's switches (client.dart's _communityFeatures). */
    val COMMUNITY = flags(
        "c9s_list_members_action_api_enabled" to false,
        "c9s_superc9s_indication_enabled" to false,
    )

    /** AudioSpaceById's switches. */
    val AUDIO_SPACE = flags(
        "spaces_2022_h2_clipping" to true,
        "spaces_2022_h2_spaces_communities" to true,
    )
}
