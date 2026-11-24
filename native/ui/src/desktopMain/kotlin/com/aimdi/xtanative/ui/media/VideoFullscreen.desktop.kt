package com.aimdi.xtanative.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.WindowPlacement
import com.aimdi.xtanative.ui.common.ActiveWindow

/** The window fills the screen while a video is full screen, as XTA's desktop fullscreen does, and goes back as it was after. */
@Composable
internal actual fun FullscreenWindow(sideways: Boolean) {
    DisposableEffect(Unit) {
        val window = ActiveWindow.compose()
        val before = window?.placement
        window?.placement = WindowPlacement.Fullscreen
        onDispose { if (window != null && before != null) window.placement = before }
    }
}

/** A desktop window is not turned. */
internal actual val FullscreenTurns: Boolean = false

/** The desktop has no picture-in-picture. */
@Composable
internal actual fun rememberPictureInPicture(onDismissed: () -> Unit): PictureInPicture? = null
