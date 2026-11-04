package com.aimdi.xtanative.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TabSwipesTest {
    @Test
    fun aSwipeTurnsOneTabWhenItGoesFarOrFastEnough() {
        assertEquals(2, TabSwipes.turned(1, 4, distance = -80f, velocity = 0f), "left leads on")
        assertEquals(0, TabSwipes.turned(1, 4, distance = 80f, velocity = 0f), "right leads back")
        assertEquals(1, TabSwipes.turned(1, 4, distance = -40f, velocity = -100f), "too short and too slow")
        assertEquals(2, TabSwipes.turned(1, 4, distance = -40f, velocity = -900f), "a flick")
        assertEquals(1, TabSwipes.turned(1, 4, distance = -10f, velocity = -900f), "a tap that moved")
        assertEquals(1, TabSwipes.turned(1, 4, distance = -80f, velocity = 300f), "released the other way")
        assertEquals(3, TabSwipes.turned(3, 4, distance = -80f, velocity = 0f), "no wrapping")
        assertEquals(0, TabSwipes.turned(1, 4, distance = -80f, velocity = 0f, rtl = true), "left leads back right to left")
        assertEquals(0, TabSwipes.turned(0, 1, distance = -80f, velocity = 0f), "one tab")
        assertEquals(5, TabSwipes.turned(5, 4, distance = -80f, velocity = 0f), "a tab that is not there")
    }

    @Test
    fun onlyASwipeMoreAcrossThanDownTurns() {
        assertEquals(1, TabSwipes.swiped(0, 3, dx = -90f, dy = 60f, velocity = 0f))
        assertEquals(0, TabSwipes.swiped(0, 3, dx = -90f, dy = 70f, velocity = 0f), "too steep")
        assertEquals(0, TabSwipes.swiped(0, 3, dx = -90f, dy = -80f, velocity = -900f), "a flick that is mostly a scroll")
    }

    @Test
    fun aSwipeThatTurnsBackLeavesTheTabs() {
        assertEquals(0f, TabSwipes.firstWay(0f, -10f), "not far enough to tell")
        assertEquals(-1f, TabSwipes.firstWay(0f, -30f))
        assertEquals(-1f, TabSwipes.firstWay(-1f, 30f), "the first way stays")
        assertTrue(TabSwipes.reversed(-1f, 5f))
        assertFalse(TabSwipes.reversed(-1f, -50f))
        assertFalse(TabSwipes.reversed(0f, 5f), "no way yet")
    }
}
