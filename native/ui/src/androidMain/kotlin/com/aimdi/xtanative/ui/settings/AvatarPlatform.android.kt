package com.aimdi.xtanative.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The system's picker over images, as XTA's file picker of images. */
@Composable
internal actual fun rememberImageChooser(title: String, onChosen: (read: suspend () -> ByteArray?) -> Unit): () -> Unit {
    val resolver = LocalContext.current.contentResolver
    val chosen = rememberUpdatedState(onChosen)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            chosen.value { withContext(Dispatchers.IO) { runCatching { resolver.openInputStream(uri)?.use(ChromeAvatar::pictureOf) }.getOrNull() } }
        }
    }
    return remember(pick) { { pick.launch("image/*") } }
}
