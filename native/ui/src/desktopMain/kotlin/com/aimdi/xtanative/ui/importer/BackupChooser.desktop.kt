package com.aimdi.xtanative.ui.importer

import androidx.compose.runtime.Composable
import com.aimdi.xtanative.ui.common.FileChoosers
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.import_choose
import org.jetbrains.compose.resources.stringResource

/** The desktop's open dialog (its portal's, else GTK's), over any file. */
@Composable
internal actual fun rememberBackupChooser(onChosen: (read: suspend () -> String?) -> Unit): () -> Unit {
    val title = stringResource(Res.string.import_choose)
    return { FileChoosers.launch { FileChoosers.open(title)?.let { file -> onChosen { file.readText() } } } }
}
