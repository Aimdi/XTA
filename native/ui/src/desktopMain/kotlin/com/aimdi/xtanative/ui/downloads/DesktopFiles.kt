package com.aimdi.xtanative.ui.downloads

import com.aimdi.xtanative.store.downloads.DownloadCancellation
import com.aimdi.xtanative.store.downloads.DownloadEntry
import com.aimdi.xtanative.store.downloads.SharedDownloadFolder
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.downloads_choose_location
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString

/**
 * The desktop's files, as XTA's desktop_download_directory.dart saves them: a
 * download saved without asking goes into `XTA` in the XDG folder for its
 * type (Pictures, Videos, else Downloads), one for a picked folder into that
 * folder when it is one of this computer's, and any other where the save
 * dialog says; files open in the desktop's own apps.
 */
class DesktopFiles(
    private val home: File = File(System.getProperty("user.home")),
    private val env: (String) -> String? = System::getenv,
) : PlatformFiles {
    override suspend fun save(entry: DownloadEntry, file: File, cancellation: DownloadCancellation): String? {
        val tree = entry.treeUri
        val shared = DesktopFolders.shared(SharedDownloadFolder.of(entry.fileName), home, env)
        val folder = when {
            tree != null -> DesktopFolders.localFolder(tree) ?: shared
            entry.background -> shared
            else -> return ask(entry.fileName)?.let { target -> withContext(Dispatchers.IO) { write(file, target, cancellation) } }
        }
        return withContext(Dispatchers.IO) {
            val dir = entry.subfolder?.let { File(folder, it) } ?: folder
            if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot make $dir")
            write(file, DesktopFolders.unique(dir, entry.fileName), cancellation)
        }
    }

    /** The folder a download saved without a dialog went to; one picked in the dialog is the reader's to know. */
    override fun folderOf(entry: DownloadEntry): String? {
        if (!entry.background || entry.treeUri != null) return null
        val saved = entry.savedUri?.let(DesktopFolders::fileOf) ?: return null
        return saved.parentFile?.let { DesktopFolders.shown(it, home) }
    }

    override suspend fun open(savedUri: String, fileName: String): Boolean {
        val file = DesktopFolders.fileOf(savedUri) ?: return false
        return openFile(file, "")
    }

    override suspend fun openFile(file: File, mime: String): Boolean = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext false
        val opened = runCatching {
            val desktop = Desktop.getDesktop()
            desktop.isSupported(Desktop.Action.OPEN).also { supported -> if (supported) desktop.open(file) }
        }.getOrDefault(false)
        opened || runCatching { ProcessBuilder("xdg-open", file.path).start(); true }.getOrDefault(false)
    }

    /** The system's save dialog, opening in the downloads folder with [name] filled in; null when the reader backs out. */
    private suspend fun ask(name: String): File? {
        val title = getString(Res.string.downloads_choose_location)
        return withContext(Dispatchers.IO) {
            val start = DesktopFolders.downloads(home, env)
            val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE)
            if (start.isDirectory) dialog.directory = start.path
            dialog.file = name
            dialog.isVisible = true
            val chosen = dialog.file?.let { File(dialog.directory ?: start.path, it) }
            dialog.dispose()
            chosen
        }
    }

    private fun write(file: File, target: File, cancellation: DownloadCancellation): String {
        try {
            target.outputStream().use { copyCancellable(file, it, cancellation) }
        } catch (e: Exception) {
            target.delete()
            throw e
        }
        return target.toURI().toString()
    }
}
