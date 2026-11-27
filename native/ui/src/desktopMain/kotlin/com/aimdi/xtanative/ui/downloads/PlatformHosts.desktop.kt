package com.aimdi.xtanative.ui.downloads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.awtTransferable
import com.aimdi.xtanative.ui.common.showingDropTarget
import com.aimdi.xtanative.store.saved.LocalPosts
import com.aimdi.xtanative.ui.common.FileChoosers
import com.aimdi.xtanative.ui.common.FileFilter
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.files_filter_media
import com.aimdi.xtanative.ui.generated.resources.files_filter_pictures
import com.aimdi.xtanative.ui.plugins.DroppedFiles
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/** The desktop's save dialog opens from the download itself: nothing to hold in the window. */
@Composable
internal actual fun PlatformFilesHost() = Unit

/** The desktop's open dialog (its portal's, else GTK's), offering the pictures (and videos) it can attach. */
@Composable
internal actual fun rememberFilePicker(images: Boolean, title: String): suspend () -> PickedFile? {
    val kinds = stringResource(if (images) Res.string.files_filter_pictures else Res.string.files_filter_media)
    return remember(images, title, kinds) {
        val filter = FileFilter(kinds, if (images) pictures else media)
        val open: suspend () -> PickedFile? = { FileChoosers.open(title, filter)?.let { file -> withContext(Dispatchers.IO) { read(file) } } }
        open
    }
}

private val pictures = setOf("png", "jpg", "jpeg")
private val media = pictures + setOf("gif", "webp", "heic", "heif", "mp4", "mov", "webm", "mkv")

internal fun accepts(name: String, images: Boolean): Boolean = name.substringAfterLast('.', "").lowercase() in (if (images) pictures else media)

internal fun read(file: File): PickedFile {
    if (!file.isFile || file.length() > PickedFile.MAX_BYTES) throw IOException("Cannot attach ${file.name}")
    return PickedFile(file.name, LocalPosts.inferMime(file.name, null), file.readBytes())
}

/**
 * The files of a drop the picker would take by their names ([accepts]); none
 * for a drop of anything else. None is looked at on disk, so a drop does not
 * keep the window waiting on a slow mount: [readDropped] does that.
 */
internal fun droppedFiles(transfer: Transferable): List<File> = DroppedFiles.files(transfer).filter { accepts(it.name, images = false) }

/** A dropped [file] read off the window's thread; null for one that is not a regular file (a folder named like a picture). */
internal suspend fun readDropped(file: File): PickedFile? = withContext(Dispatchers.IO) { if (file.isFile) read(file) else null }

// Compose's desktop drag data (awtTransferable) still carries @ExperimentalComposeUiApi.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun Modifier.takesDroppedFiles(enabled: Boolean, onDropped: (List<suspend () -> PickedFile?>) -> Unit): Modifier {
    if (!enabled) return this
    val latest by rememberUpdatedState(onDropped)
    return showingDropTarget(
        offered = { event -> DroppedFiles.offered(event.awtTransferable) },
        takes = { event -> droppedFiles(event.awtTransferable).isNotEmpty() },
    ) { event ->
        val files = droppedFiles(event.awtTransferable).takeIf { it.isNotEmpty() }
        files?.let { latest(it.map { file -> suspend { readDropped(file) } }) }
        files != null
    }
}

@Composable
internal actual fun rememberClipboardPicture(): (suspend () -> PickedFile?)? = remember { { ClipboardPicture.read() } }

/** The clipboard's picture as a PNG a note attaches (a screenshot copied); none while it holds text or nothing of the kind. */
internal object ClipboardPicture {
    /** The name a pasted picture gets, as a screenshot's would. */
    const val NAME = "pasted.png"

    suspend fun read(): PickedFile? = withContext(Dispatchers.IO) {
        runCatching {
            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            pictureOf(clipboard.getContents(null))
        }.getOrNull()
    }

    /** [transfer]'s picture, PNG-encoded; null when it offers text (a paste's for the field) or no picture. */
    fun pictureOf(transfer: Transferable?): PickedFile? {
        transfer ?: return null
        if (transfer.isDataFlavorSupported(DataFlavor.stringFlavor) || !transfer.isDataFlavorSupported(DataFlavor.imageFlavor)) return null
        val image = transfer.getTransferData(DataFlavor.imageFlavor) as? java.awt.Image ?: return null
        val pixels = java.awt.image.BufferedImage(image.getWidth(null).coerceAtLeast(1), image.getHeight(null).coerceAtLeast(1), java.awt.image.BufferedImage.TYPE_INT_ARGB)
        pixels.createGraphics().apply { drawImage(image, 0, 0, null); dispose() }
        val bytes = java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(pixels, "png", it) }.toByteArray()
        return bytes.takeIf { it.isNotEmpty() && it.size <= PickedFile.MAX_BYTES }?.let { PickedFile(NAME, "image/png", it) }
    }
}
