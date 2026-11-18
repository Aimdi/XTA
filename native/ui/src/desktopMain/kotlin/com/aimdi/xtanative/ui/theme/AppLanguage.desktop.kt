package com.aimdi.xtanative.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import java.util.Locale

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
