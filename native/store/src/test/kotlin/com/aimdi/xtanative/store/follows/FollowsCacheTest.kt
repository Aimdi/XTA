package com.aimdi.xtanative.store.follows

import com.aimdi.xtanative.store.saved.tempStore
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/** XTA's DiscoveryFollowsCache (lib/group/group_discovery_follows.dart), kept across launches. */
class FollowsCacheTest {
    private val dir = tempStore()
    private var now = 1_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun cache() = FollowsCache(dir, clock = { now })

    private fun follow(id: String) = KnownFollow(id, "h$id", "Name $id", "https://pbs/$id.jpg", "bio $id")

    @Test
    fun aListReadIsThereAfterARestart() {
        runBlocking {
            cache().remember("a", listOf(follow("p"), follow("q")))
            val again = cache().remembered()
            assertEquals(listOf("p", "q"), again.getValue("a").ids)
            assertEquals(follow("p"), again.getValue("a").follows.first())
            assertEquals(now, again.getValue("a").at)
        }
    }

    @Test
    fun aListIsTrustedForAMonthAndNoLonger() {
        runBlocking {
            cache().remember("a", listOf(follow("p")))
            now += 30 * day
            assertEquals(setOf("a"), cache().remembered().keys)
            now += 1
            assertTrue(cache().remembered().isEmpty())
        }
    }

    @Test
    fun aReadAsksOnlyForListsMissingOrOlderThanThreeDays() {
        runBlocking {
            val cache = cache()
            cache.remember("old", listOf(follow("1")))
            now += 3 * day + 1
            cache.remember("fresh", listOf(follow("2")))
            val asked = mutableListOf<String>()
            val read = cache.read(listOf("fresh", "old", "never"), maxFetches = 4, timeoutMillis = 1_000, fetch = { member ->
                synchronized(asked) { asked += member }
                listOf(follow("n-$member"))
            })
            assertEquals(setOf("never", "old"), asked.toSet())
            assertEquals(setOf("fresh", "old", "never"), read.follows.keys)
            assertEquals(listOf("n-old"), read.follows.getValue("old").map { it.id })
            assertNull(read.error)
        }
    }

    @Test
    fun theNeverReadComeFirstThenTheLongestUnreadUpToTheCap() {
        runBlocking {
            val cache = cache()
            cache.remember("b", emptyList())
            now += day
            cache.remember("a", emptyList())
            now += 4 * day
            assertEquals(listOf("c", "d", "b", "a"), cache.prioritize(listOf("a", "b", "c", "d")))
            val asked = mutableListOf<String>()
            cache.read(listOf("a", "b", "c", "d"), maxFetches = 3, timeoutMillis = 1_000, fetch = { member ->
                synchronized(asked) { asked += member }
                emptyList()
            })
            assertEquals(setOf("c", "d", "b"), asked.toSet())
        }
    }

    @Test
    fun aFailedReadKeepsTheOldListAndSaysWhy() {
        runBlocking {
            val cache = cache()
            cache.remember("a", listOf(follow("p")))
            now += 4 * day
            val partials = mutableListOf<Set<String>>()
            val read = cache.read(listOf("a", "b"), maxFetches = 2, timeoutMillis = 1_000, fetch = { member ->
                if (member == "a") throw IOException("offline")
                listOf(follow("q"))
            }, onPartial = { synchronized(partials) { partials += it.keys } })
            assertIs<IOException>(read.error)
            assertEquals(listOf("p"), read.follows.getValue("a").map { it.id })
            assertEquals(listOf("q"), read.follows.getValue("b").map { it.id })
            assertTrue(partials.any { "b" in it })
        }
    }

    @Test
    fun aReadThatTakesTooLongIsAFailureNotAHang() {
        runBlocking {
            val read = cache().read(listOf("slow"), maxFetches = 1, timeoutMillis = 50, fetch = {
                delay(5_000)
                emptyList()
            })
            assertTrue(read.follows.isEmpty())
            assertTrue(read.error != null)
        }
    }

    @Test
    fun anUnreadableFileIsAListNeverRead() {
        runBlocking {
            dir.file("follows/a.json").write("{ not json")
            assertTrue(cache().remembered().isEmpty())
            assertEquals("follows/12345.json", FollowsCache.fileName("12345"))
            assertEquals("follows/ab.json", FollowsCache.fileName("../a/b"))
        }
    }

    @Test
    fun feedbackIsKeptPerGroup() {
        runBlocking {
            val store = DiscoveryFeedbackStore(dir)
            assertTrue(store.of("g1").isEmpty())
            store.set("g1", mapOf("x:1" to Judgement(Judgement.LESS, listOf("cats")), "x:2" to Judgement(Judgement.HIDE, emptyList())))
            val again = DiscoveryFeedbackStore(dir)
            assertEquals(Judgement(Judgement.LESS, listOf("cats")), again.of("g1")["x:1"])
            assertEquals(Judgement.HIDE, again.of("g1")["x:2"]?.action)
            assertTrue(again.of("g2").isEmpty())
        }
    }
}
