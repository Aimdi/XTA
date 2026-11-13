package com.aimdi.xtanative.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import java.awt.FileDialog
import java.awt.Frame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The system's open dialog (GTK's on Linux), over the kinds of picture the app draws. */
@Composable
internal actual fun rememberImageChooser(title: String, onChosen: (read: suspend () -> ByteArray?) -> Unit): () -> Unit {
    val chosen = rememberUpdatedState(onChosen)
    return remember(title) {
        {
            val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
            dialog.setFilenameFilter { _, name -> ChromeAvatar.isPictureName(name) }
            dialog.isVisible = true
            val file = dialog.files.firstOrNull()
            dialog.dispose()
            if (file != null) chosen.value { withContext(Dispatchers.IO) { runCatching { file.inputStream().use(ChromeAvatar::pictureOf) }.getOrNull() } }
        }
    }
}
