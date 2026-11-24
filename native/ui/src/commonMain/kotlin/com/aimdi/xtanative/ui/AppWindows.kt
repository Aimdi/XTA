package com.aimdi.xtanative.ui

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A window of the app's own beside its main one, as a browser opens a link
 * in a new window: its screens start on [route] (a post) and go wherever it
 * leads, and [close] closes it alone. What only the main window does stays
 * there: the tray, asking before the app quits, the update check, links
 * handed to the app from outside, and where the plugins navigate.
 */
class SecondaryWindow(val route: String, val close: () -> Unit) {
    private val part = MutableStateFlow<String?>(null)

    /** What its title names before the app's name, as the main window's [DesktopWindowTitle] does. */
    val titlePart: StateFlow<String?> = part

    internal fun showTitle(text: String?) {
        part.value = text
    }
}

/** The window the screens are drawn in when it is a [SecondaryWindow]; null in the main window and on Android. */
internal val LocalSecondaryWindow = staticCompositionLocalOf<SecondaryWindow?> { null }

/**
 * Brings the main window up (out of the tray too) when what a plugin or the
 * reading-aloud bar asked for shows in it: its navigation, its sheets and the
 * voice settings are the main window's alone, so asked from a post window
 * they would show where the reader is not looking. The desktop's shell
 * decides whether it is needed; nothing where there is one window (Android).
 */
val LocalShowMainWindow = staticCompositionLocalOf<() -> Unit> { {} }

/** Opens a route in a [SecondaryWindow] of its own; null where the platform has only the one window (Android). */
internal expect fun windowOpener(): ((route: String) -> Unit)?
