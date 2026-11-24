package com.aimdi.xtanative.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The going-back keys on a state that closes on Back (a search open in place, an earlier intro card): BackHandling on the desktop. */
class BackKeysTest {
    private fun action(key: DeskKey, alt: Boolean = false, focusInside: Boolean = false, canGoBack: Boolean = true, open: Boolean = false, backOpen: Boolean = true, ctrl: Boolean = false) =
        DesktopKeys.action(key, alt, ctrl, focusInside, canGoBack, somethingOpen = open, backOpen = backOpen)

    @Test
    fun escAltLeftAndBackCloseItBeforeGoingBack() {
        assertEquals(DeskAction.CLOSE, action(DeskKey.ESCAPE))
        assertEquals(DeskAction.CLOSE, action(DeskKey.LEFT, alt = true))
        assertEquals(DeskAction.CLOSE, action(DeskKey.BACK))
        assertEquals(DeskAction.CLOSE, action(DeskKey.ESCAPE, canGoBack = false), "on the first screen too (the intro's cards)")
        assertEquals(DeskAction.BACK, action(DeskKey.ESCAPE, backOpen = false))
    }

    @Test
    fun aFieldIsLeftFirst() {
        assertEquals(DeskAction.LEAVE_FIELD, action(DeskKey.ESCAPE, focusInside = true))
    }

    @Test
    fun itCoversNothingSoTheOtherKeysGoOn() {
        assertEquals(DeskAction.LINE_DOWN, action(DeskKey.DOWN))
        assertEquals(DeskAction.FIND_IN_PAGE, action(DeskKey.F, ctrl = true))
        assertEquals(DeskAction.NONE, action(DeskKey.W, ctrl = true), "Ctrl+W closes only what is open over the page")
        assertEquals(DeskAction.LEAVE_APP, DesktopKeys.mouseBack(canGoBack = false, somethingOpen = false))
    }

    @Test
    fun whatTheScreenHasOpenClosesFirstThenTheLastStateAdded() {
        var screenOpen = true
        val closed = mutableListOf<String>()
        val actions = DesktopActions()
        actions.take(Any(), refresh = {}, turn = {}, isOpen = { screenOpen }, close = { closed += "screen"; screenOpen = false })
        val search = Any()
        val layer = Any()
        actions.addBack(search) { closed += "search" }
        actions.addBack(layer) { closed += "layer" }
        assertTrue(actions.backOpen())
        actions.close()
        actions.close()
        assertEquals(listOf("screen", "layer"), closed)
        actions.removeBack(layer)
        actions.close()
        assertEquals(listOf("screen", "layer", "search"), closed)
        actions.removeBack(search)
        assertFalse(actions.backOpen())
    }

    @Test
    fun aStateEnabledAgainGoesOnTop() {
        val closed = mutableListOf<String>()
        val actions = DesktopActions()
        val a = Any()
        val b = Any()
        actions.addBack(a) { closed += "a" }
        actions.addBack(b) { closed += "b" }
        actions.addBack(a) { closed += "a" }
        actions.close()
        assertEquals(listOf("a"), closed)
    }
}
