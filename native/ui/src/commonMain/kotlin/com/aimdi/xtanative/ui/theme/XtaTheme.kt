package com.aimdi.xtanative.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.store.settings.AccessibilityPrefs
import com.aimdi.xtanative.store.settings.GeneralPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.LocalReducedMotion

/**
 * XTA's X Look from the settings: the background (System, Light, Dim or
 * Lights out, true black) and the accent, applied the way XTA's main.dart
 * applies them, with the reader's text size on top of the system's.
 */
@Composable
fun XtaTheme(content: @Composable () -> Unit) {
    val stored by LocalGraph.current.settings.state.collectAsStateWithLifecycle()
    val settings = stored ?: Settings()
    val systemDark = isSystemInDarkTheme()
    val tokens = XLook.tokens(settings, systemDark)
    val scheme = remember(tokens) { XLook.scheme(tokens) }
    val density = LocalDensity.current
    val textScale = settings[AccessibilityPrefs.textScale].toFloat()
    // As in XTA, a new language takes effect at once.
    val language = AppLocale.of(settings[GeneralPrefs.locale])
    CompositionLocalProvider(
        LocalXLook provides scheme,
        LocalReducedMotion provides settings[AccessibilityPrefs.disableAnimations],
        LocalDensity provides Density(density.density, density.fontScale * textScale),
    ) {
        // Inside the reader's text size: the desktop marks the density with the language.
        ProvideAppLanguage(language) {
            MaterialTheme(colorScheme = colorSchemeOf(scheme), typography = XtaTypography, content = content)
        }
    }
}
