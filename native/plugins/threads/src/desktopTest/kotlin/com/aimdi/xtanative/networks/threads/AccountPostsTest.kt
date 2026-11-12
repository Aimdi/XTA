package com.aimdi.xtanative.networks.threads

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/** One load of many accounts. */
class AccountPostsTest {
    private val posts = AccountPosts<String>(dateOf = { Instant.EPOCH }, perAccount = 5, concurrency = 1, ttlMillis = 60_000, clock = { 1_000 })

    @Test
    fun aThrottleEndsTheLoadAndTheRestGiveWhatIsHeld(): Unit = runBlocking {
        posts.seed("c", listOf("kept c"), 0)
        val asked = mutableListOf<String>()
        val merged = posts.merge(listOf("a", "b", "c", "d"), { key ->
            asked += key
            if (key == "b") throw ThreadsException(ThreadsFailure.THROTTLED, "429", 429)
            listOf("new $key")
        }, forceRefresh = true)
        assertEquals(listOf("a", "b"), asked, "Meta is not asked again once it throttled")
        assertEquals(setOf("new a", "kept c"), merged.toSet())
        assertEquals(2, posts.pendingCount(listOf("a", "b", "c", "d")))
    }

    @Test
    fun anotherFailureLetsTheRestBeAsked(): Unit = runBlocking {
        val asked = mutableListOf<String>()
        posts.merge(listOf("a", "b", "c"), { key ->
            asked += key
            if (key == "a") throw ThreadsException(ThreadsFailure.NO_SUCH_FEED, "404", 404)
            listOf(key)
        })
        assertEquals(listOf("a", "b", "c"), asked)
    }

    @Test
    fun aMissingAccountIsNamedWithWhyAndIsNotStillToReadNorAskedAgainWithinItsWindow(): Unit = runBlocking {
        val settling = AccountPosts<String>(dateOf = { Instant.EPOCH }, perAccount = 5, concurrency = 1, ttlMillis = 60_000, clock = { 1_000 }, settles = ThreadsTrouble::settles)
        val asked = mutableListOf<String>()
        val fetch: suspend (String) -> List<String> = { key ->
            asked += key
            when (key) {
                "gone" -> throw ThreadsException(ThreadsFailure.NO_SUCH_FEED, "404", 404)
                "slow" -> throw java.io.IOException("timeout")
                else -> listOf(key)
            }
        }
        settling.merge(listOf("a", "gone", "slow"), fetch)
        assertEquals(listOf("gone", "slow"), settling.failures(listOf("a", "gone", "slow")).map { it.first })
        assertEquals(ThreadsTrouble.MISSING, ThreadsTrouble.of(settling.failures(listOf("gone")).single().second.error))
        assertEquals(1, settling.pendingCount(listOf("a", "gone", "slow")), "a missing account is not still to read; one the network failed is")
        asked.clear()
        settling.merge(listOf("a", "gone", "slow"), fetch)
        assertEquals(listOf("slow"), asked, "within its window a missing account is not asked again")
        settling.forgetFailure("gone")
        assertEquals(2, settling.pendingCount(listOf("a", "gone", "slow")))
    }

    @Test
    fun anAccountThatAnswersAgainIsNoLongerNamedAsFailed(): Unit = runBlocking {
        var fail = true
        posts.merge(listOf("a", "b"), { key -> if (key == "b" && fail) throw java.io.IOException("reset") else listOf(key) })
        assertEquals(listOf("b"), posts.failures(listOf("a", "b")).map { it.first })
        fail = false
        posts.merge(listOf("a", "b"), { key -> listOf(key) }, forceRefresh = true)
        assertEquals(emptyList(), posts.failures(listOf("a", "b")))
    }

    @Test
    fun threeAccountsAreReadAtOnceAndAThrottleStopsTheReadsBesideItInOrder(): Unit = runBlocking {
        val parallel = AccountPosts<String>(dateOf = { Instant.parse(it.substringAfter('@')) }, perAccount = 5, concurrency = 3, ttlMillis = 60_000, clock = { 1_000 })
        val started = mutableListOf<String>()
        val slow = kotlinx.coroutines.CompletableDeferred<Unit>()
        val merged = parallel.merge(listOf("a", "b", "c", "d", "e"), { key ->
            synchronized(started) { started += key }
            when (key) {
                "a" -> slow.await().let { listOf("a@2026-01-01T00:00:00Z") }
                "b" -> listOf("b@2026-01-03T00:00:00Z")
                "c" -> throw ThreadsException(ThreadsFailure.THROTTLED, "429", 429)
                else -> listOf("$key@2026-01-02T00:00:00Z")
            }
        }, forceRefresh = true)
        assertEquals(listOf("a", "b", "c"), started.take(3), "a slow account does not hold the two beside it")
        assertEquals(false, "d" in started || "e" in started, "nothing is asked after the throttle")
        assertEquals(listOf("b@2026-01-03T00:00:00Z"), merged, "the slow read beside the throttle is given up; the feed stays newest first")
        assertEquals(listOf("c"), parallel.failures(listOf("a", "b", "c", "d", "e")).map { it.first }, "an account given up for the throttle is not named as failed")
    }
}
