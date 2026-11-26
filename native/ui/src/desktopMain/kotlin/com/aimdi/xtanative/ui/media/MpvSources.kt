package com.aimdi.xtanative.ui.media

import com.aimdi.xtanative.ui.common.XdgFolders
import java.io.File
import java.net.URI

/**
 * What mpv may open: http(s) addresses, which go through the app's proxy,
 * and files in the app's own folders (offline saves, note pictures, voice
 * clips). A source's address can come from network data (an RSS
 * enclosure, a plugin), and FFmpeg would follow rtmp://, udp://, smb:// and
 * the like straight past the proxy, or read any file, or run lavfi://
 * filters, so everything else is refused before mpv sees it.
 */
internal object MpvSources {
    private val WEB = setOf("http", "https")

    /** The app's folders under the XDG data and cache folders. */
    val appFolders: List<File> by lazy {
        listOf(XdgFolders.base("XDG_DATA_HOME", ".local/share"), XdgFolders.base("XDG_CACHE_HOME", ".cache")).map { File(it, "xta-native") }
    }

    /** Whether [source], an address or a file's path, may be handed to mpv, files only in [folders]. */
    fun allowed(source: String, folders: List<File> = appFolders): Boolean {
        val scheme = source.substringBefore(':', "").lowercase()
        return when {
            source.startsWith("/") -> inside(File(source), folders)
            scheme in WEB -> source.substring(scheme.length).startsWith("://")
            scheme == "file" -> runCatching { File(URI(source)) }.getOrNull()?.let { inside(it, folders) } == true
            else -> false
        }
    }

    private fun inside(file: File, folders: List<File>): Boolean {
        val path = runCatching { file.canonicalFile.toPath() }.getOrNull() ?: return false
        return folders.any { folder -> runCatching { path.startsWith(folder.canonicalFile.toPath()) }.getOrDefault(false) }
    }
}
