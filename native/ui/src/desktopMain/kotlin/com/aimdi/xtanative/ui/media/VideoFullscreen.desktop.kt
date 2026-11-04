package com.aimdi.xtanative.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.WindowPlacement
import java.awt.KeyboardFocusManager
import java.awt.Window

/** The window fills the screen while a video is full screen, as XTA's desktop fullscreen does, and goes back as it was after. */
@Composable
internal actual fun FullscreenWindow(aspectRatio: Float) {
    DisposableEffect(Unit) {
        val window = appWindow()
        val before = window?.placement
        window?.placement = WindowPlacement.Fullscreen
        onDispose { if (window != null && before != null) window.placement = before }
    }
}

/** The app's window: the one the reader is using, else the one showing. */
private fun appWindow(): ComposeWindow? =
    KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow as? ComposeWindow
        ?: Window.getWindows().filterIsInstance<ComposeWindow>().firstOrNull { it.isShowing }

/** The desktop has no picture-in-picture. */
@Composable
internal actual fun rememberPictureInPicture(onDismissed: () -> Unit): PictureInPicture? = null
