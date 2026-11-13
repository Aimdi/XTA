package com.aimdi.xtanative.store.downloads

import com.aimdi.xtanative.store.saved.waitUntil
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import com.aimdi.xtanative.store.saved.timed

class DownloadQueueTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun stop() = scope.cancel()

    private class MemoryHistory(entries: List<DownloadEntry> = emptyList()) : DownloadHistory {
        @Volatile var entries: List<DownloadEntry> = entries
        @Volatile var failReads = false
        @Volatile var failWrites = false
        @Volatile var writes = 0

        override suspend fun read(): List<DownloadEntry> = if (failReads) throw IOException("Storage unavailable") else entries

        override suspend fun write(entries: List<DownloadEntry>) {
            writes++
            if (failWrites) throw IOException("No space")
            this.entries = entries.toList()
        }
    }

    /** Transfers that end only when the test says so. */
    private class GatedRunner : DownloadRunner {
        val started = CopyOnWriteArrayList<String>()
        private val gates = ConcurrentHashMap<String, CompletableDeferred<String?>>()

        private fun gate(name: String) = gates.computeIfAbsent(name) { CompletableDeferred() }

        override suspend fun run(entry: DownloadEntry, cancellation: DownloadCancellation, progress: (Long, Long?) -> Unit, phase: (DownloadStatus) -> Unit): String? {
            started += entry.fileName
            return gate(entry.fileName).await()
        }

        fun finish(name: String) = gate(name).complete("content://provider/document/$name")
    }

    private fun DownloadQueue.idOf(name: String) = state.value.entries.single { it.fileName == name }.id

    private fun DownloadQueue.statusOf(name: String) = state.value.entries.single { it.fileName == name }.status

    private suspend fun DownloadQueue.enqueueAll(vararg names: String): List<Deferred<DownloadEntry>> =
        names.map { enqueue("https://media.example/$it", it) }

    @Test
    fun aQueuedCancelNeverStartsAndALateSuccessCannotUndoACancel() = timed {
        val runner = GatedRunner()
        val queue = DownloadQueue(MemoryHistory(), runner, scope)
        val first = queue.enqueue("https://media.example/first", "first")
        waitUntil { runner.started == listOf("first") }
        val second = queue.enqueue("https://media.example/second", "second")
        queue.cancel(queue.idOf("second"))
        assertEquals(DownloadStatus.CANCELLED, second.await().status)
        queue.cancel(queue.idOf("first"))
        runner.finish("first")
        assertEquals(DownloadStatus.CANCELLED, first.await().status)
        delay(50)
        assertEquals(listOf("first"), runner.started)
        assertTrue(queue.state.value.entries.all { it.status == DownloadStatus.CANCELLED })
        queue.close()
    }

    @Test
    fun aCancelsCleanupHoldsOffTheQueueAndARetryUntilItIsDone() = timed {
        val gate = CompletableDeferred<String?>()
        val cleanup = CompletableDeferred<Unit>()
        val started = CopyOnWriteArrayList<String>()
        val runner = DownloadRunner { entry, _, _, _ ->
            started += entry.fileName
            if (entry.fileName == "first") gate.await() else "content://provider/second"
        }
        val queue = DownloadQueue(MemoryHistory(), runner, scope, discard = { cleanup.await() })
        val first = queue.enqueue("https://media.example/first", "first")
        waitUntil { started.isNotEmpty() }
        val second = queue.enqueue("https://media.example/second", "second")
        val id = queue.idOf("second")
        val cancelling = async { queue.cancel(id) }
        waitUntil { queue.statusOf("second") == DownloadStatus.CANCELLED }
        gate.complete("content://provider/first")
        first.await()
        delay(50)
        assertEquals(listOf("first"), started)
        assertEquals(DownloadStatus.CANCELLED, second.await().status)
        val retrying = async { queue.retry(id) }
        delay(50)
        assertEquals(listOf("first"), started)
        cleanup.complete(Unit)
        cancelling.await()
        retrying.await()
        waitUntil { started == listOf("first", "second") }
        queue.close()
    }

    @Test
    fun aRetryUsesTheSameEntryAndABatchCountsOnlyWhatWasSaved() = timed {
        var attempts = 0
        val queue = DownloadQueue(MemoryHistory(), { entry, _, _, _ ->
            if (++attempts == 1) throw IOException("offline")
            "content://provider/${entry.id}"
        }, scope)
        val failed = queue.enqueue("https://media.example/photo", "photo").await()
        assertEquals(DownloadStatus.FAILED, failed.status)
        queue.retry(failed.id)
        waitUntil { queue.state.value.entries.single().status == DownloadStatus.COMPLETED }
        queue.flush()
        assertEquals(failed.id, queue.state.value.entries.single().id)
        queue.close()

        val batch = DownloadQueue(MemoryHistory(), { entry, _, _, _ ->
            if (entry.fileName.toInt() >= 7) throw IOException("failed")
            "content://provider/${entry.id}"
        }, scope)
        val result = batch.enqueueBatch((0 until 10).map { DownloadRequest("https://media.example/$it", "$it") })
        assertEquals(DownloadBatchResult(7, 10), result)
        assertEquals(3, batch.state.value.entries.count { it.status == DownloadStatus.FAILED })
        batch.close()
    }

    @Test
    fun anImmediateRetrySurvivesTheCancelledAttemptFinishingLate() = timed {
        val oldResponse = CompletableDeferred<String?>()
        val started = CompletableDeferred<Unit>()
        val retried = CompletableDeferred<Unit>()
        var requests = 0
        val queue = DownloadQueue(MemoryHistory(), { _, _, _, _ ->
            if (++requests == 1) {
                started.complete(Unit)
                oldResponse.await()
            } else {
                retried.complete(Unit)
                "content://provider/retried"
            }
        }, scope)
        val first = queue.enqueue("https://media.example/photo", "photo")
        started.await()
        val id = queue.state.value.entries.single().id
        queue.cancel(id)
        assertEquals(DownloadStatus.CANCELLED, first.await().status)
        queue.retry(id)
        assertEquals(DownloadStatus.QUEUED, queue.state.value.entries.single().status)
        oldResponse.complete("content://provider/stale-response")
        retried.await()
        waitUntil { queue.state.value.entries.single().status == DownloadStatus.COMPLETED }
        queue.flush()
        assertEquals(2, requests)
        assertEquals("content://provider/retried", queue.state.value.entries.single().savedUri)
        queue.close()
    }

    @Test
    fun aHistoryThatCouldNotBeReadIsNeverWrittenOverAndCanBeReadAgain() = timed {
        val existing = DownloadEntry("existing", "https://media.example/e.jpg", "e.jpg", status = DownloadStatus.COMPLETED)
        val history = MemoryHistory(listOf(existing)).apply { failReads = true }
        var requests = 0
        val queue = DownloadQueue(history, { _, _, _, _ -> requests++; "saved" }, scope)
        queue.initialize()
        assertTrue(queue.state.value.storageError)
        assertFailsWith<IllegalStateException> { queue.enqueue("https://media.example/new", "new") }
        queue.clearFinished()
        assertEquals(0, history.writes)
        assertEquals(listOf("existing"), history.entries.map { it.id })
        assertEquals(0, requests)

        history.failReads = false
        queue.retryHistory()
        assertEquals(listOf("existing"), queue.state.value.entries.map { it.id })
        assertFalse(queue.state.value.storageError)
        queue.enqueue("https://media.example/new", "new").await()
        queue.flush()
        assertEquals(2, queue.state.value.entries.size)
        assertTrue(history.entries.any { it.id == "existing" })
        queue.close()
    }

    @Test
    fun aFailedWriteLeavesTheNetworkAloneAndRetryingItKeepsTheFailedEntry() = timed {
        val history = MemoryHistory().apply { failWrites = true }
        var requests = 0
        val queue = DownloadQueue(history, { _, _, _, _ -> requests++; "saved" }, scope)
        val failed = queue.enqueue("https://media.example/photo", "photo").await()
        assertEquals(DownloadStatus.FAILED, failed.status)
        assertEquals(0, requests)
        assertTrue(queue.state.value.storageError)
        history.failWrites = false
        queue.retryHistory()
        assertEquals(listOf(failed.id), queue.state.value.entries.map { it.id })
        assertEquals(listOf(failed.id), history.entries.map { it.id })
        assertFalse(queue.state.value.storageError)
        queue.close()
    }

    @Test
    fun leftAtItsDefaultTheQueueRunsOneDownloadAtATimeOldestFirst() = timed {
        val runner = GatedRunner()
        val queue = DownloadQueue(MemoryHistory(), runner, scope)
        queue.enqueueAll("a", "b", "c")
        waitUntil { runner.started.size == 1 }
        assertEquals(listOf("a"), runner.started)
        runner.finish("a")
        waitUntil { runner.started.size == 2 }
        assertEquals(listOf("a", "b"), runner.started)
        queue.close()
    }

    @Test
    fun withALimitOfTwoTheThirdWaitsForAFreePlace() = timed {
        val runner = GatedRunner()
        val queue = DownloadQueue(MemoryHistory(), runner, scope).apply { setConcurrency(2) }
        val pending = queue.enqueueAll("a", "b", "c")
        waitUntil { runner.started.size == 2 }
        delay(50)
        assertEquals(listOf("a", "b"), runner.started)
        assertEquals(2, queue.state.value.entries.count { it.status == DownloadStatus.DOWNLOADING })
        runner.finish("b")
        assertEquals(DownloadStatus.COMPLETED, pending[1].await().status)
        waitUntil { runner.started.size == 3 }
        assertEquals("c", runner.started.last())
        queue.close()
    }

    @Test
    fun raisingTheLimitStartsQueuedDownloadsAndLoweringItStopsNone() = timed {
        val runner = GatedRunner()
        val queue = DownloadQueue(MemoryHistory(), runner, scope)
        queue.enqueueAll("a", "b", "c", "d")
        waitUntil { runner.started.size == 1 }
        queue.setConcurrency(3)
        waitUntil { runner.started.size == 3 }
        assertEquals(listOf("a", "b", "c"), runner.started)
        queue.setConcurrency(1)
        runner.finish("a")
        delay(100)
        assertEquals(listOf("a", "b", "c"), runner.started, "two still run, above the new limit of one")
        runner.finish("b")
        runner.finish("c")
        waitUntil { runner.started.size == 4 }
        queue.close()
    }

    @Test
    fun theLimitStaysWithinTheChoicesOffered() = timed {
        val queue = DownloadQueue(MemoryHistory(), GatedRunner(), scope)
        queue.setConcurrency(0)
        assertEquals(1, queue.concurrency)
        queue.setConcurrency(99)
        assertEquals(DownloadQueue.CONCURRENCY_CHOICES.last(), queue.concurrency)
        assertTrue(DownloadQueue.DEFAULT_CONCURRENCY in DownloadQueue.CONCURRENCY_CHOICES)
    }

    @Test
    fun aRequestCarriesItsCleanedSubfolderAndBackgroundOnlyWithoutAFolder() = timed {
        val seen = CopyOnWriteArrayList<DownloadEntry>()
        val queue = DownloadQueue(MemoryHistory(), { entry, _, _, _ -> seen += entry; "content://media/x" }, scope)
        val batch = queue.enqueueBatch(listOf(DownloadRequest("https://media.example/1", "1", subfolder = "Mika_42/../R-18"), DownloadRequest("https://media.example/2", "2")))
        assertEquals(2, batch.saved)
        assertEquals(listOf("Mika_42/R-18", null), seen.sortedBy { it.fileName }.map { it.subfolder })

        seen.clear()
        queue.enqueue("https://example.org/a.jpg", "a.jpg", background = true).await()
        queue.enqueue("https://example.org/b.jpg", "b.jpg", treeUri = "content://tree/x", background = true).await()
        assertEquals(listOf(true, false), seen.map { it.background })
        queue.close()
    }

    @Test
    fun theQueueRefusesWhatIsNoWebAddressAndMoreThanItCanHold() = timed {
        val queue = DownloadQueue(MemoryHistory(), GatedRunner(), scope)
        assertFailsWith<IllegalArgumentException> { queue.enqueue("file:///etc/passwd", "passwd") }
        repeat(DownloadQueue.MAX_ACTIVE) { queue.enqueue("https://media.example/$it", "$it") }
        assertFailsWith<IllegalStateException> { queue.enqueue("https://media.example/more", "more") }
        queue.close()
    }
}
