package com.aimdi.xtanative.ui.feed

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The colour a plugin's own card is marked with among X's posts (XTA's
 * provenanceAccentColor, provenance_accent.dart): its brand, lightened on a
 * dark surface when it is too dark to see there (readableBrandColor), then
 * taken toward black or white until it stands out from the surface two to
 * one (ensureContrast). Colours are ARGB, as Compose's toArgb gives them.
 */
object ProvenanceColors {
    fun accent(brand: Int, surface: Int): Int {
        return contrasted(brandOn(brand, dark = luminance(surface) < 0.5), surface, MIN_RATIO)
    }

    /** [brand] as a dark theme can show it (XTA's readableBrandColor): a near-black one is lightened there, any other stays. */
    fun brandOn(brand: Int, dark: Boolean): Int = if (dark && luminance(brand) < DARK_BRAND) lerp(brand, WHITE, LIGHTEN) else brand

    /** [foreground], or the nearest step of it toward black or white that reads on [background] at [ratio] (XTA's ensureContrast, 4.5 for text and marks). */
    fun readable(foreground: Int, background: Int, ratio: Double = TEXT_RATIO): Int = contrasted(foreground, background, ratio)

    /** WCAG 2.1's contrast between two colours, 1 to 21. */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** WCAG 2.1's relative luminance. */
    fun luminance(argb: Int): Double = 0.2126 * channel(argb shr 16) + 0.7152 * channel(argb shr 8) + 0.0722 * channel(argb)

    private fun channel(value: Int): Double {
        val v = (value and 0xFF) / 255.0
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    /** [foreground], or the first of twenty steps toward black or white that reaches [ratio] against [background]. */
    private fun contrasted(foreground: Int, background: Int, ratio: Double): Int {
        if (contrast(foreground, background) >= ratio) return foreground
        val target = if (luminance(background) > 0.18) BLACK else WHITE
        return (1..STEPS).map { lerp(foreground, target, it / STEPS.toDouble()) }.firstOrNull { contrast(it, background) >= ratio } ?: target
    }

    private fun lerp(a: Int, b: Int, t: Double): Int = (0..3).fold(0) { argb, i ->
        val shift = 24 - 8 * i
        val from = (a shr shift) and 0xFF
        val to = (b shr shift) and 0xFF
        argb or ((from + (to - from) * t).roundToInt().coerceIn(0, 255) shl shift)
    }

    private const val WHITE = -1
    private const val BLACK = -0x1000000
    private const val DARK_BRAND = 0.12
    private const val LIGHTEN = 0.72
    private const val MIN_RATIO = 2.0
    private const val TEXT_RATIO = 4.5
    private const val STEPS = 20
}
