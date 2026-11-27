package com.aimdi.xtanative.ui

import com.aimdi.xtanative.ui.chrome.KeptWindow
import com.aimdi.xtanative.ui.chrome.SavedWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** One window asked to open ([DesktopWindows.open]): [serial] tells it from another on the same [route]; [bounds] is where it was left, when it comes back. */
data class OpenedWindow(val serial: Int, val route: String, val bounds: SavedWindow? = null)

/**
 * The windows opened beside the app's main one, in the order they were
 * opened; the desktop's shell draws one [SecondaryWindow] for each, and
 * takes it off the list as it closes. The last ones closed are kept, so
 * Ctrl+Shift+T opens them again ([reopen]), as a browser reopens a closed tab.
 */
object DesktopWindows {
    private val windows = MutableStateFlow<List<OpenedWindow>>(emptyList())
    private val places = HashMap<Int, SavedWindow>()
    private val closed = ArrayDeque<KeptWindow>()
    private var serials = 0

    val opened: StateFlow<List<OpenedWindow>> = windows

    internal fun open(route: String) {
        windows.update { it + OpenedWindow(++serials, route) }
    }

    /** Opens [kept] again where it was left. */
    @Synchronized
    fun restore(kept: List<KeptWindow>) {
        windows.update { it + kept.map { window -> OpenedWindow(++serials, window.route, window.bounds) } }
    }

    /** [window]'s size and place as it moves, kept for when it closes or the app quits. */
    @Synchronized
    fun placed(window: OpenedWindow, bounds: SavedWindow) {
        places[window.serial] = bounds
    }

    @Synchronized
    fun close(window: OpenedWindow) {
        if (window !in windows.value) return
        windows.update { it - window }
        closed.addLast(kept(window))
        while (closed.size > CLOSED_KEPT) closed.removeFirst()
        places.remove(window.serial)
    }

    /** Opens the window closed last again, where it was; false when none was closed. */
    @Synchronized
    fun reopen(): Boolean {
        val last = closed.removeLastOrNull() ?: return false
        restore(listOf(last))
        return true
    }

    /** The windows open now, each where it is, for the next start ([KeptWindow]). */
    @Synchronized
    fun keptOpen(): List<KeptWindow> = windows.value.map(::kept)

    private fun kept(window: OpenedWindow) = KeptWindow(window.route, places[window.serial] ?: window.bounds)

    /** How many closed windows Ctrl+Shift+T can bring back. */
    const val CLOSED_KEPT = 10

    /**
     * Whether the main window comes up for what showed in it
     * ([LocalShowMainWindow]): when it is hidden, minimized or behind while
     * another of the app's windows is the one in use ([appActive]), as a post
     * window that asked for it is. With another program in use the app takes
     * no focus from it; in front already, nothing needs doing.
     */
    fun showsMain(mainShown: Boolean, mainActive: Boolean, appActive: Boolean): Boolean = appActive && !(mainShown && mainActive)
}

internal actual fun windowOpener(): ((route: String) -> Unit)? = DesktopWindows::open
