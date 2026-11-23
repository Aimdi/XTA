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

    /**
     * Whether the file manager took ShowItems, by its answer's [type]: an
     * error says none could; no answer in time is one still starting (the
     * bus starting it on demand), which shows the file once up, so the
     * folder is not opened a second time.
     */
    fun answered(type: DBusType?): Boolean = type != DBusType.ERROR

    suspend fun show(savedUri: String, connect: () -> DBusConnection? = DBusConnection::session): Boolean = withContext(Dispatchers.IO) {
        val file = DesktopFolders.fileOf(savedUri)?.takeIf { it.exists() } ?: return@withContext false
        val shown = runCatching {
            connect()?.use { bus -> answered(bus.call(NAME, PATH, NAME, "ShowItems", "ass", showItems(file))?.type) }
        }.getOrNull() == true
        shown || runCatching { ProcessBuilder("xdg-open", file.parentFile.path).start(); true }.getOrDefault(false)
    }
}
