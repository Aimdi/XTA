package com.aimdi.xtanative.ui.plugins

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.aimdi.xtanative.ui.common.FileChoosers
import com.aimdi.xtanative.ui.common.FileFilter
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.files_filter_pictures
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/** The desktop's open dialog (its portal's, else GTK's), over any file. */
@Composable
actual fun rememberTextFileChooser(title: String, onChosen: (read: suspend () -> String?) -> Unit): () -> Unit =
    rememberOpenDialog(title, filter = null) { file -> onChosen { file.takeIf { it.isFile }?.readText() } }

/** The desktop's open dialog, showing pictures. */
@Composable
actual fun rememberImageChooser(title: String, onChosen: (read: suspend () -> ByteArray?) -> Unit): () -> Unit {
    val pictures = stringResource(Res.string.files_filter_pictures)
    return rememberOpenDialog(title, FileFilter(pictures, FileFilter.PICTURE_EXTENSIONS)) { file -> onChosen { file.takeIf { it.isFile }?.readBytes() } }
}

/** The desktop's open dialog titled [title], over the files [filter] takes (any, without one); [onChosen] gets the one picked. */
@Composable
private fun rememberOpenDialog(title: String, filter: FileFilter?, onChosen: (File) -> Unit): () -> Unit {
    val chosen = rememberUpdatedState(onChosen)
    return remember(title, filter) { { FileChoosers.launch { FileChoosers.open(title, filter)?.let(chosen.value) } } }
}

/** The desktop's save dialog, opened on the downloads folder with the file's name filled in; the file is written where the reader puts it. */
@Composable
actual fun rememberTextFileSaver(title: String, mime: String, onSaved: (String?) -> Unit): (fileName: String, text: String) -> Unit {
    val saved = rememberUpdatedState(onSaved)
    return remember(title) {
        { fileName, text ->
            FileChoosers.launch {
                val file = FileChoosers.save(title, fileName)
                saved.value(file?.takeIf { written(it, text) }?.path)
            }
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
            FileChoosers.launch {
                val file = FileChoosers.save(title, fileName)
                textSaveEnded(file != null, file?.takeIf { written(it, text) }?.path, saved.value, failed.value)
            }
        }
    }
}

private suspend fun written(file: File, text: String): Boolean = withContext(Dispatchers.IO) { runCatching { file.writeText(text) }.isSuccess }
