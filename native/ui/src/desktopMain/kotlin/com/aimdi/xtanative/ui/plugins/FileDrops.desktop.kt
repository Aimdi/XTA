package com.aimdi.xtanative.ui.plugins

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import com.aimdi.xtanative.store.saved.LocalPosts
import com.aimdi.xtanative.ui.links.DraggedText
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Files dragged in from the file manager, read from Java's side: the list a
 * drop offers ([DataFlavor.javaFileListFlavor]), regular files only, and
 * what of them a screen takes.
 */
internal object DroppedFiles {
    private val types = mapOf(
        "opml" to "text/x-opml", "xml" to "application/xml", "json" to "application/json",
        "txt" to "text/plain", "csv" to "text/csv", "html" to "text/html", "htm" to "text/html",
    )

    /**
     * Whether [transfer] offers files from this machine. Java lists a
     * browser's dragged link (a `text/uri-list` of web addresses) as files
     * too, so a list naming no `file:` is none: the drop is left to the
     * window's dropped links, which a file target taking the drag would hide.
     */
    fun offered(transfer: Transferable): Boolean =
        runCatching { transfer.isDataFlavorSupported(DataFlavor.javaFileListFlavor) }.getOrDefault(false) && namesFile(DraggedText.uriList(transfer))

    /** Whether a dragged [uriList] names a local file; a drag with no list to read (only Java's own file list) does. */
    fun namesFile(uriList: String?): Boolean =
        uriList == null || uriList.lineSequence().map { it.trim() }.any { it.startsWith("file:", ignoreCase = true) }

    /** The regular files [transfer] holds, in its order; none for a drop of anything else. */
    fun files(transfer: Transferable): List<File> = runCatching {
        if (!offered(transfer)) return emptyList()
        (transfer.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>).orEmpty().filterIsInstance<File>().filter { it.isFile }
    }.getOrDefault(emptyList())

    /** [file]'s type by its name: the documents a plugin imports, else the pictures and videos the app knows. */
    fun mime(file: File): String = types[file.name.substringAfterLast('.', "").lowercase()] ?: LocalPosts.inferMime(file.name, null)

    /** The [files] a plugin's [accept] takes, each no larger than [max], as the plugin sees them. */
    fun forPlugin(files: List<File>, accept: (String, String) -> Boolean, max: Long = PluginDroppedFile.MAX_BYTES): List<PluginDroppedFile> =
        files.filter { it.length() <= max }
            .map { file -> PluginDroppedFile(file.name, mime(file), file.length()) { withContext(Dispatchers.IO) { readUpTo(file, max) } } }
            .filter { runCatching { accept(it.name, it.mime) }.getOrDefault(false) }

    /** [file]'s bytes, refused once it is gone or past [max]. */
    fun readUpTo(file: File, max: Long): ByteArray {
        if (!file.isFile || file.length() > max) throw IOException("Cannot read ${file.name}")
        return file.readBytes()
    }
}

/**
 * A drop target for files from the file manager: [onDrop] gets the regular
 * files of a drop and says whether it took them; one it does not, and a
 * dragged link (no local file, [DroppedFiles.offered]), is left to what is
 * under it (the window's dropped links). Off while not [enabled].
 */
// Compose's desktop drag data (awtTransferable) still carries @ExperimentalComposeUiApi; the drop target @ExperimentalFoundationApi.
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.takesFiles(enabled: Boolean = true, onDrop: (List<File>) -> Boolean): Modifier {
    val latest by rememberUpdatedState(onDrop)
    val on by rememberUpdatedState(enabled)
    val target = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val files = DroppedFiles.files(event.awtTransferable).takeIf { it.isNotEmpty() } ?: return false
                return on && latest(files)
            }
        }
    }
    return dragAndDropTarget(shouldStartDragAndDrop = { event -> on && DroppedFiles.offered(event.awtTransferable) }, target = target)
}

/** The files of a drop on the plugin's screen, those [accept] takes, up to [PluginDroppedFile.MAX_BYTES] each. */
@Composable
actual fun Modifier.pluginFileDrop(accept: (name: String, mime: String) -> Boolean, onFiles: (List<PluginDroppedFile>) -> Unit): Modifier {
    val accepts by rememberUpdatedState(accept)
    val latest by rememberUpdatedState(onFiles)
    return takesFiles { files ->
        val taken = DroppedFiles.forPlugin(files, accepts)
        if (taken.isNotEmpty()) latest(taken)
        taken.isNotEmpty()
    }
}
