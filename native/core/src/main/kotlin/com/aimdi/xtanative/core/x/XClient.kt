package com.aimdi.xtanative.core.x

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.model.XAudioSpace
import com.aimdi.xtanative.core.x.model.XCommunity
import com.aimdi.xtanative.core.x.model.XCommunityPage
import com.aimdi.xtanative.core.x.model.XListInfo
import com.aimdi.xtanative.core.x.model.XLive
import com.aimdi.xtanative.core.x.model.XTimelinePage
import com.aimdi.xtanative.core.x.model.XTranslation
import com.aimdi.xtanative.core.x.model.XTrendPlace
import com.aimdi.xtanative.core.x.model.XTrends
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.core.x.model.XUserPage
import com.aimdi.xtanative.core.x.parse.XCommunityParser
import com.aimdi.xtanative.core.x.parse.XLiveParser
import com.aimdi.xtanative.core.x.parse.XParser
import com.aimdi.xtanative.core.x.parse.XTrendsParser
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** How X orders the replies under an opened post (TweetDetail's `rankingMode`). */
enum class XReplyRanking(val mode: String) { RELEVANCE("Relevance"), RECENCY("Recency"), LIKES("Likes") }

/** How X orders the quotes of a post (SearchTimeline's `product`). */
enum class XQuoteOrder(val product: String) { LATEST("Latest"), TOP("Top") }

/**
 * The reads XTA Native asks of X, over XTA's own stored sessions. Read-only:
 * nothing is posted, liked or followed on X.
 *
 * Accounts are tried as XTA's transport tries them (see [XTransport]):
 * healthy ones first, a 429 sets one aside on that endpoint until X says it
 * resets, and a refusal (401, 403, 404) is tried on one more account.
 */
class XClient(
    private val http: XHttp,
    accounts: () -> List<XAccount>,
    signers: XSigners,
    val endpoints: XEndpoints = XEndpoints(),
    private val clock: () -> Long = System::currentTimeMillis,
    /** Reads as a guest when there is no account, as XTA does; null never does. */
    guest: XGuest? = null,
    /** Where each account's sign-in health is kept; in memory unless a store keeps it, as XTA's database does. */
    health: XAccountHealthStore = XAccountHealthStore.inMemory(),
    /** Accounts the reader turned off for feeds: asked only when every account is off, as XTA's AccountFetchGate. */
    disabledAccounts: () -> Set<String> = { emptySet() },
) {
    private val transport = XTransport(http, accounts, signers, endpoints, clock, guest, health, disabledAccounts)
    private val kept = ConcurrentHashMap<String, Pair<Long, Any>>()

    /** What X said of each account's limits: when a rate-limited read may be tried again. */
    val rateLimits: XRateLimits get() = transport.limits

    /** Between tries while Grok still writes a translation. */
    internal var translationWaitMillis = 1000L

    // ---- profiles ----

    /**
     * A profile by its handle, with the ids of its pinned posts. One X will
     * not show (suspended, gone) is an [XUserUnavailableException] saying why.
     */
    suspend fun profile(screenName: String): Pair<XUser, List<String>> = parsing {
        val name = screenName.removePrefix("@")
        profileOf(
            transport.graphql(
                XEndpoints.USER_BY_SCREEN_NAME,
                mapOf(
                    "variables" to json { put("screen_name", name); put("withGrokTranslatedBio", true) },
                    "features" to XFeatures.PROFILE.toString(),
                    "fieldToggles" to json { put("withPayments", false); put("withAuxiliaryUserLabels", true) },
                ),
            ),
            name,
        )
    }

    /** A profile by its account id, as XTA reads a followed account it knows only by id. */
    suspend fun profileById(userId: String): Pair<XUser, List<String>> = parsing {
        profileOf(
            transport.graphql(
                XEndpoints.USER_BY_REST_ID,
                mapOf(
                    "variables" to json {
                        put("userId", userId)
                        put("withHighlightedLabel", true)
                        put("withSafetyModeUserFields", true)
                        put("withSuperFollowsUserFields", true)
                    },
                    "features" to XFeatures.PROFILE_BY_ID.toString(),
                ),
            ),
            userId,
        )
    }

    private fun profileOf(body: J, who: String): Pair<XUser, List<String>> =
        XParser.profile(body) ?: throw XParser.error(body) ?: XUserUnavailableException(who, body["data"]["user"]["result"]["reason"].string)

    /** Whom a profile follows, newest follow first. */
    suspend fun following(userId: String, cursor: String? = null, count: Int = 200): XUserPage = follows(XEndpoints.FOLLOWING, userId, cursor, count)

    /** Who follows a profile, newest first. */
    suspend fun followers(userId: String, cursor: String? = null, count: Int = 200): XUserPage = follows(XEndpoints.FOLLOWERS, userId, cursor, count)

    private suspend fun follows(endpoint: String, userId: String, cursor: String?, count: Int): XUserPage = parsing {
        val body = transport.graphql(
            endpoint,
            mapOf(
                "variables" to json {
                    put("userId", userId)
                    put("count", count)
                    cursor?.let { put("cursor", it) }
                    put("includePromotedContent", false)
                    put("withGrokTranslatedBio", true)
                },
                "features" to XFeatures.TIMELINE.toString(),
            ),
        )
        XParser.users(body["data"]["user"]["result"]["timeline"]["timeline"]["instructions"])
    }

    // ---- a profile's tabs ----

    /** A profile's posts tab: its own posts, newest first, with its pinned post. */
    suspend fun userPosts(userId: String, cursor: String? = null, count: Int = 20): XTimelinePage =
        userTimeline(XEndpoints.USER_ORIGINALS, userId, cursor, count)

    /** What a profile reposted, newest first: X keeps these apart from its posts. */
    suspend fun userReposts(userId: String, cursor: String? = null, count: Int = 20): XTimelinePage =
        userTimeline(XEndpoints.USER_REPOSTS, userId, cursor, count)

    /** A profile's posts and its replies, as the Replies tab shows them. */
    suspend fun userReplies(userId: String, cursor: String? = null, count: Int = 20): XTimelinePage =
        userTimeline(XEndpoints.USER_REPLIES, userId, cursor, count, mapOf("withCommunity" to JsonPrimitive(true)))

    /** A profile's photos and videos: a thread per post of its media grid. */
    suspend fun userMedia(userId: String, cursor: String? = null, count: Int = 20): XTimelinePage = parsing {
        // XTA sends the toggles under both names, the typo included, as x.com has.
        val toggles = json { put("withArticlePlainText", false) }
        XParser.timeline(
            transport.graphql(
                XEndpoints.USER_MEDIA,
                mapOf(
                    "variables" to JsonObject(userVariables(userId, cursor, count)).toString(),
                    "features" to XFeatures.MEDIA.toString(),
                    "fieldToggles" to toggles,
                    "filedToggles" to toggles,
                ),
            ),
        )
    }

    private suspend fun userTimeline(
        endpoint: String,
        userId: String,
        cursor: String?,
        count: Int,
        extra: Map<String, JsonPrimitive> = emptyMap(),
    ): XTimelinePage = parsing {
        XParser.timeline(
            transport.graphql(
                endpoint,
                mapOf(
                    "variables" to JsonObject(userVariables(userId, cursor, count) + extra).toString(),
                    "features" to XFeatures.TIMELINE.toString(),
                    "fieldToggles" to json { put("withPayments", false); put("withArticlePlainText", false) },
                ),
            ),
        )
    }

    private fun userVariables(userId: String, cursor: String?, count: Int): Map<String, JsonPrimitive> = buildMap {
        put("userId", JsonPrimitive(userId))
        put("count", JsonPrimitive(count))
        put("includePromotedContent", JsonPrimitive(true))
        put("withQuickPromoteEligibilityTweetFields", JsonPrimitive(true))
        put("withVoice", JsonPrimitive(true))
        cursor?.let { put("cursor", JsonPrimitive(it)) }
    }

    /**
     * The For you timeline of [account] alone (HomeTimeline), as XTA reads
     * each account the reader picked for it: no other account stands in.
     */
    suspend fun homeTimeline(account: XAccount, cursor: String? = null, count: Int = 20): XTimelinePage = parsing {
        val url = endpoints.url(
            XEndpoints.HOME_TIMELINE,
            mapOf(
                "variables" to json {
                    put("userId", account.id)
                    put("count", count)
                    put("includePromotedContent", false)
                    put("withQuickPromoteEligibilityTweetFields", true)
                    put("withVoice", true)
                    put("withV2Timeline", true)
                    cursor?.let { put("cursor", it) }
                },
                "features" to XFeatures.TIMELINE.toString(),
                "fieldToggles" to json { put("withAuxiliaryUserLabels", false); put("withArticleRichContentState", false) },
            ),
        )
        XParser.timeline(transport.getAs(account, url, XEndpoints.HOME_TIMELINE))
    }

    // ---- posts ----

    /**
     * A post and its conversation: what it answers, then the replies, in
     * [ranking]'s order. A post X withholds keeps its place; replies X hides
     * as low quality are behind the page's show-more cursor.
     */
    suspend fun conversation(postId: String, cursor: String? = null, ranking: XReplyRanking = XReplyRanking.RELEVANCE): XTimelinePage = parsing {
        XParser.conversation(
            transport.graphql(
                XEndpoints.TWEET_DETAIL,
                mapOf(
                    "variables" to json {
                        put("focalTweetId", postId)
                        put("with_rux_injections", false)
                        put("rankingMode", ranking.mode)
                        put("includePromotedContent", true)
                        put("withCommunity", true)
                        put("withQuickPromoteEligibilityTweetFields", true)
                        put("withBirdwatchNotes", true)
                        put("withVoice", true)
                        cursor?.let { put("cursor", it) }
                    },
                    // With Grok annotations on, TweetDetail has been seen to leave out replies.
                    "features" to JsonObject(XFeatures.TIMELINE + ("responsive_web_grok_annotations_enabled" to JsonPrimitive(false))).toString(),
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
            ),
        )
    }

    /** Who reposted a post, newest first. Read only: this never reposts. */
    suspend fun reposters(postId: String, cursor: String? = null, count: Int = 20): XUserPage = parsing {
        val body = transport.graphql(
            XEndpoints.RETWEETERS,
            mapOf(
                "variables" to json {
                    put("tweetId", postId)
                    put("count", count)
                    put("includePromotedContent", false)
                    cursor?.let { put("cursor", it) }
                },
                "features" to XFeatures.TIMELINE.toString(),
            ),
        )
        val instructions = body["data"]["retweeters_timeline"]["timeline"]["instructions"]
        if (!instructions.exists) XParser.error(body)?.let { throw it }
        XParser.users(instructions)
    }

    /** The posts quoting [postId], as XTA's quotes tab reads them: a search, one row per quote. */
    suspend fun quotes(postId: String, cursor: String? = null, order: XQuoteOrder = XQuoteOrder.LATEST): XTimelinePage =
        search("quoted_tweet_id:$postId", cursor, product = order.product)

    /**
     * [postId]'s text in [language] (a tag such as `de` or `pt-BR`), as X's
     * Grok translation writes it. A translation still being written is asked
     * for again, twice, a second apart. Read only: the POST asks, it changes
     * nothing on X.
     */
    suspend fun translate(postId: String, language: String): XTranslation = parsing {
        val body = buildJsonObject { put("id", postId); put("dst_lang", language); put("content_type", "POST") }.toString()
        repeat(TRANSLATION_TRIES) { attempt ->
            if (attempt > 0) delay(translationWaitMillis)
            val response = transport.post(TRANSLATION, body, "text/plain;charset=UTF-8")
            val answer = J.parse(response.body)
            // Anything but a JSON object is Grok still writing, as XTA reads it.
            if (answer.raw is JsonObject) return@parsing translationOf(response.code, answer)
        }
        throw XTranslationException(XTranslationException.Kind.NOT_READY, "Translation is not ready yet, retry later.")
    }

    private fun translationOf(code: Int, answer: J): XTranslation {
        val error = answer["error"]
        val failure = when {
            code == 200 && error.exists -> XTranslationException.Kind.OTHER to (error["message"].string ?: error["code"].string ?: error["code"].long?.toString() ?: "")
            code == 200 -> return XParser.translation(answer) ?: throw XTranslationException(XTranslationException.Kind.FAILED, "X sent no translation")
            code == 400 && error.string?.matches(UNSUPPORTED) == true -> XTranslationException.Kind.UNSUPPORTED_LANGUAGE to "Language ${error.string}"
            code == 400 -> XTranslationException.Kind.OTHER to error.string.orEmpty()
            code == 403 -> XTranslationException.Kind.BANNED to "Banned from translation API"
            code == 429 -> XTranslationException.Kind.TOO_MANY_REQUESTS to "Sending too many frequent translation requests"
            code == 500 -> XTranslationException.Kind.FAILED to "The translation API failed to translate the post"
            else -> XTranslationException.Kind.OTHER to "X answered translation with $code"
        }
        throw XTranslationException(failure.first, failure.second)
    }

    // ---- search ----

    /** Posts for a search: [product] is `Latest`, `Top` or `Media` (a thread per post of the media grid). */
    suspend fun search(query: String, cursor: String? = null, count: Int = 20, product: String = "Latest"): XTimelinePage =
        parsing {
            val body = transport.graphql(
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
                    "features" to XFeatures.TIMELINE.toString(),
                ),
            )
            // An error must not read as "nothing found", as XTA learnt on its quotes tab.
            if (!body["data"]["search_by_raw_query"]["search_timeline"].exists) XParser.error(body)?.let { throw it }
            XParser.timeline(body)
        }

    /** Accounts for a search (people search), under SearchTimeline's own registry key. */
    suspend fun searchUsers(query: String, cursor: String? = null, count: Int = 25): XUserPage = parsing {
        val body = transport.graphql(
            XEndpoints.SEARCH_USERS,
            mapOf(
                "variables" to json {
                    put("rawQuery", query)
                    put("count", count.toString())
                    put("querySource", "typed_query")
                    put("product", "People")
                    put("withDownvotePerspective", false)
                    put("withReactionsMetadata", false)
                    put("withReactionsPerspective", false)
                    cursor?.let { put("cursor", it) }
                },
                "features" to XFeatures.TIMELINE.toString(),
            ),
        )
        XParser.users(body["data"]["search_by_raw_query"]["search_timeline"]["timeline"]["instructions"])
    }

    // ---- lists ----

    /** An X list's name and size, as XTA reads one before importing its members. */
    suspend fun listInfo(listId: String): XListInfo = parsing {
        val list = transport.graphql(
            XEndpoints.LIST_BY_REST_ID,
            mapOf("variables" to json { put("listId", listId) }, "features" to XFeatures.LIST_BY_REST_ID.toString()),
        )["data"]["list"]
        XListInfo(listId, list["name"].string, list["member_count"].int)
    }

    /** One page of an X list's members. */
    suspend fun listMembers(listId: String, cursor: String? = null, count: Int = 20): XUserPage = parsing {
        val body = transport.graphql(
            XEndpoints.LIST_MEMBERS,
            mapOf(
                "variables" to json { put("listId", listId); put("count", count); cursor?.let { put("cursor", it) } },
                "features" to XFeatures.TIMELINE.toString(),
            ),
        )
        XParser.users(body["data"]["list"]["members_timeline"]["timeline"]["instructions"])
    }

    // ---- communities ----

    /** A community's header and rules. A guest may read it. */
    suspend fun community(id: String): XCommunity = parsing {
        val body = transport.graphql(
            XEndpoints.COMMUNITY,
            mapOf("variables" to json { put("communityId", id) }, "features" to XFeatures.COMMUNITY.toString()),
        )
        XCommunityParser.header(body) ?: throw XCommunityUnavailableException(id)
    }

    /** A community's Top tab, as the web opens it, its pinned post marked. */
    suspend fun communityPosts(id: String, cursor: String? = null): XTimelinePage = parsing {
        XCommunityParser.posts(
            transport.graphql(
                XEndpoints.COMMUNITY_POSTS,
                mapOf(
                    "variables" to json {
                        put("communityId", id)
                        put("count", 20)
                        cursor?.let { put("cursor", it) }
                        put("displayLocation", "Community")
                        put("rankingMode", "Relevance")
                        put("withCommunity", true)
                    },
                    "features" to XFeatures.TIMELINE.toString(),
                ),
            ),
        )
    }

    /** One page of a community's members. The web sends no features and a null first cursor; so does this. */
    suspend fun communityMembers(id: String, cursor: String? = null): XUserPage = parsing {
        XCommunityParser.members(transport.graphql(XEndpoints.COMMUNITY_MEMBERS, mapOf("variables" to json { put("communityId", id); put("cursor", cursor) })))
    }

    /** One page of the communities matching [query]. */
    suspend fun searchCommunities(query: String, cursor: String? = null): XCommunityPage = parsing {
        XCommunityParser.search(transport.graphql(XEndpoints.COMMUNITIES_SEARCH, mapOf("variables" to json { put("query", query); put("cursor", cursor) })))
    }

    // ---- trends ----

    /** The places X has trends for, by name; kept two days, as XTA keeps them. */
    suspend fun trendPlaces(): List<XTrendPlace> = remembered("trends.places", TREND_PLACES_MILLIS) {
        parsing { XTrendsParser.places(transport.get(TRENDS_AVAILABLE, "trends/available")) }
    }

    /** The trends of the place [woeid] (1 is the world); kept two minutes, as XTA keeps them. */
    suspend fun trends(woeid: Long): XTrends = remembered("trends.$woeid", TRENDS_MILLIS) {
        parsing { XTrendsParser.trends(transport.get(TRENDS_PLACE.newBuilder().addQueryParameter("id", woeid.toString()).build(), "trends/place")) }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> remembered(key: String, millis: Long, read: suspend () -> T): T {
        kept[key]?.takeIf { clock() - it.first in 0 until millis }?.let { return it.second as T }
        return read().also { kept[key] = clock() to it }
    }

    // ---- live video and Spaces ----

    /** A Space's metadata (AudioSpaceById): its media key, title, state and host. */
    suspend fun audioSpace(spaceId: String): XAudioSpace? = parsing {
        val body = transport.graphql(
            XEndpoints.AUDIO_SPACE,
            mapOf(
                "variables" to json {
                    put("id", spaceId)
                    put("isMetatagsQuery", true)
                    put("withReplays", true)
                    put("withListeners", true)
                },
                "features" to XFeatures.AUDIO_SPACE.toString(),
            ),
        )
        XLiveParser.audioSpace(body, spaceId)
    }

    /** A broadcast's media key, from `broadcasts/show.json`. */
    suspend fun broadcastMediaKey(broadcastId: String): String? = parsing {
        XLiveParser.broadcastMediaKey(transport.get(BROADCASTS_SHOW.newBuilder().addQueryParameter("ids", broadcastId).build(), "broadcasts/show"), broadcastId)
    }

    /** The HLS playlist of the stream [mediaKey] names (`live_video_stream/status`). */
    suspend fun liveStreamUrl(mediaKey: String): String? = parsing {
        XLiveParser.playbackUrl(transport.get(LIVE_STREAM.newBuilder().addPathSegment(mediaKey).build(), "live_video_stream/status"))
    }

    /**
     * The HLS playlist of [live], as XTA's livePlaybackUrl finds it: the
     * card's media key, else one asked for the broadcast or the Space, else
     * the broadcast's own `28_` key. Null when X gives none, or fails.
     */
    suspend fun livePlaybackUrl(live: XLive): String? = try {
        val broadcast = live.broadcastId?.takeIf { it.isNotEmpty() }
        val key = live.mediaKey?.takeIf { it.isNotEmpty() }
            ?: broadcast?.let { broadcastMediaKey(it) }?.takeIf { it.isNotEmpty() }
            ?: live.spaceId?.takeIf { it.isNotEmpty() }?.let { audioSpace(it)?.mediaKey }?.takeIf { it.isNotEmpty() }
            ?: broadcast?.let { "28_$it" }
        key?.let { liveStreamUrl(it) }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    // ---- query ids ----

    /**
     * Repairs rotated query ids from XTA's published `endpoints.json`, or the
     * registry the reader set ([url], https only, as XTA requires). A failure
     * leaves the ids as they were. Returns the file applied, for a store to
     * keep and apply again at the next start before any request
     * ([XEndpoints.applyOverrides]), as XTA does; null when none was.
     */
    suspend fun refreshEndpoints(url: String = XEndpoints.OVERRIDES_URL): String? {
        val registry = url.trim().toHttpUrlOrNull()?.takeIf { it.isHttps } ?: return null
        return try {
            val response = http.get(registry, emptyMap(), followRedirects = true)
            response.body.takeIf { response.isSuccessful }?.also { endpoints.applyOverrides(J.parse(it)) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    /** X's answers are parsed away from the caller's thread, which may be the UI's. */
    private suspend fun <T> parsing(block: suspend () -> T): T = withContext(Dispatchers.Default) { block() }

    companion object {
        /** What every signed-in request carries before the account's own headers (XTA's headers.dart). */
        val BASE_HEADERS: Map<String, String> get() = XTransport.BASE_HEADERS

        private val TRENDS_AVAILABLE = "https://api.twitter.com/1.1/trends/available.json".toHttpUrl()
        private val TRENDS_PLACE = "https://api.twitter.com/1.1/trends/place.json".toHttpUrl()
        private val BROADCASTS_SHOW = "https://twitter.com/i/api/1.1/broadcasts/show.json".toHttpUrl()
        private val LIVE_STREAM = "https://twitter.com/i/api/1.1/live_video_stream/status".toHttpUrl()
        private val TRANSLATION = "https://api.x.com/2/grok/translation.json".toHttpUrl()
        private val UNSUPPORTED = Regex("""^\w+ is not supported$""")
        private const val TRANSLATION_TRIES = 3
        private const val TREND_PLACES_MILLIS = 2 * 24 * 60 * 60 * 1000L
        private const val TRENDS_MILLIS = 2 * 60 * 1000L

        private fun json(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) = buildJsonObject(block).toString()
    }
}
