package com.aimdi.xtanative.core.x

import com.aimdi.xtanative.core.json.J
import okhttp3.HttpUrl

/**
 * One GraphQL operation: its registry [name], its [operation] in the path, the
 * query id shipped, and the host it was captured from. Two names may share an
 * operation (people search and post search both ask SearchTimeline), so an
 * override can repair one without touching the other.
 */
data class XEndpoint(val name: String, val operation: String, val queryId: String, val host: String)

/**
 * The GraphQL operations XTA Native calls, with the query ids of XTA's
 * lib/client/endpoints.dart. X rotates ids without notice; the `endpoints.json`
 * XTA publishes repairs them for both apps without a release.
 */
class XEndpoints {
    @Volatile
    private var overrides: Map<String, String> = emptyMap()

    /** The query ids currently replacing shipped ones: empty until a published file is applied. */
    val overridden: Map<String, String> get() = overrides

    fun queryId(name: String): String = overrides[name] ?: endpoint(name).queryId

    fun isOverridden(name: String): Boolean = name in overrides

    fun url(name: String, params: Map<String, String>): HttpUrl {
        val endpoint = endpoint(name)
        return HttpUrl.Builder()
            .scheme("https")
            .host(endpoint.host)
            .addPathSegments("i/api/graphql/${queryId(name)}/${endpoint.operation}")
            .apply { params.forEach { (key, value) -> addQueryParameter(key, value) } }
            .build()
    }

    /**
     * Takes the ids of a published `endpoints.json`. Only operations this build
     * knows, and only ids shaped like one, are accepted: the file cannot add
     * endpoints or reach another path. Returns how many were taken.
     */
    fun applyOverrides(body: J): Int {
        val accepted = body["endpoints"].keys.mapNotNull { name ->
            val id = body["endpoints"][name].string
            if (name in shipped && id != null && queryIdPattern.matches(id)) name to id else null
        }.toMap()
        overrides = accepted
        return accepted.size
    }

    /** Back to the shipped ids. */
    fun clearOverrides() {
        overrides = emptyMap()
    }

    companion object {
        const val USER_BY_REST_ID = "UserByRestId"
        const val USER_BY_SCREEN_NAME = "UserByScreenName"
        const val FOLLOWING = "Following"
        const val FOLLOWERS = "Followers"
        const val RETWEETERS = "Retweeters"
        const val LIST_BY_REST_ID = "ListByRestId"
        const val LIST_MEMBERS = "ListMembers"
        const val USER_ORIGINALS = "UserOriginalsTimeline"
        const val TWEET_DETAIL = "TweetDetail"
        const val SEARCH = "SearchTimeline"

        /** People search: SearchTimeline under a key of its own, on twitter.com. */
        const val SEARCH_USERS = "SearchTimelineUsers"
        const val HOME_TIMELINE = "HomeTimeline"
        const val USER_REPOSTS = "UserRepostsTimeline"
        const val USER_REPLIES = "UserTweetsAndReplies"
        const val USER_MEDIA = "UserMedia"
        const val AUDIO_SPACE = "AudioSpaceById"
        const val COMMUNITY = "CommunityQuery"
        const val COMMUNITY_POSTS = "CommunityTweetsTimeline"
        const val COMMUNITY_MEMBERS = "membersSliceTimeline_Query"
        const val COMMUNITIES_SEARCH = "CommunitiesSearchQuery"

        const val OVERRIDES_URL = "https://raw.githubusercontent.com/Aimdi/XTA/HEAD/endpoints.json"

        private val queryIdPattern = Regex("^[A-Za-z0-9_-]{16,32}$")

        /** Whether [id] is shaped like a query id: what an override must be. */
        fun isQueryId(id: String): Boolean = queryIdPattern.matches(id)

        val shipped: Map<String, XEndpoint> = listOf(
            XEndpoint(USER_BY_REST_ID, "UserByRestId", "XIpMDIi_YoVzXeoON-cfAQ", "twitter.com"),
            XEndpoint(USER_BY_SCREEN_NAME, "UserByScreenName", "AMIBMjtxEEATh4z8V9GtRg", "twitter.com"),
            XEndpoint(FOLLOWING, "Following", "9l9tzqLGQU9eutAJffqqMw", "x.com"),
            XEndpoint(FOLLOWERS, "Followers", "sO2htFYgDpQopDNVjDcf1Q", "x.com"),
            XEndpoint(RETWEETERS, "Retweeters", "ROjiuYueotTnWoI8m2YaiQ", "x.com"),
            XEndpoint(LIST_BY_REST_ID, "ListByRestId", "I1h1FzuuiD__nNvG466mKQ", "x.com"),
            XEndpoint(LIST_MEMBERS, "ListMembers", "oIetCo19avgStX4mOnGsPg", "x.com"),
            XEndpoint(USER_ORIGINALS, "UserOriginalsTimeline", "qtvmQffnepvr0oPe4A8MqQ", "x.com"),
            XEndpoint(TWEET_DETAIL, "TweetDetail", "wjV7NuWEsHgTBFfW3zYKzQ", "x.com"),
            XEndpoint(SEARCH, "SearchTimeline", "BYppgupFFS2yqVmmRNjlsw", "x.com"),
            XEndpoint(SEARCH_USERS, "SearchTimeline", "BYppgupFFS2yqVmmRNjlsw", "twitter.com"),
            XEndpoint(HOME_TIMELINE, "HomeTimeline", "-SBRQaLiRjWvTFD5SI403A", "twitter.com"),
            // Reposts left the Posts timeline when X split them out.
            XEndpoint(USER_REPOSTS, "UserRepostsTimeline", "CnNg1YiKG7bRgi-RoIfKsw", "x.com"),
            XEndpoint(USER_REPLIES, "UserTweetsAndReplies", "M9Tz6NGTHeRzqHOPdj9I7g", "x.com"),
            XEndpoint(USER_MEDIA, "UserMedia", "9EovraBTXJYGSEQXZqlLmQ", "x.com"),
            XEndpoint(AUDIO_SPACE, "AudioSpaceById", "Bh0L6azTQoMs9rJKeCF4wQ", "x.com"),
            // A community's header; a guest may read it. The three below need an account.
            XEndpoint(COMMUNITY, "CommunityQuery", "-ElI1vg3dYbttVMhBhGdLw", "x.com"),
            XEndpoint(COMMUNITY_POSTS, "CommunityTweetsTimeline", "wXey4HQTUdYUlWWXltWMpg", "x.com"),
            XEndpoint(COMMUNITY_MEMBERS, "membersSliceTimeline_Query", "woAp_YdzAdqnWDrqLTNpAw", "x.com"),
            XEndpoint(COMMUNITIES_SEARCH, "CommunitiesSearchQuery", "HKgnJbw10TZW41Na4ToaSw", "x.com"),
        ).associateBy { it.name }

        /** [name]'s endpoint; an unknown name is a mistake in this build, not something X did. */
        fun endpoint(name: String): XEndpoint = shipped[name] ?: throw IllegalArgumentException("Unknown X endpoint \"$name\"")
    }
}
