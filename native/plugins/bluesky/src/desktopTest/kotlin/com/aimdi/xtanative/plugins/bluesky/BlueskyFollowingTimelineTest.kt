package com.aimdi.xtanative.plugins.bluesky

import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.bluesky.BlueskyFollowing
import com.aimdi.xtanative.networks.bluesky.BlueskyProfile
import com.aimdi.xtanative.networks.bluesky.bluePost
import com.aimdi.xtanative.networks.bluesky.ok
import com.aimdi.xtanative.networks.bluesky.postView
import com.aimdi.xtanative.store.settings.BlueskyPrefs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

/** XTA's bluesky_feed_store_test.dart: the Following timeline the plugin's screens share. */
class BlueskyFollowingTimelineTest {
    private val alice = BlueskyProfile(did = "did:plc:alice", handle = "alice.bsky.social", displayName = "Alice")
    private val bob = BlueskyProfile(did = "did:plc:bob", handle = "bob.bsky.social", displayName = "Bob")

    private fun page(vararg ids: String, did: String) =
        ok("""{"feed":[${ids.joinToString(",") { """{"post":${postView(it, did.removePrefix("did:plc:") + ".bsky.social", did)}}""" }}]}""")

    @Test
    fun anEmptyAnswerIsNotAskedAgainOnEntryButAPullAsks() = runBlocking {
        val host = FakeHost { ok("""{"feed":[]}""") }
        val app = BlueskyApp(host)
        app.follows.follow(alice, at = 1)
        app.following.start()
        eventually { app.following.state.value.loaded }
        app.following.refresh(force = false)
        eventually { !app.following.state.value.loading }
        assertEquals(1, host.asked.size)
        app.following.refresh(force = true)
        eventually { host.asked.size == 2 && !app.following.state.value.loading }
        app.close()
    }

    @Test
    fun followingAnotherAccountReadsItAndTheSamePostIsOneRow() = runBlocking {
        val host = FakeHost { url -> if (url.queryParameter("actor") == "did:plc:bob") page("b", "shared", did = "did:plc:alice") else page("a", "shared", did = "did:plc:alice") }
        val app = BlueskyApp(host)
        app.follows.follow(alice, at = 1)
        app.following.start()
        eventually { app.following.state.value.posts.isNotEmpty() }
        val painted = app.following.state.value
        app.following.refresh(force = false)
        eventually { !app.following.state.value.loading }
        assertEquals(painted.posts, app.following.state.value.posts, "an unchanged read leaves the list as it is")
        assertEquals(1, host.asked.size, "within ten minutes the page is reused")
        app.follows.follow(bob, at = 2)
        eventually { app.following.state.value.posts.size == 3 }
        assertEquals(setOf("a", "b", "shared"), app.following.state.value.posts.map { it.text }.toSet())
        app.close()
    }

    @Test
    fun aReadThatOnlyFailsLeavesThePostsShown() = runBlocking {
        var fail = false
        val host = FakeHost { if (fail) XResponse(500, "failed") else page("a", did = "did:plc:alice") }
        val app = BlueskyApp(host)
        app.follows.follow(alice, at = 1)
        app.following.start()
        eventually { app.following.state.value.posts.isNotEmpty() }
        assertNull(app.following.state.value.problem)
        fail = true
        app.prefs.set(BlueskyPrefs.instance, "https://other.test")
        eventually { host.asked.any { it.host == "other.test" } && !app.following.state.value.loading }
        assertEquals(listOf("a"), app.following.state.value.posts.map { it.text })
        assertNotNull(app.following.state.value.problem)
        app.close()
    }

    @Test
    fun aFailedFirstReadIsTriedAgainWhenTheTabIsShownAgain() = runBlocking {
        var fail = true
        val host = FakeHost { if (fail) XResponse(500, "offline") else page("recovered", did = "did:plc:alice") }
        val app = BlueskyApp(host)
        app.follows.follow(alice, at = 1)
        app.following.start()
        eventually { app.following.state.value.problem != null && !app.following.state.value.loading }
        fail = false
        app.following.start()
        eventually { app.following.state.value.posts.isNotEmpty() && !app.following.state.value.loading }
        assertEquals(listOf("recovered"), app.following.state.value.posts.map { it.text })
        assertNull(app.following.state.value.problem)
        assertEquals(2, host.asked.size)
        app.close()
    }

    @Test
    fun unfollowingEveryoneEmptiesTheTimelineAndAGroupsReadLeavesIt() = runBlocking {
        val host = FakeHost { url -> page(url.queryParameter("actor")!!.removePrefix("did:plc:"), did = "did:plc:alice") }
        val app = BlueskyApp(host)
        app.follows.follow(alice, at = 1)
        app.following.start()
        eventually { app.following.state.value.posts.isNotEmpty() }
        val before = app.following.state.value
        assertEquals(listOf("bob"), app.reader.posts(listOf("did:plc:bob"), force = true).map { it.text })
        assertSame(before, app.following.state.value, "a group's read is not Following's")
        app.follows.unfollow("did:plc:alice")
        eventually { app.following.state.value.followed == 0 && !app.following.state.value.loading }
        assertTrue(app.following.state.value.posts.isEmpty())
        app.close()
    }

    @Test
    fun anOlderReadNeverOverwritesANewerOne() = runBlocking {
        val answers = mutableListOf<CompletableDeferred<XResponse>>()
        val host = FakeHost { CompletableDeferred<XResponse>().also { synchronized(answers) { answers += it } }.await() }
        val app = BlueskyApp(host)
        app.follows.follow(alice, at = 1)
        eventually { app.follows.state.value != null }
        app.following.refresh(force = true)
        eventually { answers.size == 1 }
        app.following.refresh(force = true)
        eventually { answers.size == 2 }
        answers[1].complete(page("current", did = "did:plc:alice"))
        eventually { app.following.state.value.posts.isNotEmpty() }
        answers[0].complete(page("old", did = "did:plc:alice"))
        Thread.sleep(50)
        assertEquals(listOf("current"), app.following.state.value.posts.map { it.text })
        assertEquals(listOf("current"), app.reader.posts(listOf("did:plc:alice")).map { it.text }, "the page kept is the newer one")
        app.close()
    }

    @Test
    fun anAnswerFromTheAppViewLeftIsNeverKept() = runBlocking {
        val first = CompletableDeferred<XResponse>()
        val host = FakeHost { url -> if (url.host == "one.test") first.await() else page("two", did = "did:plc:alice") }
        val app = BlueskyApp(host)
        app.prefs.set(BlueskyPrefs.instance, "https://one.test")
        eventually { app.prefs.appView.value == "https://one.test" }
        val reader = BlueskyFollowing(app.client)
        val old = async(Dispatchers.Default) { reader.read(listOf("did:plc:alice")) }
        eventually { host.asked.isNotEmpty() }
        app.prefs.set(BlueskyPrefs.instance, "https://two.test")
        eventually { app.prefs.appView.value == "https://two.test" }
        assertEquals(listOf("two"), reader.read(listOf("did:plc:alice")).posts.map { it.text })
        first.complete(page("one", did = "did:plc:alice"))
        old.await()
        assertEquals(listOf("two"), reader.read(listOf("did:plc:alice")).posts.map { it.text })
        assertEquals(2, host.asked.size, "the page kept is the new AppView's, still fresh")
        app.close()
    }

    @Test
    fun aChangeOfWhoIsFollowedDropsTheReadUnderWayAndAnEndedAppTakesNoLateAnswer() = runBlocking {
        val alices = CompletableDeferred<XResponse>()
        val host = FakeHost { url -> if (url.queryParameter("actor") == "did:plc:alice") alices.await() else page("bob", did = "did:plc:bob") }
        val app = BlueskyApp(host)
        app.follows.follow(alice, at = 1)
        app.following.start()
        eventually { host.asked.isNotEmpty() }
        app.follows.unfollow("did:plc:alice")
        app.follows.follow(bob, at = 2)
        eventually { app.following.state.value.posts.isNotEmpty() }
        alices.complete(page("alice", did = "did:plc:alice"))
        Thread.sleep(50)
        assertEquals(listOf("bob"), app.following.state.value.posts.map { it.text })

        val late = CompletableDeferred<XResponse>()
        val ending = BlueskyApp(FakeHost { late.await() })
        ending.follows.follow(alice, at = 1)
        ending.following.start()
        Thread.sleep(50)
        ending.close()
        late.complete(page("late", did = "did:plc:alice"))
        Thread.sleep(50)
        assertTrue(ending.following.state.value.posts.isEmpty())
    }

    @Test
    fun aNewCountOrLabelOfTheSamePostIsAChange() {
        assertNotEquals(bluePost("same"), bluePost("same", likes = 3))
        assertNotEquals(bluePost("same"), bluePost("same", labels = listOf("!warn")))
    }
}
