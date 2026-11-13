package com.aimdi.xtanative.ui.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** The system's document picker: any file, since a backup may come as JSON, text or plain bytes. */
@Composable
internal actual fun rememberBackupChooser(onChosen: (read: suspend () -> String?) -> Unit): () -> Unit {
    val resolver = LocalContext.current.contentResolver
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onChosen { resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }
    }
    return { pick.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }
}
