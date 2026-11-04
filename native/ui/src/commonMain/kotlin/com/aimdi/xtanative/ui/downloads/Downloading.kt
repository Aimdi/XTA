package com.aimdi.xtanative.ui.downloads

import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.store.downloads.DownloadBatchResult
import com.aimdi.xtanative.store.downloads.DownloadCancellation
import com.aimdi.xtanative.store.downloads.DownloadEntry
import com.aimdi.xtanative.store.downloads.DownloadRequest
import com.aimdi.xtanative.store.downloads.DownloadStatus
import com.aimdi.xtanative.store.downloads.safeDownloadFolder
import com.aimdi.xtanative.store.downloads.safeDownloadName
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.ui.media.MediaSaver
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.saved.KeepNotice
import com.aimdi.xtanative.ui.saved.KeepNotices
import java.io.File
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        save(graph, target.url, target.fileName)
    }

    /** Saves [url] as [fileName], in [subfolder] of the destination when one is named, however long the screen that asked stays. */
    fun save(graph: AppGraph, url: String, fileName: String, subfolder: String? = null) {
        scope.launch { file(graph, url, fileName, subfolder) }
    }

    /** Saves a plugin's [media] as plugin [source] names it, or as [fileName] when it names it itself; a stream says there is no file. */
    fun pluginMedia(graph: AppGraph, source: String, media: MediaItem, fileName: String? = null, subfolder: String? = null) {
        val url = PluginDownloads.url(media) ?: return KeepNotices.show(KeepNotice.NoMediaFile)
        save(graph, url, fileName?.takeIf { it.isNotBlank() } ?: PluginDownloads.fileName(source, url), subfolder)
    }

    /**
     * Saves a file made on the device ([bytes] as [fileName]) where the
     * download setting says, with the downloads' notices, as XTA saves an
     * ugoira export: straight to the destination, as there is nothing to fetch.
     */
    fun made(graph: AppGraph, bytes: ByteArray, fileName: String, subfolder: String? = null) {
        scope.launch {
            KeepNotices.show(KeepNotice.Downloading)
            madeNow(graph, bytes, fileName, subfolder)
        }
    }

    /** [made], waited for and without its working notice, which the plugin saying what is under way has put up: whether the file was saved. */
    suspend fun madeNow(graph: AppGraph, bytes: ByteArray, fileName: String, subfolder: String? = null): Boolean {
        val notice = try {
            saveMade(graph, bytes, fileName, subfolder)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            KeepNotice.DownloadFailed
        }
        KeepNotices.endWorking()
        notice?.let(KeepNotices::show)
        return notice is KeepNotice.SavedTo || notice == KeepNotice.MediaSaved
    }

    /** Hands a file made on the device to the share sheet; where the platform has none (the desktop), saves it as [made] does. */
    fun shareMade(graph: AppGraph, bytes: ByteArray, fileName: String, mime: String) {
        scope.launch {
            val shared = try {
                MediaSharing.shareBytes(graph.platformFiles, bytes, fileName, mime)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (!shared) made(graph, bytes, fileName)
        }
    }

    /** [bytes] staged in a file of their own and handed to the platform as a finished download is; what the notice says, or nothing when the reader backed out of choosing where. */
    private suspend fun saveMade(graph: AppGraph, bytes: ByteArray, fileName: String, subfolder: String?): KeepNotice? {
        val destination = downloadDestination(graph.settings.load())
        val entry = DownloadEntry(
            id = UUID.randomUUID().toString(),
            url = "",
            fileName = safeDownloadName(fileName),
            treeUri = destination.treeUri,
            background = destination.background,
            subfolder = subfolder?.let(::safeDownloadFolder),
            createdAt = System.currentTimeMillis(),
        )
        val staged = withContext(Dispatchers.IO) { File.createTempFile("xta-made-", ".part").apply { writeBytes(bytes) } }
        try {
            val saved = graph.platformFiles.save(entry, staged, DownloadCancellation()) ?: return null
            val done = entry.copy(status = DownloadStatus.COMPLETED, savedUri = saved)
            return downloadNotice(done, graph.platformFiles.folderOf(done))
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { staged.delete() }
        }
    }

    /** The media viewer's Save (_media.dart's download button): the file the viewer names, saved as any download is. */
    fun saver(graph: AppGraph): MediaSaver = MediaSaver { media -> save(graph, media.url, media.fileName) }

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
