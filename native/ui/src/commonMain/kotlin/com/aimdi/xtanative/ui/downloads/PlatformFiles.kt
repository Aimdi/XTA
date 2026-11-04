package com.aimdi.xtanative.ui.downloads

import com.aimdi.xtanative.store.downloads.DownloadCancellation
import com.aimdi.xtanative.store.downloads.DownloadEntry
import com.aimdi.xtanative.store.downloads.SaveStaged
import java.io.File
import java.io.IOException

/**
 * What only the platform does with files, which each app's shell gives the
 * graph: puts a fetched download where it goes (Android's MediaStore under
 * Pictures, Movies or Download, a picked folder, or the save dialog; the
 * desktop's Downloads folder or its save dialog), says which folder that
 * was, and opens a file in the app the system has for it.
 */
interface PlatformFiles : SaveStaged {
    /** Whether a download can go to the shared folders without asking (not on Android 9 and older). */
    suspend fun sharedStorageAvailable(): Boolean = true

    /** The folder a finished download went to, as the reader knows it; null when they picked the place themselves. */
    fun folderOf(entry: DownloadEntry): String?

    /** Opens a finished download; false when no app can, or it is gone. */
    suspend fun open(savedUri: String, fileName: String): Boolean

    /** Opens a file of the app's own (a note's attachment, an offline video); false where the platform cannot share one. */
    suspend fun openFile(file: File, mime: String): Boolean

    /** Where a file waits to be handed to the share sheet; null where the platform shares no files (the desktop). */
    fun shareFolder(): File? = null

    /** The platform's share sheet for [file], one of the app's own; false where there is none. */
    suspend fun shareFile(file: File, mime: String): Boolean = false

    /** For a shell that gives none: each download fails, and can be tried again once there is one. */
    object Unavailable : PlatformFiles {
        override suspend fun save(entry: DownloadEntry, file: File, cancellation: DownloadCancellation): String? =
            throw IOException("No place to save downloads on this platform")

        override fun folderOf(entry: DownloadEntry): String? = null

        override suspend fun open(savedUri: String, fileName: String): Boolean = false

        override suspend fun openFile(file: File, mime: String): Boolean = false
    }
}

/** A file the reader picked to attach or read: its name, its type, and its bytes. */
class PickedFile(val name: String, val mime: String, val bytes: ByteArray) {
    companion object {
        /** The largest file a note takes, so a long video cannot exhaust the memory it is read into. */
        const val MAX_BYTES = 200L * 1024 * 1024
    }
}

/**
 * [file] copied into [target], through the reader's cancel: a cancelled copy
 * throws and leaves the half-written file for the caller to remove.
 */
fun copyCancellable(file: File, target: java.io.OutputStream, cancellation: DownloadCancellation) {
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            cancellation.check()
            val read = input.read(buffer)
            if (read < 0) break
            target.write(buffer, 0, read)
        }
        target.flush()
    }
}
