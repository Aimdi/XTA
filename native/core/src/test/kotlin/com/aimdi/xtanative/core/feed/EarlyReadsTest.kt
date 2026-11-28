package com.aimdi.xtanative.core.feed

import com.aimdi.xtanative.core.feed.Posts.page
import com.aimdi.xtanative.core.feed.Posts.thread
import com.aimdi.xtanative.core.x.model.XTimelinePage
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

class EarlyReadsTest {
    private class Network : FeedReads {
        val asked = CopyOnWriteArrayList<String>()
        val gates = HashMap<String, CompletableDeferred<Unit>>()
        var failing = setOf<String>()

        override suspend fun search(query: String, cursor: String?, includeReplies: Boolean): XTimelinePage = answer("search $query $cursor")

        override suspend fun userPosts(userId: String, includeReplies: Boolean): XTimelinePage = answer("posts $userId")

        private suspend fun answer(read: String): XTimelinePage {
            val number = synchronized(asked) { asked += read; asked.size }
            gates[read]?.await()
            if (read in failing) throw IOException("X failed: $read")
            return page(thread(number.toLong()))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private suspend fun waitFor(condition: () -> Boolean) = withTimeout(5_000) { while (!condition()) yield() }

    @Test
    fun aReadMadeAheadIsHandedToTheFeedOnceAndNotAskedAgain() = runBlocking {
        val network = Network()
        val reads = EarlyReads(network, scope)
        reads.ahead { it.search("from:a", null, true) }
        waitFor { network.asked.size == 1 }

        val first = reads.search("from:a", null, true)
        val again = reads.search("from:a", null, true)

        assertEquals(listOf("search from:a null", "search from:a null"), network.asked.toList())
        assertEquals("1", first.threads.single().id)
        assertEquals("2", again.threads.single().id, "the feed's second ask is a fresh read")
    }

    @Test
    fun aFeedWaitsForAReadStillUnderWayAndGetsItsFailure() = runBlocking {
        val network = Network().apply {
            gates["search from:a null"] = CompletableDeferred()
            failing = setOf("search from:a null")
        }
        val reads = EarlyReads(network, scope)
        reads.ahead { it.search("from:a", null, true) }
        waitFor { network.asked.size == 1 }

        val asking = scope.async { reads.search("from:a", null, true) }
        network.gates.getValue("search from:a null").complete(Unit)

        assertFailsWith<IOException> { asking.await() }
        assertEquals(1, network.asked.size)
    }

    @Test
    fun theFirstAskStopsTheReadingAheadSoNothingIsReadTwice() = runBlocking {
        val network = Network().apply { gates["search from:a null"] = CompletableDeferred() }
        val reads = EarlyReads(network, scope)
        reads.ahead { early ->
            early.search("from:a", null, true)
            early.userPosts("b", true)
        }
        waitFor { network.asked.size == 1 }

        val other = reads.userPosts("c", true)
        network.gates.getValue("search from:a null").complete(Unit)
        val taken = reads.search("from:a", null, true)
        val b = reads.userPosts("b", true)

        assertTrue(reads.claimed)
        assertEquals(listOf("search from:a null", "posts c", "posts b"), network.asked.toList())
        assertEquals("1", taken.threads.single().id, "the read under way when the feed asked is still handed over")
        assertEquals("3", b.threads.single().id)
        assertEquals("2", other.threads.single().id)
    }

    @Test
    fun aReadMadeAheadTooLongAgoIsLeftAndReadAfresh() = runBlocking {
        val network = Network()
        var now = 1_000L
        val reads = EarlyReads(network, scope, keepMillis = 20_000, clock = { now })
        reads.ahead { it.search("from:a", null, true) }
        waitFor { network.asked.size == 1 }
        now += 20_001

        val page = reads.search("from:a", null, true)

        assertEquals(2, network.asked.size)
        assertEquals("2", page.threads.single().id)
    }

    @Test
    fun nothingIsReadAheadOnceAFeedAsked() = runBlocking {
        val network = Network()
        val reads = EarlyReads(network, scope)
        reads.search("from:a", null, true)
        reads.ahead { it.search("from:b", null, true) }
        yield()

        assertEquals(listOf("search from:a null"), network.asked.toList())
    }
}
