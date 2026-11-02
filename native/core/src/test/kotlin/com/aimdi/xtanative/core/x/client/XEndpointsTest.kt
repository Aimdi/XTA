package com.aimdi.xtanative.core.x.client

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.XEndpoints
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** XTA's endpoints_test.dart and retweeters_parser_test.dart's registry checks. */
class XEndpointsTest {
    /** Every operation XTA's lib/client/endpoints.dart ships, with its id and host. */
    private val xta = mapOf(
        "UserByRestId" to ("XIpMDIi_YoVzXeoON-cfAQ" to "twitter.com"),
        "UserByScreenName" to ("AMIBMjtxEEATh4z8V9GtRg" to "twitter.com"),
        "Following" to ("9l9tzqLGQU9eutAJffqqMw" to "x.com"),
        "Followers" to ("sO2htFYgDpQopDNVjDcf1Q" to "x.com"),
        "Retweeters" to ("ROjiuYueotTnWoI8m2YaiQ" to "x.com"),
        "ListByRestId" to ("I1h1FzuuiD__nNvG466mKQ" to "x.com"),
        "ListMembers" to ("oIetCo19avgStX4mOnGsPg" to "x.com"),
        "TweetDetail" to ("wjV7NuWEsHgTBFfW3zYKzQ" to "x.com"),
        "SearchTimeline" to ("BYppgupFFS2yqVmmRNjlsw" to "x.com"),
        "SearchTimelineUsers" to ("BYppgupFFS2yqVmmRNjlsw" to "twitter.com"),
        "HomeTimeline" to ("-SBRQaLiRjWvTFD5SI403A" to "twitter.com"),
        "UserMedia" to ("9EovraBTXJYGSEQXZqlLmQ" to "x.com"),
        "UserTweetsAndReplies" to ("M9Tz6NGTHeRzqHOPdj9I7g" to "x.com"),
        "UserOriginalsTimeline" to ("qtvmQffnepvr0oPe4A8MqQ" to "x.com"),
        "UserRepostsTimeline" to ("CnNg1YiKG7bRgi-RoIfKsw" to "x.com"),
        "AudioSpaceById" to ("Bh0L6azTQoMs9rJKeCF4wQ" to "x.com"),
        "CommunityQuery" to ("-ElI1vg3dYbttVMhBhGdLw" to "x.com"),
        "CommunityTweetsTimeline" to ("wXey4HQTUdYUlWWXltWMpg" to "x.com"),
        "membersSliceTimeline_Query" to ("woAp_YdzAdqnWDrqLTNpAw" to "x.com"),
        "CommunitiesSearchQuery" to ("HKgnJbw10TZW41Na4ToaSw" to "x.com"),
    )

    @Test
    fun everyOperationXtaCallsIsShippedWithItsIdAndHost() {
        assertEquals(xta, XEndpoints.shipped.mapValues { (_, endpoint) -> endpoint.queryId to endpoint.host })
    }

    @Test
    fun everyShippedEndpointIsKeyedByItsOwnNameAndHasAnIdShapedId() {
        XEndpoints.shipped.forEach { (key, endpoint) ->
            assertEquals(key, endpoint.name)
            assertTrue(XEndpoints.isQueryId(endpoint.queryId), "${endpoint.name} has a malformed query id")
        }
    }

    @Test
    fun aUrlIsTheOperationOnItsHostWithTheParameters() {
        val url = XEndpoints().url(XEndpoints.USER_ORIGINALS, mapOf("variables" to "{}"))
        assertEquals("x.com", url.host)
        assertEquals("/i/api/graphql/qtvmQffnepvr0oPe4A8MqQ/UserOriginalsTimeline", url.encodedPath)
        assertEquals("{}", url.queryParameter("variables"))
        assertFalse("UserTweets" in XEndpoints.shipped, "the Posts tab moved to UserOriginalsTimeline")
        assertEquals("/i/api/graphql/CnNg1YiKG7bRgi-RoIfKsw/UserRepostsTimeline", XEndpoints().url(XEndpoints.USER_REPOSTS, emptyMap()).encodedPath)
        assertEquals("/i/api/graphql/9EovraBTXJYGSEQXZqlLmQ/UserMedia", XEndpoints().url(XEndpoints.USER_MEDIA, emptyMap()).encodedPath)
        assertEquals("/i/api/graphql/ROjiuYueotTnWoI8m2YaiQ/Retweeters", XEndpoints().url(XEndpoints.RETWEETERS, emptyMap()).encodedPath)
        assertTrue(XEndpoints().url(XEndpoints.AUDIO_SPACE, emptyMap()).encodedPath.endsWith("/AudioSpaceById"))
    }

    @Test
    fun theTwoSearchTimelineReadsKeepTheirOwnHostsAndKeys() {
        val endpoints = XEndpoints()
        val posts = endpoints.url(XEndpoints.SEARCH, emptyMap())
        val people = endpoints.url(XEndpoints.SEARCH_USERS, emptyMap())
        assertEquals("x.com", posts.host)
        assertEquals("twitter.com", people.host)
        assertTrue(posts.encodedPath.endsWith("/SearchTimeline") && people.encodedPath.endsWith("/SearchTimeline"))
        assertNotEquals(XEndpoints.SEARCH, XEndpoints.SEARCH_USERS, "an override can repair people search without touching post search")
        endpoints.applyOverrides(J.parse("""{"endpoints": {"SearchTimelineUsers": "PEOPLEPEOPLEPEOPLE01"}}"""))
        assertEquals("BYppgupFFS2yqVmmRNjlsw", endpoints.queryId(XEndpoints.SEARCH))
        assertEquals("PEOPLEPEOPLEPEOPLE01", endpoints.queryId(XEndpoints.SEARCH_USERS))
    }

    @Test
    fun anOverrideReplacesTheIdButKeepsOperationAndHost() {
        val endpoints = XEndpoints()
        endpoints.applyOverrides(J.parse("""{"endpoints": {"UserOriginalsTimeline": "AAAAAAAAAAAAAAAAAAAAAA"}}"""))
        val url = endpoints.url(XEndpoints.USER_ORIGINALS, emptyMap())
        assertEquals("/i/api/graphql/AAAAAAAAAAAAAAAAAAAAAA/UserOriginalsTimeline", url.encodedPath)
        assertEquals("x.com", url.host)
        assertTrue(endpoints.isOverridden(XEndpoints.USER_ORIGINALS))
        endpoints.clearOverrides()
        assertEquals("qtvmQffnepvr0oPe4A8MqQ", endpoints.queryId(XEndpoints.USER_ORIGINALS))
    }

    @Test
    fun unknownEndpointsAndMalformedIdsAreRejected() {
        val endpoints = XEndpoints()
        val accepted = endpoints.applyOverrides(
            J.parse(
                """{"endpoints": {"NotAnEndpoint": "AAAAAAAAAAAAAAAAAAAAAA", "UserOriginalsTimeline": "../../../evil",
                    "TweetDetail": "short", "UserMedia": "BBBBBBBBBBBBBBBBBBBBBB", "Following": 42}}""",
            ),
        )
        assertEquals(1, accepted)
        assertEquals(mapOf("UserMedia" to "BBBBBBBBBBBBBBBBBBBBBB"), endpoints.overridden)
        assertEquals("wjV7NuWEsHgTBFfW3zYKzQ", endpoints.queryId(XEndpoints.TWEET_DETAIL))
    }

    @Test
    fun aPathInAnOverrideNeverEscapesTheOperation() {
        val endpoints = XEndpoints()
        endpoints.applyOverrides(J.parse("""{"endpoints": {"UserOriginalsTimeline": "a/b/UserOriginalsTimeline?x=1"}}"""))
        assertEquals("/i/api/graphql/qtvmQffnepvr0oPe4A8MqQ/UserOriginalsTimeline", endpoints.url(XEndpoints.USER_ORIGINALS, emptyMap()).encodedPath)
    }

    @Test
    fun aRegistryOfAnotherShapeAppliesNothing() {
        listOf("{}", "[]", """{"endpoints":"nope"}""", "garbage").forEach { body ->
            assertEquals(0, XEndpoints().applyOverrides(J.parse(body)), body)
        }
    }

    @Test
    fun anUnknownEndpointNameIsAMistakeNotASilent404() {
        assertFailsWith<IllegalArgumentException> { XEndpoints().url("Nope", emptyMap()) }
    }
}
