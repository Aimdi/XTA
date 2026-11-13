package com.aimdi.xtanative.networks.bluesky

import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.NetworkRequestException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl

class BlueskyFollowingTest {
    private var now = 1_700_000_000_000L
    private var appView: String? = null

    /** Each account answers with one post named after it, unless [failing] says it is down. */
    private val failing = mutableSetOf<String>()
    private val written = mutableMapOf<String, String>()

    private val http = FakeAppView { url -> answer(url) }
    private val client = BlueskyClient(http) { appView }

    private fun answer(url: HttpUrl): XResponse {
        val actor = url.queryParameter("actor")!!
        if (actor in failing) return XResponse(503, "")
        val id = actor.substringBefore('.')
        val time = written[actor] ?: "2026-08-01T10:00:00Z"
        return ok("""{"feed":[{"post":{"uri":"at://did:plc:$id/app.bsky.feed.post/$id","author":{"did":"did:plc:$id","handle":"$actor"},"record":{"text":"$id","createdAt":"$time"}}}]}""")
    }

    private fun following(filter: String? = null) = BlueskyFollowing(client, filter, clock = { now })

    private val asked get() = http.asked.map { it.queryParameter("actor")!! }

    @Test
    fun eachAccountsPageIsReadAndMergedNewestFirst() = runBlocking {
        written["alice.test"] = "2026-08-01T09:00:00Z"
        written["bob.test"] = "2026-08-01T11:00:00Z"
        val result = following().read(listOf("alice.test", "bob.test", "alice.test"))
        assertEquals(listOf("bob", "alice"), result.posts.map { it.text })
        assertNull(result.error)
        assertEquals(listOf("alice.test", "bob.test"), asked, "each account once")
        val url = http.asked.first()
        assertEquals(mapOf("actor" to "alice.test", "limit" to "20"), url.queryParameterNames.associateWith { url.queryParameter(it) })
    }

    @Test
    fun aFeedThatHidesRepliesAsksForPostsWithoutReplies() = runBlocking {
        following(Bluesky.AUTHOR_FEED_NO_REPLIES).read(listOf("alice.test"))
        assertEquals("posts_no_replies", http.asked.single().queryParameter("filter"))
    }

    @Test
    fun theSamePostFromTwoAccountsIsOneRow() = runBlocking {
        val shared = """{"post":{"uri":"at://did:plc:alice/app.bsky.feed.post/shared","author":{"did":"did:plc:alice","handle":"alice.test"},"record":{"text":"shared","createdAt":"2026-08-01T10:00:00Z"}}"""
        val http = FakeAppView { url ->
            if (url.queryParameter("actor") == "alice.test") ok("""{"feed":[$shared}]}""")
            else ok("""{"feed":[$shared,"reason":{"${'$'}type":"app.bsky.feed.defs#reasonRepost","by":{"handle":"bob.test"},"indexedAt":"2026-08-02T10:00:00Z"}}]}""")
        }
        val rows = BlueskyFollowing(BlueskyClient(http)).read(listOf("alice.test", "bob.test")).posts
        assertEquals(1, rows.size)
        assertEquals(false, rows.single().isRepost, "the followed original wins over a repost of it")
    }

    @Test
    fun aReadWithinTenMinutesReusesThePagesUnlessForced() = runBlocking {
        val feed = following()
        feed.read(listOf("alice.test"))
        feed.read(listOf("alice.test"))
        assertEquals(1, http.asked.size)
        assertEquals(0, feed.pending(listOf("alice.test")))
        feed.read(listOf("alice.test"), force = true)
        assertEquals(2, http.asked.size, "pull-to-refresh asks again")
        now += 10 * 60 * 1000L + 1
        assertEquals(1, feed.pending(listOf("alice.test")))
        feed.read(listOf("alice.test"))
        assertEquals(3, http.asked.size, "past ten minutes the page is read again")
    }

    @Test
    fun aFailedAccountKeepsItsLastPageAndIsAskedAgain() = runBlocking {
        val feed = following()
        feed.read(listOf("alice.test", "bob.test"))
        failing += "bob.test"
        written["alice.test"] = "2026-08-02T10:00:00Z"
        val partial = feed.read(listOf("alice.test", "bob.test"), force = true)
        assertEquals(listOf("alice", "bob"), partial.posts.map { it.text }, "bob's last page stays")
        val error = assertIs<NetworkRequestException>(partial.error)
        assertEquals(503, error.code)
        assertEquals(1, feed.pending(listOf("alice.test", "bob.test")))

        failing.clear()
        val recovered = feed.read(listOf("alice.test", "bob.test"))
        assertNull(recovered.error)
        assertEquals(6, http.asked.size, "a failure makes the next read ask every account again")
        assertEquals(0, feed.pending(listOf("alice.test", "bob.test")))
    }

    @Test
    fun nothingReadAndAFailureSaysSo() {
        failing += "alice.test"
        val feed = following()
        val result = runBlocking { feed.read(listOf("alice.test")) }
        assertTrue(result.posts.isEmpty())
        assertIs<NetworkRequestException>(result.error)
        assertFailsWith<NetworkRequestException> { runBlocking { feed.posts(listOf("alice.test")) } }
        assertTrue(runBlocking { following().posts(emptyList()) }.isEmpty())
    }

    @Test
    fun aBoundedReadProgressesPastTheFirstAccounts() = runBlocking {
        val actors = (0 until Bluesky.MAX_ACCOUNTS_PER_LOAD + 5).map { "actor$it.test" }
        val feed = following()
        assertEquals(30, feed.read(actors).posts.size)
        assertEquals(actors.take(30), asked)
        assertEquals(5, feed.pending(actors))
        feed.read(actors, force = true)
        assertEquals(actors.drop(30), asked.drop(30).take(5), "the accounts never asked come first")
        assertTrue(asked.toSet().containsAll(actors))
        assertEquals(0, feed.pending(actors))
    }

    @Test
    fun aFailedBatchCannotStarveTheAccountsNeverAsked() = runBlocking {
        val actors = (0 until Bluesky.MAX_ACCOUNTS_PER_LOAD + 5).map { "actor$it.test" }
        failing += actors
        val feed = following()
        feed.read(actors)
        feed.read(actors)
        assertTrue(asked.toSet().containsAll(actors))
    }

    @Test
    fun anotherAppViewForgetsWhatTheFirstOneAnswered() = runBlocking {
        val feed = following()
        feed.read(listOf("alice.test"))
        appView = "https://appview.example"
        assertEquals(1, feed.pending(listOf("alice.test")))
        feed.read(listOf("alice.test"))
        assertEquals(listOf("public.api.bsky.app", "appview.example"), http.asked.map { it.host })
    }
}
