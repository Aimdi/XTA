package com.aimdi.xtanative.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.store.settings.AccessibilityPrefs
import com.aimdi.xtanative.store.settings.GeneralPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.common.LocalReducedMotion
import com.aimdi.xtanative.ui.settings.ProvideAppLanguage
import com.aimdi.xtanative.ui.settings.SettingsChoices

val XBlue = Color(0xFF1D9BF0)

/** The X Look colours the screens are drawn with now, for what a ColorScheme has no name for. */
val LocalXLook = staticCompositionLocalOf { XLook.scheme(XLookTokens.LIGHTS_OUT) }

private val Type = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 16.sp),
)

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
    // The language the app was started in: as in XTA, a new one takes effect at the next start.
    val language = remember(stored != null) { SettingsChoices.localeOf(settings[GeneralPrefs.locale]) }
    ProvideAppLanguage(language) {
        CompositionLocalProvider(
            LocalXLook provides scheme,
            LocalReducedMotion provides settings[AccessibilityPrefs.disableAnimations],
            LocalDensity provides Density(density.density, density.fontScale * textScale),
        ) {
            MaterialTheme(colorScheme = colorSchemeOf(scheme), typography = Type, content = content)
        }
    }
}

/** [scheme] as Material 3 names its colours; nothing is washed with a tonal tint, as in X Look. */
fun colorSchemeOf(scheme: XLookScheme): ColorScheme {
    val t = scheme.tokens
    val base = if (scheme.isLight) lightColorScheme() else darkColorScheme()
    return base.copy(
        primary = Color(scheme.primary),
        onPrimary = Color(scheme.onPrimary),
        primaryContainer = Color(scheme.primaryContainer),
        onPrimaryContainer = Color(t.onBackground),
        secondary = Color(scheme.primary),
        onSecondary = Color(scheme.onPrimary),
        secondaryContainer = Color(scheme.secondaryContainer),
        onSecondaryContainer = Color(t.onBackground),
        tertiary = Color(scheme.primary),
        onTertiary = Color(scheme.onPrimary),
        background = Color(t.background),
        onBackground = Color(t.onBackground),
        surface = Color(t.card),
        onSurface = Color(t.onBackground),
        surfaceVariant = Color(scheme.surfaceContainerHighest),
        onSurfaceVariant = Color(t.secondary),
        surfaceTint = Color.Transparent,
        surfaceBright = Color(scheme.floating),
        surfaceDim = Color(t.background),
        surfaceContainerLowest = Color(t.background),
        surfaceContainerLow = Color(t.card),
        surfaceContainer = Color(scheme.surfaceContainer),
        surfaceContainerHigh = Color(scheme.surfaceContainerHigh),
        surfaceContainerHighest = Color(scheme.surfaceContainerHighest),
        outline = Color(t.border),
        outlineVariant = Color(t.divider),
        error = Color(scheme.error),
        onError = Color(scheme.onError),
        errorContainer = Color(scheme.errorContainer),
        onErrorContainer = Color(scheme.onErrorContainer),
    )
}
