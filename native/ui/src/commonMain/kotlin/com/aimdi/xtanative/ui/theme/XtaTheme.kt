package com.aimdi.xtanative.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.store.settings.AccessibilityPrefs
import com.aimdi.xtanative.store.settings.DesktopPrefs
import com.aimdi.xtanative.store.settings.GeneralPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.common.LocalSettingsStore
import com.aimdi.xtanative.ui.settings.isDesktop

/**
 * XTA's X Look from the settings: the background (System, Light, Dim or
 * Lights out, true black) and the accent, applied the way XTA's main.dart
 * applies them, with the reader's text size on top of the system's and, on
 * the desktop, the interface size ([DesktopPrefs.uiScale]) on top of the
 * screen's density: every dp and sp drawn under it (menus, dialogs, sheets
 * and tooltips too, which read the density where they are called) grows
 * with it, as a browser's zoom grows a page.
 */
@Composable
fun XtaTheme(content: @Composable () -> Unit) {
    val stored by LocalSettingsStore.current.state.collectAsStateWithLifecycle()
    val settings = stored ?: Settings()
    val systemDark = systemPrefersDark()
    val reducesMotion = systemReducesMotion()
    val tokens = XLook.tokens(settings, systemDark, systemAccent()).let { if (systemHighContrast()) XLook.highContrast(it) else it }
    val scheme = remember(tokens) { XLook.scheme(tokens) }
    val density = LocalDensity.current
    val textScale = settings[AccessibilityPrefs.textScale].toFloat()
    val interfaceScale = if (isDesktop) settings[DesktopPrefs.uiScale].toFloat() else 1f
    val scaled = remember(density, interfaceScale, textScale) { Density(density.density * interfaceScale, density.fontScale * textScale) }
    // As in XTA, a new language takes effect at once.
    val language = AppLocale.of(settings[GeneralPrefs.locale])
    CompositionLocalProvider(
        LocalXLook provides scheme,
        // The app's own switch, or the desktop's settings turning animations off.
        LocalReducedMotion provides (settings[AccessibilityPrefs.disableAnimations] || reducesMotion),
        LocalDensity provides scaled,
    ) {
        // Inside the reader's text size: the desktop marks the density with the language.
        ProvideAppLanguage(language) {
            MaterialTheme(colorScheme = colorSchemeOf(scheme), shapes = XtaShapes, typography = XtaTypography) {
                ProvidePlatformLook(content)
            }
        }
    }
}
