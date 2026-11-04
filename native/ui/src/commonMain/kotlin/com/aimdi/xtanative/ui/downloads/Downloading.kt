package com.aimdi.xtanative.ui.downloads

import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.store.downloads.DownloadBatchResult
import com.aimdi.xtanative.store.downloads.DownloadRequest
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.ui.saved.KeepNotice
import com.aimdi.xtanative.ui.saved.KeepNotices
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Downloads the screens ask for (downloads.dart): each goes where the
 * download setting says, as many at once as it allows, and says at the foot
 * of the window how it went. A download outlives the screen that asked for it.
 */
object Downloading {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Saves the media at [index] of [post]: a photo at its original size, a video or GIF as its best MP4. */
    fun media(graph: AppGraph, post: XPost, index: Int) {
        val target = mediaDownload(post, index) ?: return KeepNotices.show(KeepNotice.NoMediaFile)
        scope.launch { file(graph, target.url, target.fileName) }
    }

    /** Saves [url] as [fileName], in [subfolder] of the destination when one is named, saying how it went. */
    suspend fun file(graph: AppGraph, url: String, fileName: String, subfolder: String? = null) {
        KeepNotices.show(KeepNotice.Downloading)
        val notice = try {
            val settings = graph.settings.load()
            graph.downloads.setConcurrency(settings[MediaPrefs.downloadConcurrency])
            val destination = downloadDestination(settings)
            val entry = graph.downloads.enqueue(url, fileName, destination.treeUri, destination.background, subfolder).await()
            downloadNotice(entry, graph.platformFiles.folderOf(entry))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            KeepNotice.DownloadFailed
        }
        KeepNotices.endWorking()
        notice?.let(KeepNotices::show)
    }

    /** Queues every request, as a folder set to auto-download asks, then says how many were saved. */
    fun batch(graph: AppGraph, requests: List<DownloadRequest>) {
        scope.launch {
            KeepNotices.show(KeepNotice.Downloading)
            val result = runCatching { queue(graph, requests) }.getOrElse { DownloadBatchResult(0, requests.size) }
            KeepNotices.endWorking()
            KeepNotices.show(KeepNotice.Batch(result.saved, result.total))
        }
    }

    private suspend fun queue(graph: AppGraph, requests: List<DownloadRequest>): DownloadBatchResult {
        graph.downloads.setConcurrency(graph.settings.load()[MediaPrefs.downloadConcurrency])
        return graph.downloads.enqueueBatch(requests)
    }
}
