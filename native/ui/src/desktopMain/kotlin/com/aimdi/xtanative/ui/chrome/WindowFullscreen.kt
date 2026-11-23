package com.aimdi.xtanative.ui.chrome

import androidx.compose.ui.window.WindowPlacement

/**
 * F11, as a Linux app's: the window fills the screen, and F11 again puts it
 * back as it was, maximized or not.
 */
object WindowFullscreen {
    /** The placement after F11 from [now], and the one to come back to; [before] is the one kept from the last F11 into full screen. */
    fun toggled(now: WindowPlacement, before: WindowPlacement?): Pair<WindowPlacement, WindowPlacement?> = when (now) {
        WindowPlacement.Fullscreen -> (before ?: WindowPlacement.Floating) to null
        else -> WindowPlacement.Fullscreen to now
    }
}
