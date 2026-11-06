package com.aimdi.xtanative.ui.downloads

import com.aimdi.xtanative.store.downloads.SharedDownloadFolder
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

    /** The XDG download folder ([userDir] of `DOWNLOAD`). */
    fun downloads(home: File, env: (String) -> String?): File = userDir("DOWNLOAD", "Downloads", home, env)

    /**
     * The XDG user folder [name] (`DOWNLOAD`, `PICTURES`, `VIDEOS`):
     * `$XDG_<name>_DIR`, else what `user-dirs.dirs` in the XDG config folder
     * says, else [fallback] in the home folder.
     */
    fun userDir(name: String, fallback: String, home: File, env: (String) -> String?): File {
        env("XDG_${name}_DIR")?.takeIf { it.isNotBlank() }?.let { expanded(it, home) }?.let { return it }
        val config = env("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }?.let(::File) ?: File(home, ".config")
        val dirs = runCatching { File(config, "user-dirs.dirs").takeIf { it.isFile }?.readText() }.getOrNull()
        return userDirsEntry(dirs, name, home) ?: File(home, fallback)
    }

    /** `XDG_<name>_DIR` in the text of `user-dirs.dirs`; null when unset, or set to the home folder, which XDG reads as none. */
    fun userDirsEntry(text: String?, name: String, home: File): File? {
        val key = "XDG_${name}_DIR="
        val line = text?.lineSequence()?.map { it.trim() }?.firstOrNull { it.startsWith(key) } ?: return null
        val value = line.removePrefix(key).trim().removeSurrounding("\"")
        return expanded(value, home)?.takeIf { it.absoluteFile != home.absoluteFile }
    }

    private fun expanded(value: String, home: File): File? {
        val path = value.replace("\$HOME", home.path).let { if (it == "~" || it.startsWith("~/")) home.path + it.drop(1) else it }
        return File(path).takeIf { path.isNotEmpty() && it.isAbsolute }
    }

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
