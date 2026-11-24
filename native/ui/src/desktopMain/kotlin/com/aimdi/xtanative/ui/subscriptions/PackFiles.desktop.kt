package com.aimdi.xtanative.ui.subscriptions

import androidx.compose.runtime.Composable
import com.aimdi.xtanative.ui.common.FileChoosers
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.groups_pack_export
import com.aimdi.xtanative.ui.generated.resources.groups_pack_import
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/** The desktop's open dialog (its portal's, else GTK's), over any file. */
@Composable
internal actual fun rememberPackChooser(onChosen: (read: suspend () -> String?) -> Unit): () -> Unit {
    val title = stringResource(Res.string.groups_pack_import)
    return { FileChoosers.launch { FileChoosers.open(title)?.let { file -> onChosen { file.readText() } } } }
}

/** The desktop's save dialog, opened on the downloads folder with the pack's name filled in. */
@Composable
internal actual fun rememberPackSaver(): (fileName: String, text: String) -> Unit {
    val title = stringResource(Res.string.groups_pack_export)
    return { fileName, text ->
        FileChoosers.launch {
            val file = FileChoosers.save(title, fileName) ?: return@launch
            withContext(Dispatchers.IO) { runCatching { file.writeText(text) } }
        }
    }
}
