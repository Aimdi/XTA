package com.aimdi.xtanative.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

/** The reader's language, read where the words are, so they are read again when it changes. */
internal val LocalLanguage = compositionLocalOf<Pair<String, String>?> { null }

/**
 * The system's language as the desktop's session names it. The JVM takes it
 * from the C library, which knows only the locales generated on the machine:
 * for one that is not (LANG=ar_EG.UTF-8 without it), the JVM says English
 * while the session, and fontconfig, which reads the same variables, say
 * Arabic. The variables win then, as they name what the reader chose.
 */
internal object SystemLocale {
    /** The variables naming the language of messages, the first set one winning, as the C library reads them. */
    private val VARIABLES = listOf("LC_ALL", "LC_MESSAGES", "LANG")

    /** The locale [variable] gives, or the JVM's ([jvm]) where none names a language or it agrees on it. */
    fun of(variable: (String) -> String?, jvm: Locale): Locale {
        val named = VARIABLES.firstNotNullOfOrNull { name -> variable(name)?.takeIf { it.isNotBlank() } } ?: return jvm
        val parsed = parse(named) ?: return jvm
        return if (parsed.language == jvm.language) jvm else parsed
    }

    /** "ar_EG.UTF-8@latin" as Arabic in Egypt; null for C, POSIX or a value that is no locale. */
    fun parse(value: String): Locale? {
        val name = value.substringBefore('.').substringBefore('@')
        val language = name.substringBefore('_').lowercase()
        if (language.length !in 2..3 || !language.all { it in 'a'..'z' }) return null
        val region = name.substringAfter('_', "").uppercase()
        if (region.isNotEmpty() && (region.length != 2 || !region.all { it in 'A'..'Z' })) return null
        return Locale.Builder().setLanguage(language).setRegion(region).build()
    }
}

/** The language the app started in, which "System" goes back to. */
private val startLocale: Locale = SystemLocale.of(System::getenv, Locale.getDefault())

internal actual fun systemLocale(): Locale = startLocale

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
 * stand, keeping where the reader is, and turn to run right to left for
 * Arabic.
 */
@Composable
actual fun ProvideAppLanguage(language: Pair<String, String>?, content: @Composable () -> Unit) {
    @Suppress("DEPRECATION")
    val locale = language?.let { Locale(it.first, it.second) } ?: startLocale
    Locale.setDefault(locale)
    val base = LocalDensity.current
    val density = remember(base, language) { LanguageDensity(base) }
    val direction = if (AppLocale.rightToLeft(locale.language)) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(
        LocalLanguage provides language,
        LocalDensity provides density,
        LocalLayoutDirection provides direction,
        content = content,
    )
}
