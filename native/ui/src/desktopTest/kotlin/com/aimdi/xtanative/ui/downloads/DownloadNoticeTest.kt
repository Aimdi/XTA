package com.aimdi.xtanative.ui.downloads

import com.aimdi.xtanative.store.downloads.DownloadCenterState
import com.aimdi.xtanative.store.downloads.DownloadEntry
import com.aimdi.xtanative.store.downloads.DownloadStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DownloadNoticeTest {
    private fun entry(name: String, status: DownloadStatus, received: Long = 0, total: Long? = null, at: Long = 0) =
        DownloadEntry(id = name, url = "https://example.test/$name", fileName = name, status = status, received = received, total = total, createdAt = at)

    private fun notice(vararg entries: DownloadEntry) = DownloadNotices.of(DownloadCenterState(entries.toList(), ready = true))

    @Test
    fun nothingIsShownOnceEveryDownloadHasEnded() {
        assertNull(notice())
        assertNull(notice(entry("a.jpg", DownloadStatus.COMPLETED), entry("b.jpg", DownloadStatus.FAILED), entry("c.jpg", DownloadStatus.INTERRUPTED)))
    }

    @Test
    fun oneDownloadIsNamedWithHowFarItIs() {
        val shown = notice(entry("a.mp4", DownloadStatus.DOWNLOADING, received = 250, total = 1000), entry("old.jpg", DownloadStatus.COMPLETED))
        assertEquals(DownloadNotice("a.mp4", 1, 25), shown)
        assertEquals(true, shown?.single)
    }

    @Test
    fun theBarRunsWhileASizeIsUnknown() {
        assertNull(notice(entry("a.mp4", DownloadStatus.DOWNLOADING, received = 250))?.percent)
        assertNull(notice(entry("a.mp4", DownloadStatus.DOWNLOADING, received = 5, total = 10), entry("b.mp4", DownloadStatus.QUEUED))?.percent)
        assertNull(notice(entry("a.mp4", DownloadStatus.DOWNLOADING, received = 5, total = 0))?.percent)
    }

    @Test
    fun severalAreCountedTogetherUnderTheOneArriving() {
        val shown = notice(
            entry("waiting.jpg", DownloadStatus.QUEUED, total = 100, at = 1),
            entry("arriving.jpg", DownloadStatus.DOWNLOADING, received = 150, total = 200, at = 2),
            entry("saving.jpg", DownloadStatus.SAVING, received = 100, total = 100, at = 3),
        )
        assertEquals(DownloadNotice("arriving.jpg", 3, 62), shown)
        assertEquals(false, shown?.single)
    }

    @Test
    fun theOldestWaitingOneIsNamedWhenNoneHasStarted() {
        assertEquals("first.jpg", notice(entry("second.jpg", DownloadStatus.QUEUED, at = 2), entry("first.jpg", DownloadStatus.QUEUED, at = 1))?.fileName)
    }

    @Test
    fun moreThanWasPromisedStillEndsAtTheWhole() {
        assertEquals(100, notice(entry("a.mp4", DownloadStatus.DOWNLOADING, received = 300, total = 200))?.percent)
    }
}
