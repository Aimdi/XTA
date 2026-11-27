package com.aimdi.xtanative.ui.chrome

import com.aimdi.xtanative.store.replaceWith
import java.io.File

/** A window of the app's own beside the main one, as it was left: the screen it was on ([route]) and its size and place, where known. */
data class KeptWindow(val route: String, val bounds: SavedWindow? = null)

/**
 * The windows left open beside the main one when the app quit, kept in
 * `windows.ini` beside `window.ini`, so they open again at the next start,
 * as a browser's windows come back: in the order they were opened, at most
 * [MAX] of them, each in a group of its own as GLib's key file writes one.
 */
object KeptWindows {
    /** The most windows brought back: more than a reader keeps open, and no flood of windows from a damaged file. */
    const val MAX = 8

    fun toIni(windows: List<KeptWindow>): String = windows.take(MAX).withIndex().joinToString("") { (at, window) ->
        val bounds = window.bounds?.let { "width=${it.width}\nheight=${it.height}\nmaximized=${it.maximized}\n" + place(it) }.orEmpty()
        "[$GROUP$at]\nroute=${window.route}\n$bounds"
    }

    /** The windows [text] keeps, in its order; a group without a route is skipped, and an unreadable file keeps none. */
    fun parse(text: String?): List<KeptWindow> = SavedWindow.groups(text.orEmpty())
        .filterKeys { it.startsWith(GROUP) }
        .entries
        .sortedBy { it.key.removePrefix(GROUP).toIntOrNull() ?: Int.MAX_VALUE }
        .mapNotNull { (_, values) ->
            val route = values["route"]?.takeIf { it.isNotBlank() && it.length <= MAX_ROUTE } ?: return@mapNotNull null
            KeptWindow(route, values.takeIf { "width" in it && "height" in it }?.let(SavedWindow::of))
        }
        .take(MAX)

    private fun place(bounds: SavedWindow): String = if (bounds.x != null && bounds.y != null) "x=${bounds.x}\ny=${bounds.y}\n" else ""

    private const val GROUP = "window"

    /** Longer than any screen's route: a line past it is a damaged file. */
    private const val MAX_ROUTE = 4096
}

/** [KeptWindows] in [file]: read once at start, written as the app quits. */
class KeptWindowsFile(private val file: File) {
    fun read(): List<KeptWindow> = KeptWindows.parse(runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull())

    /** Writes [windows] through a synced file beside, as [SavedWindowFile] does; none left open removes the file. */
    fun write(windows: List<KeptWindow>) {
        runCatching { if (windows.isEmpty()) file.delete() else file.replaceWith(KeptWindows.toIni(windows)) }
    }
}
