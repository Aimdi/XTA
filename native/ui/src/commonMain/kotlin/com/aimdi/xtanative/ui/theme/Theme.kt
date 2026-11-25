package com.aimdi.xtanative.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val XBlue = Color(0xFF1D9BF0)

/** The X Look colours the screens are drawn with now, for what a ColorScheme has no name for. */
val LocalXLook = staticCompositionLocalOf { XLook.scheme(XLookTokens.LIGHTS_OUT) }

/** XTA's type scale, which [XtaTheme] gives Material. */
internal val XtaTypography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 16.sp),
)

/** XTA's corners: a sheet's and a dialog's top is rounded as the app's own sheets are, not as wide as Material's. */
internal val XtaShapes = Shapes(extraLarge = RoundedCornerShape(20.dp))

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
        // What a sheet is drawn on: the page's own colour, in every theme.
        surfaceContainerLow = Color(t.background),
        surfaceContainer = Color(scheme.surfaceContainer),
        surfaceContainerHigh = Color(scheme.surfaceContainerHigh),
        surfaceContainerHighest = Color(scheme.surfaceContainerHighest),
        outline = Color(scheme.outline),
        outlineVariant = Color(t.divider),
        error = Color(scheme.error),
        onError = Color(scheme.onError),
        errorContainer = Color(scheme.errorContainer),
        // Behind a sheet or the drawer: the page darkens, in every theme, as behind a dialog (Compose's own black scrim).
        // X's grey-blue wash lightened Lights out's black page, which the reader keeps black on purpose.
        scrim = Color.Black,
        onErrorContainer = Color(scheme.onErrorContainer),
    )
}

/**
 * A switch in the X Look: the on knob white on the accent as X draws it
 * ([XLookScheme.onSwitch]), not Material's onPrimary, which was black on Dim's
 * blue; the off knob and edge in the outline that reads on the page.
 */
@Composable
fun xSwitchColors(): SwitchColors {
    val look = LocalXLook.current
    return SwitchDefaults.colors(
        checkedThumbColor = Color(look.onSwitch),
        uncheckedThumbColor = Color(look.outline),
        uncheckedBorderColor = Color(look.outline),
    )
}
