package com.aimdi.xtanative.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.settings_download_path
import com.aimdi.xtanative.ui.generated.resources.settings_export
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.util.Locale
import javax.swing.JFileChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

internal actual val isDesktop: Boolean = true

/** The system's save dialog, in ~/Downloads first. */
@Composable
internal actual fun rememberBackupSaver(onSaved: (String?) -> Unit): (fileName: String, text: String) -> Unit {
    val title = stringResource(Res.string.settings_export)
    val scope = rememberCoroutineScope()
    return { fileName, text ->
        val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE)
        File(System.getProperty("user.home"), "Downloads").takeIf { it.isDirectory }?.let { dialog.directory = it.path }
        dialog.file = fileName
        dialog.isVisible = true
        val chosen = dialog.files.firstOrNull()
        dialog.dispose()
        if (chosen == null) {
            onSaved(null)
        } else {
            scope.launch {
                val saved = withContext(Dispatchers.IO) { runCatching { chosen.writeText(text) }.isSuccess }
                onSaved(if (saved) chosen.name else null)
            }
        }
    }
}

/** A folder chooser; the folder's path is what is stored. */
@Composable
internal actual fun rememberFolderPicker(onPicked: (stored: String, name: String) -> Unit): () -> Unit {
    val title = stringResource(Res.string.settings_download_path)
    return {
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            currentDirectory = File(System.getProperty("user.home"))
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.let { onPicked(it.absolutePath, it.name) }
        }
    }
}

/** The reader's language, read where the words are, so they are read again when it changes. */
internal val LocalLanguage = compositionLocalOf<Pair<String, String>?> { null }

/** The language the app started in, which "System" goes back to. */
private val systemLocale: Locale = Locale.getDefault()

/**
 * The screen's density, a new one for each language: Compose Multiplatform's
 * resources read the density where they read the locale, so every word on
 * screen is read again in the new language, without the screens being made
 * anew.
 */
private class LanguageDensity(base: Density) : Density by base

/**
 * The JVM's default locale, which the resources read, set to the reader's
 * language, or back to the system's for none. A new language takes effect
 * at once, as XTA's does: the screens read their words again where they
 * stand, keeping where the reader is.
 */
@Composable
actual fun ProvideAppLanguage(language: Pair<String, String>?, content: @Composable () -> Unit) {
    @Suppress("DEPRECATION")
    Locale.setDefault(language?.let { Locale(it.first, it.second) } ?: systemLocale)
    val base = LocalDensity.current
    val density = remember(base, language) { LanguageDensity(base) }
    CompositionLocalProvider(LocalLanguage provides language, LocalDensity provides density, content = content)
}
