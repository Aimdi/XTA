package com.aimdi.xtanative.ui.plugins

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter

/** The system's open dialog (GTK's on Linux), over any file. */
@Composable
actual fun rememberTextFileChooser(title: String, onChosen: (read: suspend () -> String?) -> Unit): () -> Unit =
    rememberOpenDialog(title, filter = null) { file -> onChosen { file.takeIf { it.isFile }?.readText() } }

/** The system's open dialog, showing pictures. */
@Composable
actual fun rememberImageChooser(title: String, onChosen: (read: suspend () -> ByteArray?) -> Unit): () -> Unit =
    rememberOpenDialog(title, PICTURES) { file -> onChosen { file.takeIf { it.isFile }?.readBytes() } }

/** The system's open dialog titled [title], over the files [filter] takes (any, without one); [onChosen] gets the one picked. */
@Composable
private fun rememberOpenDialog(title: String, filter: FilenameFilter?, onChosen: (File) -> Unit): () -> Unit {
    val chosen = rememberUpdatedState(onChosen)
    return remember(title) { { dialog(title, FileDialog.LOAD, name = null, filter = filter)?.let(chosen.value) } }
}

private val PICTURE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

private val PICTURES = FilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in PICTURE_EXTENSIONS }

/** The system's save dialog, opened on ~/Downloads with the file's name filled in; the file is written where the reader puts it. */
@Composable
actual fun rememberTextFileSaver(title: String, mime: String, onSaved: (String?) -> Unit): (fileName: String, text: String) -> Unit {
    val saved = rememberUpdatedState(onSaved)
    return remember(title) {
        { fileName, text ->
            val file = dialog(title, FileDialog.SAVE, fileName)
            saved.value(file?.takeIf { runCatching { it.writeText(text) }.isSuccess }?.path)
        }
    }
}

/** The same dialog, telling a file that could not be written from a reader who closed the dialog. */
@Composable
actual fun rememberTextFileSaver(title: String, mime: String, onSaved: (String) -> Unit, onFailed: () -> Unit): (fileName: String, text: String) -> Unit {
    val saved = rememberUpdatedState(onSaved)
    val failed = rememberUpdatedState(onFailed)
    return remember(title) {
        { fileName, text ->
            val file = dialog(title, FileDialog.SAVE, fileName)
            textSaveEnded(file != null, file?.takeIf { runCatching { it.writeText(text) }.isSuccess }?.path, saved.value, failed.value)
        }
    }
}

private fun dialog(title: String, mode: Int, name: String?, filter: FilenameFilter? = null): File? {
    val dialog = FileDialog(null as Frame?, title, mode)
    if (mode == FileDialog.SAVE) File(System.getProperty("user.home"), "Downloads").takeIf { it.isDirectory }?.let { dialog.directory = it.path }
    name?.let { dialog.file = it }
    filter?.let { dialog.filenameFilter = it }
    dialog.isVisible = true
    val file = dialog.files.firstOrNull()
    dialog.dispose()
    return file
}
