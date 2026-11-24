package com.aimdi.xtanative.ui.settings

import androidx.compose.runtime.Composable
import com.aimdi.xtanative.ui.common.FileChoosers
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.settings_download_path
import com.aimdi.xtanative.ui.generated.resources.settings_export
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

internal actual val isDesktop: Boolean = true

/** The desktop's save dialog, in the downloads folder first. */
@Composable
internal actual fun rememberBackupSaver(onSaved: (String?) -> Unit): (fileName: String, text: String) -> Unit {
    val title = stringResource(Res.string.settings_export)
    return { fileName, text ->
        FileChoosers.launch {
            val chosen = FileChoosers.save(title, fileName)
            val saved = chosen != null && withContext(Dispatchers.IO) { runCatching { chosen.writeText(text) }.isSuccess }
            onSaved(chosen?.name?.takeIf { saved })
        }
    }
}

/** A folder chooser; the folder's path is what is stored. */
@Composable
internal actual fun rememberFolderPicker(onPicked: (stored: String, name: String) -> Unit): () -> Unit {
    val title = stringResource(Res.string.settings_download_path)
    return {
        FileChoosers.launch {
            FileChoosers.folder(title, File(System.getProperty("user.home")))?.let { onPicked(it.absolutePath, it.name) }
        }
    }
}

