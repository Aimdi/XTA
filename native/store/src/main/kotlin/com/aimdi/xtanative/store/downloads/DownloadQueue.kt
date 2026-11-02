package com.aimdi.xtanative.store.downloads

import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A download stopped by the reader. */
class DownloadCancelled : Exception("Download cancelled")

/** The reader's cancel of one download: what is running checks it, and listeners stop what they hold (a request, a copy). */
class DownloadCancellation {
    @Volatile
    var cancelled = false
        private set
    private val listeners = mutableListOf<() -> Unit>()

    /** Throws [DownloadCancelled] once cancelled. */
    fun check() {
        if (cancelled) throw DownloadCancelled()
    }

    /** Runs [callback] on cancel, or now when already cancelled. */
    fun onCancel(callback: () -> Unit) {
        synchronized(this) {
            if (!cancelled) {
                listeners += callback
                return
            }
        }
        callback()
    }

    fun cancel() {
        val toRun = synchronized(this) {
            if (cancelled) return
            cancelled = true
            listeners.toList().also { listeners.clear() }
        }
        toRun.forEach { runCatching { it() } }
    }
}

/**
 * What carries out one download (fetch, then save where it goes):
 * [DownloadTransfer], or a stand-in in tests. It is started on the queue's
 * own thread, in the order the queue starts downloads, so it must block
 * nowhere there: its work belongs on [Dispatchers.IO].
 */
fun interface DownloadRunner {
    /** Where the file was saved, or null when the reader backed out of saving it. */
    suspend fun run(
        entry: DownloadEntry,
        cancellation: DownloadCancellation,
        progress: (received: Long, total: Long?) -> Unit,
        phase: (DownloadStatus) -> Unit,
    ): String?
}

/** The downloads list as the downloads screen shows it. [ready] once the history was read; [storageError] while it cannot be read or written. */
data class DownloadCenterState(val entries: List<DownloadEntry> = emptyList(), val ready: Boolean = false, val storageError: Boolean = false) {
    /** The list in the screen's order: running and waiting downloads first, oldest first, then the ended ones, newest first. */
    val shown: List<DownloadEntry> get() = entries.filter { it.active }.sortedBy { it.createdAt } + entries.filterNot { it.active }.sortedByDescending { it.createdAt }
}

/**
 * The downloads queue: newest first, the oldest queued started first, at
 * most [concurrency] at once, kept in [history] so a download running when
 * the app stopped can be tried again. Nothing touches the network before the
 * queued work is on disk, and a history that could not be read is never
 * written over. Its bookkeeping runs one step at a time, as XTA's did.
 */
class DownloadQueue(
    private val history: DownloadHistory,
    private val runner: DownloadRunner,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    /** Removes what a download cancelled before it started may have left from an earlier attempt. */
    private val discard: (suspend (DownloadEntry) -> Unit)? = null,
    /** Clears what interrupted transfers left, once the history is read. */
    private val cleanup: (suspend () -> Unit)? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val serial = Dispatchers.Default.limitedParallelism(1)
    private val current = MutableStateFlow(DownloadCenterState())
    private val writes = Mutex()
    private var initializing: Deferred<Unit>? = null
    private var loading = false
    private var historyLoaded = false
    private var closed = false
    private var lastProgress = 0L
    private val attempts = HashMap<String, Int>()
    private val completion = HashMap<String, CompletableDeferred<DownloadEntry>>()
    private val cancellation = HashMap<String, DownloadCancellation>()
    private val discarding = HashMap<String, Deferred<Unit>>()
    private val running = HashSet<String>()

    val state: StateFlow<DownloadCenterState> = current.asStateFlow()

    /** How many downloads run at once: 1 until the setting says otherwise. */
    @Volatile
    var concurrency: Int = 1
        private set

    /** Reads the history, once: later calls wait for that read. A failed read shows as [DownloadCenterState.storageError]. */
    suspend fun initialize() = withContext(serial) {
        val load = initializing ?: scope.async(serial) { load() }.also { initializing = it }
        load.await()
    }

    /** Tries the history again: reads it when it could not be, writes it when that failed. */
    suspend fun retryHistory() {
        withContext(serial) {
            when {
                closed -> Unit
                historyLoaded -> persistQuietly()
                loading -> initialize()
                else -> {
                    initializing = null
                    initialize()
                }
            }
        }
    }

    /** Sets how many run at once, within [CONCURRENCY_CHOICES]; raising it starts queued downloads now, lowering it stops none. */
    fun setConcurrency(limit: Int) {
        concurrency = limit.coerceIn(CONCURRENCY_CHOICES.first(), CONCURRENCY_CHOICES.last())
        scope.launch(serial) { pump() }
    }

    /**
     * Queues [url] to be saved as [fileName]: into [treeUri] when given, else
     * into the shared folder for its type when [background], else wherever the
     * reader picks, in [subfolder] when one is named. Refused (by throwing)
     * when the history cannot be read, the URL is no web address, or
     * [MAX_ACTIVE] are waiting or running. The answer completes when the
     * download ends, however it ends.
     */
    suspend fun enqueue(
        url: String,
        fileName: String,
        treeUri: String? = null,
        background: Boolean = false,
        subfolder: String? = null,
    ): Deferred<DownloadEntry> {
        initialize()
        return withContext(serial) {
            check(!closed) { "The downloads queue is closed" }
            if (!historyLoaded) retryHistory()
            check(historyLoaded) { "Download history could not be read" }
            require(url.length <= DownloadEntry.MAX_URL && isWebUrl(url)) { "Expected a web media URL" }
            check(current.value.entries.count { it.active } < MAX_ACTIVE) { "Download queue is full" }
            val folder = treeUri?.takeIf { it.isNotEmpty() }
            val entry = DownloadEntry(newId(), url, safeDownloadName(fileName), folder, background && folder == null, subfolder?.let(::safeDownloadFolder), clock())
            val done = CompletableDeferred<DownloadEntry>().also { completion[entry.id] = it }
            publish(listOf(entry) + current.value.entries)
            try {
                persist()
                pump()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                finish(entry.copy(status = DownloadStatus.FAILED))
            }
            done
        }
    }

    /** Queues every request; counts those that reached their destination. */
    suspend fun enqueueBatch(requests: List<DownloadRequest>): DownloadBatchResult = coroutineScope {
        val saved = requests.map { request ->
            async {
                runCatching {
                    enqueue(request.url, request.fileName, request.treeUri, request.background, request.subfolder).await().status == DownloadStatus.COMPLETED
                }.getOrDefault(false)
            }
        }.awaitAll()
        DownloadBatchResult(saved.count { it }, requests.size)
    }

    /** Waits for every history write asked for so far. */
    suspend fun flush() = writes.withLock { }

    /** Tries a failed, cancelled or interrupted download again, as the same entry, once what its cancel cleans up is gone. */
    suspend fun retry(id: String) {
        initialize()
        withContext(serial) {
            discarding[id]?.await()
            val entry = find(id)
            if (closed || entry == null || !entry.canRetry) return@withContext
            attempts[id] = (attempts[id] ?: 0) + 1
            replace(entry.copy(status = DownloadStatus.QUEUED))
            try {
                persist()
                pump()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                replace(entry.copy(status = DownloadStatus.FAILED))
            }
        }
    }

    /** Cancels [id]: a running one stops where it is, and a queued one never starts. A late success cannot undo it. */
    suspend fun cancel(id: String) {
        withContext(serial) {
            val entry = find(id)
            if (closed || entry == null || !entry.canCancel) return@withContext
            val active = cancellation[id]
            active?.cancel()
            val cleaning = if (active == null) scope.async { runCatching { discard?.invoke(entry) }; Unit } else null
            cleaning?.let { discarding[id] = it }
            finish(entry.copy(status = DownloadStatus.CANCELLED))
            cleaning?.await()
            if (cleaning != null && discarding[id] === cleaning) discarding.remove(id)
            persistQuietly()
        }
    }

    /** Clears the downloads that ended well, keeping those still running or that can be tried again. */
    suspend fun clearFinished() {
        withContext(serial) {
            publish(current.value.entries.filter { it.active || it.canRetry })
            persistQuietly()
        }
    }

    /** Stops every download; those not finished end as interrupted, to be tried again next time. */
    suspend fun close() {
        withContext(serial) {
            closed = true
            cancellation.values.forEach { it.cancel() }
            current.value.entries.filter { it.active }.forEach { completion.remove(it.id)?.complete(it.copy(status = DownloadStatus.INTERRUPTED)) }
        }
        flush()
    }

    private suspend fun load() {
        loading = true
        try {
            val entries = history.read()
            runCatching { cleanup?.invoke() }
            historyLoaded = true
            if (!closed) current.value = DownloadCenterState(entries, ready = true)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (!closed) current.value = DownloadCenterState(ready = true, storageError = true)
        } finally {
            loading = false
        }
    }

    /** Starts the oldest queued downloads until [concurrency] run; each one that ends frees its place for the next. */
    private fun pump() {
        while (!closed && running.size < concurrency) {
            val next = current.value.entries.asReversed().firstOrNull { it.status == DownloadStatus.QUEUED && it.id !in running } ?: return
            running += next.id
            // Undispatched: the download is marked as running before anything else can cancel or start it.
            scope.launch(serial, CoroutineStart.UNDISPATCHED) {
                try {
                    run(next)
                } finally {
                    running -= next.id
                    pump()
                }
            }
        }
    }

    private suspend fun run(entry: DownloadEntry) {
        val token = DownloadCancellation()
        val attempt = attempts[entry.id] ?: 0
        fun isCurrent() = (attempts[entry.id] ?: 0) == attempt
        cancellation[entry.id] = token
        replace(entry.copy(status = DownloadStatus.DOWNLOADING))
        try {
            persist()
            token.check()
            val savedUri = runner.run(
                entry,
                token,
                { received, total -> scope.launch(serial) { if (isCurrent()) progress(entry.id, received, total) } },
                { status -> scope.launch(serial) { phase(entry.id, status, token, ::isCurrent) } },
            )
            token.check()
            if (!isCurrent()) return
            val now = find(entry.id) ?: entry
            finish(now.copy(status = if (savedUri == null) DownloadStatus.CANCELLED else DownloadStatus.COMPLETED, savedUri = savedUri))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (!isCurrent()) return
            val now = find(entry.id) ?: entry
            finish(now.copy(status = if (token.cancelled) DownloadStatus.CANCELLED else DownloadStatus.FAILED))
        } finally {
            if (cancellation[entry.id] === token) cancellation.remove(entry.id)
            withContext(NonCancellable) { persistQuietly() }
        }
    }

    private fun phase(id: String, status: DownloadStatus, token: DownloadCancellation, isCurrent: () -> Boolean) {
        val now = find(id) ?: return
        if (now.active && !token.cancelled && isCurrent()) replace(now.copy(status = status))
    }

    /** Progress is shown at most every [PROGRESS_INTERVAL_MS], and always when a download completes. */
    private fun progress(id: String, received: Long, total: Long?) {
        val now = find(id)
        if (closed || now == null || now.status != DownloadStatus.DOWNLOADING) return
        val time = clock()
        if (received != total && time - lastProgress < PROGRESS_INTERVAL_MS) return
        lastProgress = time
        replace(now.copy(received = received, total = total))
    }

    private fun find(id: String): DownloadEntry? = current.value.entries.firstOrNull { it.id == id }

    private fun replace(entry: DownloadEntry) = publish(current.value.entries.map { if (it.id == entry.id) entry else it })

    /** Shows [entries], keeping every active one and as many ended ones as fit in [MAX_KEPT]. */
    private fun publish(entries: List<DownloadEntry>) {
        if (closed) return
        val active = entries.filter { it.active }
        val ended = entries.filterNot { it.active }.take((MAX_KEPT - active.size).coerceAtLeast(0))
        val kept = (active + ended).mapTo(HashSet()) { it.id }
        attempts.keys.retainAll(kept)
        current.update { DownloadCenterState(entries.filter { entry -> entry.id in kept }, ready = true, storageError = it.storageError) }
    }

    private fun finish(entry: DownloadEntry) {
        replace(entry)
        completion.remove(entry.id)?.complete(entry)
    }

    /** Writes the list as it is now, after the writes asked for before; a history never read is never written over. */
    private suspend fun persist() {
        check(historyLoaded) { "Cannot overwrite an unread download history" }
        val snapshot = current.value.entries
        try {
            writes.withLock { history.write(snapshot) }
            if (!closed && current.value.storageError) current.update { it.copy(storageError = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!closed) current.update { it.copy(storageError = true) }
            throw e
        }
    }

    private suspend fun persistQuietly() {
        try {
            persist()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    companion object {
        /** How many run at once unless the reader changes it, and what they may choose. */
        const val DEFAULT_CONCURRENCY = 2
        val CONCURRENCY_CHOICES = listOf(1, 2, 3, 4)

        /** How many downloads may wait or run at once; the queue refuses more. */
        const val MAX_ACTIVE = 100

        /** How many the list keeps, ended ones dropped oldest first. */
        const val MAX_KEPT = 200

        const val PROGRESS_INTERVAL_MS = 120L
    }
}
