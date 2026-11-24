package com.aimdi.xtanative.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** One window asked to open ([DesktopWindows.open]): [serial] tells it from another on the same [route]. */
data class OpenedWindow(val serial: Int, val route: String)

/**
 * The windows opened beside the app's main one, in the order they were
 * opened; the desktop's shell draws one [SecondaryWindow] for each, and
 * takes it off the list as it closes.
 */
object DesktopWindows {
    private val windows = MutableStateFlow<List<OpenedWindow>>(emptyList())
    private var serials = 0

    val opened: StateFlow<List<OpenedWindow>> = windows

    internal fun open(route: String) {
        windows.update { it + OpenedWindow(++serials, route) }
    }

    fun close(window: OpenedWindow) {
        windows.update { it - window }
    }
}

internal actual fun windowOpener(): ((route: String) -> Unit)? = DesktopWindows::open
