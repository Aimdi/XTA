package com.aimdi.xtanative.data

import com.aimdi.xtanative.core.feed.FeedPlace
import com.aimdi.xtanative.core.feed.SavedFeed
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

/** Two screens on one feed write its cache at once; the next start still reads a whole feed. */
class FeedCacheTest {
    @Test
    fun writesAtOnceLeaveAWholeFeedAndNoStagingFiles(): Unit = runBlocking {
        val folder = Files.createTempDirectory("feed-cache").toFile()
        val cache = FeedCache(File(folder, "feed-g.json"))
        (0 until 16).map { n ->
            async(Dispatchers.IO) {
                cache.write(SavedFeed(emptyList(), null, savedAt = n.toLong(), key = "k".repeat(50_000)))
                cache.writePlace(FeedPlace("p$n", n))
            }
        }.awaitAll()
        assertEquals("k".repeat(50_000), assertNotNull(FeedCache(File(folder, "feed-g.json")).read()).key)
        assertNotNull(cache.readPlace())
        assertEquals(setOf("feed-g.json", "feed-g-place.json"), folder.list()!!.toSet())
        folder.deleteRecursively()
    }
}
