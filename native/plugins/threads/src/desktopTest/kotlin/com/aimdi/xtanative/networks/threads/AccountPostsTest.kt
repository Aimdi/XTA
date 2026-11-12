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
}
