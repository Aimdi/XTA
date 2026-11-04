package com.aimdi.xtanative.ui.downloads

import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.store.downloads.DownloadDestination
import com.aimdi.xtanative.store.downloads.DownloadEntry
import com.aimdi.xtanative.store.downloads.DownloadQueue
import com.aimdi.xtanative.store.downloads.DownloadRequest
import com.aimdi.xtanative.store.downloads.DownloadStatus
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.ui.plugins.PluginDownload
import com.aimdi.xtanative.ui.plugins.PluginQueued
import com.aimdi.xtanative.ui.saved.KeepNotice
import com.aimdi.xtanative.ui.saved.KeepNotices
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The batches plugins save through the downloads (PluginHost's downloadAll
 * and queueDownloads): a work's pages saved together with one notice for
 * them all, or watched page by page with Cancel, as XTA's Pixiv saves them,
 * and a feed's posts queued without the files the downloads have already, as
 * XTA's Booru queues them.
 */
object PluginBatches {
    private const val MAX_WORKERS = 4

    /** Saves [files] in [subfolder] of where the download setting says, saying once how it went; which of them were saved. */
    suspend fun saveAll(graph: AppGraph, files: List<PluginDownload>, subfolder: String?): List<Boolean> {
        if (files.isEmpty()) return emptyList()
        KeepNotices.show(KeepNotice.Downloading)
        val entries = try {
            val settings = graph.settings.load()
            val workers = settings[MediaPrefs.downloadConcurrency].coerceIn(1, MAX_WORKERS)
            graph.downloads.setConcurrency(workers)
            save(graph.downloads, files, downloadDestination(settings), subfolder, workers)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        KeepNotices.endWorking()
        val notice = if (entries == null) KeepNotice.DownloadFailed else notice(entries) { graph.platformFiles.folderOf(it) }
        notice?.let(KeepNotices::show)
        return entries?.map(::saved) ?: files.map { false }
    }

    /** Queues [files] where the download setting says, without those the downloads have, and lets them run; null when the setting asks where each file goes. */
    suspend fun queueAll(graph: AppGraph, files: List<PluginDownload>, subfolder: String?): PluginQueued? {
        val destination = downloadDestination(graph.settings.load())
        if (destination.asks) {
            KeepNotices.show(KeepNotice.NeedsDownloadFolder)
            return null
        }
        graph.downloads.initialize()
        val entries = graph.downloads.state.value.entries
        val (queued, skipped) = fresh(files, known(entries), DownloadQueue.MAX_ACTIVE - entries.count { it.active })
        if (queued.isNotEmpty()) Downloading.batch(graph, queued.map { DownloadRequest(it.url, it.fileName, destination.treeUri, destination.background, subfolder) })
        return PluginQueued(queued.size, skipped)
    }

    /**
     * [files] saved through [queue] in [subfolder] of [destination], [workers]
     * at a time: each one's download as it ended, in their order; null for
     * one the queue refused or that was not started once [stopped].
     * [onEnded] hears each one end.
     */
    suspend fun save(
        queue: DownloadQueue,
        files: List<PluginDownload>,
        destination: DownloadDestination,
        subfolder: String?,
        workers: Int,
        stopped: () -> Boolean = { false },
        onEnded: (DownloadEntry?) -> Unit = {},
    ): List<DownloadEntry?> {
        val entries = arrayOfNulls<DownloadEntry>(files.size)
        val next = AtomicInteger()
        coroutineScope {
            repeat(minOf(workers.coerceAtLeast(1), files.size)) {
                launch {
                    while (!stopped()) {
                        val at = next.getAndIncrement()
                        if (at >= files.size) break
                        entries[at] = ended(queue, files[at], destination, subfolder, stopped).also(onEnded)
                    }
                }
            }
        }
        return entries.toList()
    }

    private suspend fun ended(queue: DownloadQueue, file: PluginDownload, destination: DownloadDestination, subfolder: String?, stopped: () -> Boolean): DownloadEntry? = try {
        val done = queue.enqueue(file.url, file.fileName, destination.treeUri, destination.background, subfolder)
        // Stopped while it was being queued: it goes the way of those already under way.
        if (stopped()) cancelActive(queue, listOf(file))
        done.await()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /**
     * [files] saved as [saveAll] saves them, the foot of the window saying
     * which is being fetched in the plugin's words ([label] of its place from
     * 1 and the count) over a ring that fills as they end, with Cancel, which
     * starts no more of them and cancels those under way, as XTA's Pixiv's
     * PixivDownloadStore does; then how many were saved.
     */
    suspend fun saveAllShowing(graph: AppGraph, files: List<PluginDownload>, subfolder: String?, label: (current: Int, total: Int) -> String): List<Boolean> {
        if (files.isEmpty()) return emptyList()
        val watch = BatchWatch(files.size, label).also(BatchWatch::say)
        val entries = try {
            watched(graph, files, subfolder, watch)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        KeepNotices.endWorking()
        KeepNotices.show(entries?.let { KeepNotice.Batch(it.count(::saved), it.size) } ?: KeepNotice.DownloadFailed)
        return entries?.map(::saved) ?: files.map { false }
    }

    private suspend fun watched(graph: AppGraph, files: List<PluginDownload>, subfolder: String?, watch: BatchWatch): List<DownloadEntry?> = coroutineScope {
        val settings = graph.settings.load()
        val workers = settings[MediaPrefs.downloadConcurrency].coerceIn(1, MAX_WORKERS)
        graph.downloads.setConcurrency(workers)
        val stopping = launch {
            watch.asked.await()
            cancelActive(graph.downloads, files)
        }
        save(graph.downloads, files, downloadDestination(settings), subfolder, workers, stopped = watch::stopped) { watch.ended() }.also { stopping.cancel() }
    }

    /** Cancels what of [files] the downloads hold waiting or running. */
    suspend fun cancelActive(queue: DownloadQueue, files: List<PluginDownload>) {
        val urls = files.mapTo(HashSet()) { it.url }
        queue.state.value.entries.filter { it.active && it.url in urls }.forEach { queue.cancel(it.id) }
    }

    /** A batch the reader watches at the foot of the window: how far it has come, in the plugin's words, and Cancel. */
    private class BatchWatch(total: Int, private val label: (current: Int, total: Int) -> String) {
        private val count = AtomicReference(BatchCount(total))
        val asked = CompletableDeferred<Unit>()

        fun stopped(): Boolean = asked.isCompleted

        fun say() {
            val now = count.get()
            if (!stopped()) KeepNotices.show(KeepNotice.Progress(label(now.current, now.total), now.fraction, null, ::cancel))
        }

        fun ended() {
            count.updateAndGet(BatchCount::ended)
            say()
        }

        private fun cancel() {
            if (asked.complete(Unit)) KeepNotices.show(KeepNotice.Cancelled)
        }
    }

    /** What a batch says once it ended: a lone file's own notice, else how many of them were saved. */
    fun notice(entries: List<DownloadEntry?>, folderOf: (DownloadEntry) -> String?): KeepNotice? = when (entries.size) {
        0 -> null
        1 -> entries[0]?.let { downloadNotice(it, folderOf(it)) } ?: KeepNotice.RetryHint
        else -> KeepNotice.Batch(entries.count(::saved), entries.size)
    }

    /** The addresses the downloads hold saved or under way. */
    fun known(entries: List<DownloadEntry>): Set<String> = entries.filter { it.active || it.status == DownloadStatus.COMPLETED }.mapTo(HashSet()) { it.url }

    /**
     * Those of [files] with a web address that [known] does not hold and the
     * batch does not repeat, as many as [room] takes; and how many of [files]
     * were left out as known, repeated or no address, as XTA's Booru counts them.
     */
    fun fresh(files: List<PluginDownload>, known: Set<String>, room: Int): Pair<List<PluginDownload>, Int> {
        val seen = HashSet(known)
        val fresh = files.filter { it.url.toHttpUrlOrNull() != null && seen.add(it.url) }
        return fresh.take(room.coerceAtLeast(0)) to files.size - fresh.size
    }

    private fun saved(entry: DownloadEntry?): Boolean = entry?.status == DownloadStatus.COMPLETED
}

/** How far a batch the reader watches has come, as XTA's PixivDownloadProgress counts it: [done] of [total] ended. */
data class BatchCount(val total: Int, val done: Int = 0) {
    /** The file being fetched now, counted from 1. */
    val current: Int get() = (done + 1).coerceIn(1, maxOf(total, 1))

    /** How much of the batch has ended; unknown before the first has. */
    val fraction: Float? get() = if (done == 0 || total == 0) null else done.toFloat() / total

    fun ended(): BatchCount = copy(done = done + 1)
}
