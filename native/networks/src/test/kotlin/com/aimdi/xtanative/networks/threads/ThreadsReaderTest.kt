package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.x.http.XResponse
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

/**
 * What the screens read: the followed accounts' feed, a profile and a
 * conversation, as XTA's threads_store, threads_profile_store and
 * threads_thread_store read them (and their tests: threads_refresh_order,
 * threads_feed_store, threads_time_to_content, threads_profile_store,
 * threads_conversation).
 */
class ThreadsReaderTest {
    private val time = FakeTime()
    private val minutes = 60 * 1000L

    /** Which accounts guest GraphQL was asked for, in order. */
    private val asked = CopyOnWriteArrayList<String>()

    private var session = ThreadsSession()
    private var setup = ThreadsSetup()

    private fun account(request: Asked): String? =
        Regex(""""userID":"([^"]+)"""").find(request.form["variables"].orEmpty())?.groupValues?.get(1)

    /**
     * Threads for guests: a profile page carries a token, and GraphQL answers
     * each account's id (its handle here) with one post, a minute older per read.
     */
    private fun guestAnswer(request: Asked): XResponse {
        if (request.method == "GET") return ok(profilePage(userId = null))
        val handle = account(request) ?: return XResponse(500, "unexpected ${request.url}")
        asked += handle
        return ok(graphql(postJson("$handle-1", user = handle, text = "from $handle", takenAt = 1_767_225_600L - asked.size * 60)))
    }

    private val guest = FakeMeta { guestAnswer(it) }

    /** The ids of [handles] known, and a fresh token: each account read is one GraphQL request. */
    private fun knowing(handles: List<String>) =
        ThreadsMemory.InMemory(ThreadsKept(userIds = handles.associateWith { it }, guestLsd = "tok", guestLsdAt = time.now))

    private fun reader(meta: FakeMeta, memory: ThreadsMemory = ThreadsMemory.InMemory()) = ThreadsReader(
        direct = ThreadsDirectClient(meta, { session }, memory, { ThreadsRepairs() }, ThreadsPacing.NONE, time.clock),
        rssHub = ThreadsRssHub(meta),
        xy = ThreadsXy(meta),
        setup = { setup },
        clock = time.clock,
    )

    @Test
    fun successivePullsToRefreshReachEveryFollowedAccount() = runBlocking {
        val handles = (1..ThreadsReader.ACCOUNTS_PER_LOAD + 5).map { "a" + "$it".padStart(2, '0') }
        val reader = reader(guest, knowing(handles))

        reader.following(handles, force = true)
        val first = asked.toList()
        asked.clear()
        time.now += minutes
        val feed = reader.following(handles, force = true)
        val second = asked.toList()

        assertEquals(ThreadsReader.ACCOUNTS_PER_LOAD, first.size)
        assertEquals(ThreadsReader.ACCOUNTS_PER_LOAD, second.size)
        assertEquals(handles.drop(ThreadsReader.ACCOUNTS_PER_LOAD), second.take(5), "the five the first pull could not reach go first")
        assertEquals(handles.toSet(), (first + second).toSet())
        assertEquals(handles.size, feed.size, "the accounts past the budget give what they said last")
    }

    @Test
    fun anAccountReadInTheLastTenMinutesIsNotAskedAgain() = runBlocking {
        val reader = reader(guest, knowing(listOf("zuck")))
        reader.following(listOf("zuck"))
        reader.following(listOf("zuck"))
        assertEquals(listOf("zuck"), asked)
        assertEquals(1, reader.pending(listOf("zuck", "meta")), "only the account never read is pending")

        time.now += 10 * minutes + 1
        assertEquals(1, reader.pending(listOf("zuck")))
        reader.following(listOf("zuck"))
        assertEquals(listOf("zuck", "zuck"), asked)
        reader.following(listOf("zuck"), force = true)
        assertEquals(3, asked.size, "pulling to refresh asks again")
    }

    @Test
    fun theFeedIsNewestFirstWithAtMostTwentyThreadsOfEachAccount() = runBlocking {
        val meta = FakeMeta { request ->
            val handle = account(request)!!
            val count = if (handle == "busy") 25 else 1
            ok(graphql(*Array(count) { postJson("$handle-$it", user = handle, takenAt = if (handle == "quiet") null else 1_700_000_000L + it * 60) }))
        }
        val feed = reader(meta, knowing(listOf("busy", "quiet"))).following(listOf("busy", "quiet"))
        assertEquals(21, feed.size)
        assertEquals("busy-19", feed.first().id, "the newest of the twenty the account brought")
        assertEquals("quiet-0", feed.last().id, "an undated thread goes last")
    }

    @Test
    fun oneAccountFailingKeepsWhatItSaidLastAndTheOthersTheirs() = runBlocking {
        var failing = false
        val meta = FakeMeta { request -> if (failing && account(request) == "b") XResponse(500, "") else guestAnswer(request) }
        val reader = reader(meta, knowing(listOf("a", "b")))
        reader.following(listOf("a", "b"))
        failing = true
        val feed = reader.following(listOf("a", "b"), force = true)
        assertEquals(setOf("from a", "from b"), feed.map { it.text }.toSet())
    }

    @Test
    fun whenNothingAtAllCouldBeReadTheFailureIsWhatTheReaderHears() = runBlocking {
        var down = false
        val meta = FakeMeta { request -> if (down) XResponse(503, "") else guestAnswer(request) }
        val reader = reader(meta, knowing(listOf("a")))
        reader.following(listOf("a"))
        down = true
        val error = assertFailsWith<ThreadsException> { reader.following(listOf("a"), force = true) }
        assertEquals(ThreadsFailure.UNREACHABLE, error.failure, "threads kept only because their refresh failed are no answer")
    }

    @Test
    fun anotherSessionOrInstanceForgetsWhatWasRead() = runBlocking {
        val reader = reader(guest, knowing(listOf("zuck")))
        reader.following(listOf("zuck"))
        session = ThreadsSession(bearer = "IGT:2:new")
        reader.following(listOf("zuck"))
        setup = ThreadsSetup(rssHubInstance = "https://rsshub.example.org")
        reader.following(listOf("zuck"))
        assertEquals(listOf("zuck", "zuck", "zuck"), asked, "each change asked Meta again")
    }

    @Test
    fun cookiesAloneDoNotSpendTheSessionOnFollowedAccounts() = runBlocking {
        session = ThreadsSession(COOKIES)
        val reader = reader(guest, knowing(listOf("instagram")))
        assertEquals(listOf("from instagram"), reader.following(listOf("instagram")).map { it.text })
        assertTrue(guest.asked.none { it.path.contains("/text_feed/") })
    }

    @Test
    fun withSessionReadsOptedInSixAccountsAreAskedPerLoad() = runBlocking {
        session = ThreadsSession(COOKIES, useSessionApis = true)
        val handles = (1..8).map { "s$it" }
        val meta = FakeMeta { ok(fixture("text_feed.json")) }
        reader(meta, knowing(handles)).following(handles)
        assertEquals(ThreadsReader.SESSION_ACCOUNTS_PER_LOAD, meta.asked.count { it.path.contains("/text_feed/") })
    }

    @Test
    fun anRssHubInstanceIsAskedFirstAndTheGuestReadStandsInWhenItHasNothing() = runBlocking {
        setup = ThreadsSetup(rssHubInstance = "https://rsshub.example.org")
        val meta = FakeMeta { request ->
            when {
                request.url.host != "rsshub.example.org" -> guestAnswer(request)
                request.path.endsWith("/empty") -> ok("""{"items":[]}""")
                request.path.endsWith("/down") -> XResponse(503, "")
                else -> ok(fixture("rsshub_feed.json"))
            }
        }
        val feed = reader(meta, knowing(listOf("empty", "down", "zuck"))).following(listOf("empty", "down", "zuck"))
        assertEquals(listOf("empty", "down"), asked, "the instance answered for zuck itself")
        assertEquals(setOf("from empty", "from down", "Hello there\nsecond line\nNew paragraph", "Plain words win", ""), feed.map { it.text }.toSet())
    }

    @Test
    fun withNoAccountsTheBearersOwnTimelineIsTheFeed() = runBlocking {
        val meta = FakeMeta { ok(fixture("app_timeline.json")) }
        session = ThreadsSession(bearer = "IGT:2:secret")
        assertEquals(listOf("For you, first", "A post without its thread"), reader(meta).following(emptyList()).map { it.text })

        session = ThreadsSession(COOKIES)
        assertTrue(reader(meta).following(emptyList()).isEmpty(), "a session alone follows no one")
        session = ThreadsSession()
        setup = ThreadsSetup(rssHubInstance = "https://rsshub.example.org")
        assertTrue(reader(meta).following(emptyList()).isEmpty())
        setup = ThreadsSetup()
        assertEquals(ThreadsFailure.NOT_CONFIGURED, assertFailsWith<ThreadsException> { reader(meta).following(emptyList()) }.failure)
        assertEquals(1, meta.asked.size)
    }

    @Test
    fun aPastedBearerDoesNotHideTheFollowedAccounts() = runBlocking {
        session = ThreadsSession(bearer = "IGT:2:secret")
        assertEquals(listOf("from instagram"), reader(guest, knowing(listOf("instagram"))).following(listOf("instagram")).map { it.text })
        assertTrue(guest.asked.none { it.url.host == "i.instagram.com" })
    }

    @Test
    fun theFirstAccountsThreadsArriveBeforeTheSlowOnesAnswer() = runBlocking {
        val slow = CompletableDeferred<Unit>()
        val meta = FakeMeta { request ->
            if (account(request) != "a") slow.await()
            guestAnswer(request)
        }
        val partials = CopyOnWriteArrayList<List<ThreadsPost>>()
        val reader = reader(meta, knowing(listOf("a", "b", "c")))
        val reading = async { reader.following(listOf("a", "b", "c"), onPartial = { partials += it }) }
        repeat(100) { if (partials.isEmpty()) yield() }
        assertEquals(listOf("from a"), partials.last().map { it.text }, "the feed paints the first account, not waiting for all")
        slow.complete(Unit)
        assertEquals(3, reading.await().size)
        assertEquals(3, partials.last().size)
    }

    @Test
    fun aRefreshShowsWhatEachAccountSaidLastWhileItWaits() = runBlocking {
        val reader = reader(guest, knowing(listOf("a", "b")))
        reader.following(listOf("a", "b"))
        val partials = CopyOnWriteArrayList<List<ThreadsPost>>()
        reader.following(listOf("a", "b"), force = true, onPartial = { partials += it })
        assertEquals(2, partials.first().size, "the held feed is painted before the first wait")
    }

    @Test
    fun aFeedKeptAcrossARestartAsksMetaNothingWithinTenMinutes() = runBlocking {
        val reader = reader(guest, knowing(listOf("zuck")))
        val kept = ThreadsPost(id = "1", handle = "zuck", authorName = "Mark", text = "kept")
        reader.remember("zuck", listOf(kept), Instant.ofEpochMilli(time.now - minutes))
        assertEquals(0, reader.pending(listOf("zuck")))
        assertEquals(listOf("kept"), reader.following(listOf("zuck")).map { it.text })
        assertTrue(asked.isEmpty())
        assertEquals(mapOf("zuck" to Instant.ofEpochMilli(time.now - minutes)), reader.answeredAt)
        reader.forget()
        assertEquals(1, reader.pending(listOf("zuck")))
    }

    @Test
    fun aProfileIsItsCardAndItsThreadsReadTogetherFromOnePage() = runBlocking {
        val meta = FakeMeta { request ->
            when (request.path) {
                "/@zuck" -> {
                    delay(20)
                    ok(fixture("profile_page.html"))
                }
                "/api/graphql" -> ok(fixture("graphql_profile_threads.json"))
                else -> XResponse(404, "")
            }
        }
        val page = reader(meta).profile("zuck")
        assertEquals("Mark Zuckerberg", page.profile?.fullName)
        assertEquals(7, page.posts.size)
        assertEquals(1, meta.asked.count { it.path == "/@zuck" }, "the card and the threads share the page")
        assertEquals(6, page.tab(ThreadsProfileTab.POSTS).size, "the reply is not among the threads")
        assertEquals(listOf("3400000000000000007"), page.tab(ThreadsProfileTab.REPLIES).map { it.id })
        assertEquals(listOf("3400000000000000002", "3400000000000000006"), page.tab(ThreadsProfileTab.MEDIA).map { it.id })
    }

    @Test
    fun aProfileWhosePageGivesNoCardIsPiecedTogetherFromItsThreads() = runBlocking {
        val meta = FakeMeta { request -> ok(if (request.method == "GET") profilePage() else graphql(postJson("1", user = "maya", text = "kiln"))) }
        val page = reader(meta).profile("maya")
        assertEquals("MAYA", page.profile?.fullName)
        assertNull(page.profile?.followerCount)
    }

    @Test
    fun aProfileWithNeitherCardNorThreadsSurfacesTheFailure() {
        val reader = reader(FakeMeta { XResponse(404, "") })
        assertEquals(ThreadsFailure.NO_SUCH_FEED, assertFailsWith<ThreadsException> { runBlocking { reader.profile("nobody") } }.failure)
        assertEquals(ThreadsFailure.NO_SUCH_FEED, assertFailsWith<ThreadsException> { runBlocking { reader.profile("") } }.failure)
    }

    @Test
    fun aCardThePageCouldNotGiveIsAskedOfTheSessionOnceNotOfThePageTwice() = runBlocking {
        // XTA asked the public page a second time inside its session fallback: one more paced request for an answer it had.
        session = ThreadsSession(COOKIES, useSessionApis = true)
        val meta = FakeMeta { request ->
            when {
                request.path == "/@instagram" -> XResponse(404, "")
                request.path.endsWith("/info/") -> ok(fixture("user_info.json"))
                else -> ok(fixture("text_feed.json"))
            }
        }
        val page = reader(meta, knowing(listOf("instagram"))).profile("instagram")
        assertEquals("Discover what's new on Instagram", page.profile?.biography)
        assertEquals(1, meta.asked.count { it.path == "/@instagram" })
        assertEquals(listOf("From the cookie API"), page.posts.map { it.text })
    }

    @Test
    fun thenTheReadersXyServerIsAsked() = runBlocking {
        setup = ThreadsSetup(xyBase = "https://xy.example.org", xyToken = "tok")
        val meta = FakeMeta { request -> if (request.url.host == "xy.example.org") ok(fixture("xy_profile.json")) else XResponse(404, "") }
        val page = reader(meta).profile("zuck")
        assertEquals(4200000L, page.profile?.followerCount)
        assertTrue(page.posts.isEmpty())
    }

    @Test
    fun aRepliesPageThatFailsLeavesTheRepliesTheThreadsCarried() = runBlocking {
        val reader = reader(FakeMeta { XResponse(500, "") })
        val reply = ThreadsPost(id = "2", handle = "zuck", authorName = "Mark", text = "re", isReply = true)
        val page = ThreadsProfilePage(profile = null, posts = listOf(reply), replies = reader.replies("zuck"))
        assertEquals(emptyList<ThreadsPost>(), page.replies)
        assertEquals(listOf("2"), page.tab(ThreadsProfileTab.REPLIES).map { it.id })
    }

    @Test
    fun tabsSplitThreadsRepliesAndMediaWithoutRepeatsNewestFirst() {
        fun post(id: String, reply: Boolean = false, media: Boolean = false, minute: Long = 0) = ThreadsPost(
            id = id,
            handle = "zuck",
            authorName = "Mark",
            text = "post $id",
            isReply = reply,
            media = if (media) listOf(ThreadsMedia("https://cdn/p.jpg")) else emptyList(),
            publishedAt = Instant.ofEpochSecond(1_767_225_600L + minute * 60),
        )
        val page = ThreadsProfilePage(
            profile = null,
            posts = listOf(post("1", minute = 3), post("2", reply = true, minute = 2), post("3", media = true, minute = 1)),
            replies = listOf(post("2", reply = true, minute = 2), post("4", reply = true, media = true, minute = 4)),
        )
        assertEquals(listOf("1", "3"), page.tab(ThreadsProfileTab.POSTS).map { it.id })
        assertEquals(listOf("4", "2"), page.tab(ThreadsProfileTab.REPLIES).map { it.id })
        assertEquals(listOf("4", "3"), page.tab(ThreadsProfileTab.MEDIA).map { it.id })
    }

    @Test
    fun aConversationIsReadOnceAndReusedOnTheNextOpen() = runBlocking {
        val meta = FakeMeta { request ->
            assertEquals("www.threads.com", request.url.host)
            assertNull(request.url.query)
            ok(page(listOf(listOf(postJson("1")), listOf(postJson("2", user = "a")))))
        }
        val reader = reader(meta)
        val stub = ThreadsPost.linkStub("https://threads.net/@zuck/post/C1?igshid=abc", "zuck")
        val first = reader.conversation(stub)
        val second = reader.conversation(ThreadsPost(id = "1", handle = "zuck", authorName = "zuck", text = "post 1", url = "https://www.threads.com/@zuck/post/C1"))
        assertEquals(1, meta.asked.size)
        assertEquals("post 1", first.focus.text)
        assertEquals(listOf(listOf("2")), second.replies.map { chain -> chain.map { it.id } })
        reader.conversation(stub, force = true)
        assertEquals(2, meta.asked.size)
    }

    @Test
    fun aPostWithoutALinkHasNoConversationToRead() = runBlocking {
        val meta = FakeMeta { error("no request expected") }
        val conversation = reader(meta).conversation(ThreadsPost(id = "rss", handle = "zuck", authorName = "zuck", text = "from a feed"))
        assertTrue(conversation.loaded)
        assertTrue(meta.asked.isEmpty())
    }

    @Test
    fun aFailedConversationReadSaysWhy() {
        val error = assertFailsWith<ThreadsException> {
            runBlocking { reader(FakeMeta { XResponse(500, "nope") }).conversation(ThreadsPost.linkStub("https://www.threads.com/@zuck/post/C1")) }
        }
        assertEquals(ThreadsFailure.UNREACHABLE, error.failure)
    }
}
