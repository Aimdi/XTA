package com.aimdi.xtanative.ui.plugins

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A file the reader dropped on a plugin's screen ([pluginFileDrop]): its
 * [name], its [mime] type (from its name where the system says nothing) and
 * its [size] in bytes, no more than [MAX_BYTES]. Its content is read only when
 * asked, off the main thread. Plugin API 17.
 */
class PluginDroppedFile internal constructor(
    val name: String,
    val mime: String,
    val size: Long,
    private val read: suspend () -> ByteArray,
) {
    /** The file's bytes; throws an `IOException` when it is gone or has grown past [MAX_BYTES] since the drop. */
    suspend fun bytes(): ByteArray = read()

    /** The file's text, read as UTF-8 (an OPML list, a plugin's export). */
    suspend fun text(): String = bytes().decodeToString()

    companion object {
        /** The largest file a drop hands a plugin (16 MiB); larger ones are left out of it. */
        const val MAX_BYTES: Long = 16L * 1024 * 1024
    }
}

/**
 * Lets the reader drop files from the file manager onto what this modifies
 * (a plugin's settings screen taking an OPML file, as its Import row's
 * chooser does). [accept] is asked of each file by its name and type, off
 * the main thread; the ones it takes, up to [PluginDroppedFile.MAX_BYTES]
 * each, reach [onFiles] together, on the main thread. A dragged link, rather
 * than files, is left to what is under it. The Linux desktop only: nothing on Android, whose screens take
 * no drops. Plugin API 17.
 */
@Composable
expect fun Modifier.pluginFileDrop(accept: (name: String, mime: String) -> Boolean, onFiles: (List<PluginDroppedFile>) -> Unit): Modifier
