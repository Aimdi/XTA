package com.aimdi.xtanative.data

import com.aimdi.xtanative.core.feed.FeedPlace
import com.aimdi.xtanative.core.feed.SavedFeed
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

/** Two screens on one feed write its cache at once; the next start still reads a whole feed. */
class FeedCacheTest {
    private fun saved(key: String) = SavedFeed(emptyList(), null, savedAt = 1, key = key)

    @Test
    fun aFeedReadAgainUnchangedIsNotDecodedAgain(): Unit = runBlocking {
        val folder = Files.createTempDirectory("feed-cache").toFile()
        val cache = FeedCache(File(folder, "feed.json"))
        assertNull(cache.read())
        cache.write(saved("one"))
        val first = assertNotNull(cache.read())
        assertSame(first, cache.read())
        cache.writePlace(FeedPlace("p", 0))
        assertSame(assertNotNull(cache.readPlace()), cache.readPlace())
        folder.deleteRecursively()
    }

    @Test
    fun aWriteHereOrAFileChangedElsewhereIsReadAfresh(): Unit = runBlocking {
        val folder = Files.createTempDirectory("feed-cache").toFile()
        val file = File(folder, "feed.json")
        val cache = FeedCache(file)
        cache.write(saved("one"))
        val first = assertNotNull(cache.read())
        cache.write(saved("two"))
        val second = assertNotNull(cache.read())
        assertNotSame(first, second)
        assertEquals("two", second.key)
        file.writeText(SavedFeed.encode(saved("three, longer")))
        assertEquals("three, longer", cache.read()?.key, "written in place, by another")
        val replaced = File(folder, "staged.json").apply { writeText(SavedFeed.encode(saved("four!"))) }
        Files.move(replaced.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        assertEquals("four!", cache.read()?.key, "the same size, replaced whole")
        file.delete()
        assertNull(cache.read(), "cleared, as an import clears the feeds")
        folder.deleteRecursively()
    }

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
