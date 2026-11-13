package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.x.http.XResponse
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * How many requests each screen costs a guest, counted on the fake Meta:
 * what is opened again, or by two screens at once, is not asked again, and
 * Home's background reads take what an opened screen was just answered.
 * Before this, a profile opened again within ten minutes read its page
 * again, a profile opened while Home read the same account asked GraphQL
 * twice, and two screens searching the same words read the page twice.
 */
class ThreadsRequestCountTest {
    private val time = FakeTime()
    private val minutes = 60 * 1000L

    private val meta = FakeMeta { request ->
        delay(50)
        when {
            request.path == "/@zuck" -> ok(fixture("profile_page.html"))
            request.path == "/api/graphql" -> ok(fixture("graphql_profile_threads.json"))
            request.path.startsWith("/@zuck/post/") -> ok(page(listOf(listOf(postJson("1")), listOf(postJson("2", user = "a")))))
            request.path == "/search" -> ok(fixture("search_page.html"))
            else -> XResponse(404, "")
        }
    }

    private fun reader() = ThreadsReader(
        direct = ThreadsDirectClient(meta, { ThreadsSession() }, ThreadsMemory.InMemory(), { ThreadsRepairs() }, ThreadsPacing.NONE, time.clock),
        rssHub = ThreadsRssHub(meta),
        xy = ThreadsXy(meta),
        clock = time.clock,
    )

    private fun asked(): List<String> = meta.asked.map { "${it.method} ${it.path}" }.also { meta.asked.clear() }

    private val post = ThreadsPost(id = "1", handle = "zuck", authorName = "zuck", text = "post 1", url = "https://www.threads.com/@zuck/post/C1")

    @Test
    fun aProfileCostsTwoRequestsOnceAndNoneWhenOpenedAgainWithinTenMinutes() = runBlocking {
        val reader = reader()
        reader.profile("zuck")
        assertEquals(listOf("GET /@zuck", "POST /api/graphql"), asked(), "its page (card, token, id), then its threads")
        reader.profile("zuck")
        assertEquals(emptyList(), asked(), "before: [GET /@zuck], the card read again")
        time.now += 11 * minutes
        reader.profile("zuck")
        assertEquals(listOf("GET /@zuck", "POST /api/graphql"), asked(), "past ten minutes both are read again")
        reader.profile("zuck", force = true)
        assertEquals(listOf("GET /@zuck", "POST /api/graphql"), asked(), "pulling to refresh asks again")
    }

    @Test
    fun aProfilesHeaderIsHeardBeforeItsThreadsArrive() = runBlocking {
        val heard = CopyOnWriteArrayList<String>()
        val slowThreads = FakeMeta { request ->
            if (request.path == "/api/graphql") {
                delay(300)
                heard += "threads"
                ok(fixture("graphql_profile_threads.json"))
            } else {
                ok(fixture("profile_page.html"))
            }
        }
        val reader = ThreadsReader(ThreadsDirectClient(slowThreads, pacing = ThreadsPacing.NONE, clock = time.clock), ThreadsRssHub(slowThreads), ThreadsXy(slowThreads), clock = time.clock)
        val page = reader.profile("zuck") { card -> heard += "card:${card.username}" }
        assertEquals(listOf("card:zuck", "threads"), heard)
        assertEquals(7, page.posts.size)
    }

    @Test
    fun aProfileOpenedWhileHomeReadsTheSameAccountSharesItsRequests() = runBlocking {
        val reader = reader()
        val home = async { withContext(ThreadsBackground) { reader.postsFor(listOf("zuck")) } }
        val profile = async { reader.profile("zuck") }
        home.await()
        assertEquals(7, profile.await().posts.size)
        assertEquals(listOf("GET /@zuck", "POST /api/graphql"), asked(), "before: GraphQL was asked twice")
        assertTrue(reader.direct.sharedAnswers > 0)
    }

    @Test
    fun aProfileOpenedJustAfterHomeReadItsAccountAsksNothing() = runBlocking {
        val reader = reader()
        withContext(ThreadsBackground) { reader.postsFor(listOf("zuck")) }
        assertEquals(listOf("GET /@zuck", "POST /api/graphql"), asked())
        time.now += 30_000
        reader.profile("zuck")
        assertEquals(emptyList(), asked(), "before: [GET /@zuck]; Home's page of a moment ago serves the card")
        assertEquals(1, reader.direct.sharedAnswers)
    }

    @Test
    fun aConversationIsOneRequestHoweverManyScreensOpenIt() = runBlocking {
        val reader = reader()
        val a = async { reader.conversation(post) }
        val b = async { reader.conversation(post) }
        a.await()
        b.await()
        reader.conversation(post)
        assertEquals(listOf("GET /@zuck/post/C1"), asked())
    }

    @Test
    fun twoScreensSearchingTheSameWordsReadThePageOnce() = runBlocking {
        val reader = reader()
        val first = async { reader.direct.searchFound("kiln") }
        val second = async { reader.direct.searchFound("kiln") }
        assertEquals(first.await().posts, second.await().posts)
        assertEquals(listOf("GET /search"), asked(), "before: [GET /search, GET /search]")
    }

    @Test
    fun aScreensOwnRepeatIsAskedAgainWhatAnotherLaneAnsweredIsNot() = runBlocking {
        val client = reader().direct
        client.searchFound("kiln")
        client.searchFound("kiln")
        assertEquals(2, asked().size, "a search run again on its own screen is read again")
        withContext(ThreadsBackground) { client.searchFound("kiln") }
        assertEquals(emptyList(), asked(), "Home takes what the screen was answered a moment ago")
        time.now += ThreadsGuestAnswers.RECENT_MILLIS + 1
        withContext(ThreadsBackground) { client.searchFound("kiln") }
        assertEquals(1, asked().size, "past a minute it asks itself")
    }

    @Test
    fun aRefusalOrAThrottleIsNeverReused() = runBlocking {
        var answer = XResponse(429, "")
        val refusing = FakeMeta { answer }
        val client = ThreadsDirectClient(refusing, pacing = ThreadsPacing.NONE, clock = time.clock)
        runCatching { client.searchFound("kiln") }
        runCatching { withContext(ThreadsBackground) { client.searchFound("kiln") } }
        assertEquals(2, refusing.asked.size, "a 429 is not an answer to share")
        answer = ok(fixture("search_page.html"))
        client.searchFound("kiln")
        assertEquals(3, refusing.asked.size)
    }

    @Test
    fun anOpenedScreenDoesNotWaitBehindHomesQueueForTheSameRead() = runBlocking {
        val order = CopyOnWriteArrayList<String>()
        val searches = FakeMeta { asked ->
            order += asked.url.queryParameter("q").orEmpty()
            yield()
            ok(fixture("search_page.html"))
        }
        val yielding = ThreadsPacing(sessionGapMillis = 3_000, guestGapMillis = 550, sessionJitterMillis = 0, guestJitterMillis = 0, sleep = { time.sleep(it); yield() })
        val client = ThreadsDirectClient(searches, pacing = yielding, clock = time.clock)
        coroutineScope {
            listOf("home1", "home2", "kiln").forEach { query -> launch { withContext(ThreadsBackground) { client.searchPosts(query) } } }
            launch { client.searchPosts("kiln") }
        }
        assertEquals(listOf("home1", "home2", "kiln"), order, "before: [home1, home2, kiln, kiln]; Home's own takes the opened search's answer")
    }

    @Test
    fun scrollingHomeReadsNoConversation() = runBlocking {
        val reader = reader()
        withContext(ThreadsBackground) { reader.following(listOf("zuck")) }
        reader.extras(listOf("zuck"))
        assertTrue(meta.asked.none { "/post/" in it.path }, "Home never reads a post's page ahead: ${asked()}")
    }
}
