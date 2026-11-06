package com.aimdi.xtanative.core.x.client

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.core.x.XAnswerException
import com.aimdi.xtanative.core.x.XClient
import com.aimdi.xtanative.core.x.XCommunityUnavailableException
import com.aimdi.xtanative.core.x.XQuoteOrder
import com.aimdi.xtanative.core.x.XReplyRanking
import com.aimdi.xtanative.core.x.XSigners
import com.aimdi.xtanative.core.x.XTranslationException
import com.aimdi.xtanative.core.x.XUserUnavailableException
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.core.x.model.XLive
import com.aimdi.xtanative.core.x.model.XTrendPlace
import com.aimdi.xtanative.core.x.signing.TransactionSigner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl

/** Each read XTA makes of X, as XTA's client.dart sends it, against a stand-in for x.com. */
class XReadsTest {
    private fun fixture(name: String) = javaClass.getResource("/x/$name")!!.readText()

    private class Sent(val method: String, val url: HttpUrl, val headers: Map<String, String>, val body: String? = null) {
        val variables: J get() = J.parse(url.queryParameter("variables").orEmpty())
        val features: J get() = J.parse(url.queryParameter("features").orEmpty())
    }

    /** Answers each request with what [answer] gives for its last path segment. */
    private class FakeX(val answer: (Sent) -> XResponse) : XHttp {
        val sent = ArrayList<Sent>()
        override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse =
            answer(Sent("GET", url, headers).also { sent += it })

        override suspend fun post(url: HttpUrl, headers: Map<String, String>, body: String, contentType: String): XResponse =
            answer(Sent("POST", url, headers + ("content-type" to contentType), body).also { sent += it })
    }

    private val account = XAccount("ct0-a", "a", mapOf("Cookie" to "auth_token=a; ct0=ct0-a", "x-csrf-token" to "ct0-a"))
    private var now = 1_700_000_000_000L

    private fun client(x: XHttp) = XClient(x, { listOf(account) }, XSigners({ TransactionSigner(IntArray(48) { it }, "animation") }), clock = { now })

    private fun answering(body: String) = FakeX { XResponse(200, body) }

    /** The Retweeters fixture's users timeline (`{"timeline": {"instructions": …}}`) under [path]. */
    private fun usersTimeline(vararg path: String): String {
        val timeline = J.parse(fixture("retweeters.json"))["data"]["retweeters_timeline"].raw.toString()
        return path.reversed().fold(timeline) { inner, key -> """{"$key": $inner}""" }
    }

    @Test
    fun aProfileByIdAsksUserByRestIdWithXtasVariables() = runBlocking {
        val x = answering(fixture("user_modern.json"))
        val (user, _) = client(x).profileById("2095909295913103360")
        assertEquals("quax_tests", user.screenName)
        val sent = x.sent.single()
        assertEquals("/i/api/graphql/XIpMDIi_YoVzXeoON-cfAQ/UserByRestId", sent.url.encodedPath)
        assertEquals("twitter.com", sent.url.host)
        assertEquals("2095909295913103360", sent.variables["userId"].string)
        assertEquals(true, sent.variables["withSafetyModeUserFields"].boolean)
        assertEquals(true, sent.features["responsive_web_twitter_blue_verified_badge_is_enabled"].boolean)
    }

    @Test
    fun aSuspendedAccountSaysWhyAndAnErrorIsXsOwn() {
        val suspended = answering("""{"data":{"user":{"result":{"__typename":"UserUnavailable","reason":"Suspended"}}}}""")
        val error = assertFailsWith<XUserUnavailableException> { runBlocking { client(suspended).profile("@gone") } }
        assertEquals("Suspended", error.reason)
        assertEquals("gone", error.screenName)
        assertEquals(404, error.code)

        val answered = answering("""{"errors":[{"code":50,"message":"User not found."}]}""")
        val answer = assertFailsWith<XAnswerException> { runBlocking { client(answered).profile("nobody") } }
        assertEquals(50, answer.errorCode)
        assertEquals("User not found.", answer.message)
    }

    @Test
    fun followsAskTheirOperationsAndPageWithXsCursor() = runBlocking {
        val x = answering(usersTimeline("data", "user", "result", "timeline"))
        val reader = client(x)
        val following = reader.following("42", cursor = "c1", count = 100)
        reader.followers("42")
        assertEquals(listOf("alice", "bob"), following.users.map { it.screenName })
        assertEquals("cursor-next", following.bottomCursor)
        val (asked, followers) = x.sent
        assertEquals("/i/api/graphql/9l9tzqLGQU9eutAJffqqMw/Following", asked.url.encodedPath)
        assertEquals("/i/api/graphql/sO2htFYgDpQopDNVjDcf1Q/Followers", followers.url.encodedPath)
        assertEquals("""{"userId":"42","count":100,"cursor":"c1","includePromotedContent":false,"withGrokTranslatedBio":true}""", asked.url.queryParameter("variables"))
        assertEquals(200L, followers.variables["count"].long, "XTA reads two hundred a page")
        assertTrue(asked.features["view_counts_everywhere_api_enabled"].boolean == true)
    }

    @Test
    fun repostersAreReadFromRetweetersAndAnErrorIsNotAnEmptyList() = runBlocking {
        val x = answering(fixture("retweeters.json"))
        val page = client(x).reposters("2001", cursor = "c")
        assertEquals(listOf("1", "2"), page.users.map { it.id })
        val sent = x.sent.single()
        assertEquals("/i/api/graphql/ROjiuYueotTnWoI8m2YaiQ/Retweeters", sent.url.encodedPath)
        assertEquals("""{"tweetId":"2001","count":20,"includePromotedContent":false,"cursor":"c"}""", sent.url.queryParameter("variables"))

        val refused = answering("""{"errors":[{"message":"Rate limit exceeded","code":88}]}""")
        assertEquals("Rate limit exceeded", assertFailsWith<XAnswerException> { client(refused).reposters("2001") }.message)
    }

    @Test
    fun quotesAreASearchForTheQuotedPostOnePerRow() = runBlocking {
        val x = answering(fixture("user_originals_page.json"))
        val page = client(x).quotes("2095934584533680451", order = XQuoteOrder.TOP)
        val sent = x.sent.single()
        assertEquals("/i/api/graphql/BYppgupFFS2yqVmmRNjlsw/SearchTimeline", sent.url.encodedPath)
        assertEquals("quoted_tweet_id:2095934584533680451", sent.variables["rawQuery"].string)
        assertEquals("Top", sent.variables["product"].string)
        assertEquals("20", sent.variables["count"].string, "SearchTimeline takes its count as a string")
        assertTrue(page.threads.all { it.posts.size == 1 }, "each quote is its own row, never folded by conversation")
    }

    @Test
    fun aSearchXAnswersWithAnErrorIsNotNothingFound() {
        val x = answering("""{"errors":[{"message":"Query: Unspecified","code":214}]}""")
        assertEquals(214, assertFailsWith<XAnswerException> { runBlocking { client(x).search("from:nasa") } }.errorCode)
        assertTrue(runBlocking { client(answering("{}")).search("from:nasa") }.threads.isEmpty(), "without an error, an empty answer is no results")
    }

    @Test
    fun peopleSearchAsksSearchTimelineOnTwitterComForPeople() = runBlocking {
        val x = answering(usersTimeline("data", "search_by_raw_query", "search_timeline"))
        val page = client(x).searchUsers("flutter")
        assertEquals(listOf("alice", "bob"), page.users.map { it.screenName })
        val sent = x.sent.single()
        assertEquals("twitter.com", sent.url.host)
        assertEquals("People", sent.variables["product"].string)
        assertEquals("25", sent.variables["count"].string)
        assertEquals(false, sent.variables["withReactionsPerspective"].boolean)
    }

    @Test
    fun aListIsReadForItsNameAndItsMembers() = runBlocking {
        val info = client(answering("""{"data":{"list":{"name":"Flutter folks","member_count":42}}}""")).listInfo("77")
        assertEquals("Flutter folks", info.name)
        assertEquals(42, info.memberCount)
        assertNull(client(answering("""{"data":{}}""")).listInfo("78").name, "a list that is gone or private has no name")

        val x = answering(usersTimeline("data", "list", "members_timeline"))
        val members = client(x).listMembers("77")
        assertEquals(2, members.users.size)
        assertEquals("/i/api/graphql/oIetCo19avgStX4mOnGsPg/ListMembers", x.sent.single().url.encodedPath)
        assertEquals("""{"listId":"77","count":20}""", x.sent.single().url.queryParameter("variables"))
    }

    @Test
    fun aCommunityIsReadWithXtasVariablesAndFeatures() = runBlocking {
        val x = FakeX { sent ->
            when (sent.url.pathSegments.last()) {
                "CommunityQuery" -> XResponse(200, fixture("community.json"))
                "CommunityTweetsTimeline" -> XResponse(200, fixture("community_timeline_cards.json"))
                "membersSliceTimeline_Query" -> XResponse(200, fixture("community_members.json"))
                else -> XResponse(200, fixture("community_search.json"))
            }
        }
        val reader = client(x)
        assertEquals("Flutter", reader.community("1472249315724771329").name)
        val posts = reader.communityPosts("1472249315724771329", cursor = "c")
        assertEquals(3, reader.communityMembers("1472249315724771329").users.size)
        assertEquals(3, reader.searchCommunities("flutter").communities.size)
        assertTrue(posts.threads.isNotEmpty())

        val (header, timeline, members, search) = x.sent
        assertEquals("""{"communityId":"1472249315724771329"}""", header.url.queryParameter("variables"))
        assertEquals(false, header.features["c9s_superc9s_indication_enabled"].boolean)
        assertEquals(
            """{"communityId":"1472249315724771329","count":20,"cursor":"c","displayLocation":"Community","rankingMode":"Relevance","withCommunity":true}""",
            timeline.url.queryParameter("variables"),
        )
        assertEquals("""{"communityId":"1472249315724771329","cursor":null}""", members.url.queryParameter("variables"), "the web sends a null first cursor")
        assertNull(members.url.queryParameter("features"), "and no features")
        assertEquals("""{"query":"flutter","cursor":null}""", search.url.queryParameter("variables"))
    }

    @Test
    fun aCommunityXDoesNotSendIsUnavailable() {
        val error = assertFailsWith<XCommunityUnavailableException> { runBlocking { client(answering("""{"data":{}}""")).community("9") } }
        assertEquals("9", error.id)
    }

    @Test
    fun trendsAndTheirPlacesAreReadAndKeptAWhileAsXtaKeepsThem() = runBlocking {
        val x = FakeX { sent ->
            when (sent.url.pathSegments.last()) {
                "available.json" -> XResponse(
                    200,
                    """[{"name":"Paris","woeid":615702,"country":"France","countryCode":"FR","parentid":23424819,"placeType":{"code":7,"name":"Town"}},
                        {"name":"Worldwide","woeid":1,"country":"","parentid":0,"placeType":{"code":19,"name":"Supername"}}]""",
                )
                else -> XResponse(
                    200,
                    """[{"trends":[{"name":"#Flutter","url":"http://twitter.com/search?q=%23Flutter","promoted_content":null,"query":"%23Flutter","tweet_volume":12000},
                        {"name":"Dart lang","query":"Dart+lang","tweet_volume":null}],"as_of":"2026-10-30T12:00:00Z","created_at":"2026-10-30T11:55:00Z","locations":[{"name":"Worldwide","woeid":1}]}]""",
                )
            }
        }
        val reader = client(x)
        val places = reader.trendPlaces()
        assertEquals(listOf("Paris", "Worldwide"), places.map { it.name })
        val trends = reader.trends(1)
        assertEquals(listOf("#Flutter", "Dart lang"), trends.trends.map { it.query }, "a trend's query is decoded for search")
        assertEquals(12000L, trends.trends.first().volume)
        assertEquals(listOf(XTrendPlace("Worldwide", 1)), trends.places)

        reader.trends(1)
        reader.trendPlaces()
        assertEquals(2, x.sent.size, "trends are kept two minutes, places two days")
        now += 3 * 60_000
        reader.trends(1)
        assertEquals(3, x.sent.size)

        val (available, place) = x.sent
        assertEquals("https://api.twitter.com/1.1/trends/available.json", available.url.toString())
        assertEquals("https://api.twitter.com/1.1/trends/place.json?id=1", place.url.toString())
        assertEquals("auth_token=a; ct0=ct0-a", place.headers["Cookie"], "with the account's session, like every read")
    }

    @Test
    fun aSpaceIsReadForItsMetadata() = runBlocking {
        val x = answering(
            """{"data":{"audioSpace":{"metadata":{"rest_id":"1yoKMZ","state":"Ended","title":"Late night","media_key":"28_space",
               "started_at":1761800000000,"ended_at":"1761803600000","total_live_listeners":420,"total_replay_watched":17,"is_space_available_for_replay":true,
               "creator_results":{"result":{"rest_id":"9","core":{"screen_name":"host","name":"Host"}}}}}}}""",
        )
        val space = client(x).audioSpace("1yoKMZ")!!
        assertEquals("28_space", space.mediaKey)
        assertEquals("Late night", space.title)
        assertEquals("Ended", space.state)
        assertEquals("host", space.creator?.screenName)
        assertEquals(1761803600000L, space.endedAt?.toEpochMilli())
        assertEquals(420L, space.liveListeners)
        assertTrue(space.replayAvailable)
        val sent = x.sent.single()
        assertEquals("/i/api/graphql/Bh0L6azTQoMs9rJKeCF4wQ/AudioSpaceById", sent.url.encodedPath)
        assertEquals("""{"id":"1yoKMZ","isMetatagsQuery":true,"withReplays":true,"withListeners":true}""", sent.url.queryParameter("variables"))
        assertEquals(true, sent.features["spaces_2022_h2_clipping"].boolean)
        assertNull(client(answering("""{"data":{}}""")).audioSpace("x"))
    }

    @Test
    fun aLiveStreamIsFoundFromWhateverThePostGave() = runBlocking {
        val x = FakeX { sent ->
            when (sent.url.encodedPath) {
                "/i/api/1.1/broadcasts/show.json" -> XResponse(200, """{"broadcasts":{"1abc":{"media_key":"28_fromshow"}}}""")
                "/i/api/graphql/Bh0L6azTQoMs9rJKeCF4wQ/AudioSpaceById" -> XResponse(200, """{"data":{"audioSpace":{"metadata":{"media_key":"28_space"}}}}""")
                else -> XResponse(200, """{"source":{"noRedirectPlaybackUrl":"https://video.example/${sent.url.pathSegments.last()}.m3u8"}}""")
            }
        }
        val reader = client(x)
        assertEquals("https://video.example/28_card.m3u8", reader.livePlaybackUrl(XLive(mediaKey = "28_card", broadcastId = "1abc")))
        assertEquals("https://video.example/28_fromshow.m3u8", reader.livePlaybackUrl(XLive(broadcastId = "1abc")))
        assertEquals("https://video.example/28_space.m3u8", reader.livePlaybackUrl(XLive(spaceId = "1room")))
        assertEquals("twitter.com", x.sent.first().url.host)
        assertEquals("/i/api/1.1/live_video_stream/status/28_card", x.sent.first().url.encodedPath)
        assertEquals("1abc", x.sent[1].url.queryParameter("ids"))
        assertNull(reader.livePlaybackUrl(XLive()), "nothing to ask with")

        val unknown = FakeX { sent -> if (sent.url.encodedPath.endsWith("show.json")) XResponse(200, "{}") else XResponse(200, """{"source":{"noRedirectPlaybackUrl":"${sent.url.encodedPath}"}}""") }
        assertEquals("/i/api/1.1/live_video_stream/status/28_1xyz", client(unknown).livePlaybackUrl(XLive(broadcastId = "1xyz")), "else the broadcast's own key")
        assertNull(client(FakeX { XResponse(500, "") }).livePlaybackUrl(XLive(broadcastId = "1xyz")), "a failure is no stream, not an error")
    }

    @Test
    fun aTranslationIsAskedOfGrokWithOneAccount() = runBlocking {
        val x = answering(
            """{"result":{"text":"Hallo @dart #flutter","entities":{"user_mentions":[{"screen_name":"dart","indices":[6,11]}],"hashtags":[{"text":"flutter","indices":[12,20]}]}}}""",
        )
        val translation = client(x).translate("2001", "de")
        assertEquals("Hallo @dart #flutter", translation.text)
        assertEquals(2, translation.entities.size)
        val sent = x.sent.single()
        assertEquals("POST", sent.method)
        assertEquals("https://api.x.com/2/grok/translation.json", sent.url.toString())
        assertEquals("""{"id":"2001","dst_lang":"de","content_type":"POST"}""", sent.body)
        assertEquals("text/plain;charset=UTF-8", sent.headers["content-type"])
        assertEquals("auth_token=a; ct0=ct0-a", sent.headers["Cookie"])
        assertTrue(sent.headers.getValue("x-client-transaction-id").isNotEmpty())
    }

    @Test
    fun aTranslationStillBeingWrittenIsAskedForAgain() = runBlocking {
        var calls = 0
        val x = FakeX { calls++; if (calls < 3) XResponse(200, "still writing") else XResponse(200, """{"result":{"text":"fertig"}}""") }
        val reader = client(x).apply { translationWaitMillis = 1 }
        assertEquals("fertig", reader.translate("1", "de").text)
        assertEquals(3, calls)
        val never = client(FakeX { XResponse(200, "") }).apply { translationWaitMillis = 1 }
        assertEquals(XTranslationException.Kind.NOT_READY, assertFailsWith<XTranslationException> { never.translate("1", "de") }.kind)
    }

    @Test
    fun aRefusedTranslationSaysWhyInTermsAScreenCanWord() {
        fun refusal(code: Int, body: String) = assertFailsWith<XTranslationException> { runBlocking { client(FakeX { XResponse(code, body) }).translate("1", "xx") } }
        assertEquals(XTranslationException.Kind.UNSUPPORTED_LANGUAGE, refusal(400, """{"error":"xx is not supported"}""").kind)
        assertEquals("Language xx is not supported", refusal(400, """{"error":"xx is not supported"}""").message)
        assertEquals(XTranslationException.Kind.BANNED, refusal(403, "{}").kind)
        assertEquals(XTranslationException.Kind.TOO_MANY_REQUESTS, refusal(429, "{}").kind)
        assertEquals(XTranslationException.Kind.FAILED, refusal(500, "{}").kind)
        assertEquals("nope", refusal(200, """{"error":{"message":"nope"}}""").message)
    }

    @Test
    fun aTranslationWithoutAnAccountIsAskedWithoutAnAccountsHeaders() = runBlocking {
        val asked = ArrayList<Map<String, String>>()
        val x = object : XHttp {
            override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean) = XResponse(404, "")
            override suspend fun post(url: HttpUrl, headers: Map<String, String>, body: String, contentType: String): XResponse {
                asked += headers
                return XResponse(200, """{"result":{"text":"Hallo"}}""")
            }
        }
        val translation = XClient(x, { emptyList() }, XSigners({ TransactionSigner(IntArray(48) { it }, "a") })).translate("1", "de")
        assertEquals("Hallo", translation.text)
        val headers = asked.single()
        assertEquals(XClient.BASE_HEADERS["authorization"], headers["authorization"], "the signed-in bearer, as XTA's base headers carry")
        assertNull(headers["Cookie"])
        assertTrue(headers["x-client-transaction-id"]!!.isNotEmpty(), "signed without a session")
    }

    @Test
    fun aConversationAsksForTheChosenOrderOfReplies() = runBlocking {
        val x = answering(fixture("tweet_detail_subscriber_preview.json"))
        client(x).conversation("2105589900078641579", ranking = XReplyRanking.LIKES)
        assertEquals("Likes", x.sent.single().variables["rankingMode"].string)
        assertEquals(false, x.sent.single().features["responsive_web_grok_annotations_enabled"].boolean)
    }

    @Test
    fun aChosenAccountsHomeTimelineAsksHomeTimelineWithXtasParameters() = runBlocking {
        val x = answering("""{"data":{"home":{"home_timeline_urt":{"instructions":[]}}}}""")
        client(x).homeTimeline(account, cursor = "c", count = 10)
        val sent = x.sent.single()
        assertEquals("/i/api/graphql/-SBRQaLiRjWvTFD5SI403A/HomeTimeline", sent.url.encodedPath)
        assertEquals("twitter.com", sent.url.host)
        assertEquals(
            """{"userId":"ct0-a","count":10,"includePromotedContent":false,"withQuickPromoteEligibilityTweetFields":true,"withVoice":true,"withV2Timeline":true,"cursor":"c"}""",
            sent.url.queryParameter("variables"),
        )
        assertEquals("""{"withAuxiliaryUserLabels":false,"withArticleRichContentState":false}""", sent.url.queryParameter("fieldToggles"))
    }
}
