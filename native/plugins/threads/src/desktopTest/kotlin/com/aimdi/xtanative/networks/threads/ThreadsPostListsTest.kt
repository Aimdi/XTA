package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.x.http.XResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** A post's lists for a signed-in reader: who reposted it and its quotes, how they are asked for, read, and that they are only read. */
class ThreadsPostListsTest {
    private val time = FakeTime()
    private val bearer = ThreadsSession(bearer = "Bearer IGT:2:made-up")

    @Test
    fun repostersAreTriedAtTheirLikelyPathsAndReadAsPeople() {
        assertEquals(
            listOf("/api/v1/text_feed/42/reposters/", "/api/v1/media/42/reposters/"),
            ThreadsPeople.paths(ThreadsPeopleList.REPOSTERS, "42"),
        )
        assertEquals(ThreadsPeople.paths(ThreadsPeopleList.REPOSTERS, "42").first(), ThreadsPeople.path(ThreadsPeopleList.REPOSTERS, "42"))
        assertEquals(listOf("max_id" to "c1"), ThreadsPeople.params(ThreadsPeopleList.REPOSTERS, "c1", "ann"), "a post's list is neither counted nor searched by Meta")
        assertTrue(ThreadsPeopleList.REPOSTERS.ofPost && ThreadsPeopleList.LIKERS.ofPost && !ThreadsPeopleList.FOLLOWERS.ofPost)
        val wrapped = ThreadsPeople.page(j(mapOf("reposters" to listOf(mapOf("user" to mapOf("pk" to "7", "username" to "one")), mapOf("username" to "two")), "next_max_id" to "n")))
        assertEquals(listOf("one", "two"), wrapped.items.map { it.username })
        assertEquals("n", wrapped.next)
    }

    @Test
    fun aQuotesPageReadsAFeedOrAPlainListEachPostOnceAndNeverTheQuotedPost() {
        val quoted = postJson("100", user = "author")
        val quote = postJson("101", user = "one", info = mapOf("share_info" to mapOf("quoted_post" to quoted)))
        val feed = ThreadsQuotes.page(j(mapOf("threads" to listOf(mapOf("thread_items" to listOf(mapOf("post" to quote)))), "next_max_id" to "after")), quotedId = "100")
        assertEquals(listOf("101"), feed.items.map { it.id })
        assertEquals("100", feed.items.single().quoted?.id, "a quote shows what it quotes")
        assertEquals("after", feed.next)

        val plain = ThreadsQuotes.page(j(mapOf("medias" to listOf(quote, mapOf("post" to postJson("102", user = "two")), quote, quoted), "more_available" to false)), quotedId = "100")
        assertEquals(listOf("101", "102"), plain.items.map { it.id })
        assertNull(plain.next, "Meta said there is no more")

        val paged = ThreadsQuotes.page(j(mapOf("posts" to listOf(quote), "paging_tokens" to mapOf("downwards" to "down"))))
        assertEquals("down", paged.next)
        val nothing = ThreadsQuotes.page(j(mapOf("status" to "ok", "next_max_id" to "x")))
        assertEquals(emptyList(), nothing.items)
        assertNull(nothing.next, "a page that brought nothing names no next page")
        assertEquals(listOf("max_id" to "c"), ThreadsQuotes.params(" c "))
        assertEquals(emptyList(), ThreadsQuotes.params(""))
    }

    @Test
    fun theCookiesAskThreadsComWithAGetAndAMissingPathGivesWayToTheNextOnceThenItIsRemembered(): Unit = runBlocking {
        val meta = FakeMeta { asked ->
            when (asked.path) {
                "/api/v1/text_feed/42/quotes/" -> XResponse(404, "")
                else -> ok("""{"medias":[${json(postJson("7", user = "one"))}],"next_max_id":"n"}""")
            }
        }
        val client = direct(meta, ThreadsSession(COOKIES), time = time)
        val first = client.quotes("42")
        assertEquals(listOf("7"), first.items.map { it.id })
        assertEquals("n", first.next)
        assertEquals(listOf("/api/v1/text_feed/42/quotes/", "/api/v1/media/42/quotes/"), meta.asked.map { it.path })
        assertTrue(meta.asked.all { it.method == "GET" && it.url.host == "www.threads.com" })
        assertEquals("c", meta.asked.last().headers["X-CSRFToken"])

        client.quotes("42", cursor = "n")
        assertEquals("/api/v1/media/42/quotes/", meta.asked.last().path, "the path that answered is asked first from then on")
        assertEquals("n", meta.asked.last().url.queryParameter("max_id"))
        assertEquals(3, meta.asked.size)
    }

    @Test
    fun repostersWithABearerReadTheAppApiAndEveryPathMissingIsNoSuchList(): Unit = runBlocking {
        val meta = FakeMeta { XResponse(404, "") }
        val failure = assertFailsWith<ThreadsException> { direct(meta, bearer, time = time).people(ThreadsPeopleList.REPOSTERS, "3141") }
        assertEquals(ThreadsFailure.NO_SUCH_FEED, failure.failure)
        assertEquals(listOf("/api/v1/text_feed/3141/reposters/", "/api/v1/media/3141/reposters/"), meta.asked.map { it.path })
        assertTrue(meta.asked.all { it.url.host == "i.instagram.com" && it.headers["Authorization"] == "Bearer IGT:2:made-up" })
    }

    @Test
    fun noSessionOrNoMediaIdAsksNothingAndAThrottleStopsAtOnceAndParksTheSession(): Unit = runBlocking {
        val none = FakeMeta { error("nothing is asked") }
        assertEquals(ThreadsFailure.NOT_CONFIGURED, assertFailsWith<ThreadsException> { direct(none, time = time).quotes("42") }.failure)
        assertEquals(ThreadsFailure.NO_SUCH_FEED, assertFailsWith<ThreadsException> { direct(none, bearer, time = time).quotes("C1abc") }.failure)
        assertEquals(0, none.asked.size)

        val throttled = FakeMeta { XResponse(429, "") }
        val client = direct(throttled, ThreadsSession(COOKIES), time = time)
        assertEquals(ThreadsFailure.THROTTLED, assertFailsWith<ThreadsException> { client.people(ThreadsPeopleList.REPOSTERS, "42") }.failure)
        assertEquals(1, throttled.asked.size, "a throttle is not taken for a missing path")
        assertTrue(client.isSessionParked)
    }

    @Test
    fun aThreadMetaHoldsMoreOfGoesOnAtItsLastPostWithALink() {
        val first = ThreadsPost(id = "1", handle = "a", authorName = "a", text = "1", url = "https://www.threads.com/@a/post/A1")
        val second = first.copy(id = "2", url = "https://www.threads.com/@b/post/B2")
        val stub = first.copy(id = "3", url = null)
        assertEquals("2", ThreadsReplyRow.OnWeb(listOf(first, second, stub)).continuesAt?.id)
        assertNull(ThreadsReplyRow.OnWeb(listOf(stub)).continuesAt)
    }
}
