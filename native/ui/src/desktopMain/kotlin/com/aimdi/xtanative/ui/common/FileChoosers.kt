package com.aimdi.xtanative.ui.common

import com.aimdi.xtanative.ui.media.mpris.DBusConnection
import java.awt.Dialog
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Window
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JFileChooser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One file chooser at a time: the desktop's portal ([portal]) first, so
 * GNOME's or KDE's own dialog opens over the reader's window; the app's
 * own ([fallback]) only where no portal could ask, never after the reader
 * closed the portal's. A second chooser asked for while one is open is
 * not opened (null), as the portal's dialog does not block the window.
 */
class FileChooser(
    private val portal: suspend (FileAsk, String) -> FileChoice,
    private val fallback: suspend (FileAsk, Window?) -> File?,
    private val owner: suspend () -> Window?,
    private val handle: (Window?) -> String = ParentWindow::handle,
) {
    private val busy = AtomicBoolean(false)

    /** The file (or folder) the reader picked for [ask]; null when they backed out or a chooser is already open. */
    suspend fun choose(ask: FileAsk): File? {
        if (!busy.compareAndSet(false, true)) return null
        return try {
            val window = owner()
            when (val choice = portal(ask, handle(window))) {
                is FileChoice.Picked -> choice.file
                FileChoice.Cancelled -> null
                FileChoice.Unavailable -> fallback(ask, window)
            }
        } finally {
            busy.set(false)
        }
    }
}

/** The app's file choosers on the desktop, and the scope they run in, which outlives the screen that asked. */
object FileChoosers {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val desktop = FileChooser(
        portal = PortalFileChooser(DBusConnection::session)::ask,
        fallback = { ask, window -> withContext(Dispatchers.Main) { AwtFileChooser.show(ask, window) } },
        owner = { withContext(Dispatchers.Main) { ActiveWindow.window() } },
    )

    /** What the reader picks for [ask] ([FileChooser.choose]). */
    suspend fun choose(ask: FileAsk): File? = desktop.choose(ask)

    /** A file to open, titled [title], over [filter]'s files. */
    suspend fun open(title: String, filter: FileFilter? = null): File? = choose(FileAsk(title, FileAskKind.OPEN, filter))

    /** Where to save [name], titled [title], opening in [folder] (the XDG downloads folder unless said). */
    suspend fun save(title: String, name: String, folder: File? = XdgFolders.downloads()): File? =
        choose(FileAsk(title, FileAskKind.SAVE, name = name, folder = folder))

    /** A folder, titled [title], opening in [folder]. */
    suspend fun folder(title: String, folder: File? = null): File? = choose(FileAsk(title, FileAskKind.FOLDER, folder = folder))

    /** Runs [block] on the UI thread in the choosers' scope: a click starts a chooser and goes on when it ends. */
    fun launch(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)
}

/** The app's own dialogs where no portal answers: AWT's (GTK's on Linux) for files, Swing's for a folder, over [Window] owner. Run on the UI thread. */
internal object AwtFileChooser {
    fun show(ask: FileAsk, owner: Window?): File? = if (ask.kind == FileAskKind.FOLDER) folder(ask, owner) else file(ask, owner)

    private fun file(ask: FileAsk, owner: Window?): File? {
        val mode = if (ask.kind == FileAskKind.SAVE) FileDialog.SAVE else FileDialog.LOAD
        val dialog = when (owner) {
            is Frame -> FileDialog(owner, ask.title, mode)
            is Dialog -> FileDialog(owner, ask.title, mode)
            else -> FileDialog(null as Frame?, ask.title, mode)
        }
        ask.folder?.takeIf { it.isDirectory }?.let { dialog.directory = it.path }
        ask.name?.let { dialog.file = it }
        ask.filter?.let { filter -> dialog.setFilenameFilter { _, name -> filter.accepts(name) } }
        dialog.isVisible = true
        val file = dialog.files.firstOrNull()
        dialog.dispose()
        return file
    }

    private fun folder(ask: FileAsk, owner: Window?): File? {
        val chooser = JFileChooser().apply {
            dialogTitle = ask.title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            currentDirectory = ask.folder?.takeIf { it.isDirectory } ?: File(System.getProperty("user.home"))
        }
        val picked = chooser.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION
        return chooser.selectedFile.takeIf { picked }
    }
}
