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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The batches plugins save through the downloads (PluginHost's downloadAll
 * and queueDownloads): a work's pages saved together with one notice for
 * them all, as XTA's Pixiv saves them, and a feed's posts queued without the
 * files the downloads have already, as XTA's Booru queues them.
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

    /** [files] saved through [queue] in [subfolder] of [destination], [workers] at a time: each one's download as it ended, in their order; null for one the queue refused. */
    suspend fun save(queue: DownloadQueue, files: List<PluginDownload>, destination: DownloadDestination, subfolder: String?, workers: Int): List<DownloadEntry?> {
        val entries = arrayOfNulls<DownloadEntry>(files.size)
        val next = AtomicInteger()
        coroutineScope {
            repeat(minOf(workers.coerceAtLeast(1), files.size)) {
                launch {
                    while (true) {
                        val at = next.getAndIncrement()
                        if (at >= files.size) break
                        entries[at] = ended(queue, files[at], destination, subfolder)
                    }
                }
            }
        }
        return entries.toList()
    }

    private suspend fun ended(queue: DownloadQueue, file: PluginDownload, destination: DownloadDestination, subfolder: String?): DownloadEntry? = try {
        queue.enqueue(file.url, file.fileName, destination.treeUri, destination.background, subfolder).await()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
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
