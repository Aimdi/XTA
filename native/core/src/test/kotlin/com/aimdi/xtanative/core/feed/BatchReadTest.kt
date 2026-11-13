package com.aimdi.xtanative.core.feed

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

/** XTA's reader_reliability_test (the batch reads) and future_pool_test, for the native port. */
class BatchReadTest {
    private suspend fun BatchRead<Int>.loadInts(keys: List<String>, fetch: suspend (String) -> Int, retry: Boolean = false, concurrency: Int = 3) =
        load(keys, fetch, onError = { -1 }, failed = { it < 0 }, retryFailed = retry, concurrency = concurrency)

    @Test
    fun aBatchThatAnsweredShowsBeforeASlowerOneFinishes() = runBlocking {
        val batches = BatchRead<Int>()
        val slow = CompletableDeferred<Int>()
        val load = async { batches.loadInts(listOf("fast", "slow"), { key -> if (key == "slow") slow.await() else 1 }) }
        val shown = withTimeout(10_000) { batches.state.first { it.results.isNotEmpty() } }
        assertEquals(mapOf("fast" to 1), shown.results)
        assertTrue(shown.loading)
        slow.complete(2)
        assertEquals(listOf(1, 2), load.await())
        assertEquals(false, batches.state.value.loading)
    }

    @Test
    fun aRetryReadsOnlyTheBatchesThatFailed() = runBlocking {
        val batches = BatchRead<Int>()
        val calls = ArrayList<String>()
        var failing = true
        val fetch: suspend (String) -> Int = { key ->
            calls += key
            if (key == "bad" && failing) throw IOException("offline")
            if (key == "good") 1 else 2
        }
        batches.loadInts(listOf("good", "bad"), fetch)
        assertEquals(setOf("bad"), batches.state.value.failed)
        failing = false
        batches.loadInts(listOf("good", "bad"), fetch, retry = true)
        assertEquals(listOf("good", "bad", "bad"), calls)
        assertTrue(batches.state.value.failed.isEmpty())
        assertEquals(mapOf("good" to 1, "bad" to 2), batches.state.value.results)
    }

    @Test
    fun aCancelledLoadNeitherShowsNorStartsWhatWasQueued() = runBlocking {
        val batches = BatchRead<Int>()
        val first = CompletableDeferred<Int>()
        val started = ArrayList<String>()
        val load = async { batches.loadInts(listOf("one", "two", "three"), { key -> started += key; first.await() }, concurrency = 1) }
        while (started.isEmpty()) yield()
        batches.cancel()
        assertFailsWith<CancellationException> { load.await() }
        first.complete(1)
        yield()
        assertEquals(listOf("one"), started)
        assertTrue(batches.state.value.results.isEmpty())
    }

    @Test
    fun aBatchPastItsTimeFailsAndALateAnswerChangesNothing() = runBlocking {
        val batches = BatchRead<Int>(batchMillis = 20)
        val late = CompletableDeferred<Int>()
        batches.loadInts(listOf("late", "next"), { key -> if (key == "late") late.await() else 2 }, concurrency = 1)
        assertEquals(mapOf("late" to -1, "next" to 2), batches.state.value.results)
        late.complete(100)
        yield()
        assertEquals(-1, batches.state.value.results["late"])
    }

    @Test
    fun theLoadsDeadlineKeepsWhatAnsweredAndARetryReadsOnlyWhatDidNot() = runBlocking {
        // Long enough for the first two to start on a busy machine; the slow one never answers.
        val batches = BatchRead<Int>(loadMillis = 3_000)
        val late = CompletableDeferred<Int>()
        val calls = ArrayList<String>()
        val fetch: suspend (String) -> Int = { key -> calls += key; if (key == "slow") late.await() else 1 }
        batches.loadInts(listOf("good", "slow", "queued"), fetch, concurrency = 1)
        assertEquals(listOf("good", "slow"), calls)
        assertEquals(mapOf("good" to 1, "slow" to -1, "queued" to -1), batches.state.value.results)
        assertEquals(setOf("slow", "queued"), batches.state.value.failed)
        assertEquals(false, batches.state.value.loading)

        late.complete(2)
        batches.loadInts(listOf("good", "slow", "queued"), fetch, retry = true, concurrency = 1)
        assertEquals(listOf("good", "slow", "slow", "queued"), calls)
        assertTrue(batches.state.value.failed.isEmpty())
        assertEquals(mapOf("good" to 1, "slow" to 2, "queued" to 1), batches.state.value.results)
    }

    @Test
    fun mappingKeepsTheOrderAndCapsWhatRunsAtOnce() = runBlocking {
        var running = 0
        var peak = 0
        val out = mapWithConcurrency((1..8).toList(), 2) { n ->
            running++
            peak = maxOf(peak, running)
            delay((9 - n).toLong())
            running--
            n * 10
        }
        assertEquals((1..8).map { it * 10 }, out)
        assertTrue(peak in 1..2)
        assertTrue(mapWithConcurrency(emptyList<Int>(), 2) { it }.isEmpty())
        assertEquals(listOf(1, 2), mapWithConcurrency(listOf(1, 2), 0) { it }, "a concurrency below one is one")
    }
}
