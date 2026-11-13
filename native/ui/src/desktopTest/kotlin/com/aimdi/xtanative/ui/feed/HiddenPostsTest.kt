package com.aimdi.xtanative.ui.feed

import com.aimdi.xtanative.core.feed.FeedSort
import com.aimdi.xtanative.core.feed.FeedShaping
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.store.saved.ProfileFilters
import com.aimdi.xtanative.store.saved.ProfileFiltersStore
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class HiddenPostsTest {
    private fun post(id: String, by: String, repost: Boolean = false, replyTo: String? = null) =
        XPost(id, XUser("id-$by", by, by), null, "post $id", reposted = if (repost) XPost("r$id", XUser("x", "x", "x"), null, "") else null, replyToScreenName = replyTo)

    private fun thread(post: XPost) = XThread(post.id, listOf(post))

    private val filters = ProfileFilters(hiddenReposts = mapOf("id-tommy" to "tommy"), hiddenReplies = mapOf("id-maya" to "maya"))

    @Test
    fun aFeedShapesWithTheHandlesTheFiltersHide() {
        val shaping = HiddenPosts.shaping(filters, FeedShaping(sort = FeedSort.POPULAR))
        assertEquals(setOf("tommy"), shaping.hiddenReposts)
        assertEquals(setOf("maya"), shaping.hiddenReplies)
        assertEquals(FeedSort.POPULAR, shaping.sort, "what the feed shapes with stays")
    }

    @Test
    fun theCacheShowsNothingTheReaderHid() {
        val threads = listOf(
            thread(post("1", "tommy", repost = true)),
            thread(post("2", "tommy", replyTo = "maya")),
            thread(post("3", "maya", replyTo = "tommy")),
            thread(post("4", "maya", replyTo = "maya")),
            thread(post("5", "maya", repost = true)),
        )
        assertEquals(listOf("2", "4", "5"), HiddenPosts.visible(threads, filters).map { it.id })
        assertEquals(threads, HiddenPosts.visible(threads, ProfileFilters()))
    }

    @Test
    fun theFeedsFollowTheFiltersAsTheyChange(): Unit = runBlocking {
        val dir = Files.createTempDirectory("filters").toFile()
        try {
            val store = ProfileFiltersStore(StoreDir(dir))
            val seen = Channel<ProfileFilters>(Channel.UNLIMITED)
            val feed = launch { HiddenPosts.of(store).collect { seen.send(it) } }
            assertEquals(ProfileFilters(), seen.receive(), "read even before anything else read them")
            store.setRepostsHidden("id-tommy", "Tommy", true)
            assertEquals(setOf("tommy"), seen.receive().hiddenRepostHandles)
            feed.cancel()
        } finally {
            dir.deleteRecursively()
        }
    }
}
