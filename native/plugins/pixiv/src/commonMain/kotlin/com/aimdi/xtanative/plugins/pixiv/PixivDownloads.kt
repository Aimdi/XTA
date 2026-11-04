package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivImages
import com.aimdi.xtanative.store.downloads.DownloadEntry
import com.aimdi.xtanative.store.downloads.DownloadStatus
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.ui.downloads.downloadDestination
import com.aimdi.xtanative.ui.downloads.downloadNotice
import com.aimdi.xtanative.ui.saved.KeepNotice
import com.aimdi.xtanative.ui.saved.KeepNotices
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** One file of a save: where it is fetched from and the name it gets. */
internal data class PixivFile(val url: String, val name: String)

/**
 * Pixiv's pages saved through the app's downloads: where the download
 * setting sends them, as many at once as it allows (never more waiting than
 * the queue takes), saying at the foot of the window how it went, as every
 * download in the app does. A save outlives the screen that asked for it.
 */
internal object PixivDownloads {
    /** Saves [files] into [subfolder] of the reader's destination; which of them were saved. */
    suspend fun save(graph: AppGraph, files: List<PixivFile>, subfolder: String?): List<Boolean> {
        if (files.isEmpty()) return emptyList()
        KeepNotices.show(KeepNotice.Downloading)
        val entries = try {
            fetchAll(graph, files, subfolder)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        KeepNotices.endWorking()
        noticeOf(graph, entries)?.let(KeepNotices::show)
        return entries?.map { it?.status == DownloadStatus.COMPLETED } ?: files.map { false }
    }

    private suspend fun fetchAll(graph: AppGraph, files: List<PixivFile>, subfolder: String?): List<DownloadEntry?> {
        val settings = graph.settings.load()
        val workers = settings[MediaPrefs.downloadConcurrency].coerceIn(1, 4)
        graph.downloads.setConcurrency(workers)
        val destination = downloadDestination(settings)
        val entries = arrayOfNulls<DownloadEntry>(files.size)
        val next = AtomicInteger()
        coroutineScope {
            repeat(minOf(workers, files.size)) {
                launch {
                    while (true) {
                        val at = next.getAndIncrement()
                        if (at >= files.size) break
                        entries[at] = runCatching {
                            graph.downloads.enqueue(files[at].url, files[at].name, destination.treeUri, destination.background, subfolder).await()
                        }.getOrNull()
                    }
                }
            }
        }
        return entries.toList()
    }

    private fun noticeOf(graph: AppGraph, entries: List<DownloadEntry?>?): KeepNotice? = when {
        entries == null -> KeepNotice.DownloadFailed
        entries.size == 1 -> entries[0]?.let { downloadNotice(it, graph.platformFiles.folderOf(it)) } ?: KeepNotice.RetryHint
        else -> KeepNotice.Batch(entries.count { it?.status == DownloadStatus.COMPLETED }, entries.size)
    }

    /** [pages] of [illust] as files: each original, from the reader's image server, named as their template says. */
    fun pageFiles(runtime: PixivRuntime, illust: PixivIllust, pages: List<Int>): List<PixivFile> {
        val naming = PixivSaveNaming.of(runtime.settings.now)
        val host = runtime.settings[PixivPrefs.imageHost]
        return pages.map { page -> PixivFile(PixivImages.url(illust.downloadUrlAt(page), host), naming.pageName(illust, page)) }
    }
}
