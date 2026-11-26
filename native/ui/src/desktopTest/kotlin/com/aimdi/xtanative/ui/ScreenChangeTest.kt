package com.aimdi.xtanative.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/** The desktop crossfades its screens where a phone slides them, and neither moves while motion is reduced. */
class ScreenChangeTest {
    @Test
    fun theDesktopCrossfades() {
        assertEquals(ScreenChange.CROSSFADE, ScreenChange.of(desktop = true, reduced = false))
    }

    @Test
    fun aPhoneSlides() {
        assertEquals(ScreenChange.SLIDE, ScreenChange.of(desktop = false, reduced = false))
    }

    @Test
    fun reducedMotionChangesAtOnce() {
        assertEquals(ScreenChange.NONE, ScreenChange.of(desktop = true, reduced = true))
        assertEquals(ScreenChange.NONE, ScreenChange.of(desktop = false, reduced = true))
    }
}
