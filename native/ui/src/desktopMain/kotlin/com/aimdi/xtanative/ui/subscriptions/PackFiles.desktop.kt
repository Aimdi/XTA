package com.aimdi.xtanative.ui.subscriptions

import androidx.compose.runtime.Composable
import com.aimdi.xtanative.ui.common.FileChoosers
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.groups_pack_export
import com.aimdi.xtanative.ui.generated.resources.groups_pack_import
import com.aimdi.xtanative.ui.plugins.DroppedFiles
import com.aimdi.xtanative.ui.plugins.writtenText
import com.aimdi.xtanative.ui.saved.KeepNotice
import com.aimdi.xtanative.ui.saved.KeepNotices
import org.jetbrains.compose.resources.stringResource

/** The desktop's open dialog (its portal's, else GTK's), over any file. */
@Composable
internal actual fun rememberPackChooser(onChosen: (read: suspend () -> String?) -> Unit): () -> Unit {
    val title = stringResource(Res.string.groups_pack_import)
    return { FileChoosers.launch { FileChoosers.open(title)?.let { file -> onChosen { DroppedFiles.chosen(file)?.decodeToString() } } } }
}

/** The desktop's save dialog, opened on the downloads folder with the pack's name filled in; a pack that could not be written says so. */
@Composable
internal actual fun rememberPackSaver(): (fileName: String, text: String) -> Unit {
    val title = stringResource(Res.string.groups_pack_export)
    return { fileName, text ->
        FileChoosers.launch {
            val file = FileChoosers.save(title, fileName) ?: return@launch
            if (!writtenText(file, text)) KeepNotices.show(KeepNotice.SomethingWentWrong)
        }
    }
}
