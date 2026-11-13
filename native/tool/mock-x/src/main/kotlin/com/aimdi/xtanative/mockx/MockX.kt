package com.aimdi.xtanative.mockx

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLDecoder

/** What the stand-in for X sends back for one request; [fixture] is the recorded file [body] came from, null when the stand-in wrote it itself. */
data class MockAnswer(val status: Int, val body: String, val fixture: String? = null) {
    val answered: Boolean get() = status == 200
}

/**
 * The recorded answers of one X operation, picked by the variable [key] that
 * names the account, post or community (null when one fixture answers every
 * request). [fixtures] are paths under fixtures/ by the lowercased value of
 * [key], or under `*` for any value. [missing] is X's answer for something
 * that does not exist, sent for a value without a fixture; without one, such
 * a request is not answered.
 */
data class MockRoute(val fixtures: Map<String, String>, val key: String? = null, val missing: String? = null)

/**
 * A request as the stand-in reads it: the X address the app meant, taken back
 * from `<server>/<host>/<path>` (:ui's data/MockX.kt sends it so). [shown] is
 * that address whole, for the log.
 */
data class XRequest(val path: String, val query: String?, val shown: String) {
    companion object {
        fun of(rawPath: String, rawQuery: String?): XRequest {
            val segments = rawPath.split('/').filter { it.isNotEmpty() }
            val asked = if (rawQuery == null) "" else "?$rawQuery"
            if (segments.isEmpty()) return XRequest(rawPath, rawQuery, rawPath + asked)
            val path = "/" + segments.drop(1).joinToString("/")
            return XRequest(path, rawQuery, "https://${segments.first()}$path$asked")
        }
    }
}

/**
 * A stand-in for X that answers from recorded responses, which [read] hands
 * it by their path. Anything it was not given an answer for gets a 501 saying
 * what is missing.
 */
class MockX(private val routes: Map<String, MockRoute> = ROUTES, private val read: (String) -> String = ::recorded) {
    fun answer(method: String, request: XRequest): MockAnswer {
        if (method == "POST" && request.path == "/1.1/guest/activate.json") return MockAnswer(200, """{"guest_token":"1"}""")
        val operation = GRAPHQL.matchEntire(request.path)?.groupValues?.get(1)
        val route = routes[operation]
        if (operation == null || route == null) {
            return MockAnswer(501, "The stand-in for X has no answer for $method ${request.shown}. It answers ${known()}.")
        }
        val variables = variablesOf(request.query)
        val value = route.key?.let { shown(variables[it]).lowercase() }
        val path = route.fixtures[value] ?: route.fixtures["*"]
        if (path == null) {
            return route.missing?.let { MockAnswer(200, it) }
                ?: MockAnswer(501, "No $operation recorded for ${route.key} \"$value\": only ${route.fixtures.keys.joinToString(", ")}.")
        }
        val recorded = Json.parseToJsonElement(read(path))
        val laterPage = variables["cursor"].let { it != null && it !is JsonNull }
        return MockAnswer(200, (if (laterPage) endOfList(recorded) else recorded).toString(), fixture = path)
    }

    private fun known() = (routes.keys + "the guest token").joinToString(", ")

    companion object {
        private val GRAPHQL = Regex("""^(?:/i/api)?/graphql/[\w-]+/(\w+)$""")
        private const val FLUTTER_COMMUNITY = "1472249315724771329"
        private const val EMPTY_USER_TIMELINE =
            """{"data":{"user":{"result":{"__typename":"User","timeline":{"timeline":{"instructions":[]}}}}}}"""

        /** Everything the checks open, answered from fixtures/. */
        val ROUTES: Map<String, MockRoute> = linkedMapOf(
            "UserByScreenName" to MockRoute(
                linkedMapOf(
                    "quax_tests" to "UserByScreenName/modern.json",
                    "quax_tests_2" to "UserByScreenName/quax_tests_2.json",
                    "x" to "UserByScreenName/ok.json",
                ),
                key = "screen_name",
                missing = """{"data":{}}""",
            ),
            "UserOriginalsTimeline" to MockRoute(mapOf("*" to "UserOriginalsTimeline/page.json")),
            "UserTweetsAndReplies" to MockRoute(mapOf("*" to "UserOriginalsTimeline/page.json")),
            // The Posts tab reads reposts beside the posts; the recorded accounts repost nothing.
            "UserRepostsTimeline" to MockRoute(emptyMap(), missing = EMPTY_USER_TIMELINE),
            "TweetDetail" to MockRoute(
                mapOf("2105589900078641579" to "TweetDetail/subscriber_preview.json"),
                key = "focalTweetId",
                missing = """{"data":{"threaded_conversation_with_injections_v2":{"instructions":[]}}}""",
            ),
            "CommunityQuery" to MockRoute(
                mapOf(FLUTTER_COMMUNITY to "Community/community.json"),
                key = "communityId",
                missing = """{"data":{"communityResults":{}}}""",
            ),
            "CommunityTweetsTimeline" to MockRoute(mapOf(FLUTTER_COMMUNITY to "Community/timeline.json"), key = "communityId"),
            "membersSliceTimeline_Query" to MockRoute(mapOf(FLUTTER_COMMUNITY to "Community/members.json"), key = "communityId"),
            "CommunitiesSearchQuery" to MockRoute(mapOf("*" to "Community/search.json")),
        )

        /** The lists a later page of a timeline fills; the page after the last recorded one comes back with them empty, which ends the list. */
        private val PAGED_LISTS = setOf("entries", "moduleItems", "items_results")

        private fun recorded(path: String): String =
            checkNotNull(MockX::class.java.getResourceAsStream("/fixtures/$path")) { "no fixture $path" }.use { it.readBytes().decodeToString() }

        /** The `variables` of a query, the last one where it is given twice; nothing when it is not a JSON object. */
        private fun variablesOf(query: String?): Map<String, JsonElement> {
            val raw = query.orEmpty().split('&').lastOrNull { it.substringBefore('=') == "variables" } ?: return emptyMap()
            return runCatching {
                Json.parseToJsonElement(URLDecoder.decode(raw.substringAfter('=', ""), Charsets.UTF_8)) as? JsonObject
            }.getOrNull().orEmpty()
        }

        private fun shown(value: JsonElement?): String = when (value) {
            null, JsonNull -> ""
            is JsonPrimitive -> value.content
            else -> value.toString()
        }

        private fun endOfList(json: JsonElement): JsonElement = when (json) {
            is JsonObject -> JsonObject(json.mapValues { (key, value) -> if (key in PAGED_LISTS) JsonArray(emptyList()) else endOfList(value) })
            is JsonArray -> JsonArray(json.map(::endOfList))
            else -> json
        }
    }
}
