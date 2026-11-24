package com.aimdi.xtanative.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.aimdi.xtanative.ui.common.FileChoosers
import com.aimdi.xtanative.ui.common.FileFilter
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.files_filter_pictures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/** The desktop's open dialog (its portal's, else GTK's), over the kinds of picture the app draws. */
@Composable
internal actual fun rememberImageChooser(title: String, onChosen: (read: suspend () -> ByteArray?) -> Unit): () -> Unit {
    val chosen = rememberUpdatedState(onChosen)
    val pictures = stringResource(Res.string.files_filter_pictures)
    return remember(title, pictures) {
        val filter = FileFilter(pictures, FileFilter.PICTURE_EXTENSIONS)
        val open: () -> Unit = {
            FileChoosers.launch {
                val file = FileChoosers.open(title, filter) ?: return@launch
                chosen.value { withContext(Dispatchers.IO) { runCatching { file.inputStream().use(ChromeAvatar::pictureOf) }.getOrNull() } }
            }
        }
        open
    }
}
