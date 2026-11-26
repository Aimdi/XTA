package com.aimdi.xtanative.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals

/** How a page of Home's bar comes in: only it is composed, fading in over the background. */
class BarPageFadeTest {
    @Test
    fun anotherPageFadesIn() = assertEquals(0f, BarPageFade.from("feed", "saved", reduced = false))

    @Test
    fun thePageShownStaysAsItIs() = assertEquals(1f, BarPageFade.from("feed", "feed", reduced = false))

    @Test
    fun reducedMotionShowsItAtOnce() = assertEquals(1f, BarPageFade.from("feed", "saved", reduced = true))
}
