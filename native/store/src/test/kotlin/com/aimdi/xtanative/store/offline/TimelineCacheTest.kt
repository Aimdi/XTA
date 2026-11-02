package com.aimdi.xtanative.store.offline

import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.store.saved.sha256
import com.aimdi.xtanative.store.saved.tempStore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

class TimelineCacheTest {
    private val dir = tempStore()
    private var now = System.currentTimeMillis()
    private val cache = TimelineCache(dir) { now }
    private val hour = 60L * 60 * 1000

    private fun page(text: String, bottom: String? = null, showMore: String? = null) =
        CachedTimeline(listOf(XThread("123", listOf(XPost("123", XUser("1", "amy", "Amy"), null, text)))), bottomCursor = bottom, showMoreCursor = showMore)

    @Test
    fun aWrittenThreadReadsBackWithItsCursors() = runBlocking {
        cache.write(TimelineCache.threadKey("123"), page("the post", bottom = "CURSOR-2", showMore = "SHOWMORE-Z"))
        val read = cache.read(TimelineCache.threadKey("123"), maxAgeMs = hour)!!
        assertEquals("the post", read.threads.single().posts.single().text)
        assertEquals("CURSOR-2", read.bottomCursor)
        assertEquals("SHOWMORE-Z", read.showMoreCursor)
    }

    @Test
    fun anOldPageIsAMissButStillThereForWhenTheNetworkGivesNothing() = runBlocking {
        cache.write("thread:1", page("old"))
        now += 2 * hour
        assertNull(cache.read("thread:1", maxAgeMs = hour))
        assertEquals("old", cache.readStale("thread:1")?.threads?.single()?.posts?.single()?.text)
    }

    @Test
    fun theNewerPageWinsAndAnEmptyOneNeverReplacesIt() = runBlocking {
        cache.write("thread:1", page("first"))
        cache.write("thread:1", page("second"))
        cache.write("thread:1", CachedTimeline(emptyList()))
        assertEquals("second", cache.readStale("thread:1")?.threads?.single()?.posts?.single()?.text)
    }

    @Test
    fun aRemovedMissingOrUnreadablePageIsAMissNotAnError() = runBlocking {
        cache.write("thread:1", page("kept"))
        cache.remove("thread:1")
        assertNull(cache.read("thread:1", hour))
        assertNull(cache.readStale("thread:nope"))
        File(dir.root, "timelines/${sha256("thread:bad")}.json").apply { parentFile.mkdirs(); writeText("{\"v\":1,\"key\":\"thread:bad\",\"threads\":\"nope\"}") }
        assertNull(cache.readStale("thread:bad"))
    }

    @Test
    fun threadsAndProfileVariantsNeverShareAKey() {
        assertNotEquals(TimelineCache.threadKey("1"), TimelineCache.profileKey("1", "tweets", includeReplies = false))
        assertNotEquals(TimelineCache.profileKey("1", "tweets", includeReplies = true), TimelineCache.profileKey("1", "tweets", includeReplies = false))
        assertNotEquals(TimelineCache.profileKey("1", "tweets", includeReplies = false), TimelineCache.profileKey("1", "media", includeReplies = false))
    }

    @Test
    fun clearEmptiesTheCacheAndTrimKeepsTheNewestWithinAWeek() = runBlocking {
        val folder = File(dir.root, "timelines")
        (0 until 25).forEach { i ->
            cache.write("thread:$i", page("$i"))
            File(folder, "${sha256("thread:$i")}.json").setLastModified(now - (25 - i) * 60_000L)
        }
        cache.trim(keep = 10)
        assertEquals(10, folder.list()!!.size)
        assertEquals("24", cache.readStale("thread:24")?.threads?.single()?.posts?.single()?.text, "the newest must survive")
        assertNull(cache.readStale("thread:14"), "the 11th newest must not")
        cache.trim(maxAgeMs = 0)
        assertEquals(0, folder.list()!!.size)
        cache.write("thread:new", page("new"))
        cache.clear()
        assertNull(cache.readStale("thread:new"))
    }
}
