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

/**
 * A file the reader opened with the app (`xta-native feeds.opml`) for a
 * plugin's screen to take as if it had been dropped there: the next
 * [pluginFileDrop] shown whose `accept` takes it gets it, once, within
 * [WAIT_MS] of the reader choosing the plugin.
 */
internal object OpenedPluginFile {
    private const val WAIT_MS = 30_000L
    private var waiting: Pair<PluginDroppedFile, Long>? = null

    @Synchronized
    fun offer(file: PluginDroppedFile, now: Long = System.currentTimeMillis()) {
        waiting = file to now
    }

    /** The file waiting, when [accept] takes it; gone from here once taken. */
    @Synchronized
    fun take(accept: (name: String, mime: String) -> Boolean, now: Long = System.currentTimeMillis()): PluginDroppedFile? {
        val (file, at) = waiting ?: return null
        if (now - at > WAIT_MS) return null.also { waiting = null }
        if (!runCatching { accept(file.name, file.mime) }.getOrDefault(false)) return null
        waiting = null
        return file
    }

    /** [bytes] as a file named [name] of type [mime], for [offer]. */
    fun of(name: String, mime: String, bytes: ByteArray): PluginDroppedFile = PluginDroppedFile(name, mime, bytes.size.toLong()) { bytes }
}
