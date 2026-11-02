package com.aimdi.xtanative.ui.importer

import androidx.compose.runtime.Composable
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.import_choose
import java.awt.FileDialog
import java.awt.Frame
import org.jetbrains.compose.resources.stringResource

/** The system's open dialog (GTK's on Linux), over any file. */
@Composable
internal actual fun rememberBackupChooser(onChosen: (read: suspend () -> String?) -> Unit): () -> Unit {
    val title = stringResource(Res.string.import_choose)
    return {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        dialog.isVisible = true
        val file = dialog.files.firstOrNull()
        dialog.dispose()
        if (file != null) onChosen { file.readText() }
    }
}
