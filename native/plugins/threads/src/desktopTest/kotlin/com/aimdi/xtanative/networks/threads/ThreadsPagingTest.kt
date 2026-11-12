package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.x.http.XResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A Threads list read on past its first page: the pager's rules, and the client asking Meta as threads.com's own client does. */
class ThreadsPagingTest {
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun pager(limit: Int = ThreadsPaging.MAX_ITEMS, pages: (String) -> ThreadsMore<String>): Pair<ThreadsPaging<String>, MutableList<String>> {
        val asked = mutableListOf<String>()
        return ThreadsPaging<String>(scope, { it }, limit) { cursor -> asked += cursor; pages(cursor) } to asked
    }

    @Test
    fun eachPageAddsWhatIsNewAndTheListEndsWhereMetaSaysSo() {
        val (paging, asked) = pager { cursor ->
            when (cursor) {
                "c1" -> ThreadsMore(listOf("b", "c"), "c2")
                else -> ThreadsMore(listOf("d"), null)
            }
        }
        paging.start(listOf("a", "b"), "c1")
        paging.more()
        assertEquals(listOf("c"), paging.state.value.items, "what the first page showed is not shown twice")
        paging.more()
        assertEquals(listOf("c", "d"), paging.state.value.items)
        assertNull(paging.state.value.next)
        assertFalse(paging.state.value.onWeb)
        paging.more()
        assertEquals(listOf("c1", "c2"), asked, "the end asks nothing")
    }

    @Test
    fun pagesThatBringNothingNewArePassedOverAFewTimesAndACursorThatComesRoundEndsTheList() {
        val (paging, asked) = pager { cursor -> if (cursor == "c3") ThreadsMore(listOf("z"), "c1") else ThreadsMore(listOf("a"), "c" + (cursor.last() + 1)) }
        paging.start(listOf("a"), "c1")
        paging.more()
        assertEquals(listOf("z"), paging.state.value.items)
        assertEquals(listOf("c1", "c2", "c3"), asked)
        assertNull(paging.state.value.next, "c1 came round again")

        val (endless, read) = pager { cursor -> ThreadsMore(listOf("a"), cursor + "+") }
        endless.start(listOf("a"), "x")
        endless.more()
        assertEquals(ThreadsPaging.PASSES, read.size)
        assertNull(endless.state.value.next)
    }

    @Test
    fun aFailureWaitsForTheReaderAndAnAnswerNotUnderstoodLeavesTheRestToThreadsCom() {
        var fail = true
        val (paging, asked) = pager { if (fail) throw ThreadsException(ThreadsFailure.THROTTLED, "429", 429) else ThreadsMore(emptyList(), null, refused = true) }
        paging.start(listOf("a"), "c1")
        paging.more()
        assertNotNull(paging.state.value.problem)
        paging.more()
        assertEquals(1, asked.size, "nothing is asked again before the reader retries")
        fail = false
        paging.retry()
        assertNull(paging.state.value.problem)
        assertTrue(paging.state.value.onWeb)
        assertNull(paging.state.value.next)
    }

    @Test
    fun aLongListStopsAtWhatIsKeptInMemoryAndAFreshFirstPageStartsAnew() {
        val (paging, _) = pager(limit = 3) { cursor -> ThreadsMore(listOf("$cursor-1", "$cursor-2"), cursor + "+") }
        paging.start(listOf("a"), "c")
        paging.more()
        assertEquals(listOf("c-1", "c-2"), paging.state.value.items)
        assertTrue(paging.state.value.onWeb)
        paging.start(listOf("a"), "c")
        assertTrue(paging.state.value.onWeb, "the first page already started from keeps its pages")
        paging.start(listOf("a", "b"), "d")
        assertEquals(ThreadsPagingState<String>(next = "d"), paging.state.value)
    }

    @Test
    fun aProfilesPageNamesTheCursorAndItsNextPageIsAskedAsThreadsComAsksIt(): Unit = runBlocking {
        val meta = FakeMeta { asked ->
            if (asked.method == "GET") ok(fixture("paging_profile_page.html")) else ok(fixture("paging_next_answer.json"))
        }
        val client = direct(meta)
        client.guestProfile("zuck")
        val cursor = assertNotNull(client.profileNext("zuck"))

        val more = client.moreThreads("zuck", cursor)

        assertEquals(listOf("3400000000000000102", "3400000000000000103"), more.items.map { it.id })
        assertEquals("QVFIcursor2", ThreadsNextPage.decode(more.next)?.after)
        val post = meta.asked.last()
        assertEquals("/api/graphql", post.path)
        assertEquals("24000000000000001", post.form["doc_id"])
        assertEquals("BarcelonaProfileThreadsTabDirectQuery", post.form["fb_api_req_friendly_name"])
        val variables = ThreadsJson.decode(post.form.getValue("variables")) as JsonObject
        assertEquals(JsonPrimitive("QVFIcursor1"), variables["after"])
        assertEquals(JsonPrimitive("314216"), variables["userID"])
        assertEquals("AVpaging1", post.headers["X-FB-LSD"], "the token of the page read last is sent back")
        assertEquals(2, meta.asked.size, "a fresh token asks for no page again")
    }

    @Test
    fun metaRefusingForNowIsRetriedButAnAnswerInAnotherShapeEndsTheListHere(): Unit = runBlocking {
        var answer = XResponse(429, "")
        val meta = FakeMeta { asked -> if (asked.method == "GET") ok(fixture("paging_profile_page.html")) else answer }
        val client = direct(meta)
        client.guestProfile("zuck")
        val cursor = client.profileNext("zuck")!!

        assertEquals(ThreadsFailure.THROTTLED, runCatching { client.moreThreads("zuck", cursor) }.exceptionOrNull()?.let { (it as ThreadsException).failure })
        answer = XResponse(502, "")
        assertEquals(ThreadsFailure.UNREACHABLE, (runCatching { client.moreThreads("zuck", cursor) }.exceptionOrNull() as ThreadsException).failure)
        listOf(XResponse(400, "{}"), ok("<!DOCTYPE html><html></html>"), ok("""{"errors":[{"code":1675030}],"data":null}""")).forEach {
            answer = it
            assertTrue(client.moreThreads("zuck", cursor).refused)
        }
        assertTrue(client.moreThreads("zuck", "guest:1").refused, "a cursor that is not one of ours asks nothing")
    }

    @Test
    fun theFrontPagesCursorCarriesForYouOn(): Unit = runBlocking {
        val front = fixture("paging_profile_page.html").replace("BarcelonaProfileThreadsTabDirectQuery", "BarcelonaLoggedOutFeedContainerQuery")
        val meta = FakeMeta { asked -> if (asked.method == "GET") ok(front) else ok(fixture("paging_next_answer.json")) }
        val forYou = ThreadsForYou(direct(meta))

        val first = forYou.page()
        assertTrue(first.next.orEmpty().startsWith("threads-next:"))
        val second = forYou.page(first.next)
        assertEquals(listOf("3400000000000000102", "3400000000000000103"), second.posts.map { it.id })
        assertNotNull(second.next)
    }

    @Test
    fun moreReplyThreadsJoinTheConversationEachPostOnce() {
        val focus = ThreadsPost(id = "1", handle = "zuck", authorName = "Z", text = "t")
        val reply = { id: String -> ThreadsPost(id = id, handle = "dana", authorName = "D", text = "r") }
        val conversation = ThreadsConversation(focus, replies = listOf(listOf(reply("2"))), loaded = true)
        val more = conversation.withMore(listOf(ThreadsReplyThread(listOf(reply("2"))), ThreadsReplyThread(listOf(reply("3"), reply("4")), mapOf("4" to 2), hasMore = true)))
        assertEquals(listOf(listOf("2"), listOf("3", "4")), more.replies.map { chain -> chain.map { it.id } })
        assertEquals(2, more.levels["4"])
        assertEquals(setOf("3"), more.unfinished)
    }
}
