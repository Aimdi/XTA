package com.aimdi.xtanative.ui.chrome

import androidx.compose.runtime.Composable
import java.awt.Frame
import java.awt.event.WindowEvent

/** The app's window asked to close, as its own close button asks: the shell then ends the app. */
@Composable
internal actual fun rememberCloseApp(): () -> Unit = ::closeWindow

private fun closeWindow() {
    val window = Frame.getFrames().filter { it.isShowing }.let { shown -> shown.firstOrNull { it.isActive } ?: shown.firstOrNull() } ?: return
    window.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING))
}
