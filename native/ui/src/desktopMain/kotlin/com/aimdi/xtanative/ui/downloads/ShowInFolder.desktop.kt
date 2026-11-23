package com.aimdi.xtanative.ui.downloads

import androidx.compose.runtime.Composable
import com.aimdi.xtanative.ui.media.mpris.DBusConnection
import com.aimdi.xtanative.ui.media.mpris.DBusType
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberShowInFolder(): (suspend (savedUri: String) -> Boolean)? = { saved -> FileManagerShow.show(saved) }

/**
 * "Show in folder" the desktop's way: the file manager's
 * org.freedesktop.FileManager1 ShowItems on the session bus, which opens
 * the folder with the file picked out (Nautilus, Dolphin, Nemo, Thunar,
 * Flea answer it); where none answers, the folder itself through
 * xdg-open.
 */
internal object FileManagerShow {
    const val NAME = "org.freedesktop.FileManager1"
    const val PATH = "/org/freedesktop/FileManager1"

    /** ShowItems' arguments for [file]: its file:// address, and no startup id. */
    fun showItems(file: File): List<Any> = listOf(listOf(file.absoluteFile.toPath().toUri().toASCIIString()), "")

    suspend fun show(savedUri: String, connect: () -> DBusConnection? = DBusConnection::session): Boolean = withContext(Dispatchers.IO) {
        val file = DesktopFolders.fileOf(savedUri)?.takeIf { it.exists() } ?: return@withContext false
        val shown = runCatching {
            connect()?.use { bus -> bus.call(NAME, PATH, NAME, "ShowItems", "ass", showItems(file))?.type == DBusType.METHOD_RETURN }
        }.getOrNull() == true
        shown || runCatching { ProcessBuilder("xdg-open", file.parentFile.path).start(); true }.getOrDefault(false)
    }
}
