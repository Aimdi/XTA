package com.aimdi.xtanative.core.x

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.model.XTimelinePage
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.core.x.parse.XParser
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** An X session XTA stored: its id (the `ct0`), its name and the headers it signs in with. */
data class XAccount(val id: String, val screenName: String?, val headers: Map<String, String>) {
    val cookie: String? get() = headers.entries.firstOrNull { it.key.equals("cookie", ignoreCase = true) }?.value?.trim()?.ifEmpty { null }
}

/**
 * X answered, but not with what was asked. A connection that failed before
 * any answer is an [java.io.IOException] instead.
 */
sealed class XReadException(message: String) : Exception(message)

/** [code] is X's HTTP status. */
class XRequestException(val code: Int, message: String) : XReadException(message)

/** Every account was rate-limited on this endpoint. */
class XRateLimitedException : XReadException("Every account is rate-limited here for now")

/** There is no X account to read with, and X did not answer a guest either: XTA's backup has to be imported first. */
class XNoAccountException(why: String = "No X account") : XReadException(why)

/**
 * The reads XTA Native asks of X, over XTA's own stored sessions. Read-only:
 * nothing is posted, liked or followed on X.
 *
 * Accounts are tried in turn, as XTA's transport does: one rate-limited on an
 * endpoint (429) is skipped there until X says it resets, and one that is
 * refused (401, 403, 404) hands the request to the next.
 */
class XClient(
    private val http: XHttp,
    private val accounts: () -> List<XAccount>,
    private val signers: XSigners,
    val endpoints: XEndpoints = XEndpoints(),
    private val clock: () -> Long = System::currentTimeMillis,
    /** Reads as a guest when there is no account, as XTA does; null never does. */
    private val guest: XGuest? = null,
) {
    private val limitedUntil = ConcurrentHashMap<Pair<String, String>, Long>()

    /** Reads take turns across the accounts, so no one account reaches X's limits first. */
    private val turn = AtomicInteger()

    suspend fun profile(screenName: String): Pair<XUser, List<String>> = parsing {
        val body = get(
            XEndpoints.USER_BY_SCREEN_NAME,
            mapOf(
                "variables" to json { put("screen_name", screenName.removePrefix("@")); put("withGrokTranslatedBio", true) },
                "features" to PROFILE_FEATURES.toString(),
                "fieldToggles" to json { put("withPayments", false); put("withAuxiliaryUserLabels", true) },
            ),
        )
        XParser.profile(body) ?: throw XRequestException(404, "No such account: $screenName")
    }

    /** A profile's posts tab: its own posts, newest first, with its pinned post. */
    suspend fun userPosts(userId: String, cursor: String? = null, count: Int = 20): XTimelinePage =
        userTimeline(XEndpoints.USER_ORIGINALS, userId, cursor, count)

    /** What a profile reposted, newest first: X keeps these apart from its posts. */
    suspend fun userReposts(userId: String, cursor: String? = null, count: Int = 20): XTimelinePage =
        userTimeline(XEndpoints.USER_REPOSTS, userId, cursor, count)

    /** A profile's posts and its replies, as the Replies tab shows them. */
    suspend fun userReplies(userId: String, cursor: String? = null, count: Int = 20): XTimelinePage =
        userTimeline(XEndpoints.USER_REPLIES, userId, cursor, count, mapOf("withCommunity" to JsonPrimitive(true)))

    /** A profile's photos and videos: one grid module per page. */
    suspend fun userMedia(userId: String, cursor: String? = null, count: Int = 20): XTimelinePage = parsing {
        // XTA sends the toggles under both names, the typo included, as x.com has.
        val toggles = json { put("withArticlePlainText", false) }
        XParser.timeline(get(
            XEndpoints.USER_MEDIA,
            mapOf(
                "variables" to JsonObject(userVariables(userId, cursor, count)).toString(),
                "features" to MEDIA_FEATURES.toString(),
                "fieldToggles" to toggles,
                "filedToggles" to toggles,
            ),
        ))
    }

    private suspend fun userTimeline(
        endpoint: String,
        userId: String,
        cursor: String?,
        count: Int,
        extra: Map<String, JsonPrimitive> = emptyMap(),
    ): XTimelinePage = parsing {
        XParser.timeline(get(
            endpoint,
            mapOf(
                "variables" to JsonObject(userVariables(userId, cursor, count) + extra).toString(),
                "features" to TIMELINE_FEATURES.toString(),
                "fieldToggles" to json { put("withPayments", false); put("withArticlePlainText", false) },
            ),
        ))
    }

    private fun userVariables(userId: String, cursor: String?, count: Int): Map<String, JsonPrimitive> = buildMap {
        put("userId", JsonPrimitive(userId))
        put("count", JsonPrimitive(count))
        put("includePromotedContent", JsonPrimitive(true))
        put("withQuickPromoteEligibilityTweetFields", JsonPrimitive(true))
        put("withVoice", JsonPrimitive(true))
        cursor?.let { put("cursor", JsonPrimitive(it)) }
    }

    /** A post and its conversation: what it answers, then the replies. */
    suspend fun conversation(postId: String, cursor: String? = null): XTimelinePage = parsing {
        XParser.timeline(get(
            XEndpoints.TWEET_DETAIL,
            mapOf(
                "variables" to json {
                    put("focalTweetId", postId)
                    put("with_rux_injections", false)
                    put("rankingMode", "Relevance")
                    put("includePromotedContent", true)
                    put("withCommunity", true)
                    put("withQuickPromoteEligibilityTweetFields", true)
                    put("withBirdwatchNotes", true)
                    put("withVoice", true)
                    cursor?.let { put("cursor", it) }
                },
                // With Grok annotations on, TweetDetail has been seen to leave out replies.
                "features" to JsonObject(TIMELINE_FEATURES + ("responsive_web_grok_annotations_enabled" to JsonPrimitive(false))).toString(),
                "fieldToggles" to json {
                    put("withPayments", false)
                    put("withArticleRichContentState", true)
                    put("withArticlePlainText", false)
                    put("withArticleSummaryText", true)
                    put("withArticleVoiceOver", true)
                    put("withGrokAnalyze", false)
                    put("withDisallowedReplyControls", false)
                },
            ),
        ))
    }

    /** Latest posts for a search, as the Following feed asks for each chunk of accounts. */
    suspend fun search(query: String, cursor: String? = null, count: Int = 20, product: String = "Latest"): XTimelinePage =
        parsing {
            XParser.timeline(get(
                XEndpoints.SEARCH,
                mapOf(
                    "variables" to json {
                        put("rawQuery", query)
                        put("count", count.toString())
                        put("querySource", "typed_query")
                        put("product", product)
                        put("withGrokTranslatedBio", true)
                        put("withBirdwatchNotes", true)
                        put("withQuickPromoteEligibilityTweetFields", false)
                        cursor?.let { put("cursor", it) }
                    },
                    "features" to TIMELINE_FEATURES.toString(),
                ),
            ))
        }

    /** Repairs rotated query ids from XTA's published `endpoints.json`; a failure leaves the shipped ids. */
    suspend fun refreshEndpoints() {
        runCatching {
            val response = http.get(XEndpoints.OVERRIDES_URL.toHttpUrl(), emptyMap(), followRedirects = true)
            if (response.isSuccessful) endpoints.applyOverrides(J.parse(response.body))
        }
    }

    /** X's answers are parsed away from the caller's thread, which may be the UI's. */
    private suspend fun <T> parsing(block: suspend () -> T): T = withContext(Dispatchers.Default) { block() }

    private suspend fun get(endpoint: String, params: Map<String, String>): J {
        val url = endpoints.url(endpoint, params)
        val all = accounts()
        if (all.isEmpty()) return asGuest(endpoint, url)
        return when (val first = tryAccounts(endpoint, url, all)) {
            is Outcome.Answer -> first.body
            // Every account refused with 404: X answers a stale signature that
            // way, so the signers are derived again once before giving up.
            is Outcome.Failed -> if (first.error.code == 404) {
                signers.clear()
                when (val second = tryAccounts(endpoint, url, all)) {
                    is Outcome.Answer -> second.body
                    is Outcome.Failed -> throw second.error
                }
            } else {
                throw first.error
            }
        }
    }

    /**
     * The read as a guest: X's logged-out bearer, a guest token, and a
     * signature derived without a session. A refused token is replaced once.
     */
    private suspend fun asGuest(endpoint: String, url: HttpUrl): J {
        val guest = guest ?: throw XNoAccountException()
        val signer = signers.forSession(null)
        suspend fun send(fresh: Boolean) = http.get(
            url,
            BASE_HEADERS + mapOf(
                "authorization" to XGuest.BEARER,
                "x-guest-token" to guest.token(fresh),
                "x-client-transaction-id" to signer.transactionId("GET", url.encodedPath),
            ),
            followRedirects = true,
        )
        val first = send(fresh = false)
        val response = if (first.code == 401 || first.code == 403) send(fresh = true) else first
        return when {
            response.isSuccessful -> J.parse(response.body)
            response.code == 429 -> throw XRateLimitedException()
            else -> throw XNoAccountException("X did not answer $endpoint for a guest (${response.code})")
        }
    }

    private sealed interface Outcome {
        class Answer(val body: J) : Outcome
        class Failed(val error: XRequestException) : Outcome
    }

    private suspend fun tryAccounts(endpoint: String, url: HttpUrl, all: List<XAccount>): Outcome {
        val path = url.encodedPath
        val now = clock()
        val open = all.filter { (limitedUntil[it.id to path] ?: 0) <= now }
        val first = if (open.isEmpty()) 0 else Math.floorMod(turn.getAndIncrement(), open.size)
        var refused: XRequestException? = null
        for (account in open.drop(first) + open.take(first)) {
            val signer = signers.forSession(account.cookie)
            val headers = BASE_HEADERS + account.headers + ("x-client-transaction-id" to signer.transactionId("GET", path))
            val response = http.get(url, headers, followRedirects = true)
            when {
                response.isSuccessful -> return Outcome.Answer(J.parse(response.body))
                response.code == 429 -> limitedUntil[account.id to path] =
                    response.header("x-rate-limit-reset")?.toLongOrNull()?.times(1000) ?: (clock() + RATE_LIMIT_FALLBACK_MILLIS)
                response.code in REFUSALS -> refused = XRequestException(response.code, "X refused $endpoint (${response.code})")
                else -> throw XRequestException(response.code, "X answered $endpoint with ${response.code}")
            }
        }
        // Nothing refused and nothing answered: every account is rate-limited here.
        return Outcome.Failed(refused ?: throw XRateLimitedException())
    }

    companion object {
        private val REFUSALS = setOf(401, 403, 404)
        private const val RATE_LIMIT_FALLBACK_MILLIS = 15 * 60 * 1000L

        private const val BEARER =
            "Bearer AAAAAAAAAAAAAAAAAAAAANRILgAAAAAAnNwIzUejRCOuH5E6I8xnZz4puTs%3D1Zv7ttfk8LF81IUq16cHjhLTvJu4FA33AGWWjCpTnA"

        val BASE_HEADERS = mapOf(
            "accept" to "*/*",
            "accept-language" to "en-US,en;q=0.9",
            "authorization" to BEARER,
            "cache-control" to "no-cache",
            "content-type" to "application/json",
            "pragma" to "no-cache",
            "priority" to "u=1, i",
            "referer" to "https://x.com/",
            "user-agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.3",
            "x-twitter-active-user" to "yes",
            "x-twitter-client-language" to "en",
        )

        private fun json(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) = buildJsonObject(block).toString()

        private fun flags(vararg pairs: Pair<String, Boolean>) = JsonObject(pairs.associate { (key, on) -> key to JsonPrimitive(on) })

        /** The switches x.com sends with every timeline-shaped read, as XTA records them (client.dart). */
        val TIMELINE_FEATURES = flags(
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

        /** UserMedia's switches, as XTA records them (client.dart's _userMediaFeatures). */
        val MEDIA_FEATURES = flags(
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

        /** UserByScreenName's switches, as XTA records them. */
        val PROFILE_FEATURES = flags(
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
    }
}
