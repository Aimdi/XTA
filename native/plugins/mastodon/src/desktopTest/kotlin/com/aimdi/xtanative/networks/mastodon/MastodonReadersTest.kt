package com.aimdi.xtanative.networks.mastodon

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray

/** The screens' readers, as XTA's Mastodon stores behave: timelines, Following, a profile, a thread and a search. */
class MastodonReadersTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun stop() = scope.cancel()

    private suspend fun <T> StateFlow<T>.until(done: (T) -> Boolean): T = withTimeout(5_000) { first(done) }

    private fun post(id: String) = MastodonPost(id = id, acct = "a@one.example", authorName = "A", text = "post $id", url = "https://one.example/@a/$id")

    @Test
    fun aPublicTimelineGoesOnAtTheInstanceThatGaveIt(): Unit = runBlocking {
        val fake = FakeMastodon { asked ->
            when {
                asked.host == "down.example" -> answer(503)
                asked.query("max_id") == null -> ok(pageJson(30, "a"))
                else -> ok(pageJson(3, "b"))
            }
        }
        val reader = MastodonPublicReader(MastodonClient(fake), { listOf("https://down.example", "https://up.example") }, local = true, scope)
        reader.refresh()
        val first = reader.state.until { it.loaded }
        assertEquals("https://up.example", first.instance)
        assertEquals(30, first.posts.size)
        assertEquals("a-29", first.next)
        reader.loadMore()
        val more = reader.state.until { it.posts.size == 33 && !it.loadingMore }
        assertNull(more.next)
        val last = fake.asked.last()
        assertEquals(listOf("up.example", "a-29", "true"), listOf(last.host, last.query("max_id"), last.query("local")))
    }

    @Test
    fun aFailedRefreshKeepsWhatWasShown(): Unit = runBlocking {
        var fail = false
        val reader = MastodonPublicReader(MastodonClient(FakeMastodon { if (fail) answer(429) else ok(pageJson(2)) }), { listOf("https://one.example") }, false, scope)
        reader.refresh()
        reader.state.until { it.loaded && !it.loading }
        fail = true
        reader.refresh()
        val failed = reader.state.until { it.error != null }
        assertEquals(2, failed.posts.size)
        assertEquals(MastodonFailure.RATE_LIMITED, MastodonFailure.of(failed.error!!))
    }

    @Test
    fun restoredPostsShowAtOnceAndGoOnBelowTheLast() {
        val reader = MastodonPublicReader(MastodonClient(FakeMastodon { ok(pageJson(0)) }), { emptyList() }, true, scope)
        val posts = listOf(post("1"), post("2"), post("3"))
        reader.restore(posts, "https://one.example")
        assertEquals(posts, reader.state.value.posts)
        assertEquals("3", reader.state.value.next)
        assertTrue(reader.state.value.loaded)
        reader.restore(listOf(post("9")), null)
        assertEquals(3, reader.state.value.posts.size)
    }

    @Test
    fun aForgottenReadChangesNothing(): Unit = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val reader = MastodonPublicReader(MastodonClient(FakeMastodon { gate.await(); ok(pageJson(2)) }), { listOf("https://one.example") }, true, scope)
        reader.refresh()
        reader.forget()
        gate.complete(Unit)
        delay(200)
        assertEquals(MastodonTimelineState(), reader.state.value)
    }

    @Test
    fun followingShowsAccountsAsTheyAnswer(): Unit = runBlocking {
        val held = CompletableDeferred<Unit>()
        val fake = FakeMastodon { asked ->
            if (asked.path == "/api/v1/accounts/lookup") {
                val acct = asked.query("acct")!!
                ok(accountJson(acct.substringBefore('@'), acct.substringBefore('@'), acct))
            } else {
                val user = asked.path.split('/')[4]
                if (user == "slow") held.await()
                ok(JsonArray(listOf(statusJson("$user-1", "https://origin.example/@$user/1", user, user, "$user@origin.example"))))
            }
        }
        val reader = MastodonFollowingReader(MastodonFollowing(MastodonClient(fake)), { listOf("fast@origin.example", "slow@origin.example") }, scope)
        reader.refresh(force = false)
        val partial = reader.state.until { it.posts.isNotEmpty() }
        assertEquals(listOf("fast@origin.example"), partial.posts.map { it.acct })
        assertTrue(partial.loading)
        held.complete(Unit)
        val done = reader.state.until { !it.loading }
        assertEquals(setOf("fast@origin.example", "slow@origin.example"), done.posts.map { it.acct }.toSet())
    }

    @Test
    fun followingKeepsOnlyWhatFollowedAccountsWroteOrBoosted() {
        val boosted = post("2").copy(acct = "other@x.example", boosted = true, boostedByAcct = "a@one.example")
        val kept = MastodonFollowingReader.kept(listOf(post("1"), boosted, post("3").copy(acct = "stranger@x.example"))) { it == "a@one.example" }
        assertEquals(listOf("1", "2"), kept.map { it.id })
    }

    @Test
    fun aProfileOpensOnItsPostsPinnedFirstAndReadsATabWhenShown(): Unit = runBlocking {
        val fake = FakeMastodon { asked ->
            when {
                asked.path == "/api/v1/accounts/lookup" -> ok(accountJson("7", "alice"))
                asked.query("pinned") == "true" -> ok(list(statusJson("pin", "https://one.example/@alice/pin", "pinned", "alice")))
                asked.query("exclude_replies") == "false" -> ok(pageJson(2, "r"))
                else -> ok(pageJson(20, "p"))
            }
        }
        val reader = MastodonProfileReader(MastodonClient(fake), { listOf("https://one.example") }, "alice@one.example", scope)
        reader.refresh()
        val opened = reader.state.until { it.profile != null }
        assertEquals("https://one.example", opened.instance)
        assertEquals("pin", opened.current.posts.first().id)
        assertEquals(setOf("pin"), opened.pinnedIds)
        assertTrue(opened.current.more)
        reader.select(MastodonProfileTab.REPLIES)
        val replies = reader.state.until { it.timeline(MastodonProfileTab.REPLIES).loaded }
        assertEquals(2, replies.current.posts.size)
        assertFalse(replies.current.more)
    }

    @Test
    fun aThreadFoldsItsRepliesAndAuthorFocusUnfoldsThem(): Unit = runBlocking {
        val url = "https://one.example/@b/1"
        val fake = FakeMastodon { asked ->
            if (asked.path.endsWith("/context")) {
                ok(obj("ancestors" to list(), "descendants" to list(reply("2", "1"), reply("3", "2"), reply("4", "1"))))
            } else {
                ok(statusJson("1", url, "root"))
            }
        }
        val seed = MastodonPost(id = "1", acct = "b@one.example", authorName = "B", text = "", url = url)
        val reader = MastodonThreadReader(MastodonClient(fake), { listOf("https://one.example") }, seed, MastodonReplyOrder.OLDEST, scope)
        reader.refresh()
        val read = reader.state.until { !it.loading && it.thread.descendants.isNotEmpty() }
        assertEquals(listOf("2", "3", "4"), read.replies.map { it.post.id })
        reader.setAllExpanded(false)
        assertEquals(listOf("2", "4"), reader.state.value.replies.map { it.post.id })
        reader.selectAuthor(true)
        assertEquals(emptySet(), reader.state.value.collapsed)
        reader.toggle("2")
        assertEquals(setOf("2"), reader.state.value.collapsed)
    }

    @Test
    fun aSearchOpensOnTheFirstTabThatFoundSomething() {
        val profile = MastodonProfile("1", "a@x.example", "a", "A", "", "https://x.example/@a")
        assertEquals(MastodonSearchTab.ACCOUNTS, MastodonSearchReader.firstTab(MastodonSearchPage(accounts = listOf(profile), posts = listOf(post("1")))))
        assertEquals(MastodonSearchTab.POSTS, MastodonSearchReader.firstTab(MastodonSearchPage(posts = listOf(post("1")))))
        assertEquals(MastodonSearchTab.HASHTAGS, MastodonSearchReader.firstTab(MastodonSearchPage(tags = listOf(MastodonTag("cats")))))
        assertEquals(MastodonSearchTab.ACCOUNTS, MastodonSearchReader.firstTab(MastodonSearchPage()))
    }

    private fun reply(id: String, to: String) = statusJson(id, "https://one.example/@b/$id", "reply $id", extra = listOf("in_reply_to_id" to to))
}
