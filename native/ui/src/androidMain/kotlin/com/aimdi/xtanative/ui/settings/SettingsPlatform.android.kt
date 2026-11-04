package com.aimdi.xtanative.ui.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.provider.DocumentsContract
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.aimdi.xtanative.services.links.InstalledBrowser
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

internal actual val isDesktop: Boolean = false

/** Every app that takes an https link, but this one, by its name. */
@Composable
internal actual fun rememberInstalledBrowsers(): List<InstalledBrowser> {
    val context = LocalContext.current
    val browsers by produceState(emptyList<InstalledBrowser>(), context) {
        value = withContext(Dispatchers.IO) { runCatching { browsersOf(context) }.getOrDefault(emptyList()) }
    }
    return browsers
}

private fun browsersOf(context: Context): List<InstalledBrowser> {
    val manager = context.packageManager
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
    return manager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        .map { InstalledBrowser(it.activityInfo.packageName, it.loadLabel(manager).toString()) }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

/** The system's text-to-speech, asked once per engine for its engines and voices, then let go. */
@Composable
internal actual fun rememberSpeechOptions(engine: String?): SpeechOptions? {
    val context = LocalContext.current
    val options by produceState<SpeechOptions?>(null, context, engine) {
        value = runCatching { speechOptions(context.applicationContext, engine) }.getOrDefault(SpeechOptions())
    }
    return options
}

private suspend fun speechOptions(context: Context, engine: String?): SpeechOptions {
    var tts: TextToSpeech? = null
    val ready = suspendCancellableCoroutine<Boolean> { done ->
        val listener = TextToSpeech.OnInitListener { status -> if (done.isActive) done.resume(status == TextToSpeech.SUCCESS) }
        tts = if (engine.isNullOrEmpty()) TextToSpeech(context, listener) else TextToSpeech(context, listener, engine)
        done.invokeOnCancellation { tts?.shutdown() }
    }
    val speaker = tts ?: return SpeechOptions()
    return try {
        val engines = speaker.engines.map { it.name } + listOfNotNull(speaker.defaultEngine).filter { it.isNotEmpty() }
        val voices = if (ready) speaker.voices.orEmpty().map { SpeechVoice(it.name, it.locale.toLanguageTag()) }.sortedBy { it.locale } else emptyList()
        SpeechOptions(engines.distinct(), voices)
    } finally {
        speaker.shutdown()
    }
}

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

/** The configuration the resources read, with the reader's language, as Compose Multiplatform's guide sets it. */
@Composable
actual fun ProvideAppLanguage(language: Pair<String, String>?, content: @Composable () -> Unit) {
    if (language == null) {
        content()
        return
    }
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val locale = Locale(language.first, language.second)
    Locale.setDefault(locale)
    val configuration = Configuration(LocalConfiguration.current).apply { setLocale(locale) }
    @Suppress("DEPRECATION")
    context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
    CompositionLocalProvider(LocalConfiguration provides configuration) {
        key(language) { content() }
    }
}
