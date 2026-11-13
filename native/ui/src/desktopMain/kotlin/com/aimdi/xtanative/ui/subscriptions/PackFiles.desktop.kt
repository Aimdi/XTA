package com.aimdi.xtanative.ui.subscriptions

import androidx.compose.runtime.Composable
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.groups_pack_export
import com.aimdi.xtanative.ui.generated.resources.groups_pack_import
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import org.jetbrains.compose.resources.stringResource

/** The system's open dialog (GTK's on Linux), over any file. */
@Composable
internal actual fun rememberPackChooser(onChosen: (read: suspend () -> String?) -> Unit): () -> Unit {
    val title = stringResource(Res.string.groups_pack_import)
    return {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        dialog.isVisible = true
        val file = dialog.files.firstOrNull()
        dialog.dispose()
        if (file != null) onChosen { file.readText() }
    }
}

/** The system's save dialog, opened on ~/Downloads with the pack's name filled in. */
@Composable
internal actual fun rememberPackSaver(): (fileName: String, text: String) -> Unit {
    val title = stringResource(Res.string.groups_pack_export)
    return { fileName, text ->
        val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE)
        File(System.getProperty("user.home"), "Downloads").takeIf { it.isDirectory }?.let { dialog.directory = it.path }
        dialog.file = fileName
        dialog.isVisible = true
        val file = dialog.files.firstOrNull()
        dialog.dispose()
        file?.let { runCatching { it.writeText(text) } }
    }
}
