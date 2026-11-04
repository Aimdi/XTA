package com.aimdi.xtanative.ui.subscriptions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The system's document picker: any file, since a pack may come as JSON, text or plain bytes. */
@Composable
internal actual fun rememberPackChooser(onChosen: (read: suspend () -> String?) -> Unit): () -> Unit {
    val resolver = LocalContext.current.contentResolver
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onChosen { resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }
    }
    return { pick.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }
}

/** The system's save dialog for a JSON file; the pack is written where the reader puts it. */
@Composable
internal actual fun rememberPackSaver(): (fileName: String, text: String) -> Unit {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    val pending = remember { arrayOfNulls<String>(1) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val text = pending[0]
        pending[0] = null
        if (uri != null && text != null) {
            scope.launch(Dispatchers.IO) { runCatching { resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } } }
        }
    }
    return { fileName, text ->
        pending[0] = text
        save.launch(fileName)
    }
}
