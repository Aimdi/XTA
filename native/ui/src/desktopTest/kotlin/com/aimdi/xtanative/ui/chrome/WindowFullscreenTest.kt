package com.aimdi.xtanative.ui.chrome

import androidx.compose.ui.window.WindowPlacement
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowFullscreenTest {
    @Test
    fun f11FillsTheScreenAndPutsTheWindowBackAsItWas() {
        assertEquals(WindowPlacement.Fullscreen to WindowPlacement.Maximized, WindowFullscreen.toggled(WindowPlacement.Maximized, null))
        assertEquals(WindowPlacement.Maximized to null, WindowFullscreen.toggled(WindowPlacement.Fullscreen, WindowPlacement.Maximized))
        assertEquals(WindowPlacement.Fullscreen to WindowPlacement.Floating, WindowFullscreen.toggled(WindowPlacement.Floating, WindowPlacement.Maximized))
    }

    @Test
    fun fullScreenEnteredSomeOtherWayGoesBackToAWindow() {
        assertEquals(WindowPlacement.Floating to null, WindowFullscreen.toggled(WindowPlacement.Fullscreen, null))
    }
}
