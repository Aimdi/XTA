package com.aimdi.xtanative.ui.downloads

import com.aimdi.xtanative.store.downloads.DownloadDestination
import com.aimdi.xtanative.store.downloads.DownloadEntry
import com.aimdi.xtanative.store.downloads.DownloadHistory
import com.aimdi.xtanative.store.downloads.DownloadQueue
import com.aimdi.xtanative.store.downloads.DownloadRunner
import com.aimdi.xtanative.store.downloads.DownloadStatus
import com.aimdi.xtanative.ui.plugins.PluginDownload
import com.aimdi.xtanative.ui.saved.KeepNotice
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking

class PluginBatchesTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun stop() = scope.cancel()

    private class MemoryHistory : DownloadHistory {
        @Volatile var entries: List<DownloadEntry> = emptyList()

        override suspend fun read(): List<DownloadEntry> = entries

        override suspend fun write(entries: List<DownloadEntry>) {
            this.entries = entries.toList()
        }
    }

    /** Saves every file but those named "broken". */
    private val runner = DownloadRunner { entry, _, _, _ ->
        if (entry.fileName.startsWith("broken")) throw IOException("refused") else "content://saved/${entry.fileName}"
    }

    private fun page(n: Int) = PluginDownload("https://i.example/work_p$n.png", "work_p$n.png")

    @Test
    fun aWorksPagesAreSavedTogetherAndEachSaysHowItEnded(): Unit = runBlocking {
        val queue = DownloadQueue(MemoryHistory(), runner, scope)
        val files = listOf(page(0), PluginDownload("https://i.example/x.png", "broken.png"), page(2))
        val ended = PluginBatches.save(queue, files, DownloadDestination.SHARED, "artist", workers = 2)

        assertEquals(listOf(DownloadStatus.COMPLETED, DownloadStatus.FAILED, DownloadStatus.COMPLETED), ended.map { it?.status })
        assertEquals(listOf("work_p0.png", "broken.png", "work_p2.png"), ended.map { it?.fileName }, "in the batch's order")
        assertEquals("artist", ended.first()?.subfolder)
        assertEquals(KeepNotice.Batch(2, 3), PluginBatches.notice(ended) { null }, "one notice for the batch")
    }

    @Test
    fun aFileTheQueueRefusesIsNotSaved(): Unit = runBlocking {
        val queue = DownloadQueue(MemoryHistory(), runner, scope)
        val ended = PluginBatches.save(queue, listOf(PluginDownload("not a web address", "odd.png"), page(1)), DownloadDestination.SHARED, null, workers = 4)
        assertNull(ended.first())
        assertEquals(DownloadStatus.COMPLETED, ended.last()?.status)
    }

    @Test
    fun aLoneFileSaysWhatAnyDownloadSays() {
        val saved = DownloadEntry("1", "https://i.example/a.png", "a.png", null, true, null, 0, status = DownloadStatus.COMPLETED, savedUri = "content://a")
        assertEquals(KeepNotice.MediaSaved, PluginBatches.notice(listOf(saved)) { null })
        assertEquals(KeepNotice.SavedTo("Pictures", "content://a", "a.png"), PluginBatches.notice(listOf(saved)) { "Pictures" })
        assertEquals(KeepNotice.RetryHint, PluginBatches.notice(listOf(null)) { null }, "one the queue refused")
        assertNull(PluginBatches.notice(emptyList()) { null })
    }

    @Test
    fun aFeedsPostsAreQueuedWithoutWhatTheDownloadsHave() {
        val known = setOf(page(1).url)
        val files = listOf(page(0), page(1), page(0), PluginDownload("", "nothing.png"), page(2), page(3))
        val (queued, skipped) = PluginBatches.fresh(files, known, room = 2)

        assertEquals(listOf(page(0), page(2)), queued, "as many as the queue takes")
        assertEquals(3, skipped, "the one downloaded, the one repeated and the one without an address, as XTA counts them")
        assertEquals(emptyList(), PluginBatches.fresh(files, known, room = -1).first)
    }

    @Test
    fun theDownloadsHoldWhatWasSavedAndWhatIsUnderWay() {
        fun entry(url: String, status: DownloadStatus) = DownloadEntry(url, url, "f", null, true, null, 0, status = status)
        val entries = listOf(
            entry("https://a", DownloadStatus.COMPLETED),
            entry("https://b", DownloadStatus.QUEUED),
            entry("https://c", DownloadStatus.FAILED),
            entry("https://d", DownloadStatus.CANCELLED),
        )
        assertEquals(setOf("https://a", "https://b"), PluginBatches.known(entries))
    }
}
