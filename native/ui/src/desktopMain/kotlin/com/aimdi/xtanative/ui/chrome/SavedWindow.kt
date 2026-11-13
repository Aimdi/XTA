package com.aimdi.xtanative.ui.chrome

import androidx.compose.ui.window.WindowPlacement
import java.io.File

/**
 * The desktop window's size and whether it was maximized, kept as XTA's
 * linux/runner/window_state.cc keeps them: the size is the one the window has
 * while neither maximized nor full screen, in `window.ini` beside the app's
 * other data.
 */
data class SavedWindow(val width: Int, val height: Int, val maximized: Boolean = false) {
    /** This size cut down to fit a screen's work area of [areaWidth] by [areaHeight]; a side of 0 or less is unknown and fits anything. */
    fun fitTo(areaWidth: Int, areaHeight: Int): SavedWindow = copy(
        width = if (areaWidth > 0) minOf(width, areaWidth) else width,
        height = if (areaHeight > 0) minOf(height, areaHeight) else height,
    )

    /** What is kept once the window is in [placement] at [width] by [height]: full screen changes nothing, maximized keeps the size. */
    fun after(placement: WindowPlacement, width: Int, height: Int): SavedWindow = when (placement) {
        WindowPlacement.Fullscreen -> this
        WindowPlacement.Maximized -> copy(maximized = true)
        WindowPlacement.Floating -> SavedWindow(width, height, maximized = false)
    }

    /** The file's text, as GLib's key file writes it. */
    fun toIni(): String = "[$GROUP]\nwidth=$width\nheight=$height\nmaximized=$maximized\n"

    companion object {
        /** The smallest window, which keeps the phone layouts from overflowing. */
        const val MIN_WIDTH = 360
        const val MIN_HEIGHT = 560

        /** A first window, tall rather than wide: the layouts are a reading column. */
        val FIRST = SavedWindow(1100, 820)

        /** Larger than any screen: a side past it is a damaged file, not a size. */
        private const val MAX_SIDE = 16384
        private const val GROUP = "window"

        /** The window [text] keeps; a size missing or out of range is [FIRST]'s, and an unreadable file is [FIRST]. */
        fun parse(text: String?): SavedWindow {
            val values = text?.let(::windowGroup) ?: return FIRST
            val width = values["width"]?.toIntOrNull()
            val height = values["height"]?.toIntOrNull()
            val sized = width != null && height != null && width in 1..MAX_SIDE && height in 1..MAX_SIDE
            return SavedWindow(
                width = if (sized) width!! else FIRST.width,
                height = if (sized) height!! else FIRST.height,
                maximized = values["maximized"]?.trim() == "true",
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

/** [SavedWindow] in [file]: read once at start, written after each change has rested and when the window closes. */
class SavedWindowFile(private val file: File) {
    fun read(): SavedWindow = SavedWindow.parse(runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull())

    /** Writes [window] through a file beside, so a write cut short never leaves half a file. */
    fun write(window: SavedWindow) {
        runCatching {
            file.parentFile?.mkdirs()
            val next = File(file.path + ".new")
            next.writeText(window.toIni())
            if (!next.renameTo(file)) {
                file.writeText(window.toIni())
                next.delete()
            }
        }
    }
}
