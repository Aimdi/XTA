package com.aimdi.xtanative.ui.downloads

import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.store.downloads.DownloadCancellation
import com.aimdi.xtanative.store.downloads.DownloadEntry
import com.aimdi.xtanative.store.downloads.MediaHttp
import com.aimdi.xtanative.store.downloads.MediaResponse
import com.aimdi.xtanative.ui.media.MediaSave
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class MediaSharingTest {
    /** A platform that shares files from [folder], or none while it is null. */
    private class SharingFiles(private val folder: File?) : PlatformFiles {
        val shared = ArrayList<String>()

        override suspend fun save(entry: DownloadEntry, file: File, cancellation: DownloadCancellation): String? = null

        override fun folderOf(entry: DownloadEntry): String? = null

        override suspend fun open(savedUri: String, fileName: String): Boolean = false

        override suspend fun openFile(file: File, mime: String): Boolean = false

        override fun shareFolder(): File? = folder

        override suspend fun shareFile(file: File, mime: String): Boolean = true.also { shared += file.name + " " + mime + " " + file.readText() }
    }

    private fun answering(code: Int, body: String) = MediaHttp { _, _ -> MediaResponse(code, emptyMap(), body.length.toLong(), body.byteInputStream()) }

    private val banner = MediaSave("https://pbs.twimg.com/profile_banners/1/1600000000", "maya-1600000000", XMedia.Kind.PHOTO, "")

    @Test
    fun aPhotoIsSharedAsThePictureInPlaceOfTheOneBefore(): Unit = runBlocking {
        val folder = Files.createTempDirectory("shared").toFile()
        File(folder, "maya-old.jpg").writeText("old")
        val files = SharingFiles(folder)
        assertTrue(MediaSharing.share(files, banner, answering(200, "picture")))
        assertEquals(listOf("maya-1600000000.jpg image/jpeg picture"), files.shared)
        assertEquals(listOf("maya-1600000000.jpg"), folder.list()!!.toList())
        folder.deleteRecursively()
    }

    @Test
    fun aVideoAPlatformWithoutSharingOrAMissingFileShareTheAddress(): Unit = runBlocking {
        val folder = Files.createTempDirectory("shared").toFile()
        val video = MediaSave("https://video.twimg.com/a/high.mp4", "maya-high.mp4", XMedia.Kind.VIDEO, "42")
        assertFalse(MediaSharing.share(SharingFiles(folder), video, answering(200, "video")))
        assertFalse(MediaSharing.share(SharingFiles(null), banner, answering(200, "picture")))
        assertFalse(MediaSharing.share(SharingFiles(folder), banner, answering(404, "")))
        assertNull(MediaSharing.fetch(answering(500, ""), banner.url, folder, "a.jpg"))
        assertFalse(MediaSharing.share(SharingFiles(folder), banner, MediaHttp { _, _ -> throw IOException("offline") }), "no connection: the address is shared")
        assertEquals("maya-face_400x400.jpg", MediaSharing.fileName(banner.copy(fileName = "maya-face_400x400.jpg")))
        folder.deleteRecursively()
    }

    @Test
    fun theButtonSaysAPictureGoesOnlyWhereThePictureItselfIsShared() {
        val folder = File("shared")
        val video = banner.copy(kind = XMedia.Kind.VIDEO)
        assertTrue(MediaSharing.sharesPicture(SharingFiles(folder), banner))
        assertFalse(MediaSharing.sharesPicture(SharingFiles(folder), video), "a video shares its address")
        assertFalse(MediaSharing.sharesPicture(SharingFiles(null), banner), "the desktop shares the address")
    }
}
