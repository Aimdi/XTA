package com.aimdi.xtanative.ui.theme

import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

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
