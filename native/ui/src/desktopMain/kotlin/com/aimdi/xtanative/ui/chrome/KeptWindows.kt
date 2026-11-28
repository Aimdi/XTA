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

    /**
     * The windows [text] keeps, in its order; a group without a route, or
     * with one no window opens on ([restorable]), is skipped,
     * and an unreadable file keeps none.
     */
    fun parse(text: String?): List<KeptWindow> = SavedWindow.groups(text.orEmpty())
        .filterKeys { it.startsWith(GROUP) }
        .entries
        .sortedBy { it.key.removePrefix(GROUP).toIntOrNull() ?: Int.MAX_VALUE }
        .mapNotNull { (_, values) ->
            val route = values["route"]?.takeIf { it.length <= MAX_ROUTE && restorable(it) } ?: return@mapNotNull null
            KeptWindow(route, values.takeIf { "width" in it && "height" in it }?.let(SavedWindow::of))
        }
        .take(MAX)

    /**
     * Whether [route] is one the app opens a window on ([com.aimdi.xtanative.ui.WindowRoutes.opens]):
     * a post, profile, community, list or search. Written out here, as the
     * plugins reach this file and must not reach the app's navigation.
     */
    fun restorable(route: String): Boolean = WINDOW_ROUTE.matches(route)

    private val WINDOW_ROUTE = Regex("""(post|profile|community|list)/[^/?]+(/about)?(\?.*)?|search(\?.*)?""")

    private fun place(bounds: SavedWindow): String = if (bounds.x != null && bounds.y != null) "x=${bounds.x}\ny=${bounds.y}\n" else ""

    private const val GROUP = "window"

    /** Longer than any screen's route: a line past it is a damaged file. */
    private const val MAX_ROUTE = 4096
}

/**
 * [KeptWindows] in [file]: read once at start, written as the app quits;
 * after a crash ([crashed]) removed instead, so a window that breaks as it
 * opens does not break every start after.
 */
class KeptWindowsFile(private val file: File) {
    @Volatile
    private var crashed = false

    fun read(): List<KeptWindow> = KeptWindows.parse(runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull())

    /** Writes [windows] through a synced file beside, as [SavedWindowFile] does; none left open, or a crash, removes the file. */
    fun write(windows: List<KeptWindow>) {
        runCatching { if (windows.isEmpty() || crashed) file.delete() else file.replaceWith(KeptWindows.toIni(windows)) }
    }

    /** A window crashed: no window is kept from now on, the file removed. */
    fun crashed() {
        crashed = true
        write(emptyList())
    }
}
