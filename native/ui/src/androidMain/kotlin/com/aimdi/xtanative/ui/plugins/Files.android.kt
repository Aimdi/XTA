package com.aimdi.xtanative.ui.plugins

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The system's document picker, over any file: an OPML list comes as XML, text or plain bytes. */
@Composable
actual fun rememberTextFileChooser(title: String, onChosen: (read: suspend () -> String?) -> Unit): () -> Unit {
    val resolver = LocalContext.current.contentResolver
    val chosen = rememberUpdatedState(onChosen)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) chosen.value { withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } } }
    }
    return remember(pick) { { pick.launch(arrayOf("*/*")) } }
}

/** The system's save dialog for a file of [mime]; the text is written where the reader puts it. */
@Composable
actual fun rememberTextFileSaver(title: String, mime: String, onSaved: (String?) -> Unit): (fileName: String, text: String) -> Unit {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    val saved = rememberUpdatedState(onSaved)
    val pending = remember { arrayOfNulls<String>(1) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri ->
        val text = pending[0]
        pending[0] = null
        if (uri == null || text == null) {
            saved.value(null)
        } else {
            scope.launch { saved.value(withContext(Dispatchers.IO) { write(resolver, uri, text) }) }
        }
    }
    return remember(save) {
        { fileName, text ->
            pending[0] = text
            save.launch(fileName)
        }
    }
}

/** [text] written to [uri]; the name the reader sees it under, or null when it could not be written. */
private fun write(resolver: ContentResolver, uri: Uri, text: String): String? = runCatching {
    resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: return null
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        ?: uri.lastPathSegment
}.getOrNull()
