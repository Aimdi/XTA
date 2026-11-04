package com.aimdi.xtanative.ui.plugins

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** The system's open dialog (GTK's on Linux), over any file. */
@Composable
actual fun rememberTextFileChooser(title: String, onChosen: (read: suspend () -> String?) -> Unit): () -> Unit {
    val chosen = rememberUpdatedState(onChosen)
    return remember(title) {
        {
            val file = dialog(title, FileDialog.LOAD, name = null)
            if (file != null) chosen.value { file.takeIf { it.isFile }?.readText() }
        }
    }
}

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

private fun dialog(title: String, mode: Int, name: String?): File? {
    val dialog = FileDialog(null as Frame?, title, mode)
    if (mode == FileDialog.SAVE) File(System.getProperty("user.home"), "Downloads").takeIf { it.isDirectory }?.let { dialog.directory = it.path }
    name?.let { dialog.file = it }
    dialog.isVisible = true
    val file = dialog.files.firstOrNull()
    dialog.dispose()
    return file
}
