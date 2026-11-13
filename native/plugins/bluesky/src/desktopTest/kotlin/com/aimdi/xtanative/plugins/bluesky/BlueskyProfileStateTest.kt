package com.aimdi.xtanative.plugins.bluesky

import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.bluesky.BlueskyPost
import com.aimdi.xtanative.networks.bluesky.BlueskyProfileTab
import com.aimdi.xtanative.networks.bluesky.bluePost
import com.aimdi.xtanative.networks.bluesky.ok
import com.aimdi.xtanative.networks.bluesky.postView
import com.aimdi.xtanative.ui.plugins.PagedItems
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import okhttp3.HttpUrl

/** XTA's bluesky_profile_upgrade_test.dart: a profile, its tabs and its pinned post. */
class BlueskyProfileStateTest {
    private fun method(url: HttpUrl) = url.encodedPath.substringAfterLast('/')

    private fun profile(did: String, pin: String? = null) =
        ok("""{"did":"$did","handle":"maya.bsky.social"${pin?.let { ""","pinnedPost":{"uri":"at://$did/app.bsky.feed.post/$it"}""" } ?: ""}}""")

    private fun feed(vararg ids: String, did: String) = ok("""{"feed":[${ids.joinToString(",") { """{"post":${postView(it, "maya.bsky.social", did)}}""" }}]}""")

    private fun texts(pages: PagedItems<BlueskyPost>?) = pages!!.state.value.items.map { it.text }

    @Test
    fun aFailedProfileReadKeepsWhatWasShownAndItsPostsReadAgain() = runBlocking {
        var failProfile = false
        var post = "old"
        val host = FakeHost { url ->
            when (method(url)) {
                "app.bsky.actor.getProfile" -> if (failProfile) XResponse(500, "failed") else profile("did:plc:maya")
                else -> feed(post, did = "did:plc:maya")
            }
        }
        val app = BlueskyApp(host)
        val scope = CoroutineScope(coroutineContext + Job())
        val state = ProfileState(app, "maya.bsky.social", scope)
        eventually { state.state.value.profile != null }
        val pages = state.pages(BlueskyProfileTab.POSTS)
        eventually { pages!!.state.value.loaded }
        failProfile = true
        post = "fresh"
        state.load()
        pages!!.refresh()
        eventually { state.state.value.problem != null && texts(pages) == listOf("fresh") }
        assertEquals("did:plc:maya", state.state.value.profile?.did, "the profile stays")
        assertNull(host.asked.last { method(it) == "app.bsky.feed.getAuthorFeed" }.queryParameter("cursor"), "page one again")
        scope.cancel()
        app.close()
    }

    @Test
    fun aHandleNowNamingAnotherAccountDropsTheOldAccountsPages() = runBlocking {
        var did = "did:plc:old"
        val host = FakeHost { url ->
            when (method(url)) {
                "app.bsky.actor.getProfile" -> profile(did)
                else -> feed(url.queryParameter("actor")!!.substringAfterLast(':'), did = did)
            }
        }
        val app = BlueskyApp(host)
        val scope = CoroutineScope(coroutineContext + Job())
        val state = ProfileState(app, "maya.bsky.social", scope)
        eventually { state.state.value.profile != null }
        val old = state.pages(BlueskyProfileTab.POSTS)
        eventually { old!!.state.value.loaded }
        assertSame(old, state.pages(BlueskyProfileTab.POSTS))
        did = "did:plc:new"
        state.load()
        eventually { state.state.value.profile?.did == did }
        val replaced = state.pages(BlueskyProfileTab.POSTS)
        assertNotSame(old, replaced)
        eventually { replaced!!.state.value.loaded }
        assertEquals(listOf("new"), texts(replaced))
        scope.cancel()
        app.close()
    }

    @Test
    fun aPinThatFailsKeepsTheFeedAndIsTriedAgainOnItsOwnAndARemovedPinGoes() = runBlocking {
        var failPin = true
        var pinned: String? = "pin"
        val host = FakeHost { url ->
            when (method(url)) {
                "app.bsky.actor.getProfile" -> profile("did:plc:maya", pinned)
                "app.bsky.feed.getPosts" -> if (failPin) XResponse(500, "failed") else ok("""{"posts":[${postView("pin", "maya.bsky.social", "did:plc:maya")}]}""")
                else -> feed("one", did = "did:plc:maya")
            }
        }
        val app = BlueskyApp(host)
        val scope = CoroutineScope(coroutineContext + Job())
        val state = ProfileState(app, "maya.bsky.social", scope)
        eventually { state.state.value.pinFailure != null }
        val pages = state.pages(BlueskyProfileTab.POSTS)
        eventually { pages!!.state.value.loaded }
        assertNull(state.state.value.problem)
        val feedReads = host.asked.count { method(it) == "app.bsky.feed.getAuthorFeed" }
        failPin = false
        state.retryPin()
        eventually { state.state.value.pinned != null }
        assertNull(state.state.value.pinFailure)
        assertEquals(listOf("pin", "one"), BlueskyProfileTab.POSTS.withPin(pages!!.state.value.items, state.state.value.pinned).map { it.text })
        assertEquals(feedReads, host.asked.count { method(it) == "app.bsky.feed.getAuthorFeed" }, "the pin is read on its own")

        pinned = null
        state.load()
        eventually { !state.state.value.loading && state.state.value.pinned == null }
        scope.cancel()
        app.close()
    }

    @Test
    fun aRefreshDropsAPageStillComingOfTheListBefore() = runBlocking {
        val scope = CoroutineScope(coroutineContext + Job())
        val late = CompletableDeferred<Page<BlueskyPost>>()
        var first = "old"
        val pages = PagedItems(scope, key = BlueskyPost::uri) { cursor -> if (cursor == null) Page(listOf(bluePost(first)), "next") else late.await() }
        repeat(5) { yield() }
        pages.more()
        repeat(5) { yield() }
        first = "new"
        pages.refresh()
        repeat(5) { yield() }
        late.complete(Page(listOf(bluePost("stale")), null))
        repeat(5) { yield() }
        assertEquals(listOf("new"), texts(pages))
        assertNotNull(pages.state.value.next)
        scope.cancel()
    }
}
