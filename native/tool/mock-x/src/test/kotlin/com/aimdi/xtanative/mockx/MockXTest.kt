package com.aimdi.xtanative.mockx

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The stand-in's answers, as XTA's mock_x_test.dart held its Dart original to them. */
class MockXTest {
    private val mock = MockX()

    private fun graphql(operation: String, variables: String, host: String = "x.com", prefix: String = "/i/api") =
        XRequest.of("/$host$prefix/graphql/someId/$operation", "variables=" + URLEncoder.encode(variables, Charsets.UTF_8))

    @Test
    fun aRequestToTheStandInIsReadAsTheXAddressItStandsFor() {
        val request = XRequest.of("/x.com/i/api/graphql/someId/UserByScreenName", "variables=%7B%7D&features=%7B%7D")
        assertEquals("/i/api/graphql/someId/UserByScreenName", request.path)
        assertEquals("https://x.com/i/api/graphql/someId/UserByScreenName?variables=%7B%7D&features=%7B%7D", request.shown)
        assertEquals(XRequest("/", null, "/"), XRequest.of("/", null))
    }

    @Test
    fun answersWhatItHoldsAndWhatXSaysAboutTheRest() {
        val known = mock.answer("GET", graphql("UserByScreenName", """{"screen_name":"Quax_Tests_2"}"""))
        assertEquals("UserByScreenName/quax_tests_2.json", known.fixture)
        assertEquals(200, known.status)
        val unknown = mock.answer("GET", graphql("UserByScreenName", """{"screen_name":"nobody_by_this_name"}"""))
        assertEquals(MockAnswer(200, """{"data":{}}"""), unknown)
        assertEquals(
            """{"data":{"threaded_conversation_with_injections_v2":{"instructions":[]}}}""",
            mock.answer("GET", graphql("TweetDetail", """{"focalTweetId":"1"}""")).body,
        )
        assertEquals(
            """{"data":{"user":{"result":{"__typename":"User","timeline":{"timeline":{"instructions":[]}}}}}}""",
            mock.answer("GET", graphql("UserRepostsTimeline", """{"userId":"1","cursor":"DAAH"}""")).body,
        )
    }

    @Test
    fun everyRouteAnswersWithItsRecording() {
        MockX.ROUTES.forEach { (operation, route) ->
            route.fixtures.forEach { (value, path) ->
                val variables = if (route.key == null) "{}" else """{"${route.key}":"$value"}"""
                val answer = mock.answer("GET", graphql(operation, variables, host = "api.x.com", prefix = ""))
                assertEquals(path, answer.fixture, operation)
                val recorded = Json.parseToJsonElement(MockX::class.java.getResource("/fixtures/$path")!!.readText())
                assertEquals(recorded, Json.parseToJsonElement(answer.body), "$operation answers $path as recorded")
            }
        }
    }

    @Test
    fun endsAListOnThePageAfterTheRecordedOne() {
        val first = mock.answer("GET", graphql("UserOriginalsTimeline", """{"userId":"1"}"""))
        assertContains(first.body, "\"entryId\"")
        val next = mock.answer("GET", graphql("UserOriginalsTimeline", """{"userId":"1","cursor":"DAAH"}"""))
        assertEquals("UserOriginalsTimeline/page.json", next.fixture)
        assertFalse("\"entryId\"" in next.body)
        assertContains(next.body, "\"entries\":[]")
        assertContains(mock.answer("GET", graphql("UserOriginalsTimeline", """{"userId":"1","cursor":null}""")).body, "\"entryId\"")
    }

    @Test
    fun saysWhatItHasInsteadOfGuessing() {
        val other = mock.answer("GET", graphql("HomeTimeline", "{}"))
        assertEquals(501, other.status)
        assertNull(other.fixture)
        assertTrue(other.body.startsWith("The stand-in for X has no answer for GET https://x.com/i/api/graphql/someId/HomeTimeline?variables="))
        assertTrue(
            other.body.endsWith(
                "It answers UserByScreenName, UserOriginalsTimeline, UserTweetsAndReplies, UserRepostsTimeline, TweetDetail, " +
                    "CommunityQuery, CommunityTweetsTimeline, membersSliceTimeline_Query, CommunitiesSearchQuery, the guest token.",
            ),
        )
        val elsewhere = mock.answer("GET", graphql("CommunityTweetsTimeline", """{"communityId":"2"}"""))
        assertEquals(MockAnswer(501, "No CommunityTweetsTimeline recorded for communityId \"2\": only 1472249315724771329."), elsewhere)
        assertEquals(501, mock.answer("GET", XRequest.of("/", null)).status)
        assertEquals(501, mock.answer("GET", XRequest.of("/x.com/i/api/graphql/UserByScreenName", null)).status)
    }

    @Test
    fun variablesThatAreNotAnObjectNameNothing() {
        fun asked(query: String?) = mock.answer("GET", XRequest.of("/x.com/i/api/graphql/someId/UserByScreenName", query))
        listOf(null, "", "variables=", "variables=%5B1%5D", "variables=not+json", "features=%7B%7D").forEach {
            assertEquals("""{"data":{}}""", asked(it).body, "$it")
        }
        assertEquals("UserByScreenName/ok.json", asked("variables=%7B%22screen_name%22%3A%22nobody%22%7D&variables=%7B%22screen_name%22%3A%22X%22%7D").fixture)
        assertEquals("UserByScreenName/ok.json", asked("variables={\"screen_name\":+\"x\"}").fixture)
    }

    @Test
    fun handsOutAGuestToken() {
        assertEquals(MockAnswer(200, """{"guest_token":"1"}"""), mock.answer("POST", XRequest.of("/api.x.com/1.1/guest/activate.json", null)))
        assertEquals(501, mock.answer("GET", XRequest.of("/api.x.com/1.1/guest/activate.json", null)).status)
    }

    @Test
    fun servesOverLoopbackAsJsonOrAsTextSayingWhatIsMissing() {
        val seen = mutableListOf<Pair<String, MockAnswer>>()
        val server = serveMockX(mock, port = 0) { method, request, answer -> synchronized(seen) { seen += "$method ${request.path}" to answer } }
        try {
            fun open(path: String, method: String = "GET") =
                (URI("http://127.0.0.1:${server.address.port}$path").toURL().openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    if (method == "POST") {
                        doOutput = true
                        outputStream.use { it.write("kept".toByteArray()) }
                    }
                }
            val profile = open("/x.com/i/api/graphql/someId/UserByScreenName?variables=%7B%22screen_name%22%3A%22quax_tests%22%7D")
            assertEquals(200, profile.responseCode)
            assertEquals("application/json; charset=utf-8", profile.contentType)
            val user = Json.parseToJsonElement(profile.inputStream.readBytes().decodeToString()).jsonObject
            assertTrue("data" in user)
            val token = open("/api.x.com/1.1/guest/activate.json", "POST")
            assertEquals("""{"guest_token":"1"}""", token.inputStream.readBytes().decodeToString())
            val root = open("/")
            assertEquals(501, root.responseCode)
            assertEquals("text/plain; charset=utf-8", root.contentType)
            assertContains(root.errorStream.readBytes().decodeToString(), "has no answer for GET /.")
            assertEquals(
                listOf("GET /i/api/graphql/someId/UserByScreenName" to 200, "POST /1.1/guest/activate.json" to 200, "GET /" to 501),
                seen.map { it.first to it.second.status },
            )
        } finally {
            server.stop(0)
        }
    }
}
