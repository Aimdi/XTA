package com.aimdi.xtanative.core.x

import com.aimdi.xtanative.core.json.J
import okhttp3.HttpUrl

/** One GraphQL operation: its name in the path, the query id shipped, and the host it was captured from. */
data class XEndpoint(val name: String, val operation: String, val queryId: String, val host: String)

/**
 * The GraphQL operations XTA Native calls, with the query ids of XTA's
 * lib/client/endpoints.dart. X rotates ids without notice; the `endpoints.json`
 * XTA publishes repairs them for both apps without a release.
 */
class XEndpoints {
    @Volatile
    private var overrides: Map<String, String> = emptyMap()

    fun queryId(name: String): String = overrides[name] ?: shipped.getValue(name).queryId

    fun url(name: String, params: Map<String, String>): HttpUrl {
        val endpoint = shipped.getValue(name)
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

    companion object {
        const val USER_BY_SCREEN_NAME = "UserByScreenName"
        const val USER_ORIGINALS = "UserOriginalsTimeline"
        const val TWEET_DETAIL = "TweetDetail"
        const val SEARCH = "SearchTimeline"
        const val USER_REPOSTS = "UserRepostsTimeline"
        const val USER_REPLIES = "UserTweetsAndReplies"
        const val USER_MEDIA = "UserMedia"

        const val OVERRIDES_URL = "https://raw.githubusercontent.com/Aimdi/XTA/HEAD/endpoints.json"

        private val queryIdPattern = Regex("^[A-Za-z0-9_-]{16,32}$")

        val shipped: Map<String, XEndpoint> = listOf(
            XEndpoint(USER_BY_SCREEN_NAME, "UserByScreenName", "AMIBMjtxEEATh4z8V9GtRg", "twitter.com"),
            XEndpoint(USER_ORIGINALS, "UserOriginalsTimeline", "qtvmQffnepvr0oPe4A8MqQ", "x.com"),
            XEndpoint(TWEET_DETAIL, "TweetDetail", "wjV7NuWEsHgTBFfW3zYKzQ", "x.com"),
            XEndpoint(SEARCH, "SearchTimeline", "BYppgupFFS2yqVmmRNjlsw", "x.com"),
            // Reposts left the Posts timeline when X split them out.
            XEndpoint(USER_REPOSTS, "UserRepostsTimeline", "CnNg1YiKG7bRgi-RoIfKsw", "x.com"),
            XEndpoint(USER_REPLIES, "UserTweetsAndReplies", "M9Tz6NGTHeRzqHOPdj9I7g", "x.com"),
            XEndpoint(USER_MEDIA, "UserMedia", "9EovraBTXJYGSEQXZqlLmQ", "x.com"),
        ).associateBy { it.name }
    }
}
