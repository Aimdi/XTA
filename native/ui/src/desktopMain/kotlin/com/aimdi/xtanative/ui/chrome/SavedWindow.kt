package com.aimdi.xtanative.ui.chrome

import androidx.compose.ui.window.WindowPlacement
import com.aimdi.xtanative.store.replaceWith
import java.io.File
import kotlin.math.roundToInt

/**
 * The desktop window's size, place and whether it was maximized, kept as
 * XTA's linux/runner/window_state.cc keeps them: the size and place ([x],
 * [y], its top left corner on the desktop; null when unknown, as in files
 * written before places were kept) are the ones the window has while neither
 * maximized nor full screen, in `window.ini` beside the app's other data.
 */
data class SavedWindow(val width: Int, val height: Int, val maximized: Boolean = false, val x: Int? = null, val y: Int? = null) {
    /** This size cut down to fit a screen's work area of [areaWidth] by [areaHeight]; a side of 0 or less is unknown and fits anything. */
    fun fitTo(areaWidth: Int, areaHeight: Int): SavedWindow = copy(
        width = if (areaWidth > 0) minOf(width, areaWidth) else width,
        height = if (areaHeight > 0) minOf(height, areaHeight) else height,
    )

    /**
     * This window placed on the desktop's [screens] (work areas, the primary
     * first): where enough of it was left on one of them, sized to fit that
     * screen and moved wholly onto it; else fitted to the primary screen with
     * no place, so the desktop places it as on a first start. No screens
     * known keeps it as it is.
     */
    fun placedOn(screens: List<ScreenArea>): SavedWindow {
        val primary = screens.firstOrNull() ?: return this
        val unplaced = fitTo(primary.width, primary.height).copy(x = null, y = null)
        val left = x ?: return unplaced
        val top = y ?: return unplaced
        val home = screens.maxBy { it.overlap(left, top, width, height) }
        if (home.overlap(left, top, width, height) < minOf(MIN_SHOWN_AREA, width.toLong() * height / 4)) return unplaced
        val fitted = fitTo(home.width, home.height)
        return fitted.copy(
            x = left.coerceIn(home.x, home.x + home.width - fitted.width),
            y = top.coerceIn(home.y, home.y + home.height - fitted.height),
        )
    }

    /** What is kept once the window is in [placement] at [width] by [height], its corner at [x], [y] where known: full screen changes nothing, maximized keeps the size and place. */
    fun after(placement: WindowPlacement, width: Int, height: Int, x: Int? = null, y: Int? = null): SavedWindow = when (placement) {
        WindowPlacement.Fullscreen -> this
        WindowPlacement.Maximized -> copy(maximized = true)
        WindowPlacement.Floating -> SavedWindow(width, height, maximized = false, x = x ?: this.x, y = y ?: this.y)
    }

    /** The file's text, as GLib's key file writes it. */
    fun toIni(): String {
        val place = if (x != null && y != null) "x=$x\ny=$y\n" else ""
        return "[$GROUP]\nwidth=$width\nheight=$height\nmaximized=$maximized\n$place"
    }

    companion object {
        /** The smallest window, which keeps the phone layouts from overflowing. */
        const val MIN_WIDTH = 360
        const val MIN_HEIGHT = 560

        /**
         * The smallest window at the interface size [scale] (DesktopPrefs.uiScale):
         * [MIN_WIDTH] by [MIN_HEIGHT] grown or shrunk with it, as the layouts are,
         * but never past a work area of [areaWidth] by [areaHeight] (0 or less:
         * unknown), so a large size never asks for more than the screen has.
         */
        fun minimum(scale: Double, areaWidth: Int = 0, areaHeight: Int = 0): Pair<Int, Int> {
            val width = (MIN_WIDTH * scale).roundToInt()
            val height = (MIN_HEIGHT * scale).roundToInt()
            return (if (areaWidth > 0) minOf(width, areaWidth) else width) to (if (areaHeight > 0) minOf(height, areaHeight) else height)
        }

        /**
         * A post window's size at the interface size [scale]: about the
         * reading column's width (HomeWide's widest, with its margins) grown
         * with it, and as tall as a reading height; neither past a work area
         * of [areaWidth] by [areaHeight] (0 or less: unknown).
         */
        fun postWindow(scale: Double, areaWidth: Int = 0, areaHeight: Int = 0): Pair<Int, Int> {
            val width = (POST_WIDTH * scale).roundToInt()
            return (if (areaWidth > 0) minOf(width, areaWidth) else width) to (if (areaHeight > 0) minOf(POST_HEIGHT, areaHeight) else POST_HEIGHT)
        }

        private const val POST_WIDTH = 680
        private const val POST_HEIGHT = 900

        /** A first window, tall rather than wide: the layouts are a reading column. */
        val FIRST = SavedWindow(1100, 820)

        /** Larger than any screen: a side past it is a damaged file, not a size. */
        private const val MAX_SIDE = 16384

        /** Farther than any desktop of screens reaches: a corner past it is a damaged file, not a place. */
        private const val MAX_PLACE = 65536

        /** How much of a window, in square pixels, a screen must show for its place to be kept: about a title bar's width by a row's height and more. */
        private const val MIN_SHOWN_AREA = 200L * 120
        private const val GROUP = "window"

        /** The window [text] keeps; a size missing or out of range is [FIRST]'s, and an unreadable file is [FIRST]. */
        fun parse(text: String?): SavedWindow {
            val values = text?.let(::windowGroup) ?: return FIRST
            val width = values["width"]?.toIntOrNull()
            val height = values["height"]?.toIntOrNull()
            val sized = width != null && height != null && width in 1..MAX_SIDE && height in 1..MAX_SIDE
            val x = values["x"]?.toIntOrNull()?.takeIf { it in -MAX_PLACE..MAX_PLACE }
            val y = values["y"]?.toIntOrNull()?.takeIf { it in -MAX_PLACE..MAX_PLACE }
            val placed = sized && x != null && y != null
            return SavedWindow(
                width = if (sized) width!! else FIRST.width,
                height = if (sized) height!! else FIRST.height,
                maximized = values["maximized"]?.trim() == "true",
                x = if (placed) x else null,
                y = if (placed) y else null,
            )
        }

        private fun windowGroup(text: String): Map<String, String> {
            var group: String? = null
            val values = HashMap<String, String>()
            text.lineSequence().map { it.trim() }.forEach { line ->
                when {
                    line.startsWith("[") && line.endsWith("]") -> group = line.substring(1, line.length - 1)
                    group == GROUP && '=' in line -> values[line.substringBefore('=').trim()] = line.substringAfter('=').trim()
                }
            }
            return values
        }
    }
}

/** A screen's work area on the desktop, in the window's units: where windows may be, without its panels. */
data class ScreenArea(val x: Int, val y: Int, val width: Int, val height: Int) {
    /** How many square units of a [width] by [height] window at [left], [top] this area shows. */
    fun overlap(left: Int, top: Int, width: Int, height: Int): Long {
        val across = minOf(x + this.width, left + width) - maxOf(x, left)
        val down = minOf(y + this.height, top + height) - maxOf(y, top)
        return if (across > 0 && down > 0) across.toLong() * down else 0L
    }
}

/** [SavedWindow] in [file]: read once at start, written after each change has rested and when the window closes. */
class SavedWindowFile(private val file: File) {
    fun read(): SavedWindow = SavedWindow.parse(runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull())

    /** Writes [window] through a synced file beside, so neither a crash nor a power cut leaves half a file. */
    fun write(window: SavedWindow) {
        runCatching { file.replaceWith(window.toIni()) }
    }
}
