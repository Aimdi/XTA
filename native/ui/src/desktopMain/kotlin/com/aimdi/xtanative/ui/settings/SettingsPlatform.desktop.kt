package com.aimdi.xtanative.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.settings_download_path
import com.aimdi.xtanative.ui.generated.resources.settings_export
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

internal actual val isDesktop: Boolean = true

/** The system's save dialog, in ~/Downloads first. */
@Composable
internal actual fun rememberBackupSaver(onSaved: (String?) -> Unit): (fileName: String, text: String) -> Unit {
    val title = stringResource(Res.string.settings_export)
    val scope = rememberCoroutineScope()
    return { fileName, text ->
        val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE)
        File(System.getProperty("user.home"), "Downloads").takeIf { it.isDirectory }?.let { dialog.directory = it.path }
        dialog.file = fileName
        dialog.isVisible = true
        val chosen = dialog.files.firstOrNull()
        dialog.dispose()
        if (chosen == null) {
            onSaved(null)
        } else {
            scope.launch {
                val saved = withContext(Dispatchers.IO) { runCatching { chosen.writeText(text) }.isSuccess }
                onSaved(if (saved) chosen.name else null)
            }
        }
    }
}

/** A folder chooser; the folder's path is what is stored. */
@Composable
internal actual fun rememberFolderPicker(onPicked: (stored: String, name: String) -> Unit): () -> Unit {
    val title = stringResource(Res.string.settings_download_path)
    return {
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            currentDirectory = File(System.getProperty("user.home"))
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.let { onPicked(it.absolutePath, it.name) }
        }
    }
}

