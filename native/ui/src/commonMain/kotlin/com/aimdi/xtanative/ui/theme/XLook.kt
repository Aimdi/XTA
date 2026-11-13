package com.aimdi.xtanative.ui.theme

import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.ThemeAccent
import com.aimdi.xtanative.store.settings.ThemeBackground
import com.aimdi.xtanative.store.settings.ThemePrefs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** X Look's colours for one background (x_look_theme.dart), as ARGB. */
data class XLookTokens(
    val accent: Long,
    val background: Long,
    val onBackground: Long,
    val secondary: Long,
    val divider: Long,
    val card: Long,
    val border: Long,
) {
    companion object {
        private const val BLUE = 0xFF1D9BF0

        val LIGHT = XLookTokens(BLUE, 0xFFFFFFFF, 0xFF0F1419, 0xFF536471, 0xFFEFF3F4, 0xFFFFFFFF, 0xFFEFF3F4)
        val DIM = XLookTokens(BLUE, 0xFF15202B, 0xFFF7F9F9, 0xFF8899A4, 0xFF38444D, 0xFF192734, 0xFF38444D)
        val LIGHTS_OUT = XLookTokens(BLUE, 0xFF000000, 0xFFE7E9EA, 0xFF71767B, 0xFF2F3336, 0xFF000000, 0xFF2F3336)
    }
}

/** Whether the app follows the system's light or dark, or names one. */
enum class XLookMode { SYSTEM, LIGHT, DARK }

/**
 * Every colour the app is drawn with, derived from [tokens] as XTA's
 * `xLookThemeData` derives its ColorScheme: the accent and the error red are
 * corrected until they read on the page and on floating surfaces.
 */
data class XLookScheme(
    val tokens: XLookTokens,
    val isLight: Boolean,
    val primary: Long,
    val onPrimary: Long,
    val error: Long,
    val onError: Long,
    val errorContainer: Long,
    val onErrorContainer: Long,
    val surfaceContainer: Long,
    val surfaceContainerHigh: Long,
    val surfaceContainerHighest: Long,
    val primaryContainer: Long,
    val secondaryContainer: Long,
    /** Menus, dialogs, sheets: the page lifted a little in Lights out. */
    val floating: Long,
    val inset: Long,
    /** The raw accent as a fill, and what reads on it. */
    val accentFill: Long,
    val onAccentFill: Long,
)

/** The theme the settings ask for (main.dart, x_look_theme.dart). */
object XLook {
    private const val ERROR_RED = 0xFFF4212E
    private const val BLACK = 0xFF000000
    private const val WHITE = 0xFFFFFFFF

    /** Tokens for a concrete background, tinted with [accent]; System and Lights out resolve here to Lights out. */
    fun tokensFor(background: ThemeBackground, accent: ThemeAccent): XLookTokens {
        val base = when (background) {
            ThemeBackground.LIGHT -> XLookTokens.LIGHT
            ThemeBackground.DIM -> XLookTokens.DIM
            ThemeBackground.SYSTEM, ThemeBackground.LIGHTS_OUT -> XLookTokens.LIGHTS_OUT
        }
        return base.copy(accent = accent.argb)
    }

    /** The dark half: System darkens to Lights out, the cheapest on an OLED panel; Dim is a deliberate choice. */
    fun darkTokensFor(background: ThemeBackground, accent: ThemeAccent): XLookTokens =
        tokensFor(if (background == ThemeBackground.DIM) ThemeBackground.DIM else ThemeBackground.LIGHTS_OUT, accent)

    /** Only System defers to the device; every other background names a brightness. */
    fun modeFor(background: ThemeBackground): XLookMode = when (background) {
        ThemeBackground.SYSTEM -> XLookMode.SYSTEM
        ThemeBackground.LIGHT -> XLookMode.LIGHT
        ThemeBackground.DIM, ThemeBackground.LIGHTS_OUT -> XLookMode.DARK
    }

    /** The tokens in force for [settings] while the system is dark or not. */
    fun tokens(settings: Settings, systemDark: Boolean): XLookTokens {
        val background = settings[ThemePrefs.background]
        val accent = settings[ThemePrefs.accent]
        val dark = when (modeFor(background)) {
            XLookMode.SYSTEM -> systemDark
            XLookMode.LIGHT -> false
            XLookMode.DARK -> true
        }
        return if (dark) darkTokensFor(background, accent) else tokensFor(ThemeBackground.LIGHT, accent)
    }

    fun isLightsOut(tokens: XLookTokens): Boolean = tokens.background == BLACK && tokens.card == BLACK

    /** The quiet fill of an inset control: six percent above the page where cards are the page. */
    fun insetSurface(tokens: XLookTokens): Long =
        if (tokens.card == tokens.background) Contrast.alphaBlend(Contrast.withAlpha(tokens.onBackground, 0.06), tokens.card) else tokens.card

    /** What floats above the page: ten percent lifted where cards are the page. */
    fun floatingSurface(tokens: XLookTokens): Long =
        if (tokens.card == tokens.background) Contrast.alphaBlend(Contrast.withAlpha(tokens.onBackground, 0.10), tokens.card) else tokens.card

    fun scheme(tokens: XLookTokens): XLookScheme {
        val floating = floatingSurface(tokens)
        val inset = insetSurface(tokens)
        val lightsOut = isLightsOut(tokens)
        val readableAccent = Contrast.ensure(Contrast.ensure(tokens.accent, tokens.background), floating)
        val error = Contrast.ensure(Contrast.ensure(ERROR_RED, tokens.background), floating)
        val errorContainer = Contrast.alphaBlend(Contrast.withAlpha(error, 0.14), tokens.background)
        return XLookScheme(
            tokens = tokens,
            isLight = Contrast.luminance(tokens.background) > 0.5,
            primary = readableAccent,
            onPrimary = Contrast.foreground(readableAccent),
            error = error,
            onError = Contrast.foreground(error),
            errorContainer = errorContainer,
            onErrorContainer = Contrast.ensure(error, errorContainer),
            surfaceContainer = if (lightsOut) inset else tokens.card,
            surfaceContainerHigh = if (lightsOut) inset else tokens.card,
            surfaceContainerHighest = if (lightsOut) floating else tokens.border,
            primaryContainer = Contrast.alphaBlend(Contrast.withAlpha(tokens.accent, 0.14), tokens.card),
            secondaryContainer = Contrast.alphaBlend(Contrast.withAlpha(tokens.onBackground, 0.08), tokens.background),
            floating = floating,
            inset = inset,
            accentFill = tokens.accent,
            onAccentFill = Contrast.foreground(tokens.accent),
        )
    }

    /** The swatch a background shows in the picker: System is drawn half Light, half Lights out. */
    fun swatch(background: ThemeBackground): Long? = when (background) {
        ThemeBackground.SYSTEM -> null
        ThemeBackground.LIGHT -> WHITE
        ThemeBackground.DIM -> XLookTokens.DIM.background
        ThemeBackground.LIGHTS_OUT -> BLACK
    }
}

/** WCAG contrast on ARGB colours, as XTA's contrast.dart computes it. */
object Contrast {
    fun alpha(argb: Long): Int = ((argb shr 24) and 0xFF).toInt()

    fun red(argb: Long): Int = ((argb shr 16) and 0xFF).toInt()

    fun green(argb: Long): Int = ((argb shr 8) and 0xFF).toInt()

    fun blue(argb: Long): Int = (argb and 0xFF).toInt()

    fun argb(a: Int, r: Int, g: Int, b: Int): Long =
        (a.coerceIn(0, 255).toLong() shl 24) or (r.coerceIn(0, 255).toLong() shl 16) or (g.coerceIn(0, 255).toLong() shl 8) or b.coerceIn(0, 255).toLong()

    /** WCAG 2.1 relative luminance. */
    fun luminance(argb: Long): Double {
        fun channel(value: Int): Double {
            val v = value / 255.0
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(red(argb)) + 0.7152 * channel(green(argb)) + 0.0722 * channel(blue(argb))
    }

    fun ratio(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** [foreground], or the nearest lighter or darker shade of it that reaches [minRatio] on [background]. */
    fun ensure(foreground: Long, background: Long, minRatio: Double = 4.5): Long {
        if (ratio(foreground, background) >= minRatio) return foreground
        val target = if (luminance(background) > 0.18) 0xFF000000 else 0xFFFFFFFF
        for (step in 1..20) {
            val candidate = lerp(foreground, target, step / 20.0)
            if (ratio(candidate, background) >= minRatio) return candidate
        }
        return target
    }

    /** Black or white, whichever reads better on [background]. */
    fun foreground(background: Long): Long =
        if (ratio(0xFF000000, background) >= ratio(0xFFFFFFFF, background)) 0xFF000000 else 0xFFFFFFFF

    fun lerp(a: Long, b: Long, t: Double): Long {
        fun mix(x: Int, y: Int) = (x + (y - x) * t).roundToInt()
        return argb(mix(alpha(a), alpha(b)), mix(red(a), red(b)), mix(green(a), green(b)), mix(blue(a), blue(b)))
    }

    fun withAlpha(argb: Long, alpha: Double): Long = (argb and 0xFFFFFF) or ((alpha * 255).roundToInt().coerceIn(0, 255).toLong() shl 24)

    /** [foreground] laid over [background], as Flutter's Color.alphaBlend lays it. */
    fun alphaBlend(foreground: Long, background: Long): Long {
        val a = alpha(foreground)
        if (a == 0) return background
        val inverse = 255 - a
        val back = alpha(background)
        if (back == 255) {
            return argb(
                255,
                (a * red(foreground) + inverse * red(background)) / 255,
                (a * green(foreground) + inverse * green(background)) / 255,
                (a * blue(foreground) + inverse * blue(background)) / 255,
            )
        }
        val backAlpha = back * inverse / 255
        val out = a + backAlpha
        return argb(
            out,
            (red(foreground) * a + red(background) * backAlpha) / out,
            (green(foreground) * a + green(background) * backAlpha) / out,
            (blue(foreground) * a + blue(background) * backAlpha) / out,
        )
    }
}
