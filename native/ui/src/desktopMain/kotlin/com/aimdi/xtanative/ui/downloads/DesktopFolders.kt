package com.aimdi.xtanative.ui.downloads

import com.aimdi.xtanative.store.downloads.SharedDownloadFolder
import com.aimdi.xtanative.ui.common.XdgFolders
import java.io.File
import java.net.URI

/** Where downloads land on the Linux desktop, and the names they take there. */
object DesktopFolders {
    /** The folder a download saved without asking goes into, inside the shared folder for its type, as on Android (Pictures/XTA). */
    const val APP_FOLDER = "XTA"

    /**
     * The shared folder for [folder]'s type, with [APP_FOLDER] in it, as XTA's
     * desktop_download_directory.dart maps Android's: XDG's Pictures for
     * pictures, Videos for videos, Downloads for anything else.
     */
    fun shared(folder: SharedDownloadFolder, home: File, env: (String) -> String?): File {
        val (name, fallback) = when (folder) {
            SharedDownloadFolder.PICTURES -> "PICTURES" to "Pictures"
            SharedDownloadFolder.MOVIES -> "VIDEOS" to "Videos"
            SharedDownloadFolder.DOWNLOADS -> "DOWNLOAD" to "Downloads"
        }
        return File(userDir(name, fallback, home, env), APP_FOLDER)
    }

    /** The XDG download folder ([XdgFolders.downloads]). */
    fun downloads(home: File, env: (String) -> String?): File = XdgFolders.downloads(home, env)

    /** [XdgFolders.userDir]. */
    fun userDir(name: String, fallback: String, home: File, env: (String) -> String?): File = XdgFolders.userDir(name, fallback, home, env)

    /** [XdgFolders.userDirsEntry]. */
    fun userDirsEntry(text: String?, name: String, home: File): File? = XdgFolders.userDirsEntry(text, name, home)

    /** [name] in [dir], or `name (1).ext`, `name (2).ext`… when it is taken, as a phone names a second copy. */
    fun unique(dir: File, name: String): File {
        val first = File(dir, name)
        if (!first.exists()) return first
        val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
        val stem = name.substring(0, dot)
        val extension = name.substring(dot)
        return generateSequence(1) { it + 1 }.map { File(dir, "$stem ($it)$extension") }.first { !it.exists() }
    }

    /** The folder [treeUri] names on this computer (a path or a `file:` URI); null for another device's, such as Android's `content:`. */
    fun localFolder(treeUri: String): File? {
        val file = when {
            treeUri.startsWith("file:") -> runCatching { File(URI(treeUri)) }.getOrNull()
            treeUri.startsWith("/") -> File(treeUri)
            else -> null
        }
        return file?.takeIf { it.isAbsolute }
    }

    /** [folder] as the reader reads it, with `~` for the home folder. */
    fun shown(folder: File, home: File): String {
        val path = folder.absolutePath
        val root = home.absolutePath
        return when {
            path == root -> "~"
            path.startsWith("$root/") -> "~" + path.removePrefix(root)
            else -> path
        }
    }

    /** The file a `file:` URI names; null for any other. */
    fun fileOf(savedUri: String): File? = if (savedUri.startsWith("file:")) runCatching { File(URI(savedUri)) }.getOrNull() else null
}
