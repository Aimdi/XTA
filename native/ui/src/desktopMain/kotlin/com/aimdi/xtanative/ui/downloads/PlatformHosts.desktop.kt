package com.aimdi.xtanative.ui.downloads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.aimdi.xtanative.store.saved.LocalPosts
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The desktop's save dialog opens from the download itself: nothing to hold in the window. */
@Composable
internal actual fun PlatformFilesHost() = Unit

/** The system's open dialog (GTK's on Linux), offering the pictures (and videos) it can attach. */
@Composable
internal actual fun rememberFilePicker(images: Boolean, title: String): suspend () -> PickedFile? =
    remember(images, title) {
        val open: suspend () -> PickedFile? = { pick(images, title) }
        open
    }

private suspend fun pick(images: Boolean, title: String): PickedFile? = withContext(Dispatchers.IO) {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
    dialog.setFilenameFilter { _, name -> accepts(name, images) }
    dialog.isVisible = true
    val file = dialog.file?.let { File(dialog.directory.orEmpty(), it) }
    dialog.dispose()
    file?.let(::read)
}

private val pictures = setOf("png", "jpg", "jpeg")
private val media = pictures + setOf("gif", "webp", "heic", "heif", "mp4", "mov", "webm", "mkv")

private fun accepts(name: String, images: Boolean): Boolean = name.substringAfterLast('.', "").lowercase() in (if (images) pictures else media)

private fun read(file: File): PickedFile {
    if (!file.isFile || file.length() > PickedFile.MAX_BYTES) throw IOException("Cannot attach ${file.name}")
    return PickedFile(file.name, LocalPosts.inferMime(file.name, null), file.readBytes())
}
