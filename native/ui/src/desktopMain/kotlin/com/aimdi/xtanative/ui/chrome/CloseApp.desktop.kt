package com.aimdi.xtanative.ui.chrome

import androidx.compose.runtime.Composable
import java.awt.Frame
import java.awt.event.WindowEvent

/** The app's window asked to close, as its own close button asks: the shell then ends the app. */
@Composable
internal actual fun rememberCloseApp(): () -> Unit = ::closeWindow

/**
 * Whether the app was asked to quit (Ctrl+Q, leaving from the back key), not
 * just its window to close: a window that closes to the tray quits then too.
 */
object AppQuit {
    @Volatile
    private var asked = false

    /** Whether a quit was asked since the last look, which this look clears. */
    fun taken(): Boolean = asked.also { asked = false }

    internal fun ask() {
        asked = true
    }
}

private fun closeWindow() {
    AppQuit.ask()
    val window = Frame.getFrames().filter { it.isShowing }.let { shown -> shown.firstOrNull { it.isActive } ?: shown.firstOrNull() } ?: return
    window.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING))
}
