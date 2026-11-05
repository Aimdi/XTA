package com.aimdi.xtanative.store.feed

import com.aimdi.xtanative.store.saved.tempStore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive

class FeedSnapshotsTest {
    private val dir = tempStore()
    private var now = 1_000_000_000_000L
    private val snapshots = FeedSnapshots(dir) { now }
    private val day = 24L * 60 * 60 * 1000

    private fun post(id: String, date: Long = 5) = SnapshotPost(id, date, JsonPrimitive("record $id"))

    @Test
    fun aSourcesAnswerReadsBackWithWhenItWasKept() = runBlocking {
        snapshots.write("bluesky:a", listOf(post("1", 20), post("2", 10)))
        now += day
        val read = assertNotNull(snapshots.read("bluesky:a"))
        assertEquals(now - day, read.at)
        assertEquals(listOf(post("1", 20), post("2", 10)), read.posts)
    }

    @Test
    fun aSnapshotOlderThanAWeekIsAMiss() = runBlocking {
        snapshots.write("k", listOf(post("1")))
        now += 7 * day + 1
        assertNull(snapshots.read("k"))
    }

    @Test
    fun onlyAHundredPostsOfOneAnswerAreKept() = runBlocking {
        snapshots.write("k", (1..150).map { post("$it") })
        assertEquals(100, snapshots.read("k")?.posts?.size)
    }

    @Test
    fun aDamagedOrMissingSnapshotIsAMissNotAnError() = runBlocking {
        assertNull(snapshots.read("nothing"))
        snapshots.write("k", listOf(post("1")))
        File(dir.root, "feed-snapshots").listFiles()!!.single().writeText("{not json")
        assertNull(snapshots.read("k"))
    }

    @Test
    fun theOldestSourcesGoPastForty() = runBlocking {
        repeat(45) { i ->
            snapshots.write("source-$i", listOf(post("$i")))
            File(dir.root, "feed-snapshots").listFiles()!!.forEach { it.setLastModified(it.lastModified() - 1_000) }
        }
        assertTrue(File(dir.root, "feed-snapshots").listFiles()!!.size <= FeedSnapshots.MAX_ENTRIES)
        assertNotNull(snapshots.read("source-44"))
        assertNull(snapshots.read("source-0"))
    }
}
