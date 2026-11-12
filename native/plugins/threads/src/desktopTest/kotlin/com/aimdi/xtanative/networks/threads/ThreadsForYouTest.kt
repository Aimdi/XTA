package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.x.http.XResponse
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * The For you page: whose it reads and with what request, its pages, and
 * what it says when Meta shows nothing, refuses, or rate limits. Built from
 * the shapes XTA's threads_direct_test and threads_parse_test pin; no real
 * token is in here, only the words "IGT:2:test".
 */
class ThreadsForYouTest {
    private val time = FakeTime()
    private val bearer = ThreadsSession(bearer = "IGT:2:test")

    private fun Asked.line() = "$method ${url.host}$path"

    private fun timeline(vararg posts: Map<String, Any?>, next: String? = null, more: Boolean? = null): String = json(
        buildMap {
            put("items", posts.map { mapOf("thread_items" to listOf(mapOf("post" to it))) })
            next?.let { put("next_max_id", it) }
            more?.let { put("more_available", it) }
            put("status", "ok")
        },
    ).toString()

    private fun frontPage(vararg posts: Map<String, Any?>) = page(posts.map { listOf(it) }, head = "<script>[\"LSD\",[],{\"token\":\"front-lsd\"}]</script>")

    // ---- What is asked ----

    @Test
    fun aBearerReadsItsOwnForYouPageAndFollowsMetasCursor(): Unit = runBlocking {
        val meta = FakeMeta { asked ->
            if (asked.url.queryParameter("max_id") == null) {
                ok(timeline(postJson("1", user = "meta"), postJson("2", user = "maya"), next = "cursor-2", more = true))
            } else {
                ok(timeline(postJson("3", user = "ola"), next = "cursor-3", more = false))
            }
        }
        val memory = ThreadsMemory.InMemory(ThreadsKept(deviceId = "device-1"))
        val forYou = ThreadsForYou(direct(meta, bearer, memory, time))
        assertEquals(ThreadsForYouSource.SESSION, forYou.source)

        val first = forYou.page()
        assertEquals(ThreadsForYouSource.SESSION, first.source)
        assertEquals(listOf("1", "2"), first.posts.map { it.id })
        assertEquals("cursor-2", first.next)

        val second = forYou.page(first.next)
        assertEquals(listOf("3"), second.posts.map { it.id })
        assertNull(second.next, "Meta said there is no more")

        assertEquals(
            listOf(
                "https://i.instagram.com/api/v1/feed/text_post_app_timeline/?feed_type=for_you&feed_view_info=%5B%5D&reason=cold_start_fetch" +
                    "&client_session_id=device-1&pagination_source_module=feed_unit",
                "https://i.instagram.com/api/v1/feed/text_post_app_timeline/?feed_type=for_you&feed_view_info=%5B%5D&reason=pagination" +
                    "&client_session_id=device-1&pagination_source_module=feed_unit&max_id=cursor-2",
            ),
            meta.asked.map { it.url.toString() },
        )
        assertTrue(meta.asked.all { it.method == "GET" }, "reading only: nothing is posted to Meta")
        assertTrue(meta.asked.all { it.url.queryParameter("feed_view_info") == "[]" }, "no post is reported as seen")
    }

    @Test
    fun aNumericCursorIsFollowedToo(): Unit = runBlocking {
        val meta = FakeMeta { ok("""{"items":[{"post":{"pk":"1","code":"C1","caption":{"text":"a"},"user":{"username":"meta"}}}],"next_max_id":42}""") }
        assertEquals("42", direct(meta, bearer, time = time).forYouTimeline().next)
    }

    @Test
    fun withoutASessionThePublicFrontPageIsRead(): Unit = runBlocking {
        val meta = FakeMeta { ok(frontPage(postJson("10", user = "nasa", text = "recommended"), postJson("11", user = "bbc"))) }
        val memory = ThreadsMemory.InMemory()
        val forYou = ThreadsForYou(direct(meta, memory = memory, time = time))
        assertEquals(ThreadsForYouSource.GUEST, forYou.source)

        val page = forYou.page()
        assertEquals(ThreadsForYouSource.GUEST, page.source)
        assertEquals(listOf("recommended", "post 11"), page.posts.map { it.text })
        assertNull(page.sessionProblem)

        val asked = meta.asked.single()
        assertEquals("https://www.threads.com/", asked.url.toString())
        assertEquals("GET", asked.method)
        assertFalse(asked.headers.keys.any { it.equals("Cookie", true) || it.equals("Authorization", true) }, "no session is spent on it")
        assertEquals("front-lsd", memory.kept.guestLsd, "its token serves the next profile read")
    }

    @Test
    fun pastedCookiesAloneReadThePublicPageAndAreNotSent(): Unit = runBlocking {
        val meta = FakeMeta { ok(frontPage(postJson("10", user = "nasa"))) }
        val forYou = ThreadsForYou(direct(meta, ThreadsSession(COOKIES, useSessionApis = true), time = time))
        assertEquals(ThreadsForYouSource.GUEST, forYou.page().source)
        assertFalse(meta.asked.single().headers.containsKey("Cookie"))
    }

    @Test
    fun thePublicPageIsReadAgainForMoreAFewTimesAndNoFurther(): Unit = runBlocking {
        val meta = FakeMeta { ok(frontPage(postJson("${it.hashCode()}", user = "nasa"))) }
        val forYou = ThreadsForYou(direct(meta, time = time))
        val cursors = generateSequence(forYou.page()) { page -> page.next?.let { runBlocking { forYou.page(it) } } }.map { it.next }.toList()
        assertEquals(listOf("guest:1", "guest:2", "guest:3", "guest:4", null), cursors)
        assertEquals(1 + ThreadsForYou.GUEST_EXTRA_READS, meta.asked.size)
    }

    @Test
    fun aRefusedSessionFallsBackToThePublicPageAndSaysWhy(): Unit = runBlocking {
        val meta = FakeMeta { asked ->
            if (asked.url.host == "i.instagram.com") XResponse(401, "{}") else ok(frontPage(postJson("10", user = "nasa")))
        }
        val page = ThreadsForYou(direct(meta, bearer, time = time)).page()
        assertEquals(ThreadsForYouSource.GUEST, page.source)
        assertEquals(ThreadsFailure.UNAUTHORIZED, page.sessionProblem)
        assertEquals(listOf("GET i.instagram.com/api/v1/feed/text_post_app_timeline/", "GET www.threads.com/"), meta.asked.map { it.line() })
    }

    @Test
    fun aThrottledSessionIsParkedAndThePublicPageStandsInWithoutAskingItAgain(): Unit = runBlocking {
        val meta = FakeMeta { asked ->
            if (asked.url.host == "i.instagram.com") XResponse(429, "") else ok(frontPage(postJson("10", user = "nasa")))
        }
        val client = direct(meta, bearer, time = time)
        val forYou = ThreadsForYou(client)
        assertEquals(ThreadsFailure.THROTTLED, forYou.page().sessionProblem)
        assertTrue(client.isSessionParked)
        assertEquals(ThreadsForYouSource.GUEST, forYou.source)

        val again = forYou.page()
        assertNull(again.sessionProblem, "the parked session was not tried")
        assertEquals(1, meta.asked.count { it.url.host == "i.instagram.com" })
    }

    @Test
    fun aLaterPageThatFailsDoesNotSwapTheSessionsFeedForThePublicOne(): Unit = runBlocking {
        val meta = FakeMeta { XResponse(500, "") }
        val error = runCatching { ThreadsForYou(direct(meta, bearer, time = time)).page("cursor-2") }.exceptionOrNull()
        assertEquals(ThreadsFailure.UNREACHABLE, (error as ThreadsException).failure)
        assertEquals(1, meta.asked.size)
    }

    // ---- The feed a screen shows ----

    private fun post(id: String) = ThreadsPost(id = id, handle = "h$id", authorName = "H", text = "t")

    private fun pageOf(vararg ids: String, next: String? = null, source: ThreadsForYouSource = ThreadsForYouSource.SESSION) =
        ThreadsForYouPage(source, ids.map(::post), next)

    @Test
    fun pagesAddUpEachPostOnceAndTheFeedEndsWhenMetaNamesNoMore(): Unit = runBlocking {
        val cursors = mutableListOf<String?>()
        val feed = ThreadsForYouFeed({ cursor ->
            cursors += cursor
            when (cursor) {
                null -> pageOf("1", "2", next = "a")
                "a" -> pageOf("2", "3", next = "b")
                else -> pageOf("4")
            }
        }, time.clock)
        feed.refresh()
        assertEquals(listOf("1", "2"), feed.state.value.posts.map { it.id })
        assertFalse(feed.state.value.ended)
        feed.more()
        feed.more()
        assertEquals(listOf("1", "2", "3", "4"), feed.state.value.posts.map { it.id })
        assertTrue(feed.state.value.ended)
        feed.more()
        assertEquals(listOf(null, "a", "b"), cursors, "nothing is asked past the end")
        assertEquals(Instant.ofEpochMilli(time.now), feed.state.value.readAt)
    }

    @Test
    fun aPageWithNothingNewEndsTheFeed(): Unit = runBlocking {
        val feed = ThreadsForYouFeed({ cursor -> if (cursor == null) pageOf("1", next = "guest:1") else pageOf("1", next = "guest:2") })
        feed.refresh()
        feed.more()
        assertEquals(listOf("1"), feed.state.value.posts.map { it.id })
        assertTrue(feed.state.value.ended)
    }

    @Test
    fun aRefreshThatFailsKeepsWhatWasShownAndSaysWhy(): Unit = runBlocking {
        var fail = false
        val feed = ThreadsForYouFeed({ if (fail) throw ThreadsException(ThreadsFailure.UNREACHABLE, "down", 500) else pageOf("1", "2", next = "a") })
        feed.refresh()
        fail = true
        feed.refresh()
        val state = feed.state.value
        assertEquals(listOf("1", "2"), state.posts.map { it.id })
        assertEquals(ThreadsFailure.UNREACHABLE, (state.problem as ThreadsException).failure)
        assertFalse(state.loading)
    }

    @Test
    fun aNextPageThatFailsWaitsForTheReaderToAskAgain(): Unit = runBlocking {
        var calls = 0
        val feed = ThreadsForYouFeed({ cursor ->
            calls++
            when {
                cursor == null -> pageOf("1", next = "a")
                calls == 2 -> throw ThreadsException(ThreadsFailure.UNREACHABLE, "down", 500)
                else -> pageOf("2")
            }
        })
        feed.refresh()
        feed.more()
        assertTrue(feed.state.value.moreProblem != null)
        feed.more()
        assertEquals(2, calls, "scrolling on does not hammer a page that failed")
        feed.retryMore()
        assertEquals(listOf("1", "2"), feed.state.value.posts.map { it.id })
        assertNull(feed.state.value.moreProblem)
    }

    @Test
    fun whileMetaRateLimitsNothingIsAskedBeforeTheTimeItNamed(): Unit = runBlocking {
        var calls = 0
        val until = Instant.ofEpochMilli(time.now + 15 * 60_000)
        val feed = ThreadsForYouFeed({
            calls++
            if (calls == 1) throw ThreadsException(ThreadsFailure.THROTTLED, "429", 429, retryAt = until) else pageOf("1")
        }, time.clock)
        feed.refresh()
        assertEquals(until, feed.state.value.throttledUntil)
        assertFalse(feed.mayAsk)
        feed.refresh()
        assertEquals(1, calls, "a pull during the wait asks nothing")
        time.now += 15 * 60_000
        assertTrue(feed.mayAsk)
        feed.refresh()
        assertEquals(listOf("1"), feed.state.value.posts.map { it.id })
        assertNull(feed.state.value.throttledUntil)
    }

    @Test
    fun aGuestShownNothingNeedsToSignInAndASessionShownNothingDoesNot(): Unit = runBlocking {
        val guest = ThreadsForYouFeed({ ThreadsForYouPage(ThreadsForYouSource.GUEST, emptyList()) })
        guest.refresh()
        assertTrue(guest.state.value.needsSignIn)
        assertFalse(guest.state.value.ended)

        val session = ThreadsForYouFeed({ ThreadsForYouPage(ThreadsForYouSource.SESSION, emptyList()) })
        session.refresh()
        assertFalse(session.state.value.needsSignIn)
        assertTrue(session.state.value.loaded)
    }

    @Test
    fun aGuestSentToTheSignInPageIsShownNothingRatherThanAnError(): Unit = runBlocking {
        val meta = FakeMeta { ok("<html><head><title>Log in</title></head><body>Log in with Instagram</body></html>") }
        val forYou = ThreadsForYou(direct(meta, time = time))
        val feed = ThreadsForYouFeed(forYou::page, time.clock)
        feed.refresh()
        assertTrue(feed.state.value.needsSignIn)
        assertNull(feed.state.value.problem)
    }

    @Test
    fun aGuestRateLimitWaitsAQuarterOfAnHour(): Unit = runBlocking {
        val meta = FakeMeta { XResponse(429, "") }
        val feed = ThreadsForYouFeed(ThreadsForYou(direct(meta, time = time))::page, time.clock)
        feed.refresh()
        assertEquals(Instant.ofEpochMilli(time.now + 15 * 60_000), feed.state.value.throttledUntil)
        feed.refresh()
        assertEquals(1, meta.asked.size)
    }

    @Test
    fun whatWasShownLastIsPaintedBeforeAnythingIsAsked(): Unit = runBlocking {
        val at = Instant.ofEpochSecond(1_700_000_000)
        val feed = ThreadsForYouFeed({ pageOf("9") })
        feed.restore(listOf(post("1"), post("1"), post("2")), ThreadsForYouSource.GUEST, at)
        val restored = feed.state.value
        assertEquals(listOf("1", "2"), restored.posts.map { it.id })
        assertEquals(at, restored.readAt)
        assertFalse(restored.loaded, "restored posts are no answer from Meta")
        feed.refresh()
        feed.restore(listOf(post("5")), ThreadsForYouSource.GUEST, at)
        assertEquals(listOf("9"), feed.state.value.posts.map { it.id }, "a restore never replaces what was read")
    }
}
