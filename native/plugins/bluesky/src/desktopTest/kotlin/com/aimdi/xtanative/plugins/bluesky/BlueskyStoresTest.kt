package com.aimdi.xtanative.plugins.bluesky

import com.aimdi.xtanative.networks.bluesky.BlueskyFollow
import com.aimdi.xtanative.networks.bluesky.BlueskyPost
import com.aimdi.xtanative.networks.bluesky.BlueskyProfile
import com.aimdi.xtanative.networks.bluesky.BlueskyReaderFilter
import com.aimdi.xtanative.networks.bluesky.BlueskyTab
import com.aimdi.xtanative.store.StoreFile
import com.aimdi.xtanative.store.settings.BlueskyPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.plugins.PluginSettings
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

class BlueskyStoresTest {
    private val dir: File = Files.createTempDirectory("bluesky-plugin").toFile()

    private fun file(name: String) = StoreFile(File(dir, name))

    private val maya = BlueskyProfile(did = "did:plc:maya", handle = "maya.bsky.social", displayName = "Maya Chen", avatarUrl = "https://cdn.example/maya.jpg")

    private fun post(id: String) = BlueskyPost(
        uri = "at://did:plc:maya/app.bsky.feed.post/$id",
        cid = "c$id",
        handle = "maya.bsky.social",
        did = "did:plc:maya",
        authorName = "Maya",
        text = "post $id",
        url = "https://bsky.app/profile/maya.bsky.social/post/$id",
    )

    /** The app's settings as a plugin sees them, kept in memory. */
    private class Prefs {
        val values = MutableStateFlow<Settings?>(Settings())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val settings = PluginSettings("bluesky", values) { change -> values.value = change(values.value ?: Settings()) }
        val prefs = BlueskyPreferences(settings, scope)
    }

    @Test
    fun aFollowIsKeptOnTheDevice(): Unit = runBlocking {
        BlueskyFollows(file("follows.json")).follow(maya, at = 1000)
        val read = BlueskyFollows(file("follows.json")).load()
        assertEquals(listOf(BlueskyFollow("did:plc:maya", "maya.bsky.social", "Maya Chen", "https://cdn.example/maya.jpg", 1000)), read)
    }

    @Test
    fun unfollowingByHandleOrDidRemovesTheAccount(): Unit = runBlocking {
        val follows = BlueskyFollows(file("follows.json"))
        follows.follow(maya, at = 1000)
        assertTrue(follows.follows("MAYA.bsky.social"))
        follows.unfollow("MAYA.bsky.social")
        assertEquals(emptyList(), follows.load())
        follows.follow(maya, at = 1000)
        follows.unfollow("did:plc:maya")
        assertEquals(emptyList(), follows.load())
    }

    @Test
    fun anImportCountsOnlyTheAccountsItAdded(): Unit = runBlocking {
        val follows = BlueskyFollows(file("follows.json"))
        follows.follow(maya, at = 1000)
        val added = follows.followAll(listOf(maya, BlueskyProfile("did:plc:sam", "sam.bsky.social", "Sam")), at = 2000)
        assertEquals(1, added)
        assertEquals(listOf("Maya Chen", "Sam"), follows.load().map { it.name })
    }

    @Test
    fun xtasRowsComeInAndGoOutAsXtaWritesThem(): Unit = runBlocking {
        val follows = BlueskyFollows(file("follows.json"))
        follows.follow(maya, at = 1000)
        follows.importRows("""[{"id": "sam.bsky.social", "name": "Sam", "avatar_url": null, "in_feed": 1, "created_at": "2026-01-02 03:04:05"}]""", Instant.EPOCH)
        assertEquals(listOf("maya.bsky.social", "sam.bsky.social"), follows.load().map { it.handle })
        val rows = follows.exportRows()
        assertTrue("\"id\":\"maya.bsky.social\"" in rows && "\"id\":\"sam.bsky.social\"" in rows)
        assertTrue("\"created_at\":\"2026-01-02 03:04:05\"" in rows)

        val earlier = """{"v": 1, "follows": [{"did": "did:plc:lee", "handle": "lee.bsky.social", "name": "Lee", "followedAt": 5}]}"""
        follows.importOwn(earlier)
        assertEquals(listOf("Maya Chen", "Sam", "Lee"), follows.load().map { it.name }, "an earlier build's section joins what was kept, as XTA's rows do")
    }

    @Test
    fun aLikeKeepsItsPostToShowInTheAppsSettings(): Unit = runBlocking {
        val prefs = Prefs()
        val likes = BlueskyLikes(file("likes.json"), prefs.prefs)
        likes.toggle(post("1"), at = 1)
        likes.toggle(post("2"), at = 2)
        assertTrue(likes.isLiked(post("1").uri))
        assertEquals(listOf(post("2").uri, post("1").uri), prefs.prefs.likedPosts.value.map { it.uri })
        assertTrue(BlueskyPrefs.likedPosts.key in prefs.values.value!!.values, "kept where XTA keeps them: the app's backup carries them")

        likes.toggle(post("1"), at = 3)
        assertFalse(likes.isLiked(post("1").uri))
        assertEquals(listOf(post("2").uri), prefs.prefs.likedPosts.value.map { it.uri })
        assertEquals("""[{"id":"${post("2").uri}"}]""", likes.exportRows())

        likes.importRows("""[{"id": "${post("9").uri}"}]""", Instant.EPOCH)
        assertTrue(BlueskyLikes(file("likes.json"), prefs.prefs).let { it.load(); it.isLiked(post("9").uri) })
        prefs.scope.cancel()
    }

    @Test
    fun theFiltersAndTheTabAreKeptAsXtaKeepsThem(): Unit = runBlocking {
        val prefs = Prefs()
        prefs.prefs.reading { it.copy(tab = BlueskyTab.LISTS, following = BlueskyReaderFilter(hideReplies = true, query = "not kept")) }
        val stored = prefs.values.value!![BlueskyPrefs.readerPlaces]
        assertTrue("\"tab\":2" in stored)
        assertEquals(BlueskyTab.LISTS, prefs.prefs.reading.value.tab)
        assertEquals(BlueskyReaderFilter(hideReplies = true), prefs.prefs.reading.value.following)
        prefs.scope.cancel()
    }

    @Test
    fun whatAnEarlierBuildKeptInItsFolderBecomesTheFollowingFilters(): Unit = runBlocking {
        val prefs = Prefs()
        val old = file("settings.json").also { it.write("""{"replies": false, "reposts": true}""") }
        prefs.prefs.adopt(old)
        assertEquals(BlueskyReaderFilter(hideReplies = true), prefs.prefs.reading.value.following)
        assertEquals(null, old.read(), "and it is read once")
        prefs.scope.cancel()
    }

    @Test
    fun onlyBlueskysOwnPreferencesChange(): Unit = runBlocking {
        val prefs = Prefs()
        prefs.prefs.set(BlueskyPrefs.handle, "maya.bsky.social")
        prefs.prefs.remember("cats")
        prefs.prefs.remember("dogs")
        prefs.prefs.remember("cats")
        assertEquals("maya.bsky.social", prefs.prefs.handle.value)
        assertEquals(listOf("cats", "dogs"), prefs.prefs.searches.value)
        assertTrue(prefs.values.value!!.values.keys.all { it.startsWith("plugin.bluesky.") })
        prefs.scope.cancel()
    }
}
