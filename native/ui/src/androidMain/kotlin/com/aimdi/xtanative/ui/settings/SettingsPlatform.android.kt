package com.aimdi.xtanative.ui.settings

import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal actual val isDesktop: Boolean = false

/** Android's document picker, creating a JSON file. */
@Composable
internal actual fun rememberBackupSaver(onSaved: (String?) -> Unit): (fileName: String, text: String) -> Unit {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Pair<String, String>?>(null) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val (name, text) = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (uri == null) {
            onSaved(null)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching { resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } != null }.getOrDefault(false)
            }
            onSaved(if (saved) name else null)
        }
    }
    return { fileName, text ->
        pending = fileName to text
        create.launch(fileName)
    }
}

/** Android's folder picker; the folder is kept as its document tree, which this app may keep writing to. */
@Composable
internal actual fun rememberFolderPicker(onPicked: (stored: String, name: String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull().orEmpty()
            onPicked(uri.toString(), id.substringAfter(':').substringAfterLast('/').ifEmpty { id })
        }
    }
    return { pick.launch(null) }
}

/**
 * The configuration the resources read, with the reader's language, as
 * Compose Multiplatform's guide sets it, or the system's for none. A new
 * language takes effect at once, as XTA's does: the screens read their words
 * again where they stand, keeping where the reader is.
 */
@Composable
actual fun ProvideAppLanguage(language: Pair<String, String>?, content: @Composable () -> Unit) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val locale = language?.let { Locale(it.first, it.second) } ?: (Resources.getSystem().configuration.locales[0] ?: Locale.getDefault())
    Locale.setDefault(locale)
    val system = LocalConfiguration.current
    val configuration = remember(system, locale) { Configuration(system).apply { setLocale(locale) } }
    @Suppress("DEPRECATION")
    context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
    CompositionLocalProvider(LocalConfiguration provides configuration, content = content)
}
