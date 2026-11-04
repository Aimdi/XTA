package com.aimdi.xtanative.networks.threads

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

/**
 * Requests leave one at a time, a gap apart: looking like one person reading
 * is the whole defence (XTA's threads_account_safety_test and
 * threads_time_to_content_test).
 */
class ThreadsPacingTest {
    private val time = FakeTime()
    private val start = time.now
    private val departures = CopyOnWriteArrayList<Long>()
    private val pacing = ThreadsPacing(sessionGapMillis = 3_000, guestGapMillis = 550, sessionJitterMillis = 0, guestJitterMillis = 0, sleep = time.sleep)

    private val meta = FakeMeta { asked ->
        departures += time.now
        yield()
        ok(if (asked.path.contains("/text_feed/")) fixture("text_feed.json") else fixture("search_page.html"))
    }

    private val session = ThreadsSession(COOKIES, useSessionApis = true)
    private val memory = ThreadsMemory.InMemory(ThreadsKept(userIds = mapOf("a" to "1", "b" to "2")))

    @Test
    fun twoSessionReadsStartedTogetherLeaveTheSessionGapApart() = runBlocking {
        val client = direct(meta, session, memory, time, pacing)
        coroutineScope {
            launch { client.userThreads("a") }
            launch { client.userThreads("b") }
        }
        assertEquals(listOf(start, start + 3_000), departures, "a burst is what gets a session flagged")
    }

    @Test
    fun guestReadsLeaveSoonerThanTheSessionFloor() = runBlocking {
        val client = direct(meta, session, memory, time, pacing)
        coroutineScope {
            launch { client.searchPosts("kiln") }
            launch { client.searchPosts("glaze") }
        }
        assertEquals(listOf(start, start + 550), departures, "a guest has no account to lose")
    }

    @Test
    fun theGapIsKeptBetweenDeparturesSoAReadLongAfterTheLastLeavesAtOnce() = runBlocking {
        val client = direct(meta, session, memory, time, pacing)
        client.userThreads("a")
        time.now += 5_000
        client.userThreads("b")
        assertEquals(listOf(start, start + 5_000), departures)
        assertTrue(time.slept.isEmpty())
    }

    @Test
    fun aRefusedDepartureDoesNotHoldUpTheQueueBehindIt() = runBlocking {
        val parked = ThreadsMemory.InMemory(ThreadsKept(userIds = mapOf("a" to "1"), cooldownUntil = time.now + 60_000))
        val client = direct(meta, session, parked, time, pacing)
        runCatching { client.currentUser() }
        assertEquals(3, client.searchPosts("kiln").items.size)
        assertEquals(listOf(start), departures, "the refused read never left, so the guest read did not wait for it")
    }

    @Test
    fun theJitterStaysWithinItsBound() {
        val jittered = ThreadsPacing(random = Random(7))
        val session = (1..200).map { jittered.gap(asGuest = false) }
        val guest = (1..200).map { jittered.gap(asGuest = true) }
        assertTrue(session.all { it in 3_000 until 3_750 } && session.toSet().size > 1, "$session")
        assertTrue(guest.all { it in 550 until 900 } && guest.toSet().size > 1, "$guest")
        assertEquals(0, ThreadsPacing.NONE.gap(asGuest = false))
    }
}
