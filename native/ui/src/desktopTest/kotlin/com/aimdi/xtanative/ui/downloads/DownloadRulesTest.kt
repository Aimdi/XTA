package com.aimdi.xtanative.ui.downloads

import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.core.x.model.XVideoVariant
import com.aimdi.xtanative.store.downloads.DownloadDestination
import com.aimdi.xtanative.store.downloads.DownloadEntry
import com.aimdi.xtanative.store.downloads.DownloadStatus
import com.aimdi.xtanative.store.settings.DownloadType
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.saved.KeepNotice
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadRulesTest {
    private val maya = XUser("1", "maya", "Maya")
    private val photo = XMedia(XMedia.Kind.PHOTO, "https://pbs.twimg.com/media/ABC.jpg", 10, 10)
    private val video = XMedia(
        XMedia.Kind.VIDEO,
        "https://pbs.twimg.com/ext_tw_video_thumb/1/pu/img/poster.jpg",
        10,
        10,
        variants = listOf(
            XVideoVariant("https://video.twimg.com/ext_tw_video/1/pu/vid/480x270/low.mp4?tag=12", 256_000, "video/mp4"),
            XVideoVariant("https://video.twimg.com/ext_tw_video/1/pu/vid/1280x720/high.mp4?tag=12", 2_176_000, "video/mp4"),
            XVideoVariant("https://video.twimg.com/ext_tw_video/1/pu/pl/list.m3u8", null, "application/x-mpegURL"),
        ),
    )
    private val stream = XMedia(XMedia.Kind.VIDEO, "https://pbs.twimg.com/poster.jpg", 1, 1, variants = listOf(XVideoVariant("https://x/list.m3u8", null, "application/x-mpegURL")))

    @Test
    fun theSettingSendsADownloadToAFolderTheSharedOneOrTheDialog() {
        assertEquals(DownloadDestination.SHARED, downloadDestination(Settings()))
        assertEquals(DownloadDestination.ASK, downloadDestination(Settings().with(MediaPrefs.downloadType, DownloadType.ASK)))
        val folder = Settings().with(MediaPrefs.downloadType, DownloadType.DIRECTORY).with(MediaPrefs.downloadTreeUri, "content://tree/1")
        assertEquals(DownloadDestination.folder("content://tree/1"), downloadDestination(folder))
    }

    @Test
    fun aPhotoIsSavedAtItsOriginalSizeAndAVideoAsItsBestMp4() {
        val post = XPost("5", maya, null, "", media = listOf(photo, video, stream))
        assertEquals(MediaDownload("https://pbs.twimg.com/media/ABC.jpg:orig", "maya-ABC.jpg"), mediaDownload(post, 0))
        assertEquals(MediaDownload("https://video.twimg.com/ext_tw_video/1/pu/vid/1280x720/high.mp4?tag=12", "maya-high.mp4"), mediaDownload(post, 1))
        assertNull(mediaDownload(post, 2), "a stream has no file to save")
        assertNull(mediaDownload(post, 3))
    }

    @Test
    fun aReposteSavesThePostReposted() {
        val original = XPost("5", maya, null, "", media = listOf(photo))
        val repost = XPost("6", XUser("2", "sam", "Sam"), null, "", reposted = original)
        assertEquals("maya-ABC.jpg", mediaDownload(repost, 0)?.fileName)
    }

    private fun entry(status: DownloadStatus, received: Long = 0, total: Long? = null, saved: String? = null, background: Boolean = false) =
        DownloadEntry("d", "https://x.com/a.jpg", "a.jpg", status = status, received = received, total = total, savedUri = saved, background = background)

    @Test
    fun aRowOffersWhatItsDownloadCanDo() {
        val running = DownloadRow.of(entry(DownloadStatus.DOWNLOADING, 50, 200))
        assertTrue(running.showsProgress)
        assertEquals(0.25f, running.fraction)
        assertTrue(running.canCancel)
        assertFalse(running.canRetry)
        assertEquals(DownloadIcon.DOWNLOAD, running.icon)
        assertNull(DownloadRow.of(entry(DownloadStatus.DOWNLOADING)).fraction, "an unknown size runs the bar")

        val failed = DownloadRow.of(entry(DownloadStatus.FAILED))
        assertTrue(failed.retryHint && failed.canRetry)
        assertEquals(DownloadIcon.ERROR, failed.icon)
        assertEquals(DownloadIcon.ERROR, DownloadRow.of(entry(DownloadStatus.INTERRUPTED)).icon)
        assertFalse(DownloadRow.of(entry(DownloadStatus.CHOOSING_LOCATION)).canCancel, "the dialog is the reader's to close")

        val done = DownloadRow.of(entry(DownloadStatus.COMPLETED, saved = "content://media/external/images/media/1"))
        assertTrue(done.canOpen)
        assertEquals(DownloadIcon.DONE, done.icon)
        assertEquals(DownloadIcon.CANCELLED, DownloadRow.of(entry(DownloadStatus.CANCELLED)).icon)
    }

    @Test
    fun aFinishedDownloadSaysWhereItWent() {
        val shared = entry(DownloadStatus.COMPLETED, saved = "content://media/external/images/media/1", background = true)
        assertEquals(KeepNotice.SavedTo("Pictures/XTA", "content://media/external/images/media/1", "a.jpg"), downloadNotice(shared, "Pictures/XTA"))
        assertEquals(KeepNotice.MediaSaved, downloadNotice(shared, null))
        assertEquals(KeepNotice.RetryHint, downloadNotice(entry(DownloadStatus.FAILED), null))
        assertNull(downloadNotice(entry(DownloadStatus.CANCELLED), null))
    }

    @Test
    fun theDesktopSavesInTheXdgDownloadFolder() {
        val home = Files.createTempDirectory("home").toFile()
        val none: (String) -> String? = { null }
        assertEquals(File(home, "Downloads"), DesktopFolders.downloads(home, none))
        assertEquals(File("/data/dl"), DesktopFolders.downloads(home) { if (it == "XDG_DOWNLOAD_DIR") "/data/dl" else null })
        File(home, ".config").mkdirs()
        File(home, ".config/user-dirs.dirs").writeText("# made by xdg-user-dirs-update\nXDG_DESKTOP_DIR=\"\$HOME/Desktop\"\nXDG_DOWNLOAD_DIR=\"\$HOME/Téléchargements\"\n")
        assertEquals(File(home, "Téléchargements"), DesktopFolders.downloads(home, none))
        assertNull(DesktopFolders.userDirsDownload("XDG_DOWNLOAD_DIR=\"\$HOME/\"", home), "the home folder is no download folder")
        assertNull(DesktopFolders.userDirsDownload(null, home))
    }

    @Test
    fun aTakenNameGetsANumberAsAPhoneNamesASecondCopy() {
        val dir = Files.createTempDirectory("dl").toFile()
        assertEquals("a.jpg", DesktopFolders.unique(dir, "a.jpg").name)
        File(dir, "a.jpg").writeText("1")
        File(dir, "a (1).jpg").writeText("2")
        assertEquals("a (2).jpg", DesktopFolders.unique(dir, "a.jpg").name)
        File(dir, "notes").writeText("3")
        assertEquals("notes (1)", DesktopFolders.unique(dir, "notes").name)
    }

    @Test
    fun onlyThisComputersFoldersAreUsedAndShownFromHome() {
        val home = File("/home/ann")
        assertEquals(File("/media/usb"), DesktopFolders.localFolder("/media/usb"))
        assertEquals(File("/media/usb"), DesktopFolders.localFolder("file:///media/usb"))
        assertNull(DesktopFolders.localFolder("content://com.android.externalstorage.documents/tree/primary%3ADownload"))
        assertEquals("~/Downloads/XTA Native", DesktopFolders.shown(File("/home/ann/Downloads/XTA Native"), home))
        assertEquals("/srv/files", DesktopFolders.shown(File("/srv/files"), home))
        assertEquals(File("/tmp/a b.jpg"), DesktopFolders.fileOf(File("/tmp/a b.jpg").toURI().toString()))
        assertNull(DesktopFolders.fileOf("content://media/1"))
    }
}
