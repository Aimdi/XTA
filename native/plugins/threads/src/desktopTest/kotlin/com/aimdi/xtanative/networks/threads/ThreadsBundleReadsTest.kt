package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.x.http.XResponse
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * The reads asked by a doc id only threads.com's bundles name
 * ([ThreadsBundleReads]), in both states: off, as shipped, each asks Meta
 * nothing; with an id (as `repairs.json` or a later release gives one) each
 * asks guest GraphQL as the page does and reads what it is answered. The
 * answers are made up: none was ever seen.
 */
class ThreadsBundleReadsTest {
    private val token = """<script>["LSD",[],{"token":"tok"}]</script>"""
    private val reply = ThreadsPost(id = "2", handle = "a", authorName = "a", text = "post 2", url = "https://www.threads.com/@a/post/C2")
    private val on = ThreadsRepairs(inlineRepliesDocId = "7000000000000001", aboutProfileDocId = "7000000000000002", trendingTopicsDocId = "7000000000000003")

    private fun meta(graphql: XResponse, search: String = "<html>$token</html>") = FakeMeta { request ->
        when {
            request.path == "/api/graphql" -> graphql
            request.path == "/@zuck" -> ok(fixture("profile_page.html"))
            request.path.startsWith("/@a/post/") -> ok(page(listOf(listOf(postJson("2", user = "a"))), head = token))
            request.path == "/search" -> ok(search)
            else -> XResponse(404, "")
        }
    }

    private fun asked(meta: FakeMeta): List<String> = meta.asked.map { "${it.method} ${it.path}" }

    private fun answer(data: Any?) = ok(json(mapOf("data" to data)).toString())

    @Test
    fun asShippedEveryReadIsOffAndAsksMetaNothing() = runBlocking {
        val meta = meta(answer(mapOf("trends" to listOf(mapOf("trending_keyword" to "never asked")))))
        val client = direct(meta)

        assertFalse(client.readsDeeperReplies)
        assertFalse(client.readsAboutProfile)
        assertNull(client.deeperReplies(reply))
        assertNull(client.aboutProfile("zuck"))
        assertEquals(emptyList(), asked(meta))
        assertEquals(emptyList(), client.trends())
        assertEquals(listOf("GET /search"), asked(meta), "a search page without trends is all that is read")
    }

    @Test
    fun withItsIdTheRepliesUnderAReplyAreAskedAsItsPageAsksThem() = runBlocking {
        val under = mapOf(
            "media" to postJson(
                "2", user = "a",
                info = mapOf(
                    "direct_replies" to mapOf(
                        "edges" to listOf(mapOf("node" to mapOf("thread_items" to listOf(mapOf("post" to postJson("3", user = "b", info = mapOf("reply_level" to 2))))))),
                        "page_info" to mapOf("has_next_page" to false),
                    ),
                ),
            ),
        )
        val meta = meta(answer(under))
        val client = direct(meta, repairs = on)

        assertTrue(client.readsDeeperReplies)
        val thread = client.deeperReplies(reply)

        assertEquals(listOf("3"), thread?.posts?.map { it.id })
        assertEquals(mapOf("3" to 2), thread?.levels)
        assertEquals(false, thread?.hasMore)
        assertEquals(listOf("GET /@a/post/C2", "POST /api/graphql"), asked(meta))
        val form = meta.asked.last().form
        assertEquals("7000000000000001", form["doc_id"])
        assertEquals("""{"postID":"${ThreadsPeople.mediaId(reply)}"}""", form["variables"])
        assertEquals("BarcelonaInlineRepliesExpansionQuery", form["fb_api_req_friendly_name"])
        assertEquals("tok", form["lsd"])
        assertEquals("https://www.threads.com/@a/post/C2", meta.asked.last().headers["Referer"])
    }

    @Test
    fun aPublishedVariableNameIsTheOneSent() = runBlocking {
        val meta = meta(answer(mapOf("replies" to listOf(postJson("3", user = "b")))))
        val client = direct(meta, repairs = ThreadsRepairs.published(mapOf("threads.inline_replies_doc_id" to "7000000000000009", "threads.inline_replies_variable" to "mediaID")))

        assertEquals(listOf("3"), client.deeperReplies(reply)?.posts?.map { it.id }, "an answer in another shape gives every post but the reply")
        assertEquals("""{"mediaID":"${ThreadsPeople.mediaId(reply)}"}""", meta.asked.last().form["variables"])
        assertEquals("7000000000000009", meta.asked.last().form["doc_id"])
    }

    @Test
    fun aRefusalOrAnAnswerInAnotherShapeBringsNothing() = runBlocking {
        listOf(XResponse(400, ""), ok("<html>sign in</html>"), ok("""{"errors":[{"message":"no"}]}"""), answer(mapOf("media" to postJson("2", user = "a")))).forEach { said ->
            assertNull(direct(meta(said), repairs = on).deeperReplies(reply))
            assertNull(direct(meta(said), repairs = on).aboutProfile("zuck"))
        }
        assertNull(direct(meta(answer(emptyMap<String, Any?>())), repairs = on).deeperReplies(reply.copy(url = null)), "a post without a link is not asked about")
    }

    @Test
    fun withItsIdAboutThisProfileIsAskedWithTheAccountsId() = runBlocking {
        val meta = meta(answer(mapOf("user" to mapOf("about_this_profile" to mapOf("joined_date" to 1_688_515_200)))))
        val client = direct(meta, repairs = on)
        val id = client.guestProfile("zuck").pk

        val about = client.aboutProfile("@Zuck")

        assertEquals(ThreadsProfileAbout(joinedAt = Instant.ofEpochSecond(1_688_515_200)), about)
        assertEquals(listOf("GET /@zuck", "POST /api/graphql"), asked(meta), "the card read a moment ago gives the id and the token")
        val form = meta.asked.last().form
        assertEquals("7000000000000002", form["doc_id"])
        assertEquals("""{"userID":"$id"}""", form["variables"])
        assertEquals("BarcelonaAboutThisProfileDialogQuery", form["fb_api_req_friendly_name"])
    }

    @Test
    fun aJoinDateIsReadUnderTheNamesItMightGoByAndNothingElseIs() {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        fun about(data: Any?) = ThreadsBundleReads.about(listOf(json(mapOf("data" to data))), now)

        assertEquals(ThreadsProfileAbout(joined = "July 2023"), about(mapOf("user" to mapOf("date_joined" to "  July   2023 "))))
        assertEquals(ThreadsProfileAbout(joined = "July 2023"), about(mapOf("a" to listOf(mapOf("joined_date" to mapOf("text" to "July 2023"))))))
        assertEquals(Instant.ofEpochSecond(1_688_515_200), about(mapOf("created_at" to 1_688_515_200_000))?.joinedAt, "milliseconds")
        assertEquals(Instant.ofEpochSecond(1_688_515_200), about(mapOf("joined_at" to mapOf("timestamp" to "1688515200")))?.joinedAt)
        assertNull(about(mapOf("joined_date" to 12)), "no moment an account could have joined")
        assertNull(about(mapOf("joined_date" to 4_102_444_800)), "the future")
        assertNull(about(mapOf("joined_date" to true, "name" to "July 2023")))
        assertNull(about(mapOf("joined_date" to "x".repeat(200))))
        assertNull(ThreadsBundleReads.about(listOf(json(mapOf("joined_date" to "July 2023"))), now), "only under data")
        assertNull(ThreadsBundleReads.about(emptyList(), now))
    }

    @Test
    fun withItsIdTrendsAreAskedOnlyWhenTheSearchPageCarriesNone() = runBlocking {
        val meta = meta(answer(mapOf("trends" to listOf(mapOf("trending_keyword" to "Kotlin")))))
        val client = direct(meta, repairs = on)

        assertEquals(listOf("Kotlin"), client.trends().map { it.title })
        assertEquals(listOf("GET /search", "POST /api/graphql"), asked(meta))
        assertEquals("7000000000000003", meta.asked.last().form["doc_id"])
        assertEquals("{}", meta.asked.last().form["variables"])
        assertEquals(listOf("Kotlin"), client.trends().map { it.title })
        assertEquals(2, meta.asked.size, "remembered as the page's are")

        val carried = """<html>$token<script type="application/json" data-sjs>{"data":{"trends":[{"trending_keyword":"Page"}]}}</script></html>"""
        val withTrends = meta(answer(mapOf("trends" to listOf(mapOf("trending_keyword" to "Kotlin")))), search = carried)
        assertEquals(listOf("Page"), direct(withTrends, repairs = on).trends().map { it.title })
        assertEquals(listOf("GET /search"), asked(withTrends))
    }

    @Test
    fun aRefusedTrendsReadOrAPageWithoutATokenLeavesTheTrendsEmpty() = runBlocking {
        assertEquals(emptyList(), direct(meta(XResponse(400, "")), repairs = on).trends())
        assertEquals(emptyList(), direct(meta(XResponse(500, "")), repairs = on).trends())
        val tokenless = meta(answer(mapOf("trends" to listOf(mapOf("trending_keyword" to "Kotlin")))), search = "<html></html>")
        assertEquals(emptyList(), direct(tokenless, repairs = on).trends())
        assertEquals(listOf("GET /search"), asked(tokenless), "no second page is read for a token")
    }

    @Test
    fun repliesReadInPlaceGoOnTheEndOfTheirThreadWhichIsThenFinished() {
        fun post(id: String) = ThreadsPost(id = id, handle = "h$id", authorName = id, text = id, url = "https://www.threads.com/@h$id/post/C$id")
        val conversation = ThreadsConversation(focus = post("1"), replies = listOf(listOf(post("2"), post("3")), listOf(post("4"))), loaded = true, unfinished = setOf("2", "4"))
        val brought = ThreadsReplyThread(listOf(post("3"), post("5"), post("6")), mapOf("3" to 2, "5" to 3))

        assertTrue(conversation.brings(brought))
        val deeper = conversation.withDeeper(mapOf("2" to brought))
        assertEquals(listOf(listOf("2", "3", "5", "6"), listOf("4")), deeper.replies.map { chain -> chain.map { it.id } })
        assertEquals(setOf("4"), deeper.unfinished)
        assertEquals(mapOf("5" to 3), deeper.levels, "only the new posts' levels")
        assertEquals(setOf("2", "4"), conversation.withDeeper(mapOf("2" to brought.copy(hasMore = true))).unfinished, "Meta holds still more")

        val nothingNew = ThreadsReplyThread(listOf(post("2"), post("3")))
        assertFalse(conversation.brings(nothingNew))
        assertSame(conversation, conversation.withDeeper(mapOf("2" to nothingNew)))
        assertSame(conversation, conversation.withDeeper(emptyMap()))

        val twice = brought.then(ThreadsReplyThread(listOf(post("6"), post("7")), hasMore = true))
        assertEquals(listOf("3", "5", "6", "7"), twice.posts.map { it.id })
        assertTrue(twice.hasMore)
    }
}
