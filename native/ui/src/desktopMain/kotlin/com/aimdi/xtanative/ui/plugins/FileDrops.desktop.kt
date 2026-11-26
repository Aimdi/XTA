package com.aimdi.xtanative.ui.plugins

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
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

    /**
     * The files [transfer] holds, in its order, as Java lists them; none for a
     * drop of anything else. None is looked at on disk, so a drop does not
     * keep the window waiting on a slow (network) mount: [regular] does that.
     */
    fun files(transfer: Transferable): List<File> = runCatching {
        if (!offered(transfer)) return emptyList()
        (transfer.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>).orEmpty().filterIsInstance<File>()
    }.getOrDefault(emptyList())

    /** The regular files of [files], in order: asks the disk of each, so off the window's thread. */
    fun regular(files: List<File>): List<File> = files.filter { it.isFile }

    /** [file]'s type by its name: the documents a plugin imports, else the pictures and videos the app knows. */
    fun mime(file: File): String = types[file.name.substringAfterLast('.', "").lowercase()] ?: LocalPosts.inferMime(file.name, null)

    /** The [files] a plugin's [accept] takes, each no larger than [max], as the plugin sees them; asks the disk, so off the window's thread. */
    fun forPlugin(files: List<File>, accept: (String, String) -> Boolean, max: Long = PluginDroppedFile.MAX_BYTES): List<PluginDroppedFile> =
        files.filter { it.length() <= max }
            .map { file -> PluginDroppedFile(file.name, mime(file), file.length()) { withContext(Dispatchers.IO) { readUpTo(file, max) } } }
            .filter { runCatching { accept(it.name, it.mime) }.getOrDefault(false) }

    /**
     * [file]'s bytes, refused once it is gone or past [max]: read no further
     * than one byte past it, as a file still being written, or one whose size
     * the system misreports (a network mount's), can hold more than it said.
     */
    fun readUpTo(file: File, max: Long): ByteArray {
        if (!file.isFile || file.length() > max) throw IOException("Cannot read ${file.name}")
        val bytes = file.inputStream().use { it.readNBytes((max + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) }
        if (bytes.size > max) throw IOException("Cannot read ${file.name}")
        return bytes
    }
}

/**
 * A drop target for files from the file manager. It takes a drop of files;
 * off the window's thread, as files are looked at on disk, [pick] chooses
 * from its regular files what is wanted (null for nothing), and [onDrop]
 * gets that on the window's thread. A dragged link (no local file,
 * [DroppedFiles.offered]) is left to what is under it (the window's dropped
 * links). Off while not [enabled].
 */
// Compose's desktop drag data (awtTransferable) still carries @ExperimentalComposeUiApi; the drop target @ExperimentalFoundationApi.
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun <T : Any> Modifier.takesFiles(enabled: Boolean = true, pick: (List<File>) -> T?, onDrop: (T) -> Unit): Modifier {
    val picks by rememberUpdatedState(pick)
    val latest by rememberUpdatedState(onDrop)
    val on by rememberUpdatedState(enabled)
    val scope = rememberCoroutineScope()
    val target = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                if (!on) return false
                val files = DroppedFiles.files(event.awtTransferable).takeIf { it.isNotEmpty() } ?: return false
                val choose = picks
                scope.launch {
                    val picked = withContext(Dispatchers.IO) { runCatching { choose(DroppedFiles.regular(files)) }.getOrNull() } ?: return@launch
                    if (on) latest(picked)
                }
                return true
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
    return takesFiles(pick = { files -> DroppedFiles.forPlugin(files, accepts).takeIf { it.isNotEmpty() } }) { taken -> latest(taken) }
}
